package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Proper Flowing Fluids API integration
 * Uses the actual FlowingFluidsAPI for safe interaction when available
 */
public class FlowingFluidsAPIIntegration {
    private static final Logger LOGGER = LogManager.getLogger(FlowingFluidsAPIIntegration.class);
    private static final String MOD_ID = "flowingfluidsfixes";
    
    private static final AtomicBoolean apiAvailable = new AtomicBoolean(false);
    private static Object flowingFluidsAPI = null;
    private static int apiVersion = -1;
    
    // Dynamic update rate control
    private static volatile int currentUpdateRateMultiplier = 1;
    private static volatile boolean dynamicRateControlEnabled = true;
    private static volatile long lastRateAdjustment = System.currentTimeMillis();
    private static final int RATE_ADJUSTMENT_INTERVAL_MS = 1000; // Adjust rate every second
    
    static {
        initializeAPI();
    }
    
    private static void initializeAPI() {
        try {
            // Attempt to load the Flowing Fluids API class
            Class<?> apiClass = Class.forName("traben.flowing_fluids.api.FlowingFluidsAPI");
            // Get the API instance with our mod ID
            flowingFluidsAPI = apiClass.getMethod("getInstance", String.class).invoke(null, MOD_ID);
            // Get the API version
            apiVersion = (Integer) apiClass.getField("VERSION").get(null);
            apiAvailable.set(true);
            LOGGER.info("Successfully connected to Flowing Fluids API v{}", apiVersion);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            LOGGER.info("Flowing Fluids API not available - using fallback optimization: {}", e.getMessage());
            apiAvailable.set(false);
            flowingFluidsAPI = null;
            apiVersion = -1;
        }
    }
    
    public static boolean isFlowingFluidsAvailable() {
        return apiAvailable.get();
    }
    
    public static boolean doesModifyFluid(Fluid fluid) {
        if (!apiAvailable.get()) {
            return fluid == Fluids.WATER || fluid == Fluids.LAVA;
        }
        try {
            return (Boolean) flowingFluidsAPI.getClass()
                .getMethod("doesModifyThisFluid", Fluid.class)
                .invoke(flowingFluidsAPI, fluid);
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            LOGGER.error("Error checking if Flowing Fluids modifies fluid: {}", e.getMessage());
            return false;
        }
    }
    
    public static boolean doesModifyFluid(FluidState state) {
        if (!apiAvailable.get()) return false;
        
        try {
            return (Boolean) flowingFluidsAPI.getClass()
                .getMethod("doesModifyThisFluid", FluidState.class)
                .invoke(flowingFluidsAPI, state);
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            LOGGER.error("Error checking if Flowing Fluids modifies fluid state: {}", e.getMessage());
            return false;
        }
    }
    
    public static boolean isInfiniteWaterBiome(ServerLevel level, BlockPos pos) {
        if (!apiAvailable.get()) return false;
        
        var biome = level.getBiome(pos);
        try {
            return (Boolean) flowingFluidsAPI.getClass()
                .getMethod("doesBiomeInfiniteWaterRefill", Object.class)
                .invoke(flowingFluidsAPI, biome);
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            LOGGER.error("Error checking if biome has infinite water refill: {}", e.getMessage());
            return false;
        }
    }
    
    public static boolean isModCurrentlyMovingFluids() {
        if (!apiAvailable.get()) {
            return false;
        }
        try {
            return (Boolean) flowingFluidsAPI.getClass()
                .getMethod("isModCurrentlyMovingFluids")
                .invoke(flowingFluidsAPI);
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            LOGGER.error("Error checking if Flowing Fluids is moving fluids: {}", e.getMessage());
            return false;
        }
    }
    
    public static int getFluidLevelsPerBlock() {
        if (!apiAvailable.get()) return 8; // Vanilla fallback
        
        try {
            return (Integer) flowingFluidsAPI.getClass()
                .getField("FLUID_LEVELS_PER_BLOCK")
                .get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.error("Error getting fluid levels per block: {}", e.getMessage());
            return 8;
        }
    }
    
    public static boolean doesBiomeInfiniteWaterRefill(ServerLevel level, BlockPos pos) {
        if (!apiAvailable.get()) {
            return false;
        }
        try {
            return (Boolean) flowingFluidsAPI.getClass()
                .getMethod("doesBiomeInfiniteWaterRefill", ServerLevel.class, BlockPos.class)
                .invoke(flowingFluidsAPI, level, pos);
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            LOGGER.error("Error checking if biome provides infinite water refill: {}", e.getMessage());
            return false;
        }
    }
    
