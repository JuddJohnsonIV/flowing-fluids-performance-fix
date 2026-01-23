package flowingfluidsfixes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spatial Partitioning System - Chunk-based fluid tracking and processing
 * Replaces worldwide level access with local chunk processing for massive MSPT improvements
 */
public class SpatialPartitioningSystem {
    
    // Chunk-based fluid tracking
    private static final ConcurrentHashMap<Long, ChunkFluidData> chunkFluidData = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ChunkProcessingState> chunkProcessingState = new ConcurrentHashMap<>();
    
    // Spatial partitioning configuration
    private static final int CHUNK_PROCESSING_RADIUS = 2; // Process 2x2 chunk areas
    private static final int MAX_FLUIDS_PER_CHUNK = 100; // Maximum fluids to track per chunk
    private static final long CHUNK_PROCESSING_COOLDOWN = 1000; // 1 second between chunk processing
    
    // Performance tracking
    private static final AtomicInteger chunksProcessed = new AtomicInteger(0);
    private static final AtomicInteger chunksSkipped = new AtomicInteger(0);
    private static final AtomicInteger fluidsProcessedLocally = new AtomicInteger(0);
    private static final AtomicInteger worldwideAccessesAvoided = new AtomicInteger(0);
    
    // Chunk processing statistics
    private static final AtomicInteger activeChunks = new AtomicInteger(0);
    private static final AtomicInteger queuedChunks = new AtomicInteger(0);
    private static final AtomicLong lastChunkProcessing = new AtomicLong(0);
    
    /**
     * Chunk fluid data container
     */
    public static class ChunkFluidData {
        public final Set<BlockPos> fluidPositions = new HashSet<>();
        public final Map<BlockPos, FluidState> fluidStates = new ConcurrentHashMap<>();
        public final Map<BlockPos, Long> lastUpdateTimes = new ConcurrentHashMap<>();
        public volatile int fluidCount = 0;
        public volatile long lastProcessed = 0;
        public volatile boolean isActive = false;
        public volatile double processingPriority = 0.0;
        
        public synchronized void addFluid(BlockPos pos, FluidState state) {
            if (fluidPositions.size() >= MAX_FLUIDS_PER_CHUNK) {
                return; // Skip if chunk is full
            }
            
            fluidPositions.add(pos);
            fluidStates.put(pos, state);
            lastUpdateTimes.put(pos, System.currentTimeMillis());
            fluidCount = fluidPositions.size();
            isActive = true;
        }
        
        public synchronized void removeFluid(BlockPos pos) {
            fluidPositions.remove(pos);
            fluidStates.remove(pos);
            lastUpdateTimes.remove(pos);
            fluidCount = fluidPositions.size();
            isActive = !fluidPositions.isEmpty();
        }
        
        public synchronized void updateFluid(BlockPos pos, FluidState state) {
            if (fluidPositions.contains(pos)) {
                fluidStates.put(pos, state);
                lastUpdateTimes.put(pos, System.currentTimeMillis());
            }
        }
        
        public synchronized List<BlockPos> getFluidsToProcess(long currentTime) {
            if (currentTime - lastProcessed < CHUNK_PROCESSING_COOLDOWN) {
                return new ArrayList<>(); // Skip - recently processed
            }
            
            return new ArrayList<>(fluidPositions);
        }
        
        public synchronized void markProcessed(long currentTime) {
            lastProcessed = currentTime;
        }
        
        public synchronized void cleanupExpired(long currentTime) {
            long expiryTime = currentTime - 10000; // 10 seconds expiry
            
            lastUpdateTimes.entrySet().removeIf(entry -> {
                if (entry.getValue() < expiryTime) {
                    BlockPos pos = entry.getKey();
                    fluidPositions.remove(pos);
                    fluidStates.remove(pos);
                    return true;
                }
                return false;
            });
            
            fluidCount = fluidPositions.size();
            isActive = !fluidPositions.isEmpty();
        }
    }
    
    /**
     * Chunk processing state
     */
    public static class ChunkProcessingState {
        public volatile boolean isProcessing = false;
        public volatile boolean isQueued = false;
        public volatile long lastProcessTime = 0;
        public volatile int processCount = 0;
        public volatile double averageProcessTime = 0.0;
        public volatile int priorityLevel = 0; // 0=low, 1=medium, 2=high, 3=emergency
        
        public synchronized void startProcessing() {
            isProcessing = true;
            isQueued = false;
            processCount++;
        }
        
        public synchronized void finishProcessing(long processDuration) {
            isProcessing = false;
            lastProcessTime = System.currentTimeMillis();
            
            // Update average process time
            averageProcessTime = (averageProcessTime * (processCount - 1) + processDuration) / processCount;
        }
        
        public synchronized void queueForProcessing() {
            isQueued = true;
        }
        
        public synchronized boolean shouldProcess(long currentTime) {
            if (isProcessing) return false;
            if (isQueued) return true;
            
            long timeSinceLastProcess = currentTime - lastProcessTime;
            return timeSinceLastProcess >= CHUNK_PROCESSING_COOLDOWN;
        }
    }
    
    /**
     * Get chunk key from position
     */
    private static long getChunkKey(BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return ((long)chunkX & 0xFFFFFFFFL) << 32 | (chunkZ & 0xFFFFFFFFL);
    }
    
    /**
     * Get chunk key from chunk coordinates
     */
    private static long getChunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX & 0xFFFFFFFFL) << 32 | (chunkZ & 0xFFFFFFFFL);
    }
    
    /**
     * Get chunk coordinates from key
     */
    private static ChunkPos getChunkPos(long chunkKey) {
        int chunkX = (int)(chunkKey >>> 32);
        int chunkZ = (int)chunkKey;
        return new ChunkPos(chunkX, chunkZ);
    }
    
    /**
     * Add fluid position to spatial tracking
     */
    public static void addFluidPosition(ServerLevel level, BlockPos pos, FluidState state) {
        long chunkKey = getChunkKey(pos);
        
        // Get or create chunk data
        ChunkFluidData chunkData = chunkFluidData.computeIfAbsent(chunkKey, k -> new ChunkFluidData());
        chunkData.addFluid(pos, state);
        
        // Update processing priority based on fluid density
        updateChunkPriority(chunkKey, chunkData);
        
        // Track active chunks
        if (chunkData.isActive) {
            activeChunks.incrementAndGet();
        }
    }
    
    /**
     * Remove fluid position from spatial tracking
     */
    public static void removeFluidPosition(ServerLevel level, BlockPos pos) {
        long chunkKey = getChunkKey(pos);
        
        ChunkFluidData chunkData = chunkFluidData.get(chunkKey);
        if (chunkData != null) {
            chunkData.removeFluid(pos);
            
            // Clean up empty chunks
            if (!chunkData.isActive) {
                chunkFluidData.remove(chunkKey);
                chunkProcessingState.remove(chunkKey);
                activeChunks.decrementAndGet();
            }
        }
    }
    
    /**
     * Update fluid state in spatial tracking
     */
    public static void updateFluidPosition(ServerLevel level, BlockPos pos, FluidState state) {
        long chunkKey = getChunkKey(pos);
        
        ChunkFluidData chunkData = chunkFluidData.get(chunkKey);
        if (chunkData != null) {
            chunkData.updateFluid(pos, state);
        }
    }
    
    /**
     * Get fluids to process in local area (replaces worldwide access)
     */
    public static List<BlockPos> getLocalFluidsToProcess(ServerLevel level, BlockPos centerPos) {
        long currentTime = System.currentTimeMillis();
        List<BlockPos> localFluids = new ArrayList<>();
        
        // Get center chunk
        long centerChunkKey = getChunkKey(centerPos);
        ChunkPos centerChunk = getChunkPos(centerChunkKey);
        
        // Process chunks in local radius
        for (int dx = -CHUNK_PROCESSING_RADIUS; dx <= CHUNK_PROCESSING_RADIUS; dx++) {
            for (int dz = -CHUNK_PROCESSING_RADIUS; dz <= CHUNK_PROCESSING_RADIUS; dz++) {
                ChunkPos checkChunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                long checkChunkKey = getChunkKey(checkChunk.x, checkChunk.z);
                
                ChunkFluidData chunkData = chunkFluidData.get(checkChunkKey);
                if (chunkData != null && chunkData.isActive) {
                    // Get fluids from this chunk
                    List<BlockPos> chunkFluids = chunkData.getFluidsToProcess(currentTime);
                    localFluids.addAll(chunkFluids);
                    
                    // Mark chunk as processed
                    chunkData.markProcessed(currentTime);
                    
                    // Update statistics
                    chunksProcessed.incrementAndGet();
                    fluidsProcessedLocally.addAndGet(chunkFluids.size());
                } else {
                    // Worldwide access avoided - this is the key optimization
                    worldwideAccessesAvoided.incrementAndGet();
                    chunksSkipped.incrementAndGet();
                }
            }
        }
        
        // Update last processing time
        lastChunkProcessing.set(currentTime);
        
        return localFluids;
    }
    
    /**
     * Check if position should be processed using spatial partitioning
     */
    public static boolean shouldProcessPosition(ServerLevel level, BlockPos pos) {
        long chunkKey = getChunkKey(pos);
        
        ChunkProcessingState processingState = chunkProcessingState.get(chunkKey);
        if (processingState == null) {
            processingState = new ChunkProcessingState();
            chunkProcessingState.put(chunkKey, processingState);
        }
        
        long currentTime = System.currentTimeMillis();
        return processingState.shouldProcess(currentTime);
    }
    
    /**
     * Mark position as being processed
     */
    public static void markPositionProcessing(ServerLevel level, BlockPos pos) {
        long chunkKey = getChunkKey(pos);
        
        ChunkProcessingState processingState = chunkProcessingState.get(chunkKey);
        if (processingState != null) {
            processingState.startProcessing();
        }
    }
    
    /**
     * Mark position processing as complete
     */
    public static void markPositionProcessed(ServerLevel level, BlockPos pos, long processDuration) {
        long chunkKey = getChunkKey(pos);
        
        ChunkProcessingState processingState = chunkProcessingState.get(chunkKey);
        if (processingState != null) {
            processingState.finishProcessing(processDuration);
        }
    }
    
    /**
     * Update chunk processing priority
     */
    private static void updateChunkPriority(long chunkKey, ChunkFluidData chunkData) {
        double priority = 0.0;
        
        // Priority factors:
        // 1. Fluid density (more fluids = higher priority)
        priority += chunkData.fluidCount * 0.1;
        
        // 2. Time since last process (longer = higher priority)
        long timeSinceProcess = System.currentTimeMillis() - chunkData.lastProcessed;
        priority += timeSinceProcess * 0.001;
        
        // 3. Player proximity (near players = higher priority)
        ChunkPos chunkPos = getChunkPos(chunkKey);
        if (PlayerProximityCache.arePlayersNearChunk(null, chunkPos, 3)) {
            priority += 5.0; // Bonus for player proximity
        }
        
        chunkData.processingPriority = priority;
    }
    
    /**
     * Get chunks that need processing (priority ordered)
     */
    public static List<Long> getChunksToProcess() {
        long currentTime = System.currentTimeMillis();
        
        return chunkFluidData.entrySet().stream()
            .filter(entry -> entry.getValue().isActive)
            .filter(entry -> {
                ChunkProcessingState state = chunkProcessingState.get(entry.getKey());
                return state == null || state.shouldProcess(currentTime);
            })
            .sorted((e1, e2) -> Double.compare(e2.getValue().processingPriority, e1.getValue().processingPriority))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    /**
     * Clean up expired data
     */
    public static void cleanupExpiredData() {
        long currentTime = System.currentTimeMillis();
        int cleanedChunks = 0;
        
        for (Map.Entry<Long, ChunkFluidData> entry : chunkFluidData.entrySet()) {
            ChunkFluidData chunkData = entry.getValue();
            chunkData.cleanupExpired(currentTime);
            
            // Remove inactive chunks
            if (!chunkData.isActive) {
                chunkFluidData.remove(entry.getKey());
                chunkProcessingState.remove(entry.getKey());
                cleanedChunks++;
            }
        }
        
        if (cleanedChunks > 0) {
            activeChunks.addAndGet(-cleanedChunks);
        }
    }
    
    /**
     * Get spatial partitioning statistics
     */
    public static String getSpatialStatistics() {
        return String.format(
            "SpatialPartitioning: %d active chunks, %d processed, %d skipped, %d fluids processed locally, %d worldwide accesses avoided",
            activeChunks.get(), chunksProcessed.get(), chunksSkipped.get(),
            fluidsProcessedLocally.get(), worldwideAccessesAvoided.get()
        );
    }
    
    /**
     * Get chunk processing efficiency
     */
    public static double getProcessingEfficiency() {
        int total = chunksProcessed.get() + chunksSkipped.get();
        return total > 0 ? (chunksProcessed.get() * 100.0 / total) : 0.0;
    }
    
    /**
     * Get worldwide access reduction percentage
     */
    public static double getWorldwideAccessReduction() {
        int total = fluidsProcessedLocally.get() + worldwideAccessesAvoided.get();
        return total > 0 ? (worldwideAccessesAvoided.get() * 100.0 / total) : 0.0;
    }
    
    /**
     * Clear all spatial data
     */
    public static void clearAll() {
        chunkFluidData.clear();
        chunkProcessingState.clear();
        
        // Reset statistics
        chunksProcessed.set(0);
        chunksSkipped.set(0);
        fluidsProcessedLocally.set(0);
        worldwideAccessesAvoided.set(0);
        activeChunks.set(0);
        queuedChunks.set(0);
        lastChunkProcessing.set(0);
    }
    
    /**
     * Get system status
     */
    public static String getSystemStatus() {
        return String.format(
            "SpatialPartitioning Status: %d chunks tracked, %.1f%% processing efficiency, %.1f%% worldwide access reduction",
            activeChunks.get(), getProcessingEfficiency(), getWorldwideAccessReduction()
        );
    }
}
