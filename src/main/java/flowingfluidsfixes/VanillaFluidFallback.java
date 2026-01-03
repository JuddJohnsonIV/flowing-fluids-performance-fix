package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Vanilla Fluid Fallback System
 * Step 9: Implement fallback mechanisms for when Flowing Fluids API is unavailable
 * Ensures the mod still functions with vanilla fluid behavior when Flowing Fluids is not present
 */
public class VanillaFluidFallback {
    private static final Logger LOGGER = LogManager.getLogger(VanillaFluidFallback.class);
    
    // Fallback mode state
    private static volatile boolean fallbackModeActive = false;
    private static volatile String fallbackReason = "Not initialized";
    
    // Performance tracking
    private static final AtomicLong fallbackUpdatesProcessed = new AtomicLong(0);
    private static final AtomicLong optimizedFallbackUpdates = new AtomicLong(0);
    
    // Vanilla tick delays
    private static final int VANILLA_WATER_TICK_DELAY = 5;
    private static final int VANILLA_LAVA_TICK_DELAY_OVERWORLD = 30;
    private static final int VANILLA_LAVA_TICK_DELAY_NETHER = 10;
    
    /**
     * Initialize fallback system
     */
    public static void initialize() {
        // Check if Flowing Fluids API is available
        if (!FlowingFluidsAPIIntegration.isFlowingFluidsAvailable()) {
            activateFallbackMode("Flowing Fluids API not detected");
        } else {
            deactivateFallbackMode();
        }
        
        LOGGER.info("Vanilla Fluid Fallback System initialized (active: {})", fallbackModeActive);
    }
    
    /**
     * Activate fallback mode
     */
    public static void activateFallbackMode(String reason) {
        fallbackModeActive = true;
        fallbackReason = reason;
        LOGGER.warn("Vanilla fallback mode activated: {}", reason);
    }
    
    /**
     * Deactivate fallback mode
     */
    public static void deactivateFallbackMode() {
        fallbackModeActive = false;
        fallbackReason = "Flowing Fluids API available";
        LOGGER.info("Vanilla fallback mode deactivated");
    }
    
    /**
     * Check if fallback mode is active
     */
    public static boolean isFallbackModeActive() {
        return fallbackModeActive;
    }
    
    /**
     * Get fallback reason
     */
    public static String getFallbackReason() {
        return fallbackReason;
    }
    
    /**
     * Process a fluid update with vanilla fallback logic
     * Returns true if the update was handled by fallback
     */
    public static boolean processFluidUpdateFallback(ServerLevel level, BlockPos pos, 
                                                     FluidState state, int originalDelay) {
        if (!fallbackModeActive) {
            return false; // Not using fallback
        }
        
        fallbackUpdatesProcessed.incrementAndGet();
        
        // Apply vanilla-compatible optimization
        Fluid fluid = state.getType();
        int optimizedDelay = calculateVanillaOptimizedDelay(level, pos, state, originalDelay);
        
        // Check if update should be skipped
        if (shouldSkipVanillaUpdate(level, pos, state)) {
            optimizedFallbackUpdates.incrementAndGet();
            return true; // Skipped
        }
        
        // Schedule with optimized delay
        level.scheduleTick(pos, fluid, optimizedDelay);
        
        if (optimizedDelay != originalDelay) {
            optimizedFallbackUpdates.incrementAndGet();
        }
        
        return true; // Handled by fallback
    }
    
    /**
     * Calculate optimized delay for vanilla fluids
     */
    private static int calculateVanillaOptimizedDelay(ServerLevel level, BlockPos pos, 
                                                      FluidState state, int originalDelay) {
        // Get base vanilla delay
        int baseDelay = getVanillaTickDelay(level, state.getType());
        
        // Apply distance-based optimization
        double distanceSq = getDistanceToNearestPlayerSq(level, pos);
        
        if (distanceSq > 64 * 64) {
            // Very far - use maximum delay
            return Math.min(originalDelay * 4, 40);
        } else if (distanceSq > 32 * 32) {
            // Far - use increased delay
            return Math.min(originalDelay * 2, 20);
        } else if (distanceSq > 16 * 16) {
            // Medium distance - slight delay increase
            return Math.min((int)(originalDelay * 1.5), 15);
        }
        
        // Near player - use base delay
        return baseDelay;
    }
    
    /**
     * Get vanilla tick delay for a fluid type
     */
    private static int getVanillaTickDelay(ServerLevel level, Fluid fluid) {
        if (fluid.isSame(Fluids.WATER) || fluid.isSame(Fluids.FLOWING_WATER)) {
            return VANILLA_WATER_TICK_DELAY;
        } else if (fluid.isSame(Fluids.LAVA) || fluid.isSame(Fluids.FLOWING_LAVA)) {
            // Lava flows faster in the Nether
            if (level.dimensionType().ultraWarm()) {
                return VANILLA_LAVA_TICK_DELAY_NETHER;
            }
            return VANILLA_LAVA_TICK_DELAY_OVERWORLD;
        }
        return 5; // Default
    }
    
    /**
     * Check if vanilla update should be skipped
     */
    private static boolean shouldSkipVanillaUpdate(ServerLevel level, BlockPos pos, FluidState state) {
        // Skip if too far from players
        double distanceSq = getDistanceToNearestPlayerSq(level, pos);
        if (distanceSq > 96 * 96) {
            return true;
        }
        
        // Skip if fluid is stable (not flowing)
        if (!canVanillaFluidFlow(level, pos, state)) {
            return true;
        }
        
        // Skip during emergency mode if not critical
        if (EmergencyPerformanceMode.isEmergencyMode() && distanceSq > 16 * 16) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if vanilla fluid can flow from this position
     */
    private static boolean canVanillaFluidFlow(ServerLevel level, BlockPos pos, FluidState state) {
        if (state.isEmpty()) return false;
        
        // Check if there's a place to flow
        for (Direction dir : Direction.values()) {
            BlockPos adjacent = pos.relative(dir);
            if (!level.isInWorldBounds(adjacent)) continue;
            
            FluidState adjacentState = level.getFluidState(adjacent);
            
            // Can flow into empty space
            if (adjacentState.isEmpty() && level.getBlockState(adjacent).canBeReplaced(state.getType())) {
                return true;
            }
            
            // Can flow into lower-level fluid of same type
            if (adjacentState.getType().isSame(state.getType()) && 
                adjacentState.getAmount() < state.getAmount()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get squared distance to nearest player
     */
    private static double getDistanceToNearestPlayerSq(ServerLevel level, BlockPos pos) {
        return level.players().stream()
            .mapToDouble(player -> player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
            .min()
            .orElse(Double.MAX_VALUE);
    }
    
    /**
     * Check if fluid type is supported by vanilla fallback
     */
    public static boolean isVanillaFluid(Fluid fluid) {
        return fluid.isSame(Fluids.WATER) || fluid.isSame(Fluids.FLOWING_WATER) ||
               fluid.isSame(Fluids.LAVA) || fluid.isSame(Fluids.FLOWING_LAVA);
    }
    
    /**
     * Get fallback statistics
     */
    public static String getStatsSummary() {
        return String.format("Vanilla Fallback: %s (reason: %s), %d processed, %d optimized",
            fallbackModeActive ? "ACTIVE" : "inactive", fallbackReason,
            fallbackUpdatesProcessed.get(), optimizedFallbackUpdates.get());
    }
    
    /**
     * Reset statistics
     */
    public static void resetStats() {
        fallbackUpdatesProcessed.set(0);
        optimizedFallbackUpdates.set(0);
        LOGGER.info("Vanilla fallback statistics reset");
    }
}
