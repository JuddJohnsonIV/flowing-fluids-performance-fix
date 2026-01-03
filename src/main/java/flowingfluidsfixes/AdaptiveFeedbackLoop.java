package flowingfluidsfixes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptive Feedback Loop System for Flowing Fluids Optimization
 * Step 36: Develop feedback loop to automatically adjust optimization parameters
 * based on real-time server performance data
 */
public class AdaptiveFeedbackLoop {
    private static final Logger LOGGER = LogManager.getLogger(AdaptiveFeedbackLoop.class);
    
    // Feedback loop state
    private static volatile boolean feedbackEnabled = true;
    private static volatile int adjustmentCycle = 0;
    
    // Performance history for trend analysis
    private static final List<PerformanceSnapshot> performanceHistory = new ArrayList<>();
    private static final int HISTORY_SIZE = 60; // 60 samples (1 minute at 1 sample/sec)
    
    // Adjustment thresholds
    private static final double TPS_EXCELLENT = 19.5;
    private static final double TPS_GOOD = 17.0;
    private static final double TPS_FAIR = 14.0;
    private static final double TPS_POOR = 10.0;
    
    // Current optimization parameters (can be adjusted by feedback loop)
    private static volatile int maxUpdatesPerTick = 500;
    private static volatile int criticalDistance = 16;
    private static volatile int normalDistance = 64;
    private static volatile int delayMultiplier = 1;
    
    // Adjustment tracking
    private static final AtomicInteger adjustmentsMade = new AtomicInteger(0);
    private static volatile String lastAdjustment = "None";
    
    /**
     * Collect performance snapshot - call once per second
     */
    public static void collectPerformanceData() {
        if (!feedbackEnabled) return;
        
        double tps = PerformanceMonitor.getAverageTPS();
        long fluidUpdates = FlowingFluidsPerformanceMonitor.getFluidUpdatesPerSecond();
        int deferredQueue = FluidTickScheduler.getDeferredQueueSize();
        boolean emergencyActive = EmergencyPerformanceMode.isEmergencyMode();
        
        PerformanceSnapshot snapshot = new PerformanceSnapshot(
            System.currentTimeMillis(), tps, fluidUpdates, deferredQueue, emergencyActive
        );
        
        synchronized (performanceHistory) {
            performanceHistory.add(snapshot);
            if (performanceHistory.size() > HISTORY_SIZE) {
                performanceHistory.remove(0);
            }
        }
        
        // Analyze and adjust every 10 seconds
        adjustmentCycle++;
        if (adjustmentCycle >= 10) {
            adjustmentCycle = 0;
            analyzeAndAdjust();
        }
    }
    
    /**
     * Analyze performance trends and adjust parameters
     */
    private static void analyzeAndAdjust() {
        if (performanceHistory.size() < 10) return; // Not enough data
        
        PerformanceTrend trend = analyzeTrend();
        
        LOGGER.debug("Performance trend: avg TPS={}, trend={}, updates/sec={}", 
                    String.format("%.2f", trend.averageTPS), trend.tpsTrend, trend.averageUpdates);
        
        // Make adjustments based on trend
        if (trend.averageTPS >= TPS_EXCELLENT && trend.tpsTrend >= 0) {
            // Performance is excellent - can increase optimization aggressiveness
            increaseOptimization();
        } else if (trend.averageTPS < TPS_POOR || trend.tpsTrend < -0.5) {
            // Performance is poor or declining rapidly - reduce optimization
            reduceOptimization();
        } else if (trend.averageTPS >= TPS_GOOD && trend.tpsTrend >= 0) {
            // Performance is good - maintain current settings
            LOGGER.debug("Performance stable, maintaining current settings");
        } else if (trend.averageTPS >= TPS_FAIR) {
            // Performance is fair - slight adjustments
            finetuneOptimization(trend);
        }
    }
    
    /**
     * Analyze performance trend from history
     */
    private static PerformanceTrend analyzeTrend() {
        List<PerformanceSnapshot> recentHistory;
        synchronized (performanceHistory) {
            recentHistory = new ArrayList<>(performanceHistory);
        }
        
        if (recentHistory.isEmpty()) {
            return new PerformanceTrend(20.0, 0.0, 0, 0);
        }
        
        double avgTPS = recentHistory.stream().mapToDouble(s -> s.tps).average().orElse(20.0);
        long avgUpdates = (long) recentHistory.stream().mapToLong(s -> s.fluidUpdates).average().orElse(0);
        int avgDeferred = (int) recentHistory.stream().mapToInt(s -> s.deferredQueue).average().orElse(0);
        
        // Calculate TPS trend (positive = improving, negative = declining)
        double tpsTrend = 0.0;
        if (recentHistory.size() >= 5) {
            double firstHalfAvg = recentHistory.subList(0, recentHistory.size() / 2)
                .stream().mapToDouble(s -> s.tps).average().orElse(0);
            double secondHalfAvg = recentHistory.subList(recentHistory.size() / 2, recentHistory.size())
                .stream().mapToDouble(s -> s.tps).average().orElse(0);
            tpsTrend = secondHalfAvg - firstHalfAvg;
        }
        
        return new PerformanceTrend(avgTPS, tpsTrend, avgUpdates, avgDeferred);
    }
    
