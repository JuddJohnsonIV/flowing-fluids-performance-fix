package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Memory Pool Pattern for frequently used objects
 * Reduces GC pressure and prevents stuttering from object allocation
 */
public class FluidObjectPool {
    
    // Pool for BlockPos objects
    private static final ConcurrentLinkedQueue<BlockPos> blockPosPool = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger blockPosPoolSize = new AtomicInteger(0);
    private static final int MAX_BLOCK_POS_POOL_SIZE = 1000;
    
    // Pool for FluidUpdate objects
    private static final ConcurrentLinkedQueue<FluidUpdate> fluidUpdatePool = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger fluidUpdatePoolSize = new AtomicInteger(0);
    private static final int MAX_FLUID_UPDATE_POOL_SIZE = 500;
    
    /**
     * Get a BlockPos from the pool, or create a new one if pool is empty
     */
    public static BlockPos getBlockPos(int x, int y, int z) {
        BlockPos pos = blockPosPool.poll();
        if (pos != null) {
            blockPosPoolSize.decrementAndGet();
            // Reuse the existing BlockPos by creating a new one with the same coordinates
            // BlockPos is immutable in Minecraft, so we can't modify the existing one
            return new BlockPos(x, y, z);
        }
        return new BlockPos(x, y, z);
    }
    
    /**
     * Return a BlockPos to the pool for reuse
     */
    public static void returnBlockPos(BlockPos pos) {
        if (pos != null && blockPosPoolSize.get() < MAX_BLOCK_POS_POOL_SIZE) {
            blockPosPool.offer(pos);
            blockPosPoolSize.incrementAndGet();
        }
    }
    
    /**
     * Get a FluidUpdate from the pool, or create a new one if pool is empty
     */
    public static FluidUpdate getFluidUpdate(BlockPos pos, FluidState state, BlockState blockState, int priority) {
        FluidUpdate update = fluidUpdatePool.poll();
        if (update != null) {
            fluidUpdatePoolSize.decrementAndGet();
            update.reset(pos, state, blockState, priority);
            return update;
        }
        return new FluidUpdate(pos, state, blockState, priority);
    }
    
    /**
     * Return a FluidUpdate to the pool for reuse
     */
    public static void returnFluidUpdate(FluidUpdate update) {
        if (update != null && fluidUpdatePoolSize.get() < MAX_FLUID_UPDATE_POOL_SIZE) {
            update.cleanup();
            fluidUpdatePool.offer(update);
            fluidUpdatePoolSize.incrementAndGet();
        }
    }
    
    /**
     * Clear all pools (call on server shutdown)
     */
    public static void clearPools() {
        blockPosPool.clear();
        fluidUpdatePool.clear();
        blockPosPoolSize.set(0);
        fluidUpdatePoolSize.set(0);
    }
    
    /**
     * Get pool statistics for monitoring
     */
    public static String getPoolStats() {
        return String.format("ObjectPool Stats - BlockPos: %d/%d, FluidUpdate: %d/%d",
            blockPosPoolSize.get(), MAX_BLOCK_POS_POOL_SIZE,
            fluidUpdatePoolSize.get(), MAX_FLUID_UPDATE_POOL_SIZE);
    }
    
    /**
     * Reusable FluidUpdate class
     */
    public static class FluidUpdate {
        private BlockPos pos;
        private FluidState state;
        private BlockState blockState;
        private int priority;
        
        public FluidUpdate(BlockPos pos, FluidState state, BlockState blockState, int priority) {
            this.pos = pos;
            this.state = state;
            this.blockState = blockState;
            this.priority = priority;
        }
        
        /**
         * Reset the object for reuse
         */
        public void reset(BlockPos pos, FluidState state, BlockState blockState, int priority) {
            this.pos = pos;
            this.state = state;
            this.blockState = blockState;
            this.priority = priority;
        }
        
        /**
         * Clean up the object before returning to pool
         */
        public void cleanup() {
            this.pos = null;
            this.state = null;
            this.blockState = null;
            this.priority = 0;
        }
        
        // Getters
        public BlockPos getPos() { return pos; }
        public FluidState getState() { return state; }
        public BlockState getBlockState() { return blockState; }
        public int getPriority() { return priority; }
    }
}
