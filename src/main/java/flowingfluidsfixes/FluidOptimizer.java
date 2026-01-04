package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.server.MinecraftServer;

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FluidOptimizer {
    private static final Logger LOGGER = LogManager.getLogger(FluidOptimizer.class);
    private static final int MAX_HIGH_PRIORITY_UPDATES = 2000; // Increased from 1000 for more edge water processing
    private static final int MAX_STANDARD_UPDATES = 3000; // Increased from 1500 for more fluid processing
    public static final double TPS_EMERGENCY_THRESHOLD = 12.0;
    private static final int EMERGENCY_MODE_THRESHOLD = 100;
    public static final int EMERGENCY_MODE_MULTIPLIER = 8;
    private static final int MIN_UPDATES_PER_TICK = 25;
    private static final long CACHE_CLEANUP_INTERVAL = 10000;
    private static final int MAX_CACHE_SIZE = 200000;
    public static final int BASE_UPDATES_PER_TICK = 800; // Increased from 300 for faster fluid processing

    private final PriorityBlockingQueue<FluidUpdate> highPriorityUpdates = new PriorityBlockingQueue<>(MAX_HIGH_PRIORITY_UPDATES, Comparator.comparingInt(FluidUpdate::getPriority).reversed());
    private final PriorityBlockingQueue<FluidUpdate> standardUpdates = new PriorityBlockingQueue<>(MAX_STANDARD_UPDATES, Comparator.comparingInt(FluidUpdate::getPriority).reversed());
    private final Map<BlockPos, FluidFlowPrediction> flowCache = Collections.synchronizedMap(new LRUCache<>(MAX_CACHE_SIZE));

    private final AtomicLong lastCleanupTime = new AtomicLong(0);
    private final AtomicBoolean emergencyModeActive = new AtomicBoolean(false);
    private final AtomicInteger processedUpdateCount = new AtomicInteger(0);
    private volatile double currentTps = 20.0;
    private final double[] tpsHistory = new double[10];
    private int tpsHistoryIndex = 0;
    private final Object tpsLock = new Object();
    private volatile int updateLimit = MAX_STANDARD_UPDATES;
    private static volatile FluidOptimizer instance = null;

    private FluidOptimizer() {
        // Prevent instantiation from outside
    }

    public static synchronized FluidOptimizer getInstance() {
        if (instance == null) {
            instance = new FluidOptimizer();
        }
        return instance;
    }

    /**
     * Static convenience method for mixin calls
     */
    public static void queueFluidUpdate(Level worldLevel, BlockPos blockPos, FluidState fluidState, BlockState blockState) {
        getInstance().queueFluidUpdateInstance(worldLevel, blockPos, fluidState, blockState, 1);
    }

    public void queueFluidUpdateInstance(Level worldLevel, BlockPos blockPos, FluidState fluidState, BlockState blockState, int updatePriority) {
        if (worldLevel instanceof ServerLevel serverLevel) {
            // Check if the position is already queued to avoid duplicates
            FluidUpdate newUpdate = new FluidUpdate(serverLevel, blockPos, fluidState, blockState, updatePriority);
            if (FlowingFluidsIntegration.isFloatingWaterLayer(serverLevel, blockPos, fluidState)) {
                updatePriority = Math.max(updatePriority, 10);
                newUpdate.setPriority(updatePriority);
            }
            
            // Decide which queue to use based on priority
            PriorityBlockingQueue<FluidUpdate> targetQueue = (updatePriority >= 3) ? highPriorityUpdates : standardUpdates;
            
            // Remove any existing update for the same position to avoid duplicate processing
            targetQueue.removeIf(update -> update.pos.equals(blockPos));
            
            // Add the new update
            targetQueue.offer(newUpdate);
            LOGGER.debug("Queued {} priority fluid update at {} with priority {}", (updatePriority >= 3 ? "high" : "standard"), blockPos, updatePriority);
        } else {
            LOGGER.warn("Attempted to queue fluid update in non-server level at {}", blockPos);
        }
    }


    @SubscribeEvent
    public void processTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel worldLevel = server.overworld();
        if (worldLevel == null) return;
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCleanupTime.get() >= CACHE_CLEANUP_INTERVAL) {
            cleanupFlowCache(worldLevel);
            cleanupFluidUpdateQueues();
            lastCleanupTime.set(currentTime);
            LOGGER.info("Performed flow cache cleanup for level {}", worldLevel.dimension().location().toString());
        }

        double instantTps = server.getAverageTickTime() > 0 ? Math.min(20.0, 1000.0 / server.getAverageTickTime()) : 20.0;
        synchronized (tpsLock) {
            tpsHistory[tpsHistoryIndex] = instantTps;
            tpsHistoryIndex = (tpsHistoryIndex + 1) % tpsHistory.length;
            double sumTps = 0.0;
            int validEntries = 0;
            for (double tpsValue : tpsHistory) {
                if (tpsValue > 0) {
                    sumTps += tpsValue;
                    validEntries++;
                }
            }
            currentTps = validEntries > 0 ? sumTps / validEntries : 20.0;
        }
        boolean emergencyMode = currentTps < TPS_EMERGENCY_THRESHOLD;
        emergencyModeActive.set(emergencyMode);

        int totalUpdatesProcessed = 0;
        int updatesThisTick = calculateDynamicUpdateCount(currentTps);
        updateLimit = updatesThisTick;

        if (emergencyMode) {
            LOGGER.warn("Entering emergency mode due to low TPS: {} in level {}", currentTps, worldLevel.dimension().location().toString());
            updatesThisTick = Math.max(MIN_UPDATES_PER_TICK, updatesThisTick / EMERGENCY_MODE_MULTIPLIER);
        }

        totalUpdatesProcessed += processHighPriorityUpdates(worldLevel, updatesThisTick);

        if (totalUpdatesProcessed < updatesThisTick) {
            totalUpdatesProcessed += processStandardUpdates(worldLevel, updatesThisTick - totalUpdatesProcessed);
        }

        int previousProcessedUpdateCount = processedUpdateCount.get();
        processedUpdateCount.set(totalUpdatesProcessed);
        LOGGER.debug("Previous processed update count was {}, new count is {}", previousProcessedUpdateCount, totalUpdatesProcessed);

        if (emergencyMode && totalUpdatesProcessed > EMERGENCY_MODE_THRESHOLD) {
            LOGGER.warn("Emergency mode: Processed {} updates (above threshold of {}) in level {}", totalUpdatesProcessed, EMERGENCY_MODE_THRESHOLD, worldLevel.dimension().location().toString());
        }

        if (!highPriorityUpdates.isEmpty()) {
            FluidUpdate update = highPriorityUpdates.peek();
            if (update != null && update.isValid()) {
                checkStability(worldLevel, update.pos, update.state);
                LOGGER.debug("Checked stability for high priority update at {}", update.pos);
            }
        }
        LOGGER.info("Processed {} fluid updates in tick for level {}", totalUpdatesProcessed, worldLevel.dimension().location().toString());
        LOGGER.info("Update limit for this tick is {}", updateLimit);
    }

    private int processHighPriorityUpdates(ServerLevel serverWorld, int maxUpdates) {
        int processedCount = 0;
        while (processedCount < maxUpdates && !highPriorityUpdates.isEmpty()) {
            FluidUpdate fluidUpdate = highPriorityUpdates.poll();
            if (fluidUpdate == null) break;
            try {
                if (fluidUpdate.isValid()) {
                    applyFluidUpdate(serverWorld, fluidUpdate.pos, fluidUpdate.state, fluidUpdate.blockState);
                    processedCount++;
                    LOGGER.debug("Processed high priority update at {} with priority {}", fluidUpdate.pos, fluidUpdate.getPriority());
                } else {
                    LOGGER.debug("Skipped invalid high priority update at {}", fluidUpdate.pos);
                }
            } catch (Exception e) {
                LOGGER.error("Error processing high priority fluid update at {}: {}", fluidUpdate.pos, e.getMessage());
            }
        }
        LOGGER.info("Processed {} high priority updates", processedCount);
        return processedCount;
    }

    private int processStandardUpdates(ServerLevel serverWorld, int maxUpdates) {
        int processedCount = 0;
        while (processedCount < maxUpdates && !standardUpdates.isEmpty()) {
            FluidUpdate fluidUpdate = standardUpdates.poll();
            if (fluidUpdate == null) break;
            try {
                if (fluidUpdate.isValid()) {
                    applyFluidUpdate(serverWorld, fluidUpdate.pos, fluidUpdate.state, fluidUpdate.blockState);
                    processedCount++;
                    LOGGER.debug("Processed standard update at {} with priority {}", fluidUpdate.pos, fluidUpdate.getPriority());
                } else {
                    LOGGER.debug("Skipped invalid standard update at {}", fluidUpdate.pos);
                }
            } catch (Exception e) {
                LOGGER.error("Error processing standard fluid update at {}: {}", fluidUpdate.pos, e.getMessage());
            }
        }
        LOGGER.info("Processed {} standard updates", processedCount);
        return processedCount;
    }

    private void applyFluidUpdate(ServerLevel serverWorld, BlockPos blockPos, FluidState fluidState, BlockState blockState) {
        serverWorld.setBlock(blockPos, blockState, 3);
        serverWorld.scheduleTick(blockPos, fluidState.getType(), 1);
        LOGGER.debug("Applied fluid update at {} in level {}", blockPos, serverWorld.dimension().location().toString());
    }

    // SMOOTH THROTTLING WITH HYSTERESIS - Prevent rapid mode switching
    private static double currentThrottlingFactor = 1.0;
    private static double targetThrottlingFactor = 1.0;
    private static final double THROTTLING_SMOOTHING_FACTOR = 0.1; // 10% smoothing per tick
    private static final double HYSTERESIS_THRESHOLD = 0.15; // 15% hysteresis band
    private static final double MIN_THROTTLE = 0.1; // Minimum 10% of updates
    private static final double MAX_THROTTLE = 2.0; // Maximum 200% of updates
    
    private int calculateDynamicUpdateCount(double ticksPerSecond) {
        // Calculate target throttling based on TPS with hysteresis
        double newTargetFactor = calculateTargetThrottlingFactor(ticksPerSecond);
        
        // Apply hysteresis - only change if difference exceeds threshold
        if (Math.abs(newTargetFactor - targetThrottlingFactor) > HYSTERESIS_THRESHOLD) {
            targetThrottlingFactor = newTargetFactor;
        }
        
        // Smooth transition toward target
        double factorDiff = targetThrottlingFactor - currentThrottlingFactor;
        currentThrottlingFactor += factorDiff * THROTTLING_SMOOTHING_FACTOR;
        
        // Clamp to reasonable bounds
        currentThrottlingFactor = Math.max(MIN_THROTTLE, Math.min(MAX_THROTTLE, currentThrottlingFactor));
        
        // Calculate updates with smooth throttling
        int baseUpdates = 400; // Increased base for smoother performance
        int smoothedUpdates = (int) (baseUpdates * currentThrottlingFactor);
        
        // Ensure minimum updates even under heavy load
        return Math.max(50, smoothedUpdates);
    }
    
    /**
     * Calculate target throttling factor based on TPS
     */
    private double calculateTargetThrottlingFactor(double tps) {
        if (tps < 8.0) {
            return 0.25; // 25% of updates under heavy load
        } else if (tps < 12.0) {
            return 0.5;  // 50% of updates under moderate load
        } else if (tps < 16.0) {
            return 0.75; // 75% of updates under light load
        } else if (tps < 18.0) {
            return 1.0;  // 100% of updates near optimal
        } else {
            return 1.5;  // 150% of updates when server is healthy
        }
    }

    public int calculateDynamicUpdateLimit(double tpsValue) {
        // Use smooth throttling instead of sudden changes
        double smoothFactor = currentThrottlingFactor;
        
        // Apply smooth factor to base limit
        int smoothLimit = (int) (BASE_UPDATES_PER_TICK * smoothFactor);
        
        // Ensure reasonable bounds
        return Math.max(MIN_UPDATES_PER_TICK, Math.min(MAX_STANDARD_UPDATES + MAX_HIGH_PRIORITY_UPDATES, smoothLimit));
    }
    
    /**
     * Get current throttling statistics for monitoring
     */
    public String getThrottlingStats() {
        return String.format("Throttling: Current=%.2f, Target=%.2f, BaseUpdates=%d",
            currentThrottlingFactor, targetThrottlingFactor, BASE_UPDATES_PER_TICK);
    }

    private void cleanupFlowCache(ServerLevel worldLevel) {
        // Update player position cache for distance checks
        FluidProcessingDistanceLimit.updatePlayerCache(worldLevel);
        
        // Iterate manually to avoid nested synchronization on synchronizedMap
        List<BlockPos> toRemove = new ArrayList<>();
        for (Map.Entry<BlockPos, FluidFlowPrediction> entry : flowCache.entrySet()) {
            BlockPos pos = entry.getKey();
            FluidFlowPrediction prediction = entry.getValue();
            
            // Remove if prediction is invalid OR outside simulation distance
            if (!prediction.isValid() || !FluidProcessingDistanceLimit.isWithinProcessingRange(pos)) {
                toRemove.add(pos);
            }
        }
        for (BlockPos key : toRemove) {
            flowCache.remove(key);
        }
        LOGGER.debug("Cleaned up flow cache, removed {} entries, remaining: {}", toRemove.size(), flowCache.size());
    }

    private void cleanupFluidUpdateQueues() {
        highPriorityUpdates.clear();
        standardUpdates.clear();
        LOGGER.info("Cleaned up fluid update queues, high priority size: {}, standard size: {}", highPriorityUpdates.size(), standardUpdates.size());
    }

    private FluidFlowPrediction predictFluidFlow(ServerLevel worldLevel, BlockPos pos, FluidState fluidState) {
        FluidFlowPrediction prediction = new FluidFlowPrediction(worldLevel, pos, fluidState);
        LOGGER.debug("Predicting flow for fluid state {} at {}", fluidState.getType(), pos);
        return prediction;
    }

    public boolean shouldProcessUpdate(ServerLevel worldLevel, BlockPos pos, FluidState state) {
        // Check if Flowing Fluids mod should handle this update first
        if (FlowingFluidsAPIIntegration.shouldPrioritizeFlowingFluids(state.getType())) {
            return false; // Let Flowing Fluids mod handle it
        }
        return !emergencyModeActive.get() || isCriticalUpdate(worldLevel, pos, state);
    }

    public boolean isCriticalUpdate(ServerLevel worldLevel, BlockPos pos, FluidState state) {
        if (state.isSource()) {
            return true;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(dir);
            FluidState adjacentState = worldLevel.getFluidState(adjacent);
            if (adjacentState.isEmpty() || (adjacentState.getType().isSame(state.getType()) && adjacentState.getAmount() < state.getAmount())) {
                return true;
            }
        }
        return false;
    }

    private void checkStability(ServerLevel worldLevel, BlockPos pos, FluidState fluidState) {
        FluidFlowPrediction cachedPrediction = (FluidFlowPrediction) flowCache.get(pos);
        if (cachedPrediction != null && cachedPrediction.isValid(worldLevel, pos)) {
            LOGGER.debug("Using cached flow prediction at {}", pos);
            return;
        }

        FluidFlowPrediction newPrediction = predictFluidFlow(worldLevel, pos, fluidState);
        flowCache.put(pos, newPrediction);
        
        // Verify prediction matches current state
        if (newPrediction.matchesCurrentState(worldLevel)) {
            LOGGER.debug("Cached new flow prediction at {} for fluid {}", pos, newPrediction.getFluidState().getType());
        } else {
            LOGGER.debug("Flow prediction at {} may be stale, fluid state changed", pos);
        }
    }

    public int getQueueSize() {
        return highPriorityUpdates.size() + standardUpdates.size();
    }

    public boolean isEmergencyMode() {
        return emergencyModeActive.get();
    }

    public int getProcessedUpdateCount() {
        return processedUpdateCount.get();
    }

    public double getCurrentTps() {
        return this.currentTps;
    }

    public int getUpdateLimit() {
        return updateLimit;
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Server stopping, performing comprehensive cleanup for FluidOptimizer");
        ServerLevel overworld = event.getServer().overworld();
        if (overworld != null) {
            cleanupFlowCache(overworld);
        }
        highPriorityUpdates.clear();
        standardUpdates.clear();
        
        // MEMORY LEAK FIX: Clean up all static collections and thread pools
        // Thread pool and async processing
        FluidThreadingHandler.shutdown();
        
        // ThreadLocal cleanup
        FluidEventHandler.cleanup();
        
        // Ocean/River replenishment caches
        OceanRiverWaterReplenishment.clearAllCaches();
        
        // Fluid calculation caches
        FlowingFluidsCalculationOptimizer.clearStaticCaches();
        FlowingFluidsCalculationOptimizer.clearCache();
        
        // Memory optimizer caches
        FluidMemoryOptimizer.clearAllCaches();
        
        // Tick scheduler caches
        FluidTickScheduler.clearAllCaches();
        
        // Multiplayer sync
        MultiplayerFluidSync.clearPendingSync();
        
        // Chunk batching
        ChunkBasedBatching.clearProcessedChunks();
        
        // Debug logger
        FlowingFluidsDebugLogger.shutdown();
        
        // Runtime issue handler
        RuntimeIssueHandler.clearIssues();
        
        LOGGER.info("FluidOptimizer comprehensive cleanup completed on server shutdown");
    }

    private class FluidFlowPrediction {
        private final ServerLevel world;
        private final BlockPos position;
        private final FluidState fluidState;
        private final long timestamp;

        FluidFlowPrediction(ServerLevel world, BlockPos pos, FluidState state) {
            this.world = world;
            this.position = pos.immutable();
            this.fluidState = state;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid() {
            return (System.currentTimeMillis() - timestamp) < 10000; // Reduced to 10s for more aggressive cleanup
        }

        boolean isValid(ServerLevel worldLevel, BlockPos blockPos) {
            return this.world.equals(worldLevel) && this.position.equals(blockPos) && isValid();
        }

        FluidState getFluidState() {
            return fluidState;
        }
        
        boolean matchesCurrentState(ServerLevel level) {
            return level.getFluidState(position).equals(fluidState);
        }
    }

    private static class FluidUpdate {
        private final ServerLevel level;
        private final BlockPos pos;
        private final FluidState state;
        private final BlockState blockState;
        private int priority;

        FluidUpdate(ServerLevel serverWorld, BlockPos blockPos, FluidState fluidState, BlockState blockState, int updatePriority) {
            this.level = serverWorld;
            this.pos = blockPos.immutable();
            this.state = fluidState;
            this.blockState = blockState;
            this.priority = FlowingFluidsIntegration.isFloatingWaterLayer(serverWorld, blockPos, fluidState) ? Math.max(updatePriority, 10) : updatePriority;
        }

        boolean isValid() {
            return level.isInWorldBounds(pos) && level.getFluidState(pos).equals(state) && level.getBlockState(pos).equals(blockState);
        }

        int getPriority() {
            return priority;
        }

        void setPriority(int priority) {
            this.priority = priority;
        }
    }

    /**
     * LRU Cache implementation for flow predictions with automatic eviction
     */
    private static class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;
        private final int maxSize;

        LRUCache(int maxSize) {
            super(maxSize, 0.75f, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }
}
