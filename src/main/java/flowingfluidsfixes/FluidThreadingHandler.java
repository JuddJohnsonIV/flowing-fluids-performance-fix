package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Threading and Concurrency Handler for Flowing Fluids Updates
 * Offloads fluid calculations to separate threads to prevent mob pathfinding lag
 */
public class FluidThreadingHandler {
    private static final Logger LOGGER = LogManager.getLogger(FluidThreadingHandler.class);
    
    // Dedicated thread pool for fluid processing - separate from main tick thread
    private static final int DEFAULT_THREAD_COUNT = Math.max(4, Runtime.getRuntime().availableProcessors() / 2);
    private static ExecutorService fluidThreadPool;
    private static ScheduledExecutorService scheduledExecutor;
    
    // Queue for pending fluid updates to be processed async
    // MEMORY FIX: These queues now have enforced size limits
    private static final Queue<FluidUpdateTask> pendingUpdates = new ConcurrentLinkedQueue<>();
    private static final Queue<FluidUpdateResult> completedResults = new ConcurrentLinkedQueue<>();
    private static final int MAX_PENDING_QUEUE_SIZE = 2000; // Hard limit to prevent memory bloat
    private static final int MAX_COMPLETED_QUEUE_SIZE = 1000;
    
    // Thread pool statistics
    private static final AtomicLong totalAsyncUpdates = new AtomicLong(0);
    private static final AtomicLong asyncUpdatesProcessed = new AtomicLong(0);
    private static final AtomicInteger activeWorkers = new AtomicInteger(0);
    private static final AtomicInteger queuedTasks = new AtomicInteger(0);
    
    // Thread safety locks
    private static final ReentrantLock fluidUpdateLock = new ReentrantLock();
    private static final ReentrantLock cacheAccessLock = new ReentrantLock();
    private static final ReentrantLock syncQueueLock = new ReentrantLock();
    
    // Thread state tracking
    private static final AtomicBoolean isProcessingUpdates = new AtomicBoolean(false);
    private static final AtomicInteger concurrentAccessAttempts = new AtomicInteger(0);
    private static final AtomicInteger lockContentionCount = new AtomicInteger(0);
    
    // Configuration
    private static volatile boolean asyncProcessingEnabled = true;
    private static volatile boolean strictThreadSafety = true;
    private static volatile int maxWaitTimeMs = 50;
    private static volatile int threadPoolSize = DEFAULT_THREAD_COUNT;
    private static volatile int maxQueueSize = 8000;
    
    // Initialize thread pool
    static {
        initializeThreadPool();
    }
    
    /**
     * Initialize or reinitialize the thread pool
     */
    public static synchronized void initializeThreadPool() {
        if (fluidThreadPool != null && !fluidThreadPool.isShutdown()) {
            fluidThreadPool.shutdown();
        }
        if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
            scheduledExecutor.shutdown();
        }
        