    /**
     * Increase optimization aggressiveness (server can handle more)
     */
    private static void increaseOptimization() {
        boolean adjusted = false;
        
        if (maxUpdatesPerTick < 1000) {
            maxUpdatesPerTick = Math.min(maxUpdatesPerTick + 100, 1000);
            adjusted = true;
        }
        if (criticalDistance < 24) {
            criticalDistance = Math.min(criticalDistance + 2, 24);
            adjusted = true;
        }
        if (normalDistance < 96) {
            normalDistance = Math.min(normalDistance + 8, 96);
            adjusted = true;
        }
        
        if (adjusted) {
            lastAdjustment = "Increased (TPS excellent)";
            adjustmentsMade.incrementAndGet();
            LOGGER.info("Feedback loop: Increased optimization - maxUpdates={}, critDist={}, normDist={}",
                       maxUpdatesPerTick, criticalDistance, normalDistance);
        }
    }
    
    /**
     * Reduce optimization aggressiveness (server struggling)
     */
    private static void reduceOptimization() {
        boolean adjusted = false;
        
        if (maxUpdatesPerTick > 100) {
            maxUpdatesPerTick = Math.max(maxUpdatesPerTick - 100, 100);
            adjusted = true;
        }
        if (criticalDistance > 8) {
            criticalDistance = Math.max(criticalDistance - 2, 8);
            adjusted = true;
        }
        if (normalDistance > 32) {
            normalDistance = Math.max(normalDistance - 8, 32);
            adjusted = true;
        }
        if (delayMultiplier < 4) {
            delayMultiplier = Math.min(delayMultiplier + 1, 4);
            adjusted = true;
        }
        
        if (adjusted) {
            lastAdjustment = "Reduced (TPS poor)";
            adjustmentsMade.incrementAndGet();
            LOGGER.warn("Feedback loop: Reduced optimization - maxUpdates={}, critDist={}, normDist={}, delay={}x",
                       maxUpdatesPerTick, criticalDistance, normalDistance, delayMultiplier);
        }
    }
    
    /**
     * Fine-tune optimization based on specific trend data
     */
    private static void finetuneOptimization(PerformanceTrend trend) {
        // If deferred queue is growing, increase processing
        if (trend.averageDeferred > 500) {
            maxUpdatesPerTick = Math.min(maxUpdatesPerTick + 50, 800);
            lastAdjustment = "Increased processing (queue growing)";
            adjustmentsMade.incrementAndGet();
            LOGGER.info("Feedback loop: Increased processing due to growing deferred queue");
        }
        
        // If updates per second is very high, increase delay
        if (trend.averageUpdates > 5000) {
            delayMultiplier = Math.min(delayMultiplier + 1, 3);
            lastAdjustment = "Increased delay (high update rate)";
            adjustmentsMade.incrementAndGet();
            LOGGER.info("Feedback loop: Increased delay due to high update rate");
        }
    }
    
    /**
     * Get current max updates per tick (for use by other systems)
     */
    public static int getMaxUpdatesPerTick() {
        return maxUpdatesPerTick;
    }
    
    /**
     * Get current critical distance
     */
    public static int getCriticalDistance() {
        return criticalDistance;
    }
    
    /**
     * Get current normal distance
     */
    public static int getNormalDistance() {
        return normalDistance;
    }
    
    /**
     * Get current delay multiplier
     */
    public static int getDelayMultiplier() {
        return delayMultiplier;
    }
    
    /**
     * Enable/disable feedback loop
     */
    public static void setFeedbackEnabled(boolean enabled) {
        feedbackEnabled = enabled;
        LOGGER.info("Adaptive feedback loop {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Check if feedback loop is enabled
     */
    public static boolean isFeedbackEnabled() {
        return feedbackEnabled;
    }
    
    /**
     * Get status summary
     */
    public static String getStatusSummary() {
        return String.format("Feedback Loop: %s, adjustments=%d, last=%s, params=[max=%d, crit=%d, norm=%d, delay=%dx]",
            feedbackEnabled ? "enabled" : "disabled", adjustmentsMade.get(), lastAdjustment,
            maxUpdatesPerTick, criticalDistance, normalDistance, delayMultiplier);
    }
    
    /**
     * Reset to default parameters
     */
    public static void resetToDefaults() {
        maxUpdatesPerTick = 500;
        criticalDistance = 16;
        normalDistance = 64;
        delayMultiplier = 1;
        performanceHistory.clear();
        adjustmentsMade.set(0);
        lastAdjustment = "Reset to defaults";
        LOGGER.info("Feedback loop parameters reset to defaults");
    }
    
    /**
     * Performance snapshot record
     */
    private record PerformanceSnapshot(long timestamp, double tps, long fluidUpdates, 
                                       int deferredQueue, boolean emergencyActive) {}
    
    /**
     * Performance trend analysis result
     */
    private record PerformanceTrend(double averageTPS, double tpsTrend, 
                                    long averageUpdates, int averageDeferred) {}
}
