package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TRUE Flowing Fluids optimization hook
 * 
 * This class intercepts Flowing Fluids' actual fluid flow calculations
 * and skips unnecessary neighbor checks when fluids are unlikely to move.
 * 
 * This provides REAL performance improvements by reducing computational work,
 * not just delaying ticks.
 */
public class FlowingFluidsCalculationOptimizer {
    private static final Logger LOGGER = LogManager.getLogger(FlowingFluidsCalculationOptimizer.class);
    
    // Performance tracking
    private static final AtomicLong totalCalculations = new AtomicLong(0);
    private static final AtomicLong skippedCalculations = new AtomicLong(0);
    private static final AtomicInteger optimizationsThisTick = new AtomicInteger(0);
    
    // Cache for recent fluid states to avoid redundant calculations
    private static final ConcurrentHashMap<BlockPos, FluidState> fluidStateCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, Long> lastCalculationTime = new ConcurrentHashMap<>();
    
    // Optimization thresholds - MORE AGGRESSIVE to protect mob performance
    private static final int STABLE_FLUID_THRESHOLD = 20; // Reduced from 40 to skip sooner if fluid hasn't changed
    private static final int COMPLETELY_STATIC_THRESHOLD = 100; // Reduced from 200 to mark as static sooner
    private static final int MAX_CACHE_SIZE = 40000; // Reduced from 50000 to manage memory better
    
    // COMPLETELY STATIC fluids - these are lakes, pools, oceans that NEVER change
    // They only wake up when a neighbor block changes
    private static final ConcurrentHashMap<BlockPos, StaticFluidEntry> staticFluidCache = new ConcurrentHashMap<>();
    private static final AtomicLong staticFluidsSkipped = new AtomicLong(0);
    
    // Stable source block tracking - source blocks in equilibrium
    private static final ConcurrentHashMap<BlockPos, Long> stableSourceBlocks = new ConcurrentHashMap<>();
    private static final AtomicLong stableSourcesSkipped = new AtomicLong(0);
    
    static {
        LOGGER.info("Flowing Fluids Calculation Optimizer initialized - REAL performance optimization active");
    }
    
    /**
     * Hook point: Called before Flowing Fluids calculates fluid flow for a position
     * Returns true if the calculation should be SKIPPED (performance optimization)
     * 
     * AGGRESSIVE OPTIMIZATION: Stable fluids are skipped entirely to protect mob AI
     */
    public static boolean shouldSkipFluidCalculation(ServerLevel level, BlockPos pos, FluidState currentState) {
        totalCalculations.incrementAndGet();
        
        // Reset counter each tick
        if (optimizationsThisTick.get() > 1000) {
            optimizationsThisTick.set(0);
        }
        
        // PRIORITY 0: Skip COMPLETELY STATIC fluids (lakes, pools, oceans)
        // These only wake up when notifyFluidChanged is called
        if (isCompletelyStaticFluid(pos, currentState)) {
            staticFluidsSkipped.incrementAndGet();
            return true; // SKIP - this fluid hasn't moved in a very long time
        }
        
        // PRIORITY 1: Skip stable SOURCE BLOCKS in equilibrium
        // Source blocks surrounded by other sources don't need recalculation
        if (currentState.isSource() && isStableSourceBlock(level, pos, currentState)) {
            stableSourcesSkipped.incrementAndGet();
            skippedCalculations.incrementAndGet();
            return true; // SKIP - stable source block
        }
        
        // PRIORITY 2: Skip stable fluids that haven't changed recently
        if (isStableFluid(pos, currentState)) {
            skippedCalculations.incrementAndGet();
            optimizationsThisTick.incrementAndGet();
            return true; // SKIP calculation
        }
        
        // PRIORITY 3: Skip fluids in equilibrium (all neighbors same level or higher)
        if (isFluidInEquilibrium(level, pos, currentState)) {
            skippedCalculations.incrementAndGet();
            optimizationsThisTick.incrementAndGet();
            markAsStatic(pos, currentState); // Start tracking as potentially static
            return true; // SKIP calculation
        }
        
        // PRIORITY 4: Skip isolated fluids with no neighbors
        if (isIsolatedFluid(level, pos, currentState)) {
            skippedCalculations.incrementAndGet();
            optimizationsThisTick.incrementAndGet();
            return true; // SKIP calculation
        }
        
        // PRIORITY 5: Skip distant fluids from players (more aggressive - 48 blocks)
        if (isDistantFromPlayers(level, pos)) {
            skippedCalculations.incrementAndGet();
            optimizationsThisTick.incrementAndGet();
            return true; // SKIP calculation
        }
        
        // PRIORITY 6: Skip calculations with stable fluid pressure
        if (hasStableFluidPressure(level, pos, currentState)) {
            skippedCalculations.incrementAndGet();
            optimizationsThisTick.incrementAndGet();
            return true; // SKIP calculation
        }
        
        // PRIORITY 7: Skip calculations in high-pressure areas when stable
        if (isInHighPressureArea(level, pos) && isStableFluid(pos, currentState)) {
            skippedCalculations.incrementAndGet();
            optimizationsThisTick.incrementAndGet();
            return true; // SKIP calculation
        }
        
        // This fluid needs calculation - remove from static tracking
        staticFluidCache.remove(pos);
        stableSourceBlocks.remove(pos);
        
        // Update cache and proceed with calculation
        updateCache(pos, currentState);
        return false; // PROCEED with calculation
    }
    