        // Create a thread pool with custom thread factory for naming
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "FluidOptimizer-Worker-" + threadNumber.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority than main thread
                return t;
            }
        };
        
        fluidThreadPool = new ThreadPoolExecutor(
            threadPoolSize,
            threadPoolSize * 2,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(maxQueueSize),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy() // If queue full, run on caller thread
        );
        
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FluidOptimizer-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        LOGGER.info("Fluid thread pool initialized with {} threads", threadPoolSize);
    }
    
    /**
     * Submit a fluid update for async processing
     * The calculation will happen off the main thread
     */
    public static void submitAsyncUpdate(ServerLevel level, BlockPos pos, FluidState state, int priority) {
        if (!asyncProcessingEnabled || fluidThreadPool == null || fluidThreadPool.isShutdown()) {
            return;
        }
        
        // MEMORY FIX: Enforce hard queue size limits
        if (queuedTasks.get() >= maxQueueSize || pendingUpdates.size() >= MAX_PENDING_QUEUE_SIZE) {
            // Queue full - skip all updates to prevent memory buildup
            return;
        }
        
        // Also check completed results queue
        if (completedResults.size() >= MAX_COMPLETED_QUEUE_SIZE) {
            // Results backing up - clear old ones
            while (completedResults.size() > MAX_COMPLETED_QUEUE_SIZE / 2) {
                completedResults.poll();
            }
        }
        
        FluidUpdateTask task = new FluidUpdateTask(level, pos, state, priority);
        pendingUpdates.offer(task);
        queuedTasks.incrementAndGet();
        totalAsyncUpdates.incrementAndGet();
        
        // Submit to thread pool
        fluidThreadPool.submit(() -> processFluidUpdateAsync(task));
    }
    
    /**
     * Process a fluid update asynchronously
     * This runs on a worker thread, NOT the main tick thread
     */
    private static void processFluidUpdateAsync(FluidUpdateTask task) {
        activeWorkers.incrementAndGet();
        try {
            // Calculate fluid behavior off-thread
            FluidUpdateResult result = calculateFluidUpdate(task);
            
            if (result != null) {
                // Queue result for main thread to apply
                completedResults.offer(result);
            }
            
            asyncUpdatesProcessed.incrementAndGet();
        } catch (Exception e) {
            LOGGER.debug("Async fluid update failed: {}", e.getMessage());
        } finally {
            activeWorkers.decrementAndGet();
            queuedTasks.decrementAndGet();
        }
    }
    
    /**
     * Calculate fluid update without modifying the world
     * This is the heavy computation that we offload from the main thread
     */
    private static FluidUpdateResult calculateFluidUpdate(FluidUpdateTask task) {
        try {
            // Perform calculations that don't require world modification
            boolean shouldSpread = !task.state.isSource() && task.state.getAmount() > 1;
            boolean isStable = task.state.isSource() || task.state.getAmount() <= 1;
            
            // Calculate priority for when this update should be applied
            int applyPriority = task.priority;
            if (shouldSpread) {
                applyPriority += 2;
            }
            
            return new FluidUpdateResult(
                task.levelKey,
                task.pos,
                shouldSpread,
                isStable,
                applyPriority,
                System.currentTimeMillis()
            );
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Process completed results on the main thread
     * Call this from the server tick event
     */
    public static int applyCompletedResults(ServerLevel level, int maxToProcess) {
        int processed = 0;
        long currentTime = System.currentTimeMillis();
        
        while (processed < maxToProcess && !completedResults.isEmpty()) {
            FluidUpdateResult result = completedResults.poll();
            if (result == null) break;
            
            // Skip stale results (older than 500ms)
            if (currentTime - result.timestamp > 500) {
                continue;
            }
            
            // Apply the result if it matches our level
            if (result.levelKey.equals(level.dimension().location().toString())) {
                try {
                    // Schedule the actual tick on the main thread
                    BlockPos pos = result.pos;
                    FluidState currentState = level.getFluidState(pos);
                    
                    if (!currentState.isEmpty() && !result.isStable) {
                        // Let the vanilla/Flowing Fluids system handle the actual update
                        level.scheduleTick(pos, currentState.getType(), result.applyPriority);
                    }
                    processed++;
                } catch (Exception e) {
                    // Position may have changed, skip
                }
            }
        }
        
        return processed;
    }
    
    /**
     * Get the number of pending async updates
     */
    public static int getPendingCount() {
        return queuedTasks.get();
    }
    
    /**
     * Get the number of active worker threads
     */
    public static int getActiveWorkers() {
        return activeWorkers.get();
    }
    
    /**
     * Check if async processing is enabled
     */
    public static boolean isAsyncEnabled() {
        return asyncProcessingEnabled;
    }
    
    /**
     * Enable or disable async processing
     */
    public static void setAsyncEnabled(boolean enabled) {
        asyncProcessingEnabled = enabled;
        LOGGER.info("Async fluid processing: {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Set the thread pool size (requires reinitialization)
     */
    public static void setThreadPoolSize(int size) {
        threadPoolSize = Math.max(1, Math.min(size, 16));
        LOGGER.info("Thread pool size set to: {} (will apply on restart)", threadPoolSize);
    }
    
    /**
     * Reinitialize with new settings
     */
    public static void applySettings() {
        initializeThreadPool();
    }
    
    /**
     * Acquire lock for fluid updates
     * Returns true if lock acquired, false if timeout
     */
    public static boolean acquireFluidUpdateLock() {
        // In non-strict mode, skip locking for better performance
        if (!strictThreadSafety) {
            isProcessingUpdates.set(true);
            return true;
        }
        
        try {
            boolean acquired = fluidUpdateLock.tryLock();
            if (!acquired) {
                lockContentionCount.incrementAndGet();
                // Try with timeout
                acquired = fluidUpdateLock.tryLock(maxWaitTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            
            if (acquired) {
                isProcessingUpdates.set(true);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Release fluid update lock
     */
    public static void releaseFluidUpdateLock() {
        if (fluidUpdateLock.isHeldByCurrentThread()) {
            isProcessingUpdates.set(false);
            fluidUpdateLock.unlock();
        }
    }
    
    /**
     * Acquire lock for cache access
     */
    public static boolean acquireCacheAccessLock() {
        try {
            boolean acquired = cacheAccessLock.tryLock();
            if (!acquired) {
                concurrentAccessAttempts.incrementAndGet();
                acquired = cacheAccessLock.tryLock(maxWaitTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Release cache access lock
     */
    public static void releaseCacheAccessLock() {
        if (cacheAccessLock.isHeldByCurrentThread()) {
            cacheAccessLock.unlock();
        }
    }
    
    /**
     * Acquire lock for sync queue operations
     */
    public static boolean acquireSyncQueueLock() {
        try {
            return syncQueueLock.tryLock(maxWaitTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    /**
     * Release sync queue lock
     */
    public static void releaseSyncQueueLock() {
        if (syncQueueLock.isHeldByCurrentThread()) {
            syncQueueLock.unlock();
        }
    }
    
    /**
     * Execute a task with fluid update lock
     */
    public static <T> T executeWithLock(java.util.function.Supplier<T> task) {
        if (acquireFluidUpdateLock()) {
            try {
                return task.get();
            } finally {
                releaseFluidUpdateLock();
            }
        } else {
            LOGGER.warn("Could not acquire fluid update lock - task skipped");
            return null;
        }
    }
    
    /**
     * Execute a task with fluid update lock (no return value)
     */
    public static boolean executeWithLock(Runnable task) {
        if (acquireFluidUpdateLock()) {
            try {
                task.run();
                return true;
            } finally {
                releaseFluidUpdateLock();
            }
        } else {
            LOGGER.warn("Could not acquire fluid update lock - task skipped");
            return false;
        }
    }
    
    /**
     * Check if currently processing updates
     */
    public static boolean isProcessingUpdates() {
        return isProcessingUpdates.get();
    }
    
    /**
     * Check if any locks are held by current thread
     */
    public static boolean hasAnyLock() {
        return fluidUpdateLock.isHeldByCurrentThread() ||
               cacheAccessLock.isHeldByCurrentThread() ||
               syncQueueLock.isHeldByCurrentThread();
    }
    
    /**
     * Set strict thread safety mode
     */
    public static void setStrictThreadSafety(boolean strict) {
        strictThreadSafety = strict;
        LOGGER.info("Strict thread safety: {}", strict);
    }
    
    /**
     * Set maximum wait time for locks
     */
    public static void setMaxWaitTimeMs(int ms) {
        maxWaitTimeMs = Math.max(1, ms);
        LOGGER.info("Lock max wait time: {}ms", maxWaitTimeMs);
    }
    
    /**
     * Get threading statistics
     */
    public static String getThreadingStats() {
        return String.format("Threading: contention=%d, concurrent_attempts=%d, processing=%s",
            lockContentionCount.get(), concurrentAccessAttempts.get(), isProcessingUpdates.get());
    }
    
    /**
     * Reset statistics
     */
    public static void resetStats() {
        concurrentAccessAttempts.set(0);
        lockContentionCount.set(0);
        totalAsyncUpdates.set(0);
        asyncUpdatesProcessed.set(0);
        LOGGER.info("Threading statistics reset");
    }
    
    /**
     * Get async processing statistics
     */
    public static String getAsyncStats() {
        return String.format("Async: threads=%d, active=%d, queued=%d, total=%d, processed=%d",
            threadPoolSize, activeWorkers.get(), queuedTasks.get(), 
            totalAsyncUpdates.get(), asyncUpdatesProcessed.get());
    }
    
    /**
     * Shutdown the thread pool gracefully
     * MEMORY FIX: Also clear queues on shutdown
     */
    public static void shutdown() {
        // Clear queues to release memory
        pendingUpdates.clear();
        completedResults.clear();
        queuedTasks.set(0);
        
        if (fluidThreadPool != null) {
            fluidThreadPool.shutdown();
            try {
                if (!fluidThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    fluidThreadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                fluidThreadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        LOGGER.info("Fluid thread pool shutdown complete");
    }
    
    /**
     * Clear all queues - call periodically to prevent memory buildup
     */
    public static void clearQueues() {
        pendingUpdates.clear();
        completedResults.clear();
        queuedTasks.set(0);
        LOGGER.debug("Cleared fluid threading queues");
    }
    
    /**
     * Task representing a fluid update to be processed async
     */
    public static class FluidUpdateTask {
        public final String levelKey;
        public final BlockPos pos;
        public final FluidState state;
        public final int priority;
        public final long submitTime;
        
        public FluidUpdateTask(ServerLevel level, BlockPos pos, FluidState state, int priority) {
            this.levelKey = level.dimension().location().toString();
            this.pos = pos.immutable();
            this.state = state;
            this.priority = priority;
            this.submitTime = System.currentTimeMillis();
        }
    }
    
    /**
     * Result of async fluid calculation
     */
    public static class FluidUpdateResult {
        public final String levelKey;
        public final BlockPos pos;
        public final boolean shouldSpread;
        public final boolean isStable;
        public final int applyPriority;
        public final long timestamp;
        
        public FluidUpdateResult(String levelKey, BlockPos pos, boolean shouldSpread, 
                                  boolean isStable, int applyPriority, long timestamp) {
            this.levelKey = levelKey;
            this.pos = pos;
            this.shouldSpread = shouldSpread;
            this.isStable = isStable;
            this.applyPriority = applyPriority;
            this.timestamp = timestamp;
        }
    }
}
