package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fluid Processing Distance Limit - Implements distance-based fluid processing cutoff
 * 
 * Similar to Flowing Fluids' `fluid_processing_distance` setting, this system:
 * 1. Only processes fluid updates within a configurable distance of players
 * 2. Completely skips distant fluid updates to save CPU for mob AI and game time
 * 3. Caches player positions per-tick to avoid repeated lookups
 * 4. Tracks chunks within processing range for fast checks
 * 
 * This is a HARD CUTOFF - fluids beyond this distance are NOT processed at all,
 * unlike soft priority systems that just delay them.
 */
public class FluidProcessingDistanceLimit {
    private static final Logger LOGGER = LogManager.getLogger(FluidProcessingDistanceLimit.class);
    
    // Default processing distance in chunks (like render distance)
    // 8 chunks = 128 blocks, which is a good balance
    private static volatile int processingDistanceChunks = 8;
    private static volatile int processingDistanceBlocksSq = (8 * 16) * (8 * 16); // 128^2 = 16384
    
    // Player position cache - updated once per tick
    private static final Map<Long, PlayerPositionCache> playerPositions = new ConcurrentHashMap<>();
    private static volatile long lastCacheUpdateTick = -1;
    
    // Chunks within processing range - for fast chunk-level checks
    private static final Set<Long> chunksInRange = ConcurrentHashMap.newKeySet();
    
    // Statistics
    private static final AtomicLong updatesWithinRange = new AtomicLong(0);
    private static final AtomicLong updatesOutOfRange = new AtomicLong(0);
    private static final AtomicInteger currentPlayersTracked = new AtomicInteger(0);
    
    // Performance: Skip detailed distance check for chunks known to be in range
    private static final Map<Long, Long> chunkRangeCache = new ConcurrentHashMap<>();
    private static final int CHUNK_CACHE_VALIDITY_TICKS = 20; // Cache valid for 1 second
    
    /**
     * Set the fluid processing distance in chunks
     * This acts like "render distance" but for fluid updates
     */
    public static void setProcessingDistanceChunks(int chunks) {
        processingDistanceChunks = Math.max(2, Math.min(32, chunks));
        int distanceBlocks = processingDistanceChunks * 16;
        processingDistanceBlocksSq = distanceBlocks * distanceBlocks;
        LOGGER.info("Fluid processing distance set to {} chunks ({} blocks)", 
            processingDistanceChunks, distanceBlocks);
    }
    
    /**
     * Get current processing distance in chunks
     */
    public static int getProcessingDistanceChunks() {
        return processingDistanceChunks;
    }
    
    /**
     * Update player position cache at the start of each tick
     * Called from FluidEventHandler or FluidTickScheduler
     */
    public static void updatePlayerCache(ServerLevel level) {
        long currentTick = level.getGameTime();
        if (currentTick == lastCacheUpdateTick) {
            return; // Already updated this tick
        }
        
        playerPositions.clear();
        chunksInRange.clear();
        
        int playerCount = 0;
        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();
            long playerId = player.getId();
            playerPositions.put(playerId, new PlayerPositionCache(
                pos.getX(), pos.getY(), pos.getZ(),
                new ChunkPos(pos).toLong()
            ));
            
            // Pre-calculate chunks in range for this player
            addChunksInRange(pos);
            playerCount++;
        }
        
        currentPlayersTracked.set(playerCount);
        lastCacheUpdateTick = currentTick;
        
        // Clean up old chunk cache entries
        if (currentTick % 100 == 0) {
            long cutoff = currentTick - CHUNK_CACHE_VALIDITY_TICKS * 2;
            chunkRangeCache.entrySet().removeIf(e -> e.getValue() < cutoff);
        }
    }
    
    /**
     * Add chunks within processing range of a player position
     */
    private static void addChunksInRange(BlockPos playerPos) {
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;
        
        for (int dx = -processingDistanceChunks; dx <= processingDistanceChunks; dx++) {
            for (int dz = -processingDistanceChunks; dz <= processingDistanceChunks; dz++) {
                // Use circular distance for more accurate range
                if (dx * dx + dz * dz <= processingDistanceChunks * processingDistanceChunks) {
                    long chunkKey = ChunkPos.asLong(playerChunkX + dx, playerChunkZ + dz);
                    chunksInRange.add(chunkKey);
                }
            }
        }
    }
    
    /**
     * FAST CHECK: Is this position within fluid processing range of any player?
     * This is called for EVERY fluid update, so it must be extremely fast.
     */
    public static boolean isWithinProcessingRange(BlockPos pos) {
        // Fast path: check chunk-level first
        long chunkKey = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        
        if (!chunksInRange.contains(chunkKey)) {
            updatesOutOfRange.incrementAndGet();
            return false;
        }
        
        // Chunk is in range - do detailed distance check
        // Use squared distance to avoid sqrt (2D only - Y doesn't matter for fluid visibility)
        for (PlayerPositionCache playerPos : playerPositions.values()) {
            int dx = pos.getX() - playerPos.x;
            int dz = pos.getZ() - playerPos.z;
            int distSq = dx * dx + dz * dz;
            if (distSq <= processingDistanceBlocksSq) {
                updatesWithinRange.incrementAndGet();
                return true;
            }
        }
        
        // Chunk was in range but no player close enough
        updatesOutOfRange.incrementAndGet();
        return false;
    }
    
    /**
     * FASTEST CHECK: Is this chunk within processing range?
     * Use this for batch operations on entire chunks
     */
    public static boolean isChunkInProcessingRange(ChunkPos chunkPos) {
        return chunksInRange.contains(chunkPos.toLong());
    }
    
    /**
     * FASTEST CHECK: Is this chunk key within processing range?
     */
    public static boolean isChunkKeyInProcessingRange(long chunkKey) {
        return chunksInRange.contains(chunkKey);
    }
    
    /**
     * Get count of chunks currently in processing range
     */
    public static int getChunksInRangeCount() {
        return chunksInRange.size();
    }
    
    /**
     * Get statistics summary
     */
    public static String getStatsSummary() {
        long inRange = updatesWithinRange.get();
        long outOfRange = updatesOutOfRange.get();
        long total = inRange + outOfRange;
        double skipPercent = total > 0 ? (outOfRange * 100.0 / total) : 0;
        
        return String.format("DistanceLimit: %d chunks, %d players, %d chunks in range, %.1f%% skipped (%d/%d)",
            processingDistanceChunks, currentPlayersTracked.get(), chunksInRange.size(),
            skipPercent, outOfRange, total);
    }
    
    /**
     * Reset statistics
     */
    public static void resetStats() {
        updatesWithinRange.set(0);
        updatesOutOfRange.set(0);
    }
    
    /**
     * Get the number of updates skipped due to distance
     */
    public static long getUpdatesSkippedByDistance() {
        return updatesOutOfRange.get();
    }
    
    /**
     * Get the number of updates processed within range
     */
    public static long getUpdatesWithinRange() {
        return updatesWithinRange.get();
    }
    
    /**
     * Player position cache record
     */
    private record PlayerPositionCache(int x, int y, int z, long chunkKey) {}
}