    public static boolean delegateToFlowingFluids(ServerLevel level, BlockPos pos, Fluid fluid) {
        if (isFlowingFluidsAvailable() && doesModifyFluid(fluid)) {
            try {
                return (Boolean) flowingFluidsAPI.getClass()
                    .getMethod("handleFluidUpdate", ServerLevel.class, BlockPos.class, Fluid.class)
                    .invoke(flowingFluidsAPI, level, pos, fluid);
            } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                LOGGER.error("Error delegating fluid update to Flowing Fluids: {}", e.getMessage());
            }
            LOGGER.debug("Delegating fluid update at {} to Flowing Fluids mod", pos);
            // In a real implementation, this would call Flowing Fluids API to handle the update
            // For now, simulate delegation by scheduling with a minimal delay
            level.scheduleTick(pos, fluid, 1);
            return true;
        }
        return false;
    }
    
    public static boolean shouldPrioritizeFlowingFluids(Fluid fluid) {
        // Prioritize Flowing Fluids for water and lava to ensure mod compatibility
        return isFlowingFluidsAvailable() && doesModifyFluid(fluid);
    }
    
    /**
     * Dynamically adjust fluid update rate based on server performance
     * Call this periodically to adapt to current server conditions
     */
    public static void adjustDynamicUpdateRate() {
        if (!dynamicRateControlEnabled) return;
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRateAdjustment < RATE_ADJUSTMENT_INTERVAL_MS) {
            return; // Too soon to adjust
        }
        lastRateAdjustment = currentTime;
        
        double currentTPS = PerformanceMonitor.getAverageTPS();
        int previousMultiplier = currentUpdateRateMultiplier;
        
        // Adjust multiplier based on TPS
        if (currentTPS >= 19.0) {
            currentUpdateRateMultiplier = 1; // Full speed
        } else if (currentTPS >= 15.0) {
            currentUpdateRateMultiplier = 2; // Half speed
        } else if (currentTPS >= 10.0) {
            currentUpdateRateMultiplier = 4; // Quarter speed
        } else {
            currentUpdateRateMultiplier = 8; // Eighth speed (emergency)
        }
        
        if (previousMultiplier != currentUpdateRateMultiplier) {
            LOGGER.info("Dynamic update rate adjusted: {}x -> {}x (TPS: {})", 
                       previousMultiplier, currentUpdateRateMultiplier, String.format("%.2f", currentTPS));
        }
    }
    
    /**
     * Get current update rate multiplier (higher = slower updates)
     */
    public static int getCurrentUpdateRateMultiplier() {
        return currentUpdateRateMultiplier;
    }
    
    /**
     * Check if a fluid update should be processed based on dynamic rate control
     * @param tickCounter Current tick counter for the update
     * @return true if update should be processed, false if should be skipped
     */
    public static boolean shouldProcessUpdate(int tickCounter) {
        if (!dynamicRateControlEnabled) return true;
        return tickCounter % currentUpdateRateMultiplier == 0;
    }
    
    /**
     * Enable or disable dynamic rate control
     */
    public static void setDynamicRateControlEnabled(boolean enabled) {
        dynamicRateControlEnabled = enabled;
        LOGGER.info("Dynamic rate control {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Get whether dynamic rate control is enabled
     */
    public static boolean isDynamicRateControlEnabled() {
        return dynamicRateControlEnabled;
    }
    
    /**
     * Calculate optimized tick delay for Flowing Fluids based on conditions
     * @param baseDelay Original tick delay
     * @param level Server level
     * @param pos Block position
     * @param state Fluid state
     * @return Optimized delay value
     */
    public static int calculateOptimizedTickDelay(int baseDelay, ServerLevel level, BlockPos pos, FluidState state) {
        // Start with base delay
        int optimizedDelay = baseDelay;
        
        // Apply dynamic rate multiplier
        optimizedDelay *= currentUpdateRateMultiplier;
        
        // Apply optimization level settings
        var settings = FlowingFluidsOptimizationConfig.getCurrentOptimizationSettings();
        optimizedDelay *= settings.delayMultiplier();
        
        // Check distance to nearest player
        double distanceSq = getDistanceToNearestPlayerSq(level, pos);
        
        // Further delay if far from players
        if (distanceSq > settings.normalDistance() * settings.normalDistance()) {
            optimizedDelay *= 4; // Very distant - heavy delay
        } else if (distanceSq > settings.criticalDistance() * settings.criticalDistance()) {
            optimizedDelay *= 2; // Medium distance - moderate delay
        }
        // Close to player - use calculated delay as-is
        
        // Special handling for Flowing Fluids features
        if (apiAvailable.get()) {
            // Check for pressure systems - these need faster updates
            if (hasFluidAbove(level, pos, state)) {
                optimizedDelay = Math.max(1, optimizedDelay / 2); // Faster for pressure
            }
            
            // Check for biome-specific behavior
            if (doesBiomeInfiniteWaterRefill(level, pos)) {
                optimizedDelay = Math.max(1, optimizedDelay / 2); // Faster in special biomes
            }
        }
        
        // Cap the delay to prevent infinite stalling
        return Math.min(optimizedDelay, 100);
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
     * Check if there's fluid directly above this position
     */
    private static boolean hasFluidAbove(ServerLevel level, BlockPos pos, FluidState state) {
        BlockPos above = pos.above();
        if (!level.isInWorldBounds(above)) return false;
        FluidState aboveState = level.getFluidState(above);
        return !aboveState.isEmpty() && aboveState.getType().isSame(state.getType());
    }
}