    /**
     * Check if fluid is COMPLETELY STATIC (hasn't changed in a very long time)
     * These are lakes, pools, oceans that never flow
     */
    private static boolean isCompletelyStaticFluid(BlockPos pos, FluidState currentState) {
        StaticFluidEntry entry = staticFluidCache.get(pos);
        if (entry == null) return false;
        
        // Check if state matches
        if (entry.fluidLevel != currentState.getAmount() || entry.isSource != currentState.isSource()) {
            staticFluidCache.remove(pos); // State changed - no longer static
            return false;
        }
        
        // Check if it's been static long enough
        long ticksSinceLastChange = (System.currentTimeMillis() - entry.lastChangeTime) / 50;
        return ticksSinceLastChange >= COMPLETELY_STATIC_THRESHOLD;
    }
    
    /**
     * Mark a fluid as potentially static (start tracking)
     */
    private static void markAsStatic(BlockPos pos, FluidState state) {
        staticFluidCache.computeIfAbsent(pos.immutable(), k -> 
            new StaticFluidEntry(state.getAmount(), state.isSource(), System.currentTimeMillis()));
    }
    
    /**
     * Check if a source block is stable (surrounded by other sources or solid blocks)
     */
    private static boolean isStableSourceBlock(ServerLevel level, BlockPos pos, FluidState state) {
        if (!state.isSource()) return false;
        
        // Check if we've already confirmed this source is stable
        Long lastCheck = stableSourceBlocks.get(pos);
        if (lastCheck != null) {
            long elapsed = System.currentTimeMillis() - lastCheck;
            if (elapsed < 5000) { // Recheck every 5 seconds
                return true;
            }
        }
        
        // Check all horizontal neighbors - must be sources or solid blocks
        int stableNeighbors = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (!level.isInWorldBounds(neighbor)) {
                stableNeighbors++; // World edge counts as stable
                continue;
            }
            
            FluidState neighborFluid = level.getFluidState(neighbor);
            if (neighborFluid.isSource() && neighborFluid.getType().isSame(state.getType())) {
                stableNeighbors++; // Adjacent source of same type
            } else if (isBlockSolid(level, neighbor)) {
                stableNeighbors++; // Solid block
            }
        }
        
        // Also check below - must be solid or source
        BlockPos below = pos.below();
        boolean belowStable;
        if (level.isInWorldBounds(below)) {
            FluidState belowFluid = level.getFluidState(below);
            belowStable = isBlockSolid(level, below) || 
                         (belowFluid.isSource() && belowFluid.getType().isSame(state.getType()));
        } else {
            belowStable = true; // Below world = stable
        }
        
