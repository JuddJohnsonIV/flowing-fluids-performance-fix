package flowingfluidsfixes;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("all")
public class PerformanceConfig {
    private static final Logger LOGGER = LogManager.getLogger(PerformanceConfig.class);
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.EnumValue<PerformanceProfile> PERFORMANCE_PROFILE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> AVAILABLE_PROFILES;
    private static final ForgeConfigSpec.IntValue WATER_TICK_DELAY;
    private static final ForgeConfigSpec.BooleanValue FLOWING_TEXTURE;
    private static final ForgeConfigSpec.IntValue FLUID_HEIGHT;
    private static final ForgeConfigSpec.BooleanValue FLOW_OVER_EDGES;
    private static final ForgeConfigSpec.DoubleValue EVAPORATION_RATE_OVERWORLD;
    private static final ForgeConfigSpec.DoubleValue EVAPORATION_RATE_NETHER;
    private static final ForgeConfigSpec.DoubleValue REFILL_RATE_RAIN;
    private static final ForgeConfigSpec.DoubleValue REFILL_RATE_BIOME;
    private static final ForgeConfigSpec.DoubleValue OCEAN_REPLENISH_RATE;
    private static final ForgeConfigSpec.DoubleValue RIVER_REPLENISH_RATE;
    private static final ForgeConfigSpec.IntValue MAX_REPLENISHMENTS_PER_TICK;
    private static final ForgeConfigSpec.BooleanValue OCEAN_RIVER_REPLENISHMENT_ENABLED;
    private static PerformanceConfig instance;

