package flowingfluidsfixes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;

/**
 * Player Proximity Cache - Replaces expensive level.players() loops with efficient chunk-based caching
 * Updates every 400-800ms instead of per-fluid, dramatically reducing worldwide player scanning overhead
 */
public class PlayerProximityCache {
    
    // Chunk-based player proximity cache
    private static final ConcurrentHashMap<Long, Boolean> chunkPlayerPresence = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> chunkUpdateTimestamps = new ConcurrentHashMap<>();
    
    // Player position cache for distance calculations
    private static final ConcurrentHashMap<String, PlayerPosition> playerPositions = new ConcurrentHashMap<>();
    private static final AtomicLong lastPlayerUpdate = new AtomicLong(0);
    
    // Cache configuration
    private static final long CACHE_UPDATE_INTERVAL = 600; // 600ms update interval
    private static final long CACHE_EXPIRY_TIME = 2000; // 2 seconds expiry
    private static final int MAX_CACHE_SIZE = 5000; // Prevent memory issues
    
    // Performance tracking
    private static final AtomicInteger cacheHits = new AtomicInteger(0);
    private static final AtomicInteger cacheMisses = new AtomicInteger(0);
    private static final AtomicInteger playerScans = new AtomicInteger(0);
    private static final AtomicInteger cachedLookups = new AtomicInteger(0);
    
    /**
     * Player position data structure
     */
    public static class PlayerPosition {
        public final double x, y, z;
        public final long timestamp;
        public final String playerName;
        
        public PlayerPosition(ServerPlayer player) {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
            this.timestamp = System.currentTimeMillis();
            this.playerName = player.getName().getString();
        }
    }
    
    /**
     * Check if any players are near the given chunk position
     * Uses cached data to avoid expensive player scanning
     */
    public static boolean arePlayersNearChunk(ServerLevel level, BlockPos pos, int chunkRadius) {
        return arePlayersNearChunk(level, new ChunkPos(pos), chunkRadius);
    }
    
