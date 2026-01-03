package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Biome-aware optimization system for Flowing Fluids
 * Adjusts fluid behavior based on biome characteristics
 * Step 22: Add support for Flowing Fluids biome-specific behaviors
 */
@SuppressWarnings("all")
public class BiomeOptimization {
    private static final Logger LOGGER = LogManager.getLogger(BiomeOptimization.class);
    private static final float DEFAULT_FLUID_VISCOSITY = 1.0f;
    private static final float DEFAULT_FLOW_SPEED = 1.0f;
    
    // Biome category caching for performance
    private static final Map<String, BiomeFluidProfile> biomeProfiles = new ConcurrentHashMap<>();
    
    // Special biomes for Flowing Fluids infinite water
    private static final String[] INFINITE_WATER_BIOMES = {
        "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "cold_ocean", "frozen_ocean",
        "river", "frozen_river", "swamp", "mangrove_swamp", "beach", "stony_shore"
    };
    
    // Ocean biomes - TRUE infinite water sources with accelerated replenishment
    private static final String[] OCEAN_BIOMES = {
        "ocean", "deep_ocean", "warm_ocean", "lukewarm_ocean", "cold_ocean", "frozen_ocean",
        "deep_lukewarm_ocean", "deep_cold_ocean", "deep_frozen_ocean"
    };
    
    // River biomes - TRUE infinite water sources with accelerated replenishment
    private static final String[] RIVER_BIOMES = {
        "river", "frozen_river"
    };
    
    // Hot biomes where water evaporates faster
    private static final String[] HOT_BIOMES = {
        "desert", "badlands", "eroded_badlands", "wooded_badlands", "savanna", "savanna_plateau"
    };
    
    // Cold biomes where water flows slower
    private static final String[] COLD_BIOMES = {
        "snowy_plains", "ice_spikes", "snowy_taiga", "frozen_peaks", "jagged_peaks", 
        "snowy_slopes", "grove", "frozen_river", "frozen_ocean", "deep_frozen_ocean"
    };
    
    static {
        initializeBiomeProfiles();
    }
    
    private static void initializeBiomeProfiles() {
        // Initialize profiles for special biomes
        for (String biome : INFINITE_WATER_BIOMES) {
            biomeProfiles.put(biome, new BiomeFluidProfile(true, 1.0f, 1.0f, false));
        }
        for (String biome : HOT_BIOMES) {
            biomeProfiles.put(biome, new BiomeFluidProfile(false, 1.2f, 0.8f, true));
        }
        for (String biome : COLD_BIOMES) {
            biomeProfiles.put(biome, new BiomeFluidProfile(false, 0.7f, 1.3f, false));
        }
        LOGGER.info("Biome optimization profiles initialized for {} biomes", biomeProfiles.size());
    }

    public static float getBiomeFluidViscosity(Level level, BlockPos pos, FluidState state) {
        // Use a default temperature value since getTemperature(BlockPos) is private
        float temperature = 0.5f; // Default moderate temperature
        // Use a default humidity value since getDownfall() is not available
        float humidity = 0.5f; // Default moderate humidity
        float viscosity = DEFAULT_FLUID_VISCOSITY;

        if (temperature < 0.3f) {
            viscosity *= 1.2f; // Slower flow in cold biomes
        } else if (temperature > 1.5f) {
            viscosity *= 0.8f; // Faster flow in hot biomes
        }

        if (humidity > 0.8f) {
            viscosity *= 0.9f; // Slightly faster in wet biomes
        }

        LOGGER.debug("Biome viscosity at {}: {} (temp: {}, humidity: {})", pos, viscosity, temperature, humidity);
        return viscosity;
    }

    public static float getBiomeFlowSpeed(Level level, BlockPos pos, FluidState state) {
        // Use a default temperature value since getTemperature(BlockPos) is private
        float temperature = 0.5f; // Default moderate temperature
        // Use a default humidity value since getDownfall() is not available
        float humidity = 0.5f; // Default moderate humidity
        float flowSpeed = DEFAULT_FLOW_SPEED;

        if (temperature > 1.5f) {
            flowSpeed *= 1.2f; // Faster flow in hot biomes
        } else if (temperature < 0.3f) {
            flowSpeed *= 0.8f; // Slower flow in cold biomes
        }

        if (humidity > 0.8f) {
            flowSpeed *= 1.1f; // Faster flow in wet biomes
        }

        LOGGER.debug("Biome flow speed at {}: {} (temp: {}, humidity: {})", pos, flowSpeed, temperature, humidity);
        return flowSpeed;
    }

    public static boolean shouldOptimizeFluidInBiome(Level level, BlockPos pos) {
        BiomeFluidProfile profile = getBiomeProfile(level, pos);
        // Optimize fluids in all biomes, but adjust strategy based on profile
        LOGGER.debug("Checking fluid optimization for biome at {} - profile: {}", pos, profile);
        return true;
    }
    
