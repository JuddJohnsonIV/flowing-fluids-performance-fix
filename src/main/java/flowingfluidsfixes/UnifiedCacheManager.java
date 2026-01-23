package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

/**
 * Unified Cache Manager - Consolidates multiple ConcurrentHashMap instances
 * Reduces memory overhead and contention by using composite keys and unified storage
 */
public class UnifiedCacheManager {
    
    // Unified storage with composite keys
    private static final ConcurrentHashMap<CompositeKey, Object> unifiedCache = new ConcurrentHashMap<>();
    
    // Cache statistics
    private static final AtomicInteger totalOperations = new AtomicInteger(0);
    private static final AtomicInteger cacheHits = new AtomicInteger(0);
    private static final AtomicInteger cacheMisses = new AtomicInteger(0);
    
    // Cache size limits to prevent memory issues
    private static final int MAX_CACHE_SIZE = 10000;
    private static final long CACHE_EXPIRY_TIME = 5000; // 5 seconds
    
    // Utility method to convert BlockPos to long key
    private static long blockPosToLong(BlockPos pos) {
        return ((long)pos.getX() & 0x3FFFFFF) << 38 | ((long)pos.getY() & 0xFFF) << 26 | (pos.getZ() & 0x3FFFFFF);
    }
    
    // Composite key types
    public enum KeyType {
        FLUID_FLOW_PRIORITY,
        RIVER_SOURCE_BLOCKS,
        PLAYER_NEARBY_CHUNKS,
        CHUNK_FLUID_GROUPS,
        CHUNK_PROCESSING_PRIORITY,
        THROTTLING_DECISION,
        FLUID_PRESSURE,
        CHUNK_FLOW_RATE,
        CHUNK_NEXT_PROCESS_TIME,
        CHUNK_LAST_FLOW_TIME,
        PENDING_FLUID_CHANGES,
        ZONE_THROTTLING,
        WORLD_CHANGE_TIMESTAMPS,
        PAUSED_FLUIDS,
        FLUID_COOLDOWS,
        BLOCK_STATE_CACHE,
        FLUID_STATE_CACHE,
        LAST_KNOWN_FLUID_STATE,
        FLUID_CHANGE_TIMESTAMPS,
        DISTANCE_CACHE,
        FLUID_STATE_HASH_CACHE,
        FLUID_DEPTH_CACHE
    }
    
    /**
     * Composite key for unified cache storage
     */
    public static class CompositeKey {
        private final KeyType type;
        private final long primary;
        private final long secondary;
        private final long timestamp;
        
        public CompositeKey(KeyType type, long primary) {
            this(type, primary, 0, System.currentTimeMillis());
        }
        
        public CompositeKey(KeyType type, long primary, long secondary) {
            this(type, primary, secondary, System.currentTimeMillis());
        }
        