        // Stable if 4 horizontal neighbors are stable AND below is stable
        if (stableNeighbors >= 4 && belowStable) {
            stableSourceBlocks.put(pos.immutable(), System.currentTimeMillis());
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if fluid is in equilibrium (can't flow anywhere)
     */
    private static boolean isFluidInEquilibrium(ServerLevel level, BlockPos pos, FluidState state) {
        if (state.isEmpty()) return false;
        int myLevel = state.getAmount();
        
        // Check below - if below is solid or full, vertical flow is blocked
        BlockPos below = pos.below();
        if (level.isInWorldBounds(below)) {
            FluidState belowState = level.getFluidState(below);
            boolean canFlowDown = belowState.isEmpty() || 
                (belowState.getType().isSame(state.getType()) && belowState.getAmount() < 8);
            if (canFlowDown) return false; // Can flow down - not in equilibrium
        }
        
        // Check horizontal neighbors - can only flow to lower levels
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (!level.isInWorldBounds(neighbor)) continue;
            
            if (level.getBlockState(neighbor).isAir()) {
                return false; // Can flow into air
            }
            
            FluidState neighborState = level.getFluidState(neighbor);
            if (!neighborState.isEmpty() && neighborState.getType().isSame(state.getType())) {
                if (neighborState.getAmount() < myLevel - 1) {
                    return false; // Can flow to lower neighbor
                }
            }
        }
        
        return true; // All directions blocked or in equilibrium
    }
    
    /**
     * Notify that a fluid changed - wake up nearby static fluids
     */
    public static void notifyFluidChanged(BlockPos pos) {
        // Remove this position and neighbors from static cache
        staticFluidCache.remove(pos);
        stableSourceBlocks.remove(pos);
        
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            staticFluidCache.remove(neighbor);
            stableSourceBlocks.remove(neighbor);
        }
    }
    
    /**
     * Static fluid entry for tracking completely static fluids
     */
    private record StaticFluidEntry(int fluidLevel, boolean isSource, long lastChangeTime) {}
    
    /**
     * Hook point: Called after Flowing Fluids completes fluid flow calculation
     * Updates our cache with the new fluid state
     */
    public static void onFluidCalculationComplete(BlockPos pos, FluidState newState) {
        updateCache(pos, newState);
        lastCalculationTime.put(pos, System.currentTimeMillis());
    }
    
    /**
     * OPTIMIZATION 1: Check if fluid is stable (state unchanged for a while)
     * A fluid is stable if:
     * 1. We have a cached state for it
     * 2. The cached state matches the current state
     * 3. It's been at least STABLE_FLUID_THRESHOLD ticks since last change
     */
    private static boolean isStableFluid(BlockPos pos, FluidState currentState) {
        FluidState cachedState = fluidStateCache.get(pos);
        if (cachedState == null) return false;
        
        // If state doesn't match cached, it's not stable
        if (!cachedState.equals(currentState)) {
            return false;
        }
        
        Long lastCalc = lastCalculationTime.get(pos);
        if (lastCalc == null) return false;
        
        long timeSinceLastCalc = System.currentTimeMillis() - lastCalc;
        long ticksSinceLastCalc = timeSinceLastCalc / 50; // 50ms per tick
        
        // Skip if fluid has been stable for STABLE_FLUID_THRESHOLD ticks
        return ticksSinceLastCalc >= STABLE_FLUID_THRESHOLD;
    }
    
    /**
     * OPTIMIZATION 2: Check if fluid is isolated (no neighboring fluids)
     */
    private static boolean isIsolatedFluid(ServerLevel level, BlockPos pos, FluidState currentState) {
        if (currentState.isEmpty()) return false;
        
        int neighboringFluids = 0;
        
        // Check all 6 neighbors for fluids
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            if (!level.isInWorldBounds(neighborPos)) continue;
            
            FluidState neighborState = level.getFluidState(neighborPos);
            if (!neighborState.isEmpty() && neighborState.getType().isSame(currentState.getType())) {
                neighboringFluids++;
                if (neighboringFluids >= 2) return false; // Has enough neighbors
            }
        }
        
