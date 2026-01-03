package flowingfluidsfixes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptive Threshold Manager for Flowing Fluids
 * Step 34: Implement adaptive thresholds that adjust based on server load
 */
public class AdaptiveThresholdManager {
    private static final Logger LOGGER = LogManager.getLogger(AdaptiveThresholdManager.class);
    
    // Current thresholds
    private static volatile int updateThreshold = 250;
    private static volatile int deferralThreshold = 50;
    private static volatile int emergencyThreshold = 25;
    private static volatile double tpsTargetThreshold = 16.0;
    
    // Threshold bounds
    private static final int MIN_UPDATE_THRESHOLD = 25;
    private static final int MAX_UPDATE_THRESHOLD = 1000;
    private static final int MIN_DEFERRAL_THRESHOLD = 5;
    private static final int MAX_DEFERRAL_THRESHOLD = 250;
    
    // Adjustment tracking
    private static final AtomicInteger adjustmentCount = new AtomicInteger(0);
    private static volatile String lastAdjustmentReason = "None";
    
    // Smoothing factor for gradual adjustments
    private static final double SMOOTHING_FACTOR = 0.3;
    
    /**
     * Adjust thresholds based on current server performance
     */
    public static void adjustThresholds(double currentTPS, int currentUpdates, int deferredQueueSize) {
        double targetTPS = tpsTargetThreshold;
        
        // Calculate TPS delta
        double tpsDelta = currentTPS - targetTPS;
        
        if (tpsDelta < -3.0) {
            // TPS significantly below target - reduce thresholds
            reduceThresholds("TPS significantly below target");
        } else if (tpsDelta < -1.0) {
            // TPS slightly below target - gradual reduction
            gradualReduceThresholds("TPS slightly below target");
        } else if (tpsDelta > 1.0 && deferredQueueSize > deferralThreshold) {
            // TPS good but queue building up - increase capacity
            increaseThresholds("Good TPS with queue buildup");
        } else if (tpsDelta > 2.0) {
            // TPS excellent - can increase thresholds
            gradualIncreaseThresholds("TPS above target");
        }
        
        // Check deferred queue
        if (deferredQueueSize > deferralThreshold * 2) {
            // Queue is too large - need to process more
            increaseDeferralProcessing("Large deferred queue");
        }
    }
    
    /**
     * Reduce all thresholds significantly
     */
    private static void reduceThresholds(String reason) {
        int newUpdateThreshold = Math.max(MIN_UPDATE_THRESHOLD, 
            (int)(updateThreshold * (1 - SMOOTHING_FACTOR)));
        int newDeferralThreshold = Math.max(MIN_DEFERRAL_THRESHOLD,
            (int)(deferralThreshold * (1 - SMOOTHING_FACTOR)));
        
        if (newUpdateThreshold != updateThreshold || newDeferralThreshold != deferralThreshold) {
            updateThreshold = newUpdateThreshold;
            deferralThreshold = newDeferralThreshold;
            lastAdjustmentReason = reason;
            adjustmentCount.incrementAndGet();
            LOGGER.debug("Reduced thresholds: updates={}, deferral={} ({})", 
                        updateThreshold, deferralThreshold, reason);
        }
    }
    
    /**
     * Gradually reduce thresholds
     */
    private static void gradualReduceThresholds(String reason) {
        int newUpdateThreshold = Math.max(MIN_UPDATE_THRESHOLD,
            (int)(updateThreshold * (1 - SMOOTHING_FACTOR * 0.5)));
        
        if (newUpdateThreshold != updateThreshold) {
            updateThreshold = newUpdateThreshold;
            lastAdjustmentReason = reason;
            adjustmentCount.incrementAndGet();
            LOGGER.debug("Gradually reduced update threshold to {} ({})", updateThreshold, reason);
        }
    }
    
    /**
     * Increase thresholds
     */
    private static void increaseThresholds(String reason) {
        int newUpdateThreshold = Math.min(MAX_UPDATE_THRESHOLD,
            (int)(updateThreshold * (1 + SMOOTHING_FACTOR)));
        int newDeferralThreshold = Math.min(MAX_DEFERRAL_THRESHOLD,
            (int)(deferralThreshold * (1 + SMOOTHING_FACTOR)));
        
        if (newUpdateThreshold != updateThreshold || newDeferralThreshold != deferralThreshold) {
            updateThreshold = newUpdateThreshold;
            deferralThreshold = newDeferralThreshold;
            lastAdjustmentReason = reason;
            adjustmentCount.incrementAndGet();
            LOGGER.debug("Increased thresholds: updates={}, deferral={} ({})",
                        updateThreshold, deferralThreshold, reason);
        }
    }
    
    /**
     * Gradually increase thresholds
     */
    private static void gradualIncreaseThresholds(String reason) {
        int newUpdateThreshold = Math.min(MAX_UPDATE_THRESHOLD,
            (int)(updateThreshold * (1 + SMOOTHING_FACTOR * 0.5)));
        
        if (newUpdateThreshold != updateThreshold) {
            updateThreshold = newUpdateThreshold;
            lastAdjustmentReason = reason;
            adjustmentCount.incrementAndGet();
            LOGGER.debug("Gradually increased update threshold to {} ({})", updateThreshold, reason);
        }
    }
    
    /**
     * Increase deferral processing capacity
     */
    private static void increaseDeferralProcessing(String reason) {
        int newDeferralThreshold = Math.min(MAX_DEFERRAL_THRESHOLD,
            (int)(deferralThreshold * 1.5));
        
        if (newDeferralThreshold != deferralThreshold) {
            deferralThreshold = newDeferralThreshold;
            lastAdjustmentReason = reason;
            adjustmentCount.incrementAndGet();
            LOGGER.debug("Increased deferral threshold to {} ({})", deferralThreshold, reason);
        }
    }
    
    // Getters
    
    public static int getUpdateThreshold() {
        return updateThreshold;
    }
    
    public static int getDeferralThreshold() {
        return deferralThreshold;
    }
    
    public static int getEmergencyThreshold() {
        return emergencyThreshold;
    }
    
    public static double getTpsTargetThreshold() {
        return tpsTargetThreshold;
    }
    
    /**
     * Set TPS target
     */
    public static void setTpsTargetThreshold(double target) {
        tpsTargetThreshold = Math.max(10.0, Math.min(target, 20.0));
        LOGGER.info("TPS target threshold set to {}", tpsTargetThreshold);
    }
    
    /**
     * Reset to defaults
     */
    public static void resetToDefaults() {
        updateThreshold = 250;
        deferralThreshold = 50;
        emergencyThreshold = 25;
        tpsTargetThreshold = 16.0;
        adjustmentCount.set(0);
        lastAdjustmentReason = "Reset to defaults";
        LOGGER.info("Adaptive thresholds reset to defaults");
    }
    
    /**
     * Get status summary
     */
    public static String getStatusSummary() {
        return String.format("Thresholds: update=%d, deferral=%d, emergency=%d, adjustments=%d, last=%s",
            updateThreshold, deferralThreshold, emergencyThreshold, 
            adjustmentCount.get(), lastAdjustmentReason);
    }
}
