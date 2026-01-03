package flowingfluidsfixes;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigManager {
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static ForgeConfigSpec COMMON_CONFIG;
    public static ForgeConfigSpec CLIENT_CONFIG;
    
    // Performance settings
    public static ForgeConfigSpec.IntValue MAX_FLUID_UPDATES_PER_TICK;
    public static ForgeConfigSpec.IntValue SPREAD_CHECK_RADIUS;
    public static ForgeConfigSpec.IntValue TICK_DELAY;
    
    // Feature toggles
    public static ForgeConfigSpec.BooleanValue ENABLE_FLOATING_WATER_FIX;
    public static ForgeConfigSpec.BooleanValue ENABLE_TICK_OPTIMIZATION;
    public static ForgeConfigSpec.BooleanValue ENABLE_PRESSURE_SYSTEM;
    public static ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING;
    
    // Advanced settings
    public static ForgeConfigSpec.DoubleValue FLUID_FLOW_SPEED_MULTIPLIER;
    public static ForgeConfigSpec.IntValue BATCH_SIZE;
    public static ForgeConfigSpec.BooleanValue ADAPTIVE_PERFORMANCE;
    
    // Gameplay interaction settings
    public static ForgeConfigSpec.DoubleValue ANIMAL_BREEDING_DRAINS_WATER_CHANCE;
    
    // Client-specific settings
    public static ForgeConfigSpec.BooleanValue SHOW_FLUID_DEBUG_INFO;
    public static ForgeConfigSpec.BooleanValue ENABLE_FLUID_PARTICLES;
    public static ForgeConfigSpec.IntValue FLUID_RENDER_DISTANCE;
    public static ForgeConfigSpec.BooleanValue SMOOTH_FLUID_ANIMATION;
    public static ForgeConfigSpec.DoubleValue FLUID_TRANSPARENCY;
    
    // New configuration parameters
    public static ForgeConfigSpec.IntValue PLAYER_PROXIMITY_RADIUS;
    public static ForgeConfigSpec.DoubleValue EMERGENCY_MODE_THRESHOLD;
    public static ForgeConfigSpec.DoubleValue SERVER_CAPACITY_MULTIPLIER;
    
    static {
        initCommonConfig();
        initClientConfig();
    }
    
    private static void initCommonConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        
        builder.comment("Performance Fix Configuration").push("performance");
        
        builder.comment("Performance Settings");
        MAX_FLUID_UPDATES_PER_TICK = builder
                .comment("Maximum fluid updates processed per tick to prevent lag")
                .defineInRange("maxFluidUpdatesPerTick", 40000, 1000, 100000);
                
        SPREAD_CHECK_RADIUS = builder
                .comment("Radius to check for fluid spreading (lower = better performance)")
                .defineInRange("spreadCheckRadius", 8, 1, 16);
                
        TICK_DELAY = builder
                .comment("Delay between fluid ticks (higher = better performance, slower flow)")
                .defineInRange("tickDelay", 2, 1, 10);
        
        PLAYER_PROXIMITY_RADIUS = builder
                .comment("Radius around players where fluid updates are prioritized")
                .defineInRange("playerProximityRadius", 32, 8, 64);
                
        EMERGENCY_MODE_THRESHOLD = builder
                .comment("TPS threshold below which emergency mode is activated for fluid updates")
                .defineInRange("emergencyModeThreshold", 10.0, 5.0, 15.0);
        
        builder.comment("Feature Toggles");
        ENABLE_FLOATING_WATER_FIX = builder
                .comment("Enable fix for floating water layers")
                .define("enableFloatingWaterFix", true);
                
        ENABLE_TICK_OPTIMIZATION = builder
                .comment("Enable tick optimization for mass fluid changes")
                .define("enableTickOptimization", true);
                
        ENABLE_PRESSURE_SYSTEM = builder
                .comment("Enable optimized fluid pressure system")
                .define("enablePressureSystem", true);
                
        ENABLE_DEBUG_LOGGING = builder
                .comment("Enable debug logging for troubleshooting")
                .define("enableDebugLogging", true);
        
        builder.comment("Advanced Settings");
        FLUID_FLOW_SPEED_MULTIPLIER = builder
                .comment("Multiplier for fluid flow speed (1.0 = normal, lower = slower)")
                .defineInRange("fluidFlowSpeedMultiplier", 1.0, 0.1, 2.0);
                
        BATCH_SIZE = builder
                .comment("Batch size for processing fluid updates")
                .defineInRange("batchSize", 100, 10, 500);
                
        ADAPTIVE_PERFORMANCE = builder
                .comment("Enable adaptive performance scaling based on server load")
                .define("adaptivePerformance", true);
        
        SERVER_CAPACITY_MULTIPLIER = builder
                .comment("Multiplier for performance settings based on server capacity (higher = more updates for powerful servers)")
                .defineInRange("serverCapacityMultiplier", 1.0, 0.5, 3.0);
        
        builder.comment("Gameplay Interaction Settings");
        ANIMAL_BREEDING_DRAINS_WATER_CHANCE = builder
                .comment("Chance that animal breeding will drain nearby water (0.0 = never, 1.0 = always)")
                .defineInRange("animalBreedingDrainsWaterChance", 0.2, 0.0, 1.0);
        
        builder.pop();
        
        COMMON_CONFIG = builder.build();
    }
    
    private static void initClientConfig() {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        
        builder.comment("Client-side Performance Fix Configuration").push("client");
        
        builder.comment("Visual Settings");
        SHOW_FLUID_DEBUG_INFO = builder
                .comment("Show fluid debug information overlay (for troubleshooting)")
                .define("showFluidDebugInfo", false);
        
        ENABLE_FLUID_PARTICLES = builder
                .comment("Enable fluid particle effects (disable for better performance)")
                .define("enableFluidParticles", true);
        
        FLUID_RENDER_DISTANCE = builder
                .comment("Maximum distance to render fluid updates (lower = better performance)")
                .defineInRange("fluidRenderDistance", 64, 16, 256);
        
        SMOOTH_FLUID_ANIMATION = builder
                .comment("Enable smooth fluid animation transitions")
                .define("smoothFluidAnimation", true);
        
        FLUID_TRANSPARENCY = builder
                .comment("Fluid transparency level (0.0 = opaque, 1.0 = fully transparent)")
                .defineInRange("fluidTransparency", 0.3, 0.0, 1.0);
        
        builder.pop();
        
        CLIENT_CONFIG = builder.build();
    }
    
    public static void register() {
        // Register configuration settings
        LOGGER.info("ConfigManager initialized with default settings");
        // Load configuration from file if available
        File configFile = new File("config/flowingfluidsfixes.properties");
        if (configFile.exists()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                // Load settings from properties file
                ENABLE_DEBUG_LOGGING.set(Boolean.parseBoolean(props.getProperty("debug.logging", "false")));
                ENABLE_PRESSURE_SYSTEM.set(Boolean.parseBoolean(props.getProperty("pressure.system", "true")));
                MAX_FLUID_UPDATES_PER_TICK.set(Integer.parseInt(props.getProperty("max.updates.per.tick", "5000")));
                ADAPTIVE_PERFORMANCE.set(Boolean.parseBoolean(props.getProperty("adaptive.performance", "true")));
                LOGGER.info("Configuration loaded from file");
            } catch (IOException e) {
                LOGGER.error("Failed to load config file: {}", e.getMessage());
                setDefaultConfig();
            }
        } else {
            LOGGER.info("Config file not found, using default settings");
            setDefaultConfig();
        }
    }
    
    public static int getMaxUpdatesForCurrentLoad() {
        if (!ADAPTIVE_PERFORMANCE.get()) {
            return MAX_FLUID_UPDATES_PER_TICK.get();
        }
        
        // Default values if PerformanceMonitor is not available
        double tpsValue = 20.0; // Assume optimal TPS as default
        double cpuUsage = 50.0; // Assume moderate CPU usage as default
        int baseLimit = MAX_FLUID_UPDATES_PER_TICK.get();

        // Adjust based on TPS
        if (tpsValue < EMERGENCY_MODE_THRESHOLD.get()) {
            baseLimit = (int) (baseLimit * 0.5); // Reduce updates by half if TPS is low
            LOGGER.debug("Reducing fluid updates due to low TPS: {} (threshold: {}), new limit: {}", tpsValue, EMERGENCY_MODE_THRESHOLD.get(), baseLimit);
        }

        // Further adjust based on CPU usage if available
        if (cpuUsage > 80.0) {
            baseLimit = (int) (baseLimit * 0.3); // Further reduce if CPU usage is high
            LOGGER.debug("Further reducing fluid updates due to high CPU usage: {}% (threshold: 80%), new limit: {}", cpuUsage, baseLimit);
        }

        return baseLimit;
    }
    
    public static void logConfig() {
        if (ENABLE_DEBUG_LOGGING.get()) {
            LOGGER.info("Performance Fix Configuration:");
            LOGGER.info("  Max Fluid Updates Per Tick: {}", MAX_FLUID_UPDATES_PER_TICK.get());
            LOGGER.info("  Spread Check Radius: {}", SPREAD_CHECK_RADIUS.get());
            LOGGER.info("  Tick Delay: {}", TICK_DELAY.get());
            LOGGER.info("  Floating Water Fix: {}", ENABLE_FLOATING_WATER_FIX.get());
            LOGGER.info("  Tick Optimization: {}", ENABLE_TICK_OPTIMIZATION.get());
            LOGGER.info("  Pressure System: {}", ENABLE_PRESSURE_SYSTEM.get());
            LOGGER.info("  Flow Speed Multiplier: {}", FLUID_FLOW_SPEED_MULTIPLIER.get());
            LOGGER.info("  Batch Size: {}", BATCH_SIZE.get());
            LOGGER.info("  Adaptive Performance: {}", ADAPTIVE_PERFORMANCE.get());
            LOGGER.info("  Animal Breeding Drains Water Chance: {}", ANIMAL_BREEDING_DRAINS_WATER_CHANCE.get());
            LOGGER.info("  Player Proximity Radius: {}", PLAYER_PROXIMITY_RADIUS.get());
            LOGGER.info("  Emergency Mode Threshold: {}", EMERGENCY_MODE_THRESHOLD.get());
            LOGGER.info("  Server Capacity Multiplier: {}", SERVER_CAPACITY_MULTIPLIER.get());
        }
    }
    
    public static boolean isAdaptiveMode() {
        return ADAPTIVE_PERFORMANCE.get();
    }
    
    public static boolean isLoaded() {
        return COMMON_CONFIG != null && CLIENT_CONFIG != null;
    }
    
    private static void setDefaultConfig() {
        // Set default configuration settings
        ENABLE_DEBUG_LOGGING.set(true);
        ENABLE_PRESSURE_SYSTEM.set(true);
        MAX_FLUID_UPDATES_PER_TICK.set(40000);
        ADAPTIVE_PERFORMANCE.set(true);
        PLAYER_PROXIMITY_RADIUS.set(32);
        EMERGENCY_MODE_THRESHOLD.set(10.0);
        SERVER_CAPACITY_MULTIPLIER.set(1.0);
    }
}
