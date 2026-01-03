package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Memory Optimization System for Fluid Data Structures
 * Step 24: Optimize memory usage related to Flowing Fluids data structures
 * Prevents memory leaks and reduces garbage collection pressure
 */
public class FluidMemoryOptimizer {
    private static final Logger LOGGER = LogManager.getLogger(FluidMemoryOptimizer.class);
    
    // Memory tracking
    private static final AtomicLong estimatedMemoryUsage = new AtomicLong(0);
    private static final AtomicLong cleanupCycles = new AtomicLong(0);
    private static final AtomicLong entriesRemoved = new AtomicLong(0);
    
    // Cache size limits
    private static final int MAX_POSITION_CACHE_SIZE = 10000;
    private static final int MAX_STATE_CACHE_SIZE = 5000;
    private static final int MAX_SYNC_QUEUE_SIZE = 2000;
    
    // Cleanup intervals
    private static final int CLEANUP_INTERVAL_TICKS = 600; // Every 30 seconds
    private static int tickCounter = 0;
    
    // Weak reference pools for commonly accessed positions
    private static final Map<Long, WeakReference<BlockPos>> positionPool = new ConcurrentHashMap<>();
    
    /**
     * Perform periodic memory cleanup
     * Call from server tick
     */
    public static void performPeriodicCleanup() {
        tickCounter++;
        
        if (tickCounter >= CLEANUP_INTERVAL_TICKS) {
            tickCounter = 0;
            performFullCleanup();
        }
    }
    
    /**
     * Perform full memory cleanup across all caches
     */
    public static void performFullCleanup() {
        cleanupCycles.incrementAndGet();
        long startTime = System.nanoTime();
        long removedCount = 0;
        
        // Clean up position pool
        removedCount += cleanupPositionPool();
        
        // Clean up FlowingFluidsCalculationOptimizer cache
        if (shouldCleanupCalculationCache()) {
            FlowingFluidsCalculationOptimizer.clearCache();
            removedCount += MAX_STATE_CACHE_SIZE / 2; // Estimate
        }
        
        // Clean up MultiplayerFluidSync queue if too large
        if (MultiplayerFluidSync.getSyncQueueSize() > MAX_SYNC_QUEUE_SIZE) {
            MultiplayerFluidSync.clearPendingSync();
            removedCount += MAX_SYNC_QUEUE_SIZE;
        }
        
        // Clean up FluidTickScheduler deferred queue if too large
        if (FluidTickScheduler.getDeferredQueueSize() > MAX_POSITION_CACHE_SIZE) {
            FluidTickScheduler.resetCounters();
            removedCount += MAX_POSITION_CACHE_SIZE / 2;
        }
        
        entriesRemoved.addAndGet(removedCount);
        
        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        LOGGER.debug("Memory cleanup completed: {} entries removed in {}ms", removedCount, elapsed);
        
        // Suggest GC if memory pressure is high
        if (isMemoryPressureHigh()) {
            LOGGER.info("High memory pressure detected, suggesting GC");
            System.gc();
        }
    }
    
    /**
     * Clean up weak reference position pool
     */
    private static long cleanupPositionPool() {
        long removed = 0;
        var iterator = positionPool.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().get() == null) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }
    
    /**
     * Check if calculation cache should be cleaned
     */
    private static boolean shouldCleanupCalculationCache() {
        // Clean if memory pressure is high
        if (isMemoryPressureHigh()) {
            return true;
        }
        
        // Clean periodically regardless
        return cleanupCycles.get() % 5 == 0;
    }
    
    /**
     * Check if memory pressure is high
     */
    private static boolean isMemoryPressureHigh() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double usageRatio = (double) usedMemory / maxMemory;
        return usageRatio > 0.85; // More than 85% used
    }
    
    /**
     * Get pooled BlockPos to reduce object creation
     */
    public static BlockPos getPooledPosition(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        WeakReference<BlockPos> ref = positionPool.get(key);
        
        if (ref != null) {
            BlockPos pos = ref.get();
            if (pos != null) {
                return pos;
            }
        }
        
        // Create new and cache
        BlockPos newPos = new BlockPos(x, y, z);
        if (positionPool.size() < MAX_POSITION_CACHE_SIZE) {
            positionPool.put(key, new WeakReference<>(newPos));
        }
        return newPos;
    }
    
    /**
     * Get pooled BlockPos from existing position
     */
    public static BlockPos getPooledPosition(BlockPos pos) {
        return getPooledPosition(pos.getX(), pos.getY(), pos.getZ());
    }
    
    /**
     * Estimate memory usage of a cache
     */
    public static long estimateCacheMemory(int entries, int bytesPerEntry) {
        return (long) entries * bytesPerEntry;
    }
    
    /**
     * Update estimated memory usage
     */
    public static void updateMemoryEstimate() {
        long estimate = 0;
        
        // Position pool
        estimate += positionPool.size() * 48; // Approximate bytes per entry
        
        // Deferred queue
        estimate += FluidTickScheduler.getDeferredQueueSize() * 64;
        
        // Sync queue
        estimate += MultiplayerFluidSync.getSyncQueueSize() * 32;
        
        estimatedMemoryUsage.set(estimate);
    }
    
    /**
     * Get memory statistics
     */
    public static String getMemoryStats() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double usagePercent = (double) usedMemory / maxMemory * 100;
        
        updateMemoryEstimate();
        
        return String.format("Memory: %.1f%% used, fluid caches ~%d KB, %d cleanups, %d entries removed",
            usagePercent, estimatedMemoryUsage.get() / 1024, 
            cleanupCycles.get(), entriesRemoved.get());
    }
    
    /**
     * Force immediate cleanup
     */
    public static void forceCleanup() {
        LOGGER.info("Forcing immediate memory cleanup");
        performFullCleanup();
        System.gc();
    }
    
    /**
     * Clear all caches (for world unload)
     */
    public static void clearAllCaches() {
        LOGGER.info("Clearing all fluid optimization caches");
        
        positionPool.clear();
        FlowingFluidsCalculationOptimizer.clearCache();
        MultiplayerFluidSync.clearPendingSync();
        FluidTickScheduler.resetCounters();
        
        entriesRemoved.set(0);
        cleanupCycles.set(0);
        estimatedMemoryUsage.set(0);
    }
    
    /**
     * Get cleanup cycle count
     */
    public static long getCleanupCycles() {
        return cleanupCycles.get();
    }
    
    /**
     * Get entries removed count
     */
    public static long getEntriesRemoved() {
        return entriesRemoved.get();
    }
}