    /**
     * Check if this biome provides infinite water (for Flowing Fluids compatibility)
     */
    public static boolean isInfiniteWaterBiome(Level level, BlockPos pos) {
        BiomeFluidProfile profile = getBiomeProfile(level, pos);
        return profile.infiniteWater;
    }
    
    /**
     * Check if this is an ocean biome (for accelerated water replenishment)
     * Ocean biomes are TRUE infinite water sources where we want faster refill
     */
    public static boolean isOceanBiome(Level level, BlockPos pos) {
        String biomeName = getBiomeName(level, pos);
        for (String ocean : OCEAN_BIOMES) {
            if (biomeName.equals(ocean)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if this is a river biome (for accelerated water replenishment)
     * River biomes are TRUE infinite water sources where we want faster refill
     */
    public static boolean isRiverBiome(Level level, BlockPos pos) {
        String biomeName = getBiomeName(level, pos);
        for (String river : RIVER_BIOMES) {
            if (biomeName.equals(river)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if this is an ocean OR river biome (for accelerated water replenishment)
     * These are the only biomes where water should replenish faster to reduce lag
     * from Flowing Fluids calculating water holes
     */
    public static boolean isOceanOrRiverBiome(Level level, BlockPos pos) {
        return isOceanBiome(level, pos) || isRiverBiome(level, pos);
    }
    
    /**
     * Get the accelerated replenishment rate for ocean/river biomes
     * @return Multiplier for replenishment speed (higher = faster refill)
     */
    public static float getOceanRiverReplenishmentRate(Level level, BlockPos pos) {
        if (isOceanBiome(level, pos)) {
            return 3.0f; // Oceans refill 3x faster
        } else if (isRiverBiome(level, pos)) {
            return 2.5f; // Rivers refill 2.5x faster
        }
        return 1.0f; // No acceleration for other biomes
    }
    
    /**
     * Check if water evaporates faster in this biome
     */
    public static boolean isEvaporationBiome(Level level, BlockPos pos) {
        BiomeFluidProfile profile = getBiomeProfile(level, pos);
        return profile.fastEvaporation;
    }
    
    /**
     * Get the biome profile for optimization
     */
    public static BiomeFluidProfile getBiomeProfile(Level level, BlockPos pos) {
        String biomeName = getBiomeName(level, pos);
        return biomeProfiles.getOrDefault(biomeName, BiomeFluidProfile.DEFAULT);
    }
    
    /**
     * Get biome name from position
     */
    private static String getBiomeName(Level level, BlockPos pos) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            var registry = serverLevel.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
            for (var biomeHolder : registry.holders().toList()) {
                var biome = biomeHolder.value();
                var key = registry.getKey(biome);
                if (key != null && key.getPath().equals(biome.toString())) {
                    return key.getPath();
                }
            }
        }
        return "unknown";
    }
    
    /**
     * Get optimized tick delay based on biome
     */
    public static int getOptimizedTickDelay(Level level, BlockPos pos, int baseDelay) {
        BiomeFluidProfile profile = getBiomeProfile(level, pos);
        return Math.max(1, (int)(baseDelay * profile.viscosity));
    }
    
    /**
     * Check if fluid should be preserved in this biome (for Flowing Fluids finite fluid mechanics)
     */
    public static boolean shouldPreserveFluid(Level level, BlockPos pos, FluidState state) {
        // Preserve fluids in infinite water biomes
        if (isInfiniteWaterBiome(level, pos)) {
            return true;
        }
        
        // Preserve source blocks
        if (state.isSource()) {
            return true;
        }
        
        // Preserve low-level water in non-evaporation biomes
        return state.is(Fluids.WATER) && state.getAmount() <= 2 && !isEvaporationBiome(level, pos);
    }
    
    /**
     * Get update frequency multiplier based on biome
     * Lower = more frequent updates, Higher = less frequent
     */
    public static float getUpdateFrequencyMultiplier(Level level, BlockPos pos) {
        BiomeFluidProfile profile = getBiomeProfile(level, pos);
        
        // Infinite water biomes need frequent updates for refill mechanics
        if (profile.infiniteWater) {
            return 0.5f; // More frequent
        }
        
        // Hot biomes with evaporation need frequent updates
        if (profile.fastEvaporation) {
            return 0.7f; // More frequent
        }
        
        // Cold biomes can have slower updates
        if (profile.viscosity > 1.0f) {
            return 1.5f; // Less frequent
        }
        
        return 1.0f; // Normal
    }
    
    /**
     * Biome fluid profile record
     */
    public record BiomeFluidProfile(
        boolean infiniteWater,
        float flowSpeed,
        float viscosity,
        boolean fastEvaporation
    ) {
        public static final BiomeFluidProfile DEFAULT = new BiomeFluidProfile(false, 1.0f, 1.0f, false);
    }
}