    public static boolean arePlayersNearChunk(ServerLevel level, ChunkPos chunkPos, int chunkRadius) {
        cachedLookups.incrementAndGet();
        
        // Check if cache needs updating
        updateCacheIfNeeded(level);
        
        // Check chunk and surrounding chunks for player presence
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos checkChunk = new ChunkPos(chunkPos.x + dx, chunkPos.z + dz);
                long chunkKey = chunkToLong(checkChunk);
                
                Boolean hasPlayer = chunkPlayerPresence.get(chunkKey);
                if (hasPlayer != null && hasPlayer) {
                    cacheHits.incrementAndGet();
                    return true; // Found nearby player
                }
            }
        }
        
        cacheMisses.incrementAndGet();
        return false; // No nearby players found in cache
    }
    
    /**
     * Get the distance to the nearest player
     * Uses cached player positions for efficient calculation
     */
    public static double getDistanceToNearestPlayer(ServerLevel level, BlockPos pos) {
        updateCacheIfNeeded(level);
        
        double minDistance = Double.MAX_VALUE;
        
        for (PlayerPosition playerPos : playerPositions.values()) {
            if (System.currentTimeMillis() - playerPos.timestamp < CACHE_EXPIRY_TIME) {
                double distance = Math.sqrt(
                    Math.pow(pos.getX() - playerPos.x, 2) +
                    Math.pow(pos.getY() - playerPos.y, 2) +
                    Math.pow(pos.getZ() - playerPos.z, 2)
                );
                minDistance = Math.min(minDistance, distance);
            }
        }
        
        return minDistance == Double.MAX_VALUE ? -1 : minDistance;
    }
    
    /**
     * Check if any players are within the specified distance of a position
     */
    public static boolean arePlayersWithinDistance(ServerLevel level, BlockPos pos, double distance) {
        return getDistanceToNearestPlayer(level, pos) <= distance;
    }
    
    /**
     * Get all nearby chunks that have players
     */
    public static List<ChunkPos> getNearbyPlayerChunks(ServerLevel level, BlockPos pos, int chunkRadius) {
        updateCacheIfNeeded(level);
        
        List<ChunkPos> nearbyChunks = new ArrayList<>();
        ChunkPos centerChunk = new ChunkPos(pos);
        
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkPos checkChunk = new ChunkPos(centerChunk.x + dx, centerChunk.z + dz);
                long chunkKey = chunkToLong(checkChunk);
                
                Boolean hasPlayer = chunkPlayerPresence.get(chunkKey);
                if (hasPlayer != null && hasPlayer) {
                    nearbyChunks.add(checkChunk);
                }
            }
        }
        
        return nearbyChunks;
    }
    
    /**
     * Update the cache if it's stale
     * This is the core optimization - updates every 600ms instead of per-fluid
     */
    private static void updateCacheIfNeeded(ServerLevel level) {
        long currentTime = System.currentTimeMillis();
        long lastUpdate = lastPlayerUpdate.get();
        
        // Update cache if it's stale
        if (currentTime - lastUpdate > CACHE_UPDATE_INTERVAL && 
            lastPlayerUpdate.compareAndSet(lastUpdate, currentTime)) {
            
            performCacheUpdate(level);
        }
    }
    
    /**
     * Perform the actual cache update
     * Scans all players and updates chunk presence cache
     */
    private static void performCacheUpdate(ServerLevel level) {
        playerScans.incrementAndGet();
        
        // Clear expired entries
        cleanExpiredEntries();
        
        // Update player positions
        updatePlayerPositions(level);
        
        // Update chunk presence based on new player positions
        updateChunkPresence();
        
        // Enforce cache size limits
        enforceCacheLimits();
    }
    
    /**
     * Update player position cache
     */
    private static void updatePlayerPositions(ServerLevel level) {
        // Clear old positions
        playerPositions.clear();
        
        // Add current player positions
        for (ServerPlayer player : level.players()) {
            playerPositions.put(player.getUUID().toString(), new PlayerPosition(player));
        }
    }
    
    /**
     * Update chunk presence based on player positions
     */
    private static void updateChunkPresence() {
        long currentTime = System.currentTimeMillis();
        
        // Clear old chunk presence data
        chunkPlayerPresence.clear();
        chunkUpdateTimestamps.clear();
        
        // Mark chunks with players
        for (PlayerPosition playerPos : playerPositions.values()) {
            // Mark the chunk the player is in
            ChunkPos playerChunk = new ChunkPos((int)playerPos.x >> 4, (int)playerPos.z >> 4);
            long chunkKey = chunkToLong(playerChunk);
            
            chunkPlayerPresence.put(chunkKey, true);
            chunkUpdateTimestamps.put(chunkKey, currentTime);
            
            // Mark nearby chunks (players can affect adjacent chunks)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkPos nearbyChunk = new ChunkPos(playerChunk.x + dx, playerChunk.z + dz);
                    long nearbyKey = chunkToLong(nearbyChunk);
                    
                    if (!chunkPlayerPresence.containsKey(nearbyKey)) {
                        chunkPlayerPresence.put(nearbyKey, true);
                        chunkUpdateTimestamps.put(nearbyKey, currentTime);
                    }
                }
            }
        }
    }
    
    /**
     * Clean expired entries
     */
    private static void cleanExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        
        // Remove expired chunk entries
        chunkUpdateTimestamps.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > CACHE_EXPIRY_TIME) {
                chunkPlayerPresence.remove(entry.getKey());
                return true;
            }
            return false;
        });
        
        // Remove expired player positions
        playerPositions.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > CACHE_EXPIRY_TIME);
    }
    
    /**
     * Enforce cache size limits to prevent memory issues
     */
    private static void enforceCacheLimits() {
        if (chunkPlayerPresence.size() > MAX_CACHE_SIZE) {
            // Remove oldest entries
            chunkUpdateTimestamps.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e1.getValue(), e2.getValue()))
                .limit(chunkPlayerPresence.size() - MAX_CACHE_SIZE)
                .forEach(entry -> {
                    chunkPlayerPresence.remove(entry.getKey());
                    chunkUpdateTimestamps.remove(entry.getKey());
                });
        }
    }
    
    /**
     * Convert ChunkPos to long key for efficient storage
     */
    private static long chunkToLong(ChunkPos chunk) {
        return ((long)chunk.x & 0xFFFFFFFFL) << 32 | (chunk.z & 0xFFFFFFFFL);
    }
    
    /**
     * Clear all caches (useful for world changes or testing)
     */
    public static void clearAll() {
        chunkPlayerPresence.clear();
        chunkUpdateTimestamps.clear();
        playerPositions.clear();
        lastPlayerUpdate.set(0);
        
        // Reset statistics
        cacheHits.set(0);
        cacheMisses.set(0);
        playerScans.set(0);
        cachedLookups.set(0);
    }
    
    /**
     * Get cache performance statistics
     */
    public static String getCacheStatistics() {
        int total = cacheHits.get() + cacheMisses.get();
        double hitRate = total > 0 ? (cacheHits.get() * 100.0 / total) : 0.0;
        
        return String.format(
            "PlayerProximityCache: %d chunks cached, %d players tracked, %d hits, %d misses (%.1f%% hit rate), %d scans, %d lookups",
            chunkPlayerPresence.size(),
            playerPositions.size(),
            cacheHits.get(),
            cacheMisses.get(),
            hitRate,
            playerScans.get(),
            cachedLookups.get()
        );
    }
    
    /**
     * Get cache efficiency metrics
     */
    public static double getCacheEfficiency() {
        int total = cacheHits.get() + cacheMisses.get();
        return total > 0 ? (cacheHits.get() * 100.0 / total) : 0.0;
    }
    
    /**
     * Check if cache is active and populated
     */
    public static boolean isCacheActive() {
        return !chunkPlayerPresence.isEmpty() && !playerPositions.isEmpty();
    }
    
    /**
     * Get the number of chunks with players
     */
    public static int getActiveChunkCount() {
        return chunkPlayerPresence.size();
    }
    
    /**
     * Get the number of tracked players
     */
    public static int getTrackedPlayerCount() {
        return playerPositions.size();
    }
}
