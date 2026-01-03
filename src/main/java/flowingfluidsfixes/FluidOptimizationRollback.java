package flowingfluidsfixes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rollback Mechanism for Flowing Fluids Optimizations
 * Step 25: Implement rollback mechanism to revert to default behavior if optimizations cause instability
 */
public class FluidOptimizationRollback {
    private static final Logger LOGGER = LogManager.getLogger(FluidOptimizationRollback.class);
    
    // Rollback state
    private static volatile boolean rollbackActive = false;
    private static volatile String rollbackReason = "None";
    private static volatile long rollbackTimestamp = 0;
    
    // Instability detection
    private static final AtomicInteger consecutiveLowTPSCount = new AtomicInteger(0);
    private static final AtomicInteger emergencyActivationCount = new AtomicInteger(0);
    private static final AtomicInteger rollbackCount = new AtomicInteger(0);
    
    // Thresholds
    private static final int LOW_TPS_THRESHOLD_COUNT = 60;  // 60 consecutive low TPS ticks
    private static final int EMERGENCY_THRESHOLD_COUNT = 5; // 5 emergency activations
    private static final double LOW_TPS_VALUE = 10.0;
    
    // Cooldown
    private static final long ROLLBACK_COOLDOWN_MS = 300000; // 5 minutes
    
    /**
     * Check for instability and trigger rollback if necessary
     * Call this from server tick
     */
    public static void checkForInstability() {
        if (rollbackActive) {
            // Check if we can exit rollback mode
            checkRollbackRecovery();
            return;
        }
        
        double currentTPS = PerformanceMonitor.getAverageTPS();
        
        // Track consecutive low TPS
        if (currentTPS < LOW_TPS_VALUE) {
            consecutiveLowTPSCount.incrementAndGet();
        } else {
            consecutiveLowTPSCount.set(0);
        }
        
        // Track emergency mode activations
        if (EmergencyPerformanceMode.isEmergencyMode()) {
            emergencyActivationCount.incrementAndGet();
        }
        
        // Check if rollback is needed
        if (shouldTriggerRollback()) {
            triggerRollback();
        }
    }
    
    /**
     * Determine if rollback should be triggered
     */
    private static boolean shouldTriggerRollback() {
        // Too many consecutive low TPS ticks
        if (consecutiveLowTPSCount.get() >= LOW_TPS_THRESHOLD_COUNT) {
            return true;
        }
        
        // Too many emergency mode activations
        if (emergencyActivationCount.get() >= EMERGENCY_THRESHOLD_COUNT) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Trigger rollback to default behavior
     */
    private static void triggerRollback() {
        if (rollbackActive) return;
        
        rollbackActive = true;
        rollbackTimestamp = System.currentTimeMillis();
        rollbackCount.incrementAndGet();
        
        // Determine reason
        if (consecutiveLowTPSCount.get() >= LOW_TPS_THRESHOLD_COUNT) {
            rollbackReason = String.format("Consecutive low TPS (%d ticks)", consecutiveLowTPSCount.get());
        } else if (emergencyActivationCount.get() >= EMERGENCY_THRESHOLD_COUNT) {
            rollbackReason = String.format("Frequent emergency mode (%d activations)", emergencyActivationCount.get());
        } else {
            rollbackReason = "Manual trigger";
        }
        
        LOGGER.error("=== OPTIMIZATION ROLLBACK TRIGGERED ===");
        LOGGER.error("Reason: {}", rollbackReason);
        LOGGER.error("Reverting to minimal optimization mode");
        
        // Apply rollback settings
        applyRollbackSettings();
        
        // Reset counters
        consecutiveLowTPSCount.set(0);
        emergencyActivationCount.set(0);
    }
    
    /**
     * Apply rollback settings (minimal optimization)
     */
    private static void applyRollbackSettings() {
        // Disable aggressive optimization
        FlowingFluidsAPIIntegration.setDynamicRateControlEnabled(false);
        
        // Set optimization to minimal
        // Note: This would ideally change the config, but we apply runtime changes
        LOGGER.info("Rollback: Disabled dynamic rate control");
        LOGGER.info("Rollback: Processing all fluid updates without optimization");
    }
    
    /**
     * Check if we can recover from rollback mode
     */
    private static void checkRollbackRecovery() {
        // Check if cooldown has passed
        long elapsed = System.currentTimeMillis() - rollbackTimestamp;
        if (elapsed < ROLLBACK_COOLDOWN_MS) {
            return; // Still in cooldown
        }
        
        // Check if TPS has recovered
        double currentTPS = PerformanceMonitor.getAverageTPS();
        if (currentTPS >= 18.0) {
            recoverFromRollback();
        }
    }
    
    /**
     * Recover from rollback mode
     */
    private static void recoverFromRollback() {
        rollbackActive = false;
        rollbackReason = "Recovered";
        
        LOGGER.info("=== OPTIMIZATION ROLLBACK RECOVERED ===");
        LOGGER.info("TPS stable, re-enabling optimizations");
        
        // Re-enable optimizations
        FlowingFluidsAPIIntegration.setDynamicRateControlEnabled(true);
    }
    
    /**
     * Manually trigger rollback
     */
    public static void manualRollback(String reason) {
        rollbackReason = "Manual: " + reason;
        triggerRollback();
    }
    
    /**
     * Manually recover from rollback
     */
    public static void manualRecover() {
        if (rollbackActive) {
            recoverFromRollback();
        }
    }
    
    /**
     * Check if rollback is active
     */
    public static boolean isRollbackActive() {
        return rollbackActive;
    }
    
    /**
     * Get rollback reason
     */
    public static String getRollbackReason() {
        return rollbackReason;
    }
    
    /**
     * Get rollback count
     */
    public static int getRollbackCount() {
        return rollbackCount.get();
    }
    
    /**
     * Get status summary
     */
    public static String getStatusSummary() {
        return String.format("Rollback: %s (reason: %s, count: %d, low TPS streak: %d, emergency count: %d)",
            rollbackActive ? "ACTIVE" : "inactive", rollbackReason, rollbackCount.get(),
            consecutiveLowTPSCount.get(), emergencyActivationCount.get());
    }
    
    /**
     * Reset all counters
     */
    public static void resetCounters() {
        consecutiveLowTPSCount.set(0);
        emergencyActivationCount.set(0);
        LOGGER.info("Rollback counters reset");
    }
}
