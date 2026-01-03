package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkBasedFluidManager {
    private static final Logger LOGGER = LogManager.getLogger(ChunkBasedFluidManager.class);
    
    // Chunk-based sleep/wake system
    private static final Map<ChunkPos, FluidChunkData> fluidChunks = new ConcurrentHashMap<>();
    private static final int CHUNK_SLEEP_THRESHOLD = 40; // Sleep chunks with few updates
    private static final int CHUNK_WAKE_DISTANCE = 24; // Wake chunks within 24 blocks of players
    private static final int MAX_CHUNK_UPDATES_PER_TICK = 10; // Limit per tick
    
    // Performance tracking
    private static final AtomicInteger totalChunkUpdates = new AtomicInteger(0);
    private static final AtomicInteger skippedChunkUpdates = new AtomicInteger(0);
    
    static {
        LOGGER.info("Chunk-Based Fluid Manager initialized - sleep/wake system active");
    }
    
    public static void scheduleFluidUpdate(ServerLevel level, BlockPos pos, int originalDelay) {
        totalChunkUpdates.incrementAndGet();
        ChunkPos chunkPos = new ChunkPos(pos);
        
        // Get or create chunk data
        FluidChunkData chunkData = fluidChunks.computeIfAbsent(chunkPos, k -> new FluidChunkData(chunkPos));
        
        // Check if chunk is sleeping
        if (chunkData.isSleeping()) {
            // Check if we should wake the chunk
            double distanceToPlayer = getDistanceToNearestPlayer(level, pos);
            if (distanceToPlayer <= CHUNK_WAKE_DISTANCE) {
                chunkData.wakeUp();
                LOGGER.debug("Waking up chunk {} due to player proximity ({} blocks)", chunkData.getChunkPos(), distanceToPlayer);
            } else {
                skippedChunkUpdates.incrementAndGet();
                return; // Skip update - chunk is sleeping
            }
        }
        
        // Check if we should put chunk to sleep
        if (chunkData.getUpdateCount() < CHUNK_SLEEP_THRESHOLD && getDistanceToNearestPlayer(level, pos) > CHUNK_WAKE_DISTANCE) {
            chunkData.putToSleep();
            skippedChunkUpdates.incrementAndGet();
            LOGGER.debug("Putting chunk {} to sleep due to low activity and distance", chunkData.getChunkPos());
            return;
        }
        
        // Check per-tick limits
        // Adjusted to use a static value since methods are not available in AggressiveFluidOptimizer
        if (totalChunkUpdates.get() >= 25) { // Using a hardcoded value matching MAX_UPDATES_PER_TICK
            skippedChunkUpdates.incrementAndGet();
            return; // Skip due to global limit
        }
        
        if (chunkData.getChunkUpdatesThisTick() >= MAX_CHUNK_UPDATES_PER_TICK) {
            skippedChunkUpdates.incrementAndGet();
            return; // Skip due to chunk limit
        }
        
        // Check if we should optimize this fluid update
        FluidState fluidState = level.getFluidState(pos);
        if (shouldOptimizeFlowingFluid(level, pos, fluidState)) {
            // Enhanced delay calculation based on distance
            double playerDistance = getDistanceToNearestPlayer(level, pos);
            int enhancedDelay = calculateEnhancedDelay(originalDelay, playerDistance);
            
            // Schedule with enhanced delay
            level.scheduleTick(pos, fluidState.getType(), enhancedDelay);
            chunkData.incrementUpdateCount();
            chunkData.incrementChunkUpdatesThisTick();
            // Not calling AggressiveFluidOptimizer.markUpdated(pos) as it's not accessible
            LOGGER.debug("Scheduled optimized fluid update at {} in chunk {} with enhanced delay {}", pos, chunkData.getChunkPos(), enhancedDelay);
        } else {
            // Schedule with original delay - no unused variables here
            level.scheduleTick(pos, fluidState.getType(), originalDelay);
            chunkData.incrementUpdateCount();
            chunkData.incrementChunkUpdatesThisTick();
            // Not calling AggressiveFluidOptimizer.markUpdated(pos) as it's not accessible
            LOGGER.debug("Scheduled non-optimized fluid update at {} in chunk {} with original delay {}", pos, chunkData.getChunkPos(), originalDelay);
        }
    }
    
    private static boolean shouldOptimizeFlowingFluid(ServerLevel level, BlockPos pos, FluidState fluidState) {
        
        // Log the fluid type being checked
        LOGGER.debug("Checking optimization for fluid at position {}", pos);
        
        // For Flowing Fluids, ALWAYS optimize to improve performance
        // The optimization should enhance Flowing Fluids, not avoid it
        boolean isFlowingFluidsMod = FlowingFluidsAPIIntegration.isFlowingFluidsAvailable() && 
                                   FlowingFluidsAPIIntegration.doesModifyFluid(fluidState.getType());
        
        if (isFlowingFluidsMod) {
            LOGGER.debug("Applying chunk-based optimization to Flowing Fluids at {}", pos);
            // Always optimize Flowing Fluids to improve performance
            return true;
        }
        
        // For vanilla fluids, use standard optimization logic
        // Check if this is a low-priority update that can be delayed
        if (fluidState.getAmount() < 8) { // Non-source fluid
            double distance = getDistanceToNearestPlayer(level, pos);
            if (distance > 24) { // Far from players
                return true; // Optimize (delay) this update
            }
        }
        
        return false; // Don't optimize critical updates
    }
    
    private static int calculateEnhancedDelay(int originalDelay, double playerDistance) {
        // Base delay on original
        int delay = originalDelay;
        
        // Increase delay based on distance
        if (playerDistance > 32) {
            delay *= 4; // Far away - 4x delay
        } else if (playerDistance > 16) {
            delay *= 2; // Medium distance - 2x delay
        }
        
        // Cap the delay to prevent infinite stalling
        return Math.min(delay, 40);
    }
    
    private static double getDistanceToNearestPlayer(ServerLevel level, BlockPos pos) {
        return level.players().stream()
            .mapToDouble(player -> player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
            .min()
            .orElse(Double.MAX_VALUE);
    }
    
    public static String getStatus() {
        int total = totalChunkUpdates.get();
        int skipped = skippedChunkUpdates.get();
        double skipPercent = total > 0 ? (skipped * 100.0 / total) : 0;
        int activeChunks = fluidChunks.size();
        long sleepingChunks = fluidChunks.values().stream().filter(FluidChunkData::isSleeping).count();
        return String.format("Chunk Fluid Manager: %.1f%% skipped, %d active chunks (%d sleeping)", 
                            skipPercent, activeChunks, sleepingChunks);
    }
    
    private static class FluidChunkData {
        private final ChunkPos chunkPos;
        private boolean sleeping = false;
        private int updateCount = 0;
        private final AtomicInteger chunkUpdatesThisTick = new AtomicInteger(0);
        
        FluidChunkData(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
        }
        
        public boolean isSleeping() {
            return sleeping;
        }
        
        public void putToSleep() {
            sleeping = true;
        }
        
        public void wakeUp() {
            sleeping = false;
        }
        
        public int getUpdateCount() {
            return updateCount;
        }
        
        public void incrementUpdateCount() {
            updateCount++;
        }
        
        public int getChunkUpdatesThisTick() {
            return chunkUpdatesThisTick.get();
        }
        
        public void incrementChunkUpdatesThisTick() {
            chunkUpdatesThisTick.incrementAndGet();
        }
        
        public ChunkPos getChunkPos() {
            return chunkPos;
        }
    }
}
