package flowingfluidsfixes;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced Performance Monitor for Flowing Fluids Integration
 * Tracks optimization impact and provides detailed metrics
 */
@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlowingFluidsPerformanceMonitor {
    private static final Logger LOGGER = LogManager.getLogger(FlowingFluidsPerformanceMonitor.class);
    
    // Flowing Fluids specific metrics
    private static final AtomicLong totalFlowingFluidsUpdates = new AtomicLong(0);
    private static final AtomicLong optimizedFlowingFluidsUpdates = new AtomicLong(0);
    private static final AtomicLong deferredFlowingFluidsUpdates = new AtomicLong(0);
    private static final AtomicLong criticalFlowingFluidsUpdates = new AtomicLong(0);
    private static final AtomicLong skippedFlowingFluidsUpdates = new AtomicLong(0); // Edge fluids skipped for mob pathfinding protection
    
    // Performance impact tracking
    private static final List<Double> tpsBeforeOptimization = Collections.synchronizedList(new ArrayList<>());
    private static final List<Double> tpsAfterOptimization = Collections.synchronizedList(new ArrayList<>());
    private static final List<Long> fluidUpdateTimeHistory = Collections.synchronizedList(new ArrayList<>());
    
    // Optimization level performance tracking
    private static final Map<FlowingFluidsOptimizationConfig.OptimizationLevel, PerformanceMetrics> optimizationMetrics = 
        Collections.synchronizedMap(new HashMap<>());
    
    // Detailed metrics
    private static volatile long lastOptimizationChange = System.currentTimeMillis();
    private static volatile FlowingFluidsOptimizationConfig.OptimizationLevel currentOptimizationLevel = 
        FlowingFluidsOptimizationConfig.OptimizationLevel.BALANCED;
    
    // Performance windows
    private static final int PERFORMANCE_WINDOW_SIZE = 100;
    private static final int METRICS_COLLECTION_INTERVAL = 20; // Collect detailed metrics every N ticks
    
    // TPS Impact Analysis
    private static volatile double baselineTPS = 20.0;
    private static volatile double currentTPSImpact = 0.0;
    private static volatile long fluidUpdatesPerSecond = 0;
    private static volatile long lastSecondUpdateCount = 0;
    private static volatile long lastSecondTimestamp = System.currentTimeMillis();
    
    // Fluid update frequency tracking
    private static final List<Long> updateFrequencyHistory = Collections.synchronizedList(new ArrayList<>());
    private static final int FREQUENCY_HISTORY_SIZE = 60; // Track last 60 seconds
    
    private static int tickCounter = 0;
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        tickCounter++;
        
        // Track fluid updates per second
        trackFluidUpdateFrequency();
        
        // Calculate TPS impact
        calculateTPSImpact();
        
        // Collect detailed metrics periodically
        if (tickCounter % METRICS_COLLECTION_INTERVAL == 0) {
            collectDetailedMetrics();
        }
        
        // Track optimization level changes
        trackOptimizationChanges();
    }
    
    /**
     * Track fluid update frequency over time
     */
    private static void trackFluidUpdateFrequency() {
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastSecondTimestamp;
        
        if (elapsed >= 1000) { // Every second
            long currentUpdates = totalFlowingFluidsUpdates.get();
            fluidUpdatesPerSecond = currentUpdates - lastSecondUpdateCount;
            lastSecondUpdateCount = currentUpdates;
            lastSecondTimestamp = currentTime;
            
            // Track history
            updateFrequencyHistory.add(fluidUpdatesPerSecond);
            if (updateFrequencyHistory.size() > FREQUENCY_HISTORY_SIZE) {
                updateFrequencyHistory.remove(0);
            }
        }
    }
    
    /**
     * Calculate TPS impact from fluid optimizations
     */
    private static void calculateTPSImpact() {
        double currentTPS = PerformanceMonitor.getAverageTPS();
        
        // Update baseline TPS when server is running smoothly
        if (currentTPS > 19.5 && fluidUpdatesPerSecond < 100) {
            baselineTPS = Math.max(baselineTPS, currentTPS);
        }
        
        // Calculate impact as percentage difference from baseline
        if (baselineTPS > 0) {
            currentTPSImpact = ((baselineTPS - currentTPS) / baselineTPS) * 100.0;
        }
    }
    
    /**
     * Get current TPS impact percentage
     */
    public static double getCurrentTPSImpact() {
        return currentTPSImpact;
    }
    
    /**
     * Get fluid updates per second
     */
    public static long getFluidUpdatesPerSecond() {
        return fluidUpdatesPerSecond;
    }
    
    /**
     * Get average fluid updates per second over the tracking period
     */
    public static double getAverageFluidUpdatesPerSecond() {
        if (updateFrequencyHistory.isEmpty()) return 0;
        return updateFrequencyHistory.stream().mapToLong(Long::longValue).average().orElse(0);
    }
    
    /**
     * Get peak fluid updates per second
     */
    public static long getPeakFluidUpdatesPerSecond() {
        if (updateFrequencyHistory.isEmpty()) return 0;
        return updateFrequencyHistory.stream().mapToLong(Long::longValue).max().orElse(0);
    }
    
    /**
     * Record a Flowing Fluids update
     */
    public static void recordFlowingFluidsUpdate(UpdateType type) {
        totalFlowingFluidsUpdates.incrementAndGet();
        
        switch (type) {
            case OPTIMIZED -> optimizedFlowingFluidsUpdates.incrementAndGet();
            case DEFERRED -> deferredFlowingFluidsUpdates.incrementAndGet();
            case CRITICAL -> criticalFlowingFluidsUpdates.incrementAndGet();
            case SKIPPED -> skippedFlowingFluidsUpdates.incrementAndGet();
        }
        
        // Record timing for performance analysis
        if (type != UpdateType.CRITICAL) {
            fluidUpdateTimeHistory.add(System.nanoTime());
            if (fluidUpdateTimeHistory.size() > PERFORMANCE_WINDOW_SIZE) {
                fluidUpdateTimeHistory.remove(0);
            }
        }
    }
    
    /**
     * Record TPS before optimization was applied
     */
    public static void recordTPSBeforeOptimization(double tps) {
        tpsBeforeOptimization.add(tps);
        if (tpsBeforeOptimization.size() > PERFORMANCE_WINDOW_SIZE) {
            tpsBeforeOptimization.remove(0);
        }
    }
    
    /**
     * Record TPS after optimization was applied
     */
    public static void recordTPSAfterOptimization(double tps) {
        tpsAfterOptimization.add(tps);
        if (tpsAfterOptimization.size() > PERFORMANCE_WINDOW_SIZE) {
            tpsAfterOptimization.remove(0);
        }
    }
    
    /**
     * Track optimization level changes
     */
    private static void trackOptimizationChanges() {
        var newLevel = FlowingFluidsOptimizationConfig.optimizationLevel.get();
        if (newLevel != currentOptimizationLevel) {
            // Record performance metrics for the previous level
            recordOptimizationLevelMetrics(currentOptimizationLevel);
            
            // Update current level
            currentOptimizationLevel = newLevel;
            lastOptimizationChange = System.currentTimeMillis();
            
            LOGGER.info("Optimization level changed to: {}", newLevel);
            
            // Record TPS before applying new optimization
            recordTPSBeforeOptimization(PerformanceMonitor.getAverageTPS());
        }
    }
    
    /**
     * Record performance metrics for a specific optimization level
     */
    private static void recordOptimizationLevelMetrics(FlowingFluidsOptimizationConfig.OptimizationLevel level) {
        long duration = System.currentTimeMillis() - lastOptimizationChange;
        if (duration > 0) {
            PerformanceMetrics metrics = optimizationMetrics.computeIfAbsent(level, k -> new PerformanceMetrics());
            metrics.addSample(duration, PerformanceMonitor.getAverageTPS(), 
                             PerformanceMonitor.getAverageFluidUpdates());
        }
    }
    
    /**
     * Collect detailed performance metrics
     */
    private static void collectDetailedMetrics() {
        if (!FlowingFluidsOptimizationConfig.enablePerformanceMetrics.get()) {
            return;
        }
        
        double currentTPS = PerformanceMonitor.getAverageTPS();
        recordTPSAfterOptimization(currentTPS);
        
        // Log detailed metrics if debug logging is enabled
        if (FlowingFluidsOptimizationConfig.enableDetailedLogging.get()) {
            logDetailedMetrics();
        }
    }
    
    /**
     * Log detailed performance metrics
     */
    private static void logDetailedMetrics() {
        LOGGER.info("=== Flowing Fluids Performance Metrics ===");
        LOGGER.info("Optimization Level: {}", currentOptimizationLevel);
        LOGGER.info("Total Flowing Fluids Updates: {}", totalFlowingFluidsUpdates.get());
        LOGGER.info("Optimized Updates: {}", optimizedFlowingFluidsUpdates.get());
        LOGGER.info("Deferred Updates: {}", deferredFlowingFluidsUpdates.get());
        LOGGER.info("Critical Updates: {}", criticalFlowingFluidsUpdates.get());
        
        double optimizationRate = totalFlowingFluidsUpdates.get() > 0 ? 
            (optimizedFlowingFluidsUpdates.get() * 100.0 / totalFlowingFluidsUpdates.get()) : 0;
        double deferralRate = totalFlowingFluidsUpdates.get() > 0 ? 
            (deferredFlowingFluidsUpdates.get() * 100.0 / totalFlowingFluidsUpdates.get()) : 0;
        
        LOGGER.info("Optimization Rate: {:.2f}%", optimizationRate);
        LOGGER.info("Deferral Rate: {:.2f}%", deferralRate);
        LOGGER.info("Current TPS: {:.2f}", PerformanceMonitor.getAverageTPS());
        LOGGER.info("Average Fluid Updates: {}", PerformanceMonitor.getAverageFluidUpdates());
        
        // Show optimization impact
        if (!tpsBeforeOptimization.isEmpty() && !tpsAfterOptimization.isEmpty()) {
            double beforeTPS = tpsBeforeOptimization.get(tpsBeforeOptimization.size() - 1);
            double afterTPS = tpsAfterOptimization.get(tpsAfterOptimization.size() - 1);
            double tpsImprovement = ((afterTPS - beforeTPS) / beforeTPS) * 100;
            LOGGER.info("TPS Impact: {:.2f}% ({:.2f} -> {:.2f})", tpsImprovement, beforeTPS, afterTPS);
        }
        
        LOGGER.info("==========================================");
    }
    
    /**
     * Get comprehensive performance report
     */
    public static Map<String, Object> getPerformanceReport() {
        Map<String, Object> report = new HashMap<>();
        
        // Basic metrics
        report.put("totalFlowingFluidsUpdates", totalFlowingFluidsUpdates.get());
        report.put("optimizedFlowingFluidsUpdates", optimizedFlowingFluidsUpdates.get());
        report.put("deferredFlowingFluidsUpdates", deferredFlowingFluidsUpdates.get());
        report.put("criticalFlowingFluidsUpdates", criticalFlowingFluidsUpdates.get());
        report.put("skippedFlowingFluidsUpdates", skippedFlowingFluidsUpdates.get()); // Edge fluids skipped
        
        // Rates
        long total = totalFlowingFluidsUpdates.get();
        report.put("optimizationRate", total > 0 ? (optimizedFlowingFluidsUpdates.get() * 100.0 / total) : 0);
        report.put("deferralRate", total > 0 ? (deferredFlowingFluidsUpdates.get() * 100.0 / total) : 0);
        report.put("criticalRate", total > 0 ? (criticalFlowingFluidsUpdates.get() * 100.0 / total) : 0);
        report.put("skippedRate", total > 0 ? (skippedFlowingFluidsUpdates.get() * 100.0 / total) : 0);
        
        // Current performance
        report.put("currentTPS", PerformanceMonitor.getAverageTPS());
        report.put("currentFluidUpdates", PerformanceMonitor.getAverageFluidUpdates());
        report.put("currentOptimizationLevel", currentOptimizationLevel.name());
        
        // Optimization level metrics
        Map<String, Object> levelMetrics = new HashMap<>();
        optimizationMetrics.forEach((level, metrics) -> {
            levelMetrics.put(level.name(), metrics.getSummary());
        });
        report.put("optimizationLevelMetrics", levelMetrics);
        
        // Performance impact
        if (!tpsBeforeOptimization.isEmpty() && !tpsAfterOptimization.isEmpty()) {
            double beforeTPS = tpsBeforeOptimization.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double afterTPS = tpsAfterOptimization.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double improvement = beforeTPS > 0 ? ((afterTPS - beforeTPS) / beforeTPS) * 100 : 0;
            report.put("tpsImprovement", improvement);
            report.put("tpsBeforeOptimization", beforeTPS);
            report.put("tpsAfterOptimization", afterTPS);
        }
        
        // Add TPS impact metrics
        report.put("currentTPSImpact", currentTPSImpact);
        report.put("baselineTPS", baselineTPS);
        report.put("fluidUpdatesPerSecond", fluidUpdatesPerSecond);
        report.put("averageFluidUpdatesPerSecond", getAverageFluidUpdatesPerSecond());
        report.put("peakFluidUpdatesPerSecond", getPeakFluidUpdatesPerSecond());
        report.put("deferredQueueSize", FluidTickScheduler.getDeferredQueueSize());
        report.put("adaptiveUpdateLimit", FluidTickScheduler.getAdaptiveMaxUpdatesPerTick());
        
        // Edge fluid and cache statistics for mob pathfinding protection
        report.put("edgeFluidsSkipped", FluidTickScheduler.getEdgeFluidsSkipped());
        report.put("heavyFlowCacheHits", FluidTickScheduler.getHeavyFlowCacheHits());
        report.put("cacheStats", FluidTickScheduler.getCacheStats());
        
        return report;
    }
    
    /**
     * Get optimization effectiveness analysis
     */
    public static String getOptimizationEffectiveness() {
        long total = totalFlowingFluidsUpdates.get();
        if (total == 0) {
            return "No Flowing Fluids updates recorded yet";
        }
        
        double optimizationRate = (optimizedFlowingFluidsUpdates.get() * 100.0 / total);
        double deferralRate = (deferredFlowingFluidsUpdates.get() * 100.0 / total);
        double criticalRate = (criticalFlowingFluidsUpdates.get() * 100.0 / total);
        double skippedRate = (skippedFlowingFluidsUpdates.get() * 100.0 / total);
        
        StringBuilder analysis = new StringBuilder();
        analysis.append("Flowing Fluids Optimization Analysis:\n");
        analysis.append(String.format("  Total Updates: %d\n", total));
        analysis.append(String.format("  Optimization Rate: %.2f%%\n", optimizationRate));
        analysis.append(String.format("  Deferral Rate: %.2f%%\n", deferralRate));
        analysis.append(String.format("  Critical Processing Rate: %.2f%%\n", criticalRate));
        analysis.append(String.format("  Edge Fluids Skipped: %.2f%% (protects mob pathfinding)\n", skippedRate));
        
        // Effectiveness rating
        if (optimizationRate > 50) {
            analysis.append("  Effectiveness: EXCELLENT - High optimization rate\n");
        } else if (optimizationRate > 30) {
            analysis.append("  Effectiveness: GOOD - Moderate optimization rate\n");
        } else if (optimizationRate > 15) {
            analysis.append("  Effectiveness: FAIR - Low optimization rate\n");
        } else {
            analysis.append("  Effectiveness: POOR - Very low optimization rate\n");
        }
        
        return analysis.toString();
    }
    
    /**
     * Reset all metrics
     */
    public static void resetMetrics() {
        totalFlowingFluidsUpdates.set(0);
        optimizedFlowingFluidsUpdates.set(0);
        deferredFlowingFluidsUpdates.set(0);
        criticalFlowingFluidsUpdates.set(0);
        skippedFlowingFluidsUpdates.set(0);
        tpsBeforeOptimization.clear();
        tpsAfterOptimization.clear();
        fluidUpdateTimeHistory.clear();
        optimizationMetrics.clear();
        updateFrequencyHistory.clear();
        tickCounter = 0;
        baselineTPS = 20.0;
        currentTPSImpact = 0.0;
        fluidUpdatesPerSecond = 0;
        lastSecondUpdateCount = 0;
        lastSecondTimestamp = System.currentTimeMillis();
        
        LOGGER.info("Flowing Fluids performance metrics reset");
    }
    
    /**
     * Get comprehensive TPS impact analysis
     */
    public static String getTPSImpactAnalysis() {
        StringBuilder analysis = new StringBuilder();
        analysis.append("TPS Impact Analysis:\n");
        analysis.append(String.format("  Baseline TPS: %.2f\n", baselineTPS));
        analysis.append(String.format("  Current TPS: %.2f\n", PerformanceMonitor.getAverageTPS()));
        analysis.append(String.format("  TPS Impact: %.2f%%\n", currentTPSImpact));
        analysis.append(String.format("  Fluid Updates/sec: %d\n", fluidUpdatesPerSecond));
        analysis.append(String.format("  Avg Updates/sec: %.1f\n", getAverageFluidUpdatesPerSecond()));
        analysis.append(String.format("  Peak Updates/sec: %d\n", getPeakFluidUpdatesPerSecond()));
        analysis.append(String.format("  Deferred Queue: %d\n", FluidTickScheduler.getDeferredQueueSize()));
        
        // Performance rating
        if (currentTPSImpact < 5.0) {
            analysis.append("  Performance Rating: EXCELLENT - Minimal TPS impact\n");
        } else if (currentTPSImpact < 15.0) {
            analysis.append("  Performance Rating: GOOD - Moderate TPS impact\n");
        } else if (currentTPSImpact < 30.0) {
            analysis.append("  Performance Rating: FAIR - Noticeable TPS impact\n");
        } else {
            analysis.append("  Performance Rating: POOR - Significant TPS impact\n");
        }
        
        return analysis.toString();
    }
    
    /**
     * Update types for categorizing updates
     */
    public enum UpdateType {
        OPTIMIZED,  // Update was successfully optimized
        DEFERRED,   // Update was deferred for later processing
        CRITICAL,   // Update required immediate processing
        SKIPPED     // Update was skipped entirely (edge fluids that won't flow)
    }
    
    /**
     * Performance metrics for optimization levels
     */
    private static class PerformanceMetrics {
        private final List<Long> durations = Collections.synchronizedList(new ArrayList<>());
        private final List<Double> tpsValues = Collections.synchronizedList(new ArrayList<>());
        private final List<Integer> fluidUpdateCounts = Collections.synchronizedList(new ArrayList<>());
        
        void addSample(long duration, double tps, int fluidUpdates) {
            durations.add(duration);
            tpsValues.add(tps);
            fluidUpdateCounts.add(fluidUpdates);
            
            // Keep only recent samples
            if (durations.size() > 50) {
                durations.remove(0);
                tpsValues.remove(0);
                fluidUpdateCounts.remove(0);
            }
        }
        
        Map<String, Object> getSummary() {
            Map<String, Object> summary = new HashMap<>();
            summary.put("sampleCount", durations.size());
            if (!durations.isEmpty()) {
                summary.put("averageDuration", durations.stream().mapToLong(Long::longValue).average().orElse(0));
                summary.put("averageTPS", tpsValues.stream().mapToDouble(Double::doubleValue).average().orElse(0));
                summary.put("averageFluidUpdates", fluidUpdateCounts.stream().mapToInt(Integer::intValue).average().orElse(0));
            }
            return summary;
        }
    }
}
