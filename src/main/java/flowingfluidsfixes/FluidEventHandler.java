package flowingfluidsfixes;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Enhanced fluid event handler that captures all fluid updates and routes them
 * through the optimized batch processing system while maintaining 100% Flowing Fluids parity.
 * 
 * FIXED: Removed static initialization anti-patterns and event bus registration deadlock issues
 */
@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.FORGE)
@SuppressWarnings("all")
public class FluidEventHandler {
    private static final Logger LOGGER = LogManager.getLogger(FluidEventHandler.class);
    
    // Use ThreadLocal to prevent static field memory leaks and thread safety issues
    private static final ThreadLocal<Integer> updatesQueuedThisTick = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Long> lastTickTime = ThreadLocal.withInitial(() -> 0L);

    /**
     * Handles server tick events for aggressive optimization
     * Integrates deferred processing, adaptive throttling, and async processing
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        // At START of tick: reset time budget so fluid processing has a fresh budget
        if (event.phase == TickEvent.Phase.START) {
            FluidTickScheduler.resetTickTimeBudget();
            EntityProtectionSystem.onTickStart(); // Track tick timing for entity protection
            return;
        }
        
        // At END of tick: process deferred updates and logging
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // Reset tick counters for aggressive optimization
        AggressiveFluidOptimizer.resetTickCounter();
        FlowingFluidsCalculationOptimizer.resetTickCounter();
        
        // Adjust dynamic update rate based on server performance
        FlowingFluidsAPIIntegration.adjustDynamicUpdateRate();
        
        // Check for instability and handle rollback if needed
        FluidOptimizationRollback.checkForInstability();
        
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        
        // Collect performance data for adaptive feedback loop (every second = 20 ticks)
        if (server.getTickCount() % 20 == 0) {
            AdaptiveFeedbackLoop.collectPerformanceData();
        }
        
        // Process deferred fluid updates based on optimization level
        ServerLevel overworld = server.overworld();
        int maxDeferredToProcess = getMaxDeferredUpdatesForLevel();
        FluidTickScheduler.processDeferredUpdates(overworld, maxDeferredToProcess);
        
        // Apply completed async fluid updates on main thread (thread-safe)
        // This processes results from the dedicated fluid thread pool
        if (FluidThreadingHandler.isAsyncEnabled()) {
            int asyncProcessed = FluidThreadingHandler.applyCompletedResults(overworld, maxDeferredToProcess);
            if (asyncProcessed > 0 && FlowingFluidsDebugLogger.isDebugEnabled()) {
                LOGGER.debug("Applied {} async fluid results", asyncProcessed);
            }
        }
        
        // Process multiplayer sync updates
        MultiplayerFluidSync.processPendingSync(overworld);
        
        // Process ocean/river water replenishments (ONLY affects ocean/river biomes)
        // This helps reduce lag from Flowing Fluids calculating water holes
        // by slowly refilling them, preserving finite water system elsewhere
        OceanRiverWaterReplenishment.processReplenishments(overworld);
        
        // CRITICAL: Process shore water leveling - water flowing into ocean/river
        // should merge with ocean water level to reduce constant calculations
        OceanRiverWaterReplenishment.processShoreWaterLeveling(overworld);
        
        // ENHANCED: Process thin layer leveling more frequently to prevent thick layer accumulation
        OceanRiverWaterReplenishment.processThinLayerLeveling(overworld);
        
        // CRITICAL: Instant evaporation of thin water layers sitting on ocean surface
        // This removes the darker rain water layer that spreads across the ocean causing lag
        OceanRiverWaterReplenishment.processInstantOceanSurfaceEvaporation(overworld);
        
        // ULTRA-AGGRESSIVE: Direct ocean surface filling - instantly fill holes at Y=63
        // This bypasses the queue system and directly converts non-source water to source
        OceanRiverWaterReplenishment.processDirectOceanSurfaceFilling(overworld);
        
        // ENHANCED: Aggressive rain water removal during rain to eliminate floating water
        OceanRiverWaterReplenishment.processRainWaterRemoval(overworld);
        
        // NEW: Spawn bubble particles in flowing water to show visible underwater currents
        OceanRiverWaterReplenishment.processUnderwaterCurrentParticles(overworld);
        
        // Log status periodically based on logging settings
        int logInterval = FlowingFluidsOptimizationConfig.enableDetailedLogging.get() ? 100 : 200;
        if (server.getTickCount() % logInterval == 0) {
            logPerformanceStatus();
            
            // Also log performance snapshot if debug enabled
            if (FlowingFluidsDebugLogger.isDebugEnabled()) {
                FlowingFluidsDebugLogger.logPerformanceSnapshot();
            }
        }
    }
    
    /**
     * Get max deferred updates to process based on optimization level
     */
    private static int getMaxDeferredUpdatesForLevel() {
        var level = FlowingFluidsOptimizationConfig.optimizationLevel.get();
        return switch (level) {
            case AGGRESSIVE -> 25;   // Process fewer deferred updates
            case BALANCED -> 50;     // Moderate processing
            case MINIMAL -> 100;     // Process more to maintain fluid behavior
            default -> 50;           // Default to BALANCED
        };
    }
    