        public CompositeKey(KeyType type, long primary, long secondary, long timestamp) {
            this.type = type;
            this.primary = primary;
            this.secondary = secondary;
            this.timestamp = timestamp;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CompositeKey that = (CompositeKey) obj;
            return type == that.type && primary == that.primary && secondary == that.secondary;
        }
        
        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + (int) (primary ^ (primary >>> 32));
            result = 31 * result + (int) (secondary ^ (secondary >>> 32));
            return result;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME;
        }
    }
    
    // === FLUID FLOW OPERATIONS ===
    
    public static void setFluidFlowPriority(long chunkKey, int priority) {
        put(KeyType.FLUID_FLOW_PRIORITY, chunkKey, priority);
    }
    
    public static Integer getFluidFlowPriority(long chunkKey) {
        return (Integer) get(KeyType.FLUID_FLOW_PRIORITY, chunkKey);
    }
    
    public static void setRiverSourceBlock(long chunkKey, long timestamp) {
        put(KeyType.RIVER_SOURCE_BLOCKS, chunkKey, timestamp);
    }
    
    public static Long getRiverSourceBlock(long chunkKey) {
        return (Long) get(KeyType.RIVER_SOURCE_BLOCKS, chunkKey);
    }
    
    // === PLAYER PROXITY OPERATIONS ===
    
    public static void setPlayerNearbyChunk(long chunkKey, boolean nearby) {
        put(KeyType.PLAYER_NEARBY_CHUNKS, chunkKey, nearby);
    }
    
    public static Boolean getPlayerNearbyChunk(long chunkKey) {
        return (Boolean) get(KeyType.PLAYER_NEARBY_CHUNKS, chunkKey);
    }
    
    // === CHUNK PROCESSING OPERATIONS ===
    
    public static void setChunkFluidGroups(long chunkKey, List<BlockPos> positions) {
        put(KeyType.CHUNK_FLUID_GROUPS, chunkKey, positions);
    }
    
    @SuppressWarnings("unchecked")
    public static List<BlockPos> getChunkFluidGroups(long chunkKey) {
        return (List<BlockPos>) get(KeyType.CHUNK_FLUID_GROUPS, chunkKey);
    }
    
    public static void setChunkProcessingPriority(long chunkKey, int priority) {
        put(KeyType.CHUNK_PROCESSING_PRIORITY, chunkKey, priority);
    }
    
    public static Integer getChunkProcessingPriority(long chunkKey) {
        return (Integer) get(KeyType.CHUNK_PROCESSING_PRIORITY, chunkKey);
    }
    
    // === THROTTLING OPERATIONS ===
    
    public static void setThrottlingDecision(long positionKey, Object decision) {
        put(KeyType.THROTTLING_DECISION, positionKey, decision);
    }
    
    public static Object getThrottlingDecision(long positionKey) {
        return get(KeyType.THROTTLING_DECISION, positionKey);
    }
    
    // === FLUID STATE OPERATIONS ===
    
    public static void setBlockState(BlockPos pos, BlockState state) {
        put(KeyType.BLOCK_STATE_CACHE, blockPosToLong(pos), 0, state);
    }
    
    public static BlockState getBlockState(BlockPos pos) {
        return (BlockState) get(KeyType.BLOCK_STATE_CACHE, blockPosToLong(pos));
    }
    
    public static void setFluidState(BlockPos pos, FluidState state) {
        put(KeyType.FLUID_STATE_CACHE, blockPosToLong(pos), 0, state);
    }
    
    public static FluidState getFluidState(BlockPos pos) {
        return (FluidState) get(KeyType.FLUID_STATE_CACHE, blockPosToLong(pos));
    }
    
    public static void setLastKnownFluidState(BlockPos pos, FluidState state) {
        put(KeyType.LAST_KNOWN_FLUID_STATE, blockPosToLong(pos), 0, state);
    }
    
    public static FluidState getLastKnownFluidState(BlockPos pos) {
        return (FluidState) get(KeyType.LAST_KNOWN_FLUID_STATE, blockPosToLong(pos));
    }
    
    // === FLUID COOLDOWN OPERATIONS ===
    
    public static void setPausedFluid(BlockPos pos, long timestamp) {
        put(KeyType.PAUSED_FLUIDS, blockPosToLong(pos), timestamp);
    }
    
    public static Long getPausedFluid(BlockPos pos) {
        return (Long) get(KeyType.PAUSED_FLUIDS, blockPosToLong(pos));
    }
    
    public static void setFluidCooldown(BlockPos pos, long timestamp) {
        put(KeyType.FLUID_COOLDOWS, blockPosToLong(pos), timestamp);
    }
    
    public static Long getFluidCooldown(BlockPos pos) {
        return (Long) get(KeyType.FLUID_COOLDOWS, blockPosToLong(pos));
    }
    
    // === CORE CACHE OPERATIONS ===
    
    private static void put(KeyType type, long primary, Object value) {
        totalOperations.incrementAndGet();
        cleanExpiredEntries();
        
        CompositeKey key = new CompositeKey(type, primary);
        unifiedCache.put(key, value);
        
        // Enforce cache size limit
        if (unifiedCache.size() > MAX_CACHE_SIZE) {
            cleanupOldestEntries();
        }
    }
    
    private static void put(KeyType type, long primary, long secondary, Object value) {
        totalOperations.incrementAndGet();
        cleanExpiredEntries();
        
        CompositeKey key = new CompositeKey(type, primary, secondary);
        unifiedCache.put(key, value);
        
        // Enforce cache size limit
        if (unifiedCache.size() > MAX_CACHE_SIZE) {
            cleanupOldestEntries();
        }
    }
    
    private static Object get(KeyType type, long primary) {
        totalOperations.incrementAndGet();
        
        CompositeKey key = new CompositeKey(type, primary);
        Object value = unifiedCache.get(key);
        
        if (value != null) {
            cacheHits.incrementAndGet();
            return value;
        } else {
            cacheMisses.incrementAndGet();
            return null;
        }
    }
    
    // === CACHE MAINTENANCE ===
    
    private static void cleanExpiredEntries() {
        // Clean expired entries periodically (every 100 operations)
        if (totalOperations.get() % 100 == 0) {
            unifiedCache.entrySet().removeIf(entry -> entry.getKey().isExpired());
        }
    }
    
    private static void cleanupOldestEntries() {
        // Remove oldest 10% of entries when cache is full
        int toRemove = MAX_CACHE_SIZE / 10;
        unifiedCache.entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e1.getKey().timestamp, e2.getKey().timestamp))
            .limit(toRemove)
            .forEach(entry -> unifiedCache.remove(entry.getKey()));
    }
    
    public static void clearAll() {
        unifiedCache.clear();
        totalOperations.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
    }
    
    // === STATISTICS ===
    
    public static String getCacheStatistics() {
        int total = totalOperations.get();
        int hits = cacheHits.get();
        int misses = cacheMisses.get();
        double hitRate = total > 0 ? (hits * 100.0 / total) : 0.0;
        
        return String.format(
            "UnifiedCache: %d total ops, %d hits, %d misses (%.1f%% hit rate), %d entries",
            total, hits, misses, hitRate, unifiedCache.size()
        );
    }
    
    public static int getCacheSize() {
        return unifiedCache.size();
    }
    
    public static void resetStatistics() {
        totalOperations.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
    }
}