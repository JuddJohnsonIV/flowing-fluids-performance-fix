package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Create Mod Compatibility System
 * Step 7: Ensure compatibility with Create mod for seamless integration
 * Handles special fluid mechanics interactions with Create mod components
 */
public class CreateModCompatibility {
    private static final Logger LOGGER = LogManager.getLogger(CreateModCompatibility.class);
    
    // Compatibility state
    private static volatile boolean createModLoaded = false;
    private static volatile boolean compatibilityEnabled = true;
    
    // Create mod settings (matching Flowing Fluids mod settings)
    private static volatile boolean infinitePipeFluidSource = false;
    private static volatile String waterWheelRequirement = "flow_or_river";
    
    // Performance tracking
    private static final AtomicLong createInteractions = new AtomicLong(0);
    private static final AtomicLong pipeFluidTransfers = new AtomicLong(0);
    private static final AtomicLong waterWheelChecks = new AtomicLong(0);
    
    /**
     * Initialize Create mod compatibility
     */
    public static void initialize() {
        createModLoaded = checkCreateModPresent();
        
        if (createModLoaded) {
            LOGGER.info("Create mod detected - initializing compatibility layer");
            loadCreateSettings();
        } else {
            LOGGER.info("Create mod not detected - compatibility layer inactive");
        }
    }
    