    /**
     * Log comprehensive performance status
     */
    private static void logPerformanceStatus() {
        LOGGER.info("=== Flowing Fluids Performance Status ===");
        LOGGER.info("Optimization Level: {}", FlowingFluidsOptimizationConfig.optimizationLevel.get());
        LOGGER.info("Fluid Optimization: {}", AggressiveFluidOptimizer.getStatus());
        LOGGER.info("Tick Scheduler: {}", FluidTickScheduler.getPerformanceStats());
        LOGGER.info("Time Budget: {}", FluidTickScheduler.getTimeBudgetStats());
        LOGGER.info("Deferred Queue: {} pending", FluidTickScheduler.getDeferredQueueSize());
        LOGGER.info("Adaptive Limit: {} updates/tick", FluidTickScheduler.getAdaptiveMaxUpdatesPerTick());
        LOGGER.info("Async Processing: {}", FluidThreadingHandler.getAsyncStats());
        LOGGER.info("Threading: {}", FluidThreadingHandler.getThreadingStats());
        LOGGER.info("Calculation Optimizer: {}", FlowingFluidsCalculationOptimizer.getOptimizationStats());
        LOGGER.info("Chunk Manager: {}", ChunkBasedFluidManager.getStatus());
        LOGGER.info("Current TPS: {}", String.format("%.2f", PerformanceMonitor.getAverageTPS()));
        LOGGER.info("Emergency Status: {}", EmergencyPerformanceMode.getStatusSummary());
        LOGGER.info("Multiplayer Sync: {}", MultiplayerFluidSync.getSyncStats());
        LOGGER.info("Rollback Status: {}", FluidOptimizationRollback.getStatusSummary());
        LOGGER.info("Feedback Loop: {}", AdaptiveFeedbackLoop.getStatusSummary());
        LOGGER.info("Vanilla Fallback: {}", VanillaFluidFallback.getStatsSummary());
        LOGGER.info("Create Compat: {}", CreateModCompatibility.getStatsSummary());
        LOGGER.info("Entity Protection: {}", EntityProtectionSystem.getStatusSummary());
        LOGGER.info("Tick Time Protection: {}", TickTimeProtection.getStatusSummary());
        LOGGER.info("Game Time Protection: {}", GameTimeProtection.getStatusSummary());
        LOGGER.info("Distance Limit: {}", FluidProcessingDistanceLimit.getStatsSummary());
        LOGGER.info("Cache Stats: {}", FluidTickScheduler.getCacheStats());
        LOGGER.info("Ocean/River Replenishment: enabled={}, queue={}", 
            OceanRiverWaterReplenishment.isEnabled(), OceanRiverWaterReplenishment.getQueueSize());
        LOGGER.info("===========================================");
    }
    
    /**
     * Cleanup ThreadLocal variables to prevent memory leaks
     */
    public static void cleanup() {
        updatesQueuedThisTick.remove();
        lastTickTime.remove();
    }
}
