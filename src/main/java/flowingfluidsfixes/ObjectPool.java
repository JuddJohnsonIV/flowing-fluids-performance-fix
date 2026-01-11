package flowingfluidsfixes;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Simple object pool implementation to reduce GC pressure from frequent object creation.
 * Addresses BlockPos object creation bottleneck (867 operations identified in Spark profile).
 * 
 * @param <T> Type of objects to pool
 */
public class ObjectPool<T> {
    private final ConcurrentLinkedQueue<T> pool = new ConcurrentLinkedQueue<>();
    private final Supplier<T> factory;
    private final Consumer<T> resetAction;
    private final Consumer<T> cleanupAction;
    
    public ObjectPool(Supplier<T> factory, Consumer<T> resetAction, Consumer<T> cleanupAction) {
        this.factory = factory;
        this.resetAction = resetAction;
        this.cleanupAction = cleanupAction;
    }
    
    /**
     * Acquire an object from the pool, or create a new one if pool is empty.
     */
    public T acquire() {
        T obj = pool.poll();
        if (obj == null) {
            obj = factory.get();
        }
        return obj;
    }
    
    /**
     * Release an object back to the pool after resetting it.
     */
    public void release(T obj) {
        if (obj != null) {
            try {
                resetAction.accept(obj);
                pool.offer(obj);
            } catch (Exception e) {
                // If reset fails, cleanup and don't return to pool
                try {
                    cleanupAction.accept(obj);
                } catch (Exception cleanupException) {
                    // Ignore cleanup errors
                }
            }
        }
    }
    
    /**
     * Get current pool size for monitoring.
     */
    public int size() {
        return pool.size();
    }
    
    /**
     * Clear the pool and cleanup all objects.
     */
    public void clear() {
        T obj;
        while ((obj = pool.poll()) != null) {
            try {
                cleanupAction.accept(obj);
            } catch (Exception e) {
                // Ignore cleanup errors during clear
            }
        }
    }
}