    /**
     * Check if Create mod is present
     */
    private static boolean checkCreateModPresent() {
        try {
            Class.forName("com.simibubi.create.Create");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Load Create mod compatibility settings
     */
    private static void loadCreateSettings() {
        // These settings align with Flowing Fluids' Create compatibility
        infinitePipeFluidSource = false; // Default: pipes respect finite fluid
        waterWheelRequirement = "flow_or_river"; // Default: needs flow or river biome
        
        LOGGER.info("Create compatibility settings loaded:");
        LOGGER.info("  Infinite Pipe Fluid Source: {}", infinitePipeFluidSource);
        LOGGER.info("  Water Wheel Requirement: {}", waterWheelRequirement);
    }
    
    /**
     * Check if Create mod is loaded
     */
    public static boolean isCreateModLoaded() {
        return createModLoaded;
    }
    
    /**
     * Check if compatibility is enabled
     */
    public static boolean isCompatibilityEnabled() {
        return compatibilityEnabled && createModLoaded;
    }
    
    /**
     * Set compatibility enabled state
     */
    public static void setCompatibilityEnabled(boolean enabled) {
        compatibilityEnabled = enabled;
        LOGGER.info("Create mod compatibility {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Handle fluid interaction with Create pipes
     * Returns true if the interaction should use infinite fluid behavior
     */
    public static boolean shouldPipeUseInfiniteFluid(ServerLevel level, BlockPos pipePos) {
        if (!isCompatibilityEnabled()) {
            return false;
        }
        
        createInteractions.incrementAndGet();
        pipeFluidTransfers.incrementAndGet();
        
        // Check if infinite pipe fluid source is enabled
        if (infinitePipeFluidSource) {
            LOGGER.debug("Pipe at {} using infinite fluid source mode", pipePos);
            return true;
        }
        
        // Check if in special biome that provides infinite water
        if (BiomeOptimization.isInfiniteWaterBiome(level, pipePos)) {
            LOGGER.debug("Pipe at {} in infinite water biome", pipePos);
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if water wheel should spin at this location
     * Implements Flowing Fluids' water wheel compatibility
     */
    public static boolean shouldWaterWheelSpin(ServerLevel level, BlockPos wheelPos, FluidState fluidState) {
        if (!isCompatibilityEnabled()) {
            return !fluidState.isEmpty(); // Default vanilla behavior
        }
        
        createInteractions.incrementAndGet();
        waterWheelChecks.incrementAndGet();
        
        return switch (waterWheelRequirement) {
            case "flow" -> isFluidFlowing(level, wheelPos, fluidState);
            case "flow_or_river" -> isFluidFlowing(level, wheelPos, fluidState) || 
                                   isRiverBiomeNearSeaLevel(level, wheelPos);
            case "fluid" -> !fluidState.isEmpty();
            case "full_fluid" -> fluidState.isSource();
            case "always" -> true;
            case "flow_or_river_opposite_spin" -> isFluidFlowing(level, wheelPos, fluidState) || 
                                                  isRiverBiomeNearSeaLevel(level, wheelPos);
            case "fluid_opposite_spin" -> !fluidState.isEmpty();
            case "full_fluid_opposite_spin" -> fluidState.isSource();
            case "always_opposite_spin" -> true;
            default -> isFluidFlowing(level, wheelPos, fluidState) || 
                      isRiverBiomeNearSeaLevel(level, wheelPos);
        };
    }
    
    /**
     * Check if fluid is actively flowing at position
     */
    private static boolean isFluidFlowing(ServerLevel level, BlockPos pos, FluidState state) {
        if (state.isEmpty()) {
            return false;
        }
        
        // Check for height differences that indicate flow
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(dir);
            FluidState adjacentState = level.getFluidState(adjacent);
            
            if (adjacentState.isEmpty() || adjacentState.getAmount() < state.getAmount()) {
                return true; // Flow detected
            }
        }
        
        return false;
    }
    
    /**
     * Check if position is in river biome near sea level
     */
    private static boolean isRiverBiomeNearSeaLevel(ServerLevel level, BlockPos pos) {
        // Check if near sea level (Y 62 is typical sea level)
        if (pos.getY() > 62 + 5 || pos.getY() < 62 - 10) {
            return false;
        }
        
        // Check biome
        var biome = level.getBiome(pos);
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.Registry.BIOME_REGISTRY);
        var key = registry.getKey(biome);
        String biomeName = key != null ? key.getPath() : "";
        
        return biomeName.contains("river");
    }
    
    /**
     * Get fluid extraction rate modifier for Create pumps
     * Lower values = slower extraction (preserves finite fluid)
     */
    public static float getFluidExtractionRateModifier(ServerLevel level, BlockPos pumpPos) {
        if (!isCompatibilityEnabled()) {
            return 1.0f; // No modification
        }
        
        // In infinite water biomes, allow normal extraction
        if (BiomeOptimization.isInfiniteWaterBiome(level, pumpPos)) {
            return 1.0f;
        }
        
        // For finite fluid, slow down extraction to preserve resources
        // This matches Flowing Fluids' finite fluid philosophy
        return 0.5f;
    }
    
    /**
     * Check if hose pulley should treat water as infinite
     */
    public static boolean shouldHosePulleyUseInfiniteFluid(ServerLevel level, BlockPos pulleyPos) {
        if (!isCompatibilityEnabled()) {
            return true; // Default Create behavior
        }
        
        createInteractions.incrementAndGet();
        
        // Only infinite in special biomes
        if (BiomeOptimization.isInfiniteWaterBiome(level, pulleyPos)) {
            return true;
        }
        
        // Check if infinite pipe source is enabled globally
        return infinitePipeFluidSource;
    }
    
    /**
     * Set infinite pipe fluid source setting
     */
    public static void setInfinitePipeFluidSource(boolean infinite) {
        infinitePipeFluidSource = infinite;
        LOGGER.info("Create pipe infinite fluid source set to: {}", infinite);
    }
    
    /**
     * Get infinite pipe fluid source setting
     */
    public static boolean isInfinitePipeFluidSource() {
        return infinitePipeFluidSource;
    }
    
    /**
     * Set water wheel requirement
     */
    public static void setWaterWheelRequirement(String requirement) {
        waterWheelRequirement = requirement;
        LOGGER.info("Create water wheel requirement set to: {}", requirement);
    }
    
    /**
     * Get water wheel requirement
     */
    public static String getWaterWheelRequirement() {
        return waterWheelRequirement;
    }
    
    /**
     * Get compatibility statistics
     */
    public static String getStatsSummary() {
        return String.format("Create Compatibility: %s (loaded: %s), %d interactions, %d pipe transfers, %d wheel checks",
            compatibilityEnabled ? "enabled" : "disabled", createModLoaded,
            createInteractions.get(), pipeFluidTransfers.get(), waterWheelChecks.get());
    }
    
    /**
     * Reset statistics
     */
    public static void resetStats() {
        createInteractions.set(0);
        pipeFluidTransfers.set(0);
        waterWheelChecks.set(0);
        LOGGER.info("Create compatibility statistics reset");
    }
}
