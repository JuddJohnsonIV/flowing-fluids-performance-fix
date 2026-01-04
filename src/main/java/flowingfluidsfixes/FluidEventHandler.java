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
        
        // OPTIMIZED: Process ocean/river water replenishments every 3 ticks to reduce FPS impact
        // Only affects ocean/river biomes, helps reduce lag from Flowing Fluids calculations
        if (server.getTickCount() % 3 == 0) {
            OceanRiverWaterReplenishment.processReplenishments(overworld);
        }
        
        // OPTIMIZED: Process shore water leveling every 4 ticks to reduce FPS impact
        // Water flowing into ocean/river should merge with ocean water level
        if (server.getTickCount() % 4 == 0) {
            OceanRiverWaterReplenishment.processShoreWaterLeveling(overworld);
        }
        
        // OPTIMIZED: Unified shore water replenishment every 5 ticks
        if (server.getTickCount() % 5 == 0) {
            OceanRiverWaterReplenishment.processUnifiedShoreWaterReplenishment(overworld);
        }
        
        // OPTIMIZED: Aggressive shore water replenishment every 6 ticks
        if (server.getTickCount() % 6 == 0) {
            OceanRiverWaterReplenishment.processAggressiveShoreWaterReplenishment(overworld);
        }
        
        // OPTIMIZED: Ultra-instant sea level restoration every 3 ticks (reduced from every tick)
        // RUN FIRST to override any other water mechanics
        if (server.getTickCount() % 3 == 0) {
            OceanRiverWaterReplenishment.processUltraInstantOceanSurfaceLeveling(overworld);
        }
        
        // OPTIMIZED: Direct ocean surface filling every 4 ticks (reduced from every tick)
        if (server.getTickCount() % 4 == 0) {
            OceanRiverWaterReplenishment.processDirectOceanSurfaceFilling(overworld);
        }
        
        // OPTIMIZED: Aggressive surface leveling every 5 ticks (reduced from every tick)
        if (server.getTickCount() % 5 == 0) {
            OceanRiverWaterReplenishment.processAggressiveSurfaceLeveling(overworld);
        }
        
        // RE-ENABLED: These methods now work with Ocean Flow Accelerator for better performance
        OceanRiverWaterReplenishment.processThinLayerLeveling(overworld);
        OceanRiverWaterReplenishment.processInstantOceanSurfaceEvaporation(overworld);
        OceanRiverWaterReplenishment.processInstantOceanSurfaceRestoration(overworld);
        
        // ENHANCED: Aggressive rain water removal during rain to eliminate floating water
        OceanRiverWaterReplenishment.processRainWaterRemoval(overworld);
        
        // NEW: Aggressive ocean flow acceleration for large ocean surfaces
        // Addresses slow center-pulling in massive ocean areas with similar flow values
        OceanFlowAccelerator.processOceanFlowAcceleration(overworld);
        
                
        // Log status periodically based on logging settings
        int logInterval = FlowingFluidsOptimizationConfig.enableDetailedLogging.get() ? 100 : 200;
        if (server.getTickCount() % logInterval == 0) {
            logPerformanceStatus();
            
            // Also log performance snapshot if debug enabled
            if (FlowingFluidsDebugLogger.isDebugEnabled()) {
                FlowingFluidsDebugLogger.logPerformanceSnapshot();
            }
            
            // Clean up ocean flow accelerator caches to prevent memory leaks
            OceanFlowAccelerator.cleanup();
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
        LOGGER.info("Ocean Flow Accelerator: {}", OceanFlowAccelerator.getPerformanceStats());
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