    static {
        BUILDER.push("Performance Settings");
        PERFORMANCE_PROFILE = BUILDER
                .comment("Select the performance profile for fluid updates. Options: LOW, MEDIUM, HIGH")
                .defineEnum("PerformanceProfile", PerformanceProfile.MEDIUM);
        AVAILABLE_PROFILES = BUILDER
                .comment("List of available performance profiles")
                .defineList("AvailableProfiles", Arrays.asList("LOW", "MEDIUM", "HIGH"), entry -> true);
        WATER_TICK_DELAY = BUILDER
                .comment("Delay between water updates (higher values reduce updates, improving performance but slowing water flow)")
                .defineInRange("waterTickDelay", 2, 1, 8);
        BUILDER.pop();

        BUILDER.push("Visual Settings");
        FLOWING_TEXTURE = BUILDER
                .comment("Show flowing texture on fluids with height differences (disable for still water appearance)")
                .define("flowingTexture", true);
        FLUID_HEIGHT = BUILDER
                .comment("Fluid height level at which full blocks render and affect entities (1-8)")
                .defineInRange("fluidHeight", 8, 1, 8);
        FLOW_OVER_EDGES = BUILDER
                .comment("Allow minimum level fluids to flow over edges (disable for a contained look)")
                .define("flowOverEdges", true);
        BUILDER.pop();

        BUILDER.push("Fluid Behavior Settings");
        EVAPORATION_RATE_OVERWORLD = BUILDER
                .comment("Rate at which small water puddles evaporate in the Overworld (0.0 to disable, higher for faster evaporation)")
                .defineInRange("evaporationRateOverworld", 0.1, 0.0, 1.0);
        EVAPORATION_RATE_NETHER = BUILDER
                .comment("Rate at which water evaporates in the Nether (0.0 to disable, higher for faster evaporation)")
                .defineInRange("evaporationRateNether", 0.5, 0.0, 1.0);
        REFILL_RATE_RAIN = BUILDER
                .comment("Rate at which non-full water blocks refill during rain (0.0 to disable, higher for faster refill)")
                .defineInRange("refillRateRain", 0.2, 0.0, 1.0);
        REFILL_RATE_BIOME = BUILDER
                .comment("Rate at which non-full water blocks refill in Ocean, River, or Swamp biomes below sea level (0.0 to disable)")
                .defineInRange("refillRateBiome", 0.3, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.push("Ocean/River Replenishment Settings");
        BUILDER.comment("These settings control accelerated water replenishment ONLY in ocean and river biomes.",
                "This helps reduce lag from Flowing Fluids calculating water holes by slowly refilling them.",
                "IMPORTANT: This does NOT affect other biomes - the finite water system is preserved elsewhere.");
        OCEAN_RIVER_REPLENISHMENT_ENABLED = BUILDER
                .comment("Enable accelerated water replenishment in ocean and river biomes (helps reduce lag)")
                .define("oceanRiverReplenishmentEnabled", true);
        OCEAN_REPLENISH_RATE = BUILDER
                .comment("Chance per tick for water to replenish in OCEAN biomes (0.0 to 1.0, higher = faster refill)")
                .defineInRange("oceanReplenishRate", 0.15, 0.0, 1.0);
        RIVER_REPLENISH_RATE = BUILDER
                .comment("Chance per tick for water to replenish in RIVER biomes (0.0 to 1.0, higher = faster refill)")
                .defineInRange("riverReplenishRate", 0.10, 0.0, 1.0);
        MAX_REPLENISHMENTS_PER_TICK = BUILDER
                .comment("Maximum number of water blocks to replenish per tick (prevents lag spikes)")
                .defineInRange("maxReplenishmentsPerTick", 50, 1, 200);
        BUILDER.pop();
    }

    public enum PerformanceProfile {
        LOW(1000, 5000, 0.5f, 0.2f, 32),
        MEDIUM(3000, 10000, 1.0f, 0.5f, 48),
        HIGH(5000, 20000, 1.5f, 0.8f, 64);

        private final int maxUpdatesPerTick;
        private final int maxTotalUpdates;
        private final float updateMultiplier;
        private final float throttleThreshold;
        private final int playerProximityRadius;

        PerformanceProfile(int maxUpdatesPerTick, int maxTotalUpdates, float updateMultiplier, float throttleThreshold, int playerProximityRadius) {
            this.maxUpdatesPerTick = maxUpdatesPerTick;
            this.maxTotalUpdates = maxTotalUpdates;
            this.updateMultiplier = updateMultiplier;
            this.throttleThreshold = throttleThreshold;
            this.playerProximityRadius = playerProximityRadius;
        }

        public int getMaxUpdatesPerTick() {
            return maxUpdatesPerTick;
        }

        public int getMaxTotalUpdates() {
            return maxTotalUpdates;
        }

        public float getUpdateMultiplier() {
            return updateMultiplier;
        }

        public float getThrottleThreshold() {
            return throttleThreshold;
        }

        public int getPlayerProximityRadius() {
            return playerProximityRadius;
        }
    }

    public static void loadConfig() {
        try {
            instance = new PerformanceConfig();
            LOGGER.info("Performance configuration loaded with profile: {}", PERFORMANCE_PROFILE.get());
        } catch (Exception e) {
            LOGGER.error("Failed to load performance configuration: {}", e.getMessage());
            // Fallback to default profile on failure
            PERFORMANCE_PROFILE.set(PerformanceProfile.MEDIUM);
        }
    }

    public static PerformanceConfig getInstance() {
        if (instance == null) {
            loadConfig();
        }
        return instance;
    }

    public PerformanceProfile getCurrentProfile() {
        return PERFORMANCE_PROFILE.get();
    }

    public void setPerformanceProfile(PerformanceProfile profile) {
        PERFORMANCE_PROFILE.set(profile);
        LOGGER.info("Performance profile updated to: {}", profile);
    }

    public List<String> getAvailableProfiles() {
        return new java.util.ArrayList<>(AVAILABLE_PROFILES.get());
    }

    public int getMaxUpdatesPerTick() {
        return PERFORMANCE_PROFILE.get().getMaxUpdatesPerTick();
    }

    public int getMaxTotalUpdates() {
        return PERFORMANCE_PROFILE.get().getMaxTotalUpdates();
    }

    public float getUpdateMultiplier() {
        return PERFORMANCE_PROFILE.get().getUpdateMultiplier();
    }

    public float getThrottleThreshold() {
        return PERFORMANCE_PROFILE.get().getThrottleThreshold();
    }

    public int getPlayerProximityRadius() {
        return PERFORMANCE_PROFILE.get().getPlayerProximityRadius();
    }

    public int getWaterTickDelay() {
        return WATER_TICK_DELAY.get();
    }

    public boolean isFlowingTextureEnabled() {
        return FLOWING_TEXTURE.get();
    }

    public int getFluidHeight() {
        return FLUID_HEIGHT.get();
    }

    public boolean isFlowOverEdgesEnabled() {
        return FLOW_OVER_EDGES.get();
    }

    public double getEvaporationRateOverworld() {
        return EVAPORATION_RATE_OVERWORLD.get();
    }

    public double getEvaporationRateNether() {
        return EVAPORATION_RATE_NETHER.get();
    }

    public double getRefillRateRain() {
        return REFILL_RATE_RAIN.get();
    }

    public double getRefillRateBiome() {
        return REFILL_RATE_BIOME.get();
    }

    public boolean isOceanRiverReplenishmentEnabled() {
        return OCEAN_RIVER_REPLENISHMENT_ENABLED.get();
    }

    public double getOceanReplenishRate() {
        return OCEAN_REPLENISH_RATE.get();
    }

    public double getRiverReplenishRate() {
        return RIVER_REPLENISH_RATE.get();
    }

    public int getMaxReplenishmentsPerTick() {
        return MAX_REPLENISHMENTS_PER_TICK.get();
    }

    /**
     * Apply ocean/river replenishment settings to the handler
     */
    public void applyOceanRiverSettings() {
        OceanRiverWaterReplenishment.setEnabled(isOceanRiverReplenishmentEnabled());
        OceanRiverWaterReplenishment.setOceanReplenishRate((float) getOceanReplenishRate());
        OceanRiverWaterReplenishment.setRiverReplenishRate((float) getRiverReplenishRate());
        OceanRiverWaterReplenishment.setMaxReplenishmentsPerTick(getMaxReplenishmentsPerTick());
        LOGGER.info("Ocean/River replenishment settings applied - enabled: {}, ocean rate: {}, river rate: {}",
            isOceanRiverReplenishmentEnabled(), getOceanReplenishRate(), getRiverReplenishRate());
    }
}