        // Skip if isolated (0 or 1 neighboring fluids of same type)
        return neighboringFluids <= 1;
    }
    
    /**
     * OPTIMIZATION 3: Check if fluid is too far from any players
     * MORE AGGRESSIVE: 48 blocks instead of 64 to reduce CPU load
     */
    private static boolean isDistantFromPlayers(ServerLevel level, BlockPos pos) {
        return level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 48, false) == null;
    }
    
    /**
     * OPTIMIZATION 5: Check for fluid pressure optimization
     * Skip calculations when fluid pressure is stable
     */
    private static boolean hasStableFluidPressure(ServerLevel level, BlockPos pos, FluidState currentState) {
        // Check fluid above for pressure
        BlockPos above = pos.above();
        if (!level.isInWorldBounds(above)) {
            return false;
        }
        
        FluidState aboveState = level.getFluidState(above);
        if (aboveState.isEmpty() || !aboveState.getType().isSame(currentState.getType())) {
            return false; // No pressure from above
        }
        
        // Check if pressure is stable (same fluid above)
        return fluidStateCache.get(above) != null && 
               fluidStateCache.get(above).equals(aboveState);
    }
    
    /**
     * Check if a block is solid (blocks fluid flow)
     * Uses non-deprecated method to check if block prevents fluid passage
     */
    private static boolean isBlockSolid(ServerLevel level, BlockPos pos) {
        var blockState = level.getBlockState(pos);
        // A block is "solid" for fluid purposes if it's not air, not a fluid, and has collision
        // Using getCollisionShape check instead of deprecated blocksMotion()
        return !blockState.isAir() && blockState.getFluidState().isEmpty() && 
               !blockState.getCollisionShape(level, pos).isEmpty();
    }
    
    /**
     * OPTIMIZATION 7: Check if fluid is in high-pressure area
     * Areas with many fluids above create pressure
     */
    private static boolean isInHighPressureArea(ServerLevel level, BlockPos pos) {
        int fluidCount = 0;
        
        // Check 3x3x3 area around position for fluid density
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = pos.offset(x, y, z);
                    if (level.isInWorldBounds(checkPos)) {
                        FluidState state = level.getFluidState(checkPos);
                        if (!state.isEmpty()) {
                            fluidCount++;
                        }
                    }
                }
            }
        }
        
        // High pressure if 8+ fluids in 3x3x3 area
        return fluidCount >= 8;
    }
    
    /**
     * Update our fluid state cache
     */
    private static void updateCache(BlockPos pos, FluidState state) {
        // Prevent cache from growing too large
        if (fluidStateCache.size() > MAX_CACHE_SIZE) {
            // Clear oldest entries
            fluidStateCache.clear();
            lastCalculationTime.clear();
            LOGGER.debug("Cleared fluid calculation cache to prevent memory issues");
        }
        
        fluidStateCache.put(pos, state);
        lastCalculationTime.put(pos, System.currentTimeMillis());
    }
    
    /**
     * Get optimization statistics
     */
    public static String getOptimizationStats() {
        long total = totalCalculations.get();
        long skipped = skippedCalculations.get();
        long staticSkipped = staticFluidsSkipped.get();
        long sourceSkipped = stableSourcesSkipped.get();
        double skipPercent = total > 0 ? (skipped * 100.0 / total) : 0;
        
        return String.format("FluidOpt: %d total, %d skipped (%.1f%%), %d static, %d stable sources, cache=%d",
                total, skipped, skipPercent, staticSkipped, sourceSkipped, staticFluidCache.size());
    }
    
    /**
     * Clear static fluid caches (call on dimension change or periodically)
     */
    public static void clearStaticCaches() {
        staticFluidCache.clear();
        stableSourceBlocks.clear();
        LOGGER.info("Static fluid caches cleared");
    }
    
    /**
     * Reset tick counter
     */
    public static void resetTickCounter() {
        optimizationsThisTick.set(0);
    }
    
    /**
     * Clear cache (call on world unload)
     */
    public static void clearCache() {
        fluidStateCache.clear();
        lastCalculationTime.clear();
        LOGGER.info("Flowing Fluids calculation cache cleared");
    }
}
