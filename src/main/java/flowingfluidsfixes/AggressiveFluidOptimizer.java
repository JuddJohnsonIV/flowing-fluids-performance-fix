package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.Direction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class AggressiveFluidOptimizer {
    private static final Logger LOGGER = LogManager.getLogger(AggressiveFluidOptimizer.class);
    
    // Optimization parameters - now configurable based on optimization level
    private static int distanceThreshold = 32;
    private static int delayMultiplier = 20;
    private static int maxUpdatesPerTick = 25;
    private static int updateCooldown = 2000;
    private static int criticalOnlyDistance = 16;
    
    
    // Performance tracking
    private static final AtomicLong totalUpdates = new AtomicLong(0);
    private static final AtomicLong skippedUpdates = new AtomicLong(0);
    private static final AtomicLong delayedUpdates = new AtomicLong(0);
    private static final AtomicInteger updatesThisTick = new AtomicInteger(0);
    
    // Track recent updates to prevent spam
    private static final Map<BlockPos, Long> recentUpdates = new ConcurrentHashMap<>();
    
    static {
        LOGGER.info("Aggressive Fluid Optimizer initialized");
        updateOptimizationParameters();
    }
    
    /**
     * Update optimization parameters based on current config level
     * TUNED FOR MOB RESPONSIVENESS - very low update limits to preserve mob AI time
     */
    public static void updateOptimizationParameters() {
        var level = FlowingFluidsOptimizationConfig.optimizationLevel.get();
        switch (level) {
            case AGGRESSIVE -> {
                distanceThreshold = 16;      // Smaller distance = more aggressive
                delayMultiplier = 40;        // Higher delay = more aggressive
                maxUpdatesPerTick = 5;       // VERY few updates to preserve mob AI
                updateCooldown = 5000;       // Longer cooldown = more aggressive
                criticalOnlyDistance = 8;    // Only immediate area is critical
                LOGGER.debug("Optimization set to AGGRESSIVE mode - mob-friendly");
            }
            case BALANCED -> {
                distanceThreshold = 24;
                delayMultiplier = 30;
                maxUpdatesPerTick = 10;      // Low updates to preserve mob AI
                updateCooldown = 3000;
                criticalOnlyDistance = 12;
                LOGGER.debug("Optimization set to BALANCED mode - mob-friendly");
            }
            case MINIMAL -> {
                distanceThreshold = 32;      // Larger distance = less aggressive
                delayMultiplier = 20;        // Lower delay = less aggressive
                maxUpdatesPerTick = 20;      // Still limited for mob AI
                updateCooldown = 2000;       // Shorter cooldown = less aggressive
                criticalOnlyDistance = 16;   // Larger critical area
                LOGGER.debug("Optimization set to MINIMAL mode - mob-friendly");
            }
        }
    }
    
    public static void resetTickCounter() {
        updatesThisTick.set(0);
    }
    
    public static int getUpdatesThisTick() {
        return updatesThisTick.get();
    }
    
    public static int getMaxUpdatesPerTick() {
        return maxUpdatesPerTick;
    }
    
    /**
     * Ultra-aggressive fluid update optimization
     * This is the main entry point for all fluid updates
     */
    public static boolean optimizeFluidUpdate(ServerLevel level, BlockPos pos, Fluid fluid, int originalDelay) {
        totalUpdates.incrementAndGet();
        
        // Update parameters from config periodically
        if (totalUpdates.get() % 100 == 0) {
            updateOptimizationParameters();
        }
        
        // Reset counter if needed
        if (updatesThisTick.get() >= maxUpdatesPerTick) {
            skippedUpdates.incrementAndGet();
            return true; // Skip this update
        }
        
        // Prioritize Flowing Fluids mod handling for full compatibility
        if (FlowingFluidsAPIIntegration.shouldPrioritizeFlowingFluids(fluid)) {
            if (FlowingFluidsAPIIntegration.delegateToFlowingFluids(level, pos, fluid)) {
                updatesThisTick.incrementAndGet();
                LOGGER.debug("Fluid update at {} handled by Flowing Fluids mod", pos);
                return false; // Processed by Flowing Fluids
            }
        }
        
        // ALWAYS apply optimization to Flowing Fluids - this is the whole point!
        // The optimization should IMPROVE Flowing Fluids performance, not avoid it
        boolean isFlowingFluidsMod = FlowingFluidsIntegration.isFlowingFluidsLoaded();
        
        if (isFlowingFluidsMod) {
            LOGGER.debug("Applying aggressive optimization to Flowing Fluids update at {}", pos);
        } else {
            LOGGER.debug("Applying aggressive optimization to vanilla fluid update at {}", pos);
        }
        
        // Check if we should skip this update entirely (but be more lenient for Flowing Fluids)
        if (shouldSkipUpdate(level, pos, fluid)) {
            // For Flowing Fluids, be less aggressive about skipping to preserve mechanics
            if (!isFlowingFluidsMod || getDistanceToNearestPlayer(level, pos) > distanceThreshold * 2) {
                skippedUpdates.incrementAndGet();
                return true; // Skip this update
            }
        }
        
        // Check if we should delay this update (be more conservative for Flowing Fluids)
        if (shouldDelayUpdate(level, pos, fluid)) {
            int effectiveDelayMultiplier = isFlowingFluidsMod ? delayMultiplier / 2 : delayMultiplier;
            int newDelay = Math.min(originalDelay * effectiveDelayMultiplier, 40);
            delayedUpdates.incrementAndGet();
            
            // For non-critical updates, offload to async thread pool if enabled
            // This prevents fluid calculations from blocking mob pathfinding
            FluidState fluidState = level.getFluidState(pos);
            double distance = getDistanceToNearestPlayer(level, pos);
            if (FluidThreadingHandler.isAsyncEnabled() && distance > criticalOnlyDistance) {
                int priority = calculatePriority(distance, fluidState);
                FluidThreadingHandler.submitAsyncUpdate(level, pos, fluidState, priority);
                updatesThisTick.incrementAndGet();
                return false; // Queued for async processing
            }
            
            level.scheduleTick(pos, fluid, newDelay);
            updatesThisTick.incrementAndGet();
            return false; // Processed with delay
        }
        
        // Prioritize critical updates for Flowing Fluids - these stay on main thread
        if (isFlowingFluidsMod && getDistanceToNearestPlayer(level, pos) <= criticalOnlyDistance) {
            level.scheduleTick(pos, fluid, Math.min(originalDelay, 2));
        } else {
            // Non-critical updates can be offloaded to async processing
            FluidState fluidState = level.getFluidState(pos);
            double distance = getDistanceToNearestPlayer(level, pos);
            if (FluidThreadingHandler.isAsyncEnabled() && distance > 8) {
                int priority = calculatePriority(distance, fluidState);
                FluidThreadingHandler.submitAsyncUpdate(level, pos, fluidState, priority);
            } else {
                level.scheduleTick(pos, fluid, originalDelay);
            }
        }
        updatesThisTick.incrementAndGet();
        return false; // Processed normally
    }
    
    /**
     * Calculate priority for async processing based on distance and fluid state
     */
    private static int calculatePriority(double distance, FluidState state) {
        int priority = 5; // Base priority
        
        // Source blocks get higher priority
        if (state.isSource()) {
            priority += 3;
        }
        
        // Closer updates get higher priority
        if (distance < 16) {
            priority += 2;
        } else if (distance < 32) {
            priority += 1;
        }
        
        return priority;
    }
    
    public static void markUpdated(BlockPos pos) {
        recentUpdates.put(pos, System.currentTimeMillis());
        // Clean up old entries
        recentUpdates.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > updateCooldown);
    }
    
    public static String getStatus() {
        long total = totalUpdates.get();
        long skipped = skippedUpdates.get();
        long delayed = delayedUpdates.get();
        double skipPercent = total > 0 ? (skipped * 100.0 / total) : 0;
        double delayPercent = total > 0 ? (delayed * 100.0 / total) : 0;
        return String.format("Aggressive Fluid Optimizer: %.1f%% skipped, %.1f%% delayed (%d total)", 
                            skipPercent, delayPercent, total);
    }
    
    /**
     * Aggressive skip criteria - skip as much as possible but preserve all fluid levels
     */
    private static boolean shouldSkipUpdate(ServerLevel level, BlockPos pos, Fluid fluid) {
        // Skip if too far from players
        if (getDistanceToNearestPlayer(level, pos) > distanceThreshold) {
            return true;
        }
        
        // Use fluid parameter to avoid warning - check if it's air (empty fluid)
        if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
            return true;
        }
        
        // Skip if recently updated (prevent spam)
        if (recentUpdates.containsKey(pos)) {
            return true;
        }
        
        // Skip if Flowing Fluids is already moving fluids
        if (FlowingFluidsAPIIntegration.isFlowingFluidsAvailable()) {
            if (FlowingFluidsAPIIntegration.isModCurrentlyMovingFluids()) {
                return true;
            }
        }
        
        // Skip if surrounded by non-flowing fluids on all horizontal sides
        if (isSurroundedByNonFlowingFluids(level, pos, fluid)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if fluid is surrounded by non-flowing fluids or blocks on all horizontal sides
     */
    private static boolean isSurroundedByNonFlowingFluids(ServerLevel level, BlockPos pos, Fluid fluid) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(dir);
            FluidState adjacentState = level.getFluidState(adjacent);
            // If adjacent is empty or has lower fluid level of same type, flow is possible
            if (adjacentState.isEmpty() || (adjacentState.getType().isSame(fluid) && adjacentState.getAmount() < level.getFluidState(pos).getAmount())) {
                return false;
            }
        }
        // All sides are blocked by equal or higher fluid levels or solid blocks
        return true;
    }
    
    /**
     * Delay criteria for less important updates - ULTRA aggressive but preserve all levels
     */
    private static boolean shouldDelayUpdate(ServerLevel level, BlockPos pos, Fluid fluid) {
        double distance = getDistanceToNearestPlayer(level, pos);
        
        // Delay EVERYTHING beyond critical distance
        if (distance > criticalOnlyDistance) {
            return true;
        }
        
        // For Flowing Fluids, delay non-critical updates but preserve all levels
        if (FlowingFluidsAPIIntegration.isFlowingFluidsAvailable()) {
            // Check if Flowing Fluids modifies this fluid
            if (FlowingFluidsAPIIntegration.doesModifyFluid(fluid)) {
                // Delay everything that isn't immediately near players
                return distance > 8; // Delay even close updates if not critical
            }
        }
        
        // Delay vanilla fluids that are not close to players
        return distance > 8;
    }
    
    private static double getDistanceToNearestPlayer(ServerLevel level, BlockPos pos) {
        return level.players().stream()
            .mapToDouble(player -> player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
            .min()
            .orElse(Double.MAX_VALUE);
    }
}
