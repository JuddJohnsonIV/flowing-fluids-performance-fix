package flowingfluidsfixes;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Configuration for Fluid Optimizer mod
 */
public class FluidOptimizerConfig {
    private static final Logger LOGGER = LogManager.getLogger();
    
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    
    // Performance settings
    public static final ForgeConfigSpec.IntValue maxUpdatesPerTick;
    public static final ForgeConfigSpec.IntValue maxQueueSize;
    public static final ForgeConfigSpec.DoubleValue targetTPS;
    public static final ForgeConfigSpec.DoubleValue minTPS;
    
    // Priority settings
    public static final ForgeConfigSpec.IntValue playerUpdateDistance;
    public static final ForgeConfigSpec.BooleanValue prioritizeNearPlayers;
    public static final ForgeConfigSpec.BooleanValue prioritizeSourceBlocks;
    
    // Logging settings
    public static final ForgeConfigSpec.BooleanValue enableDebugLogging;
    public static final ForgeConfigSpec.BooleanValue enablePerformanceLogging;
    
    // Optimization level config
    public static final ForgeConfigSpec.ConfigValue<String> flowingFluidsOptimizationLevelConfig;
    
    // Optimization levels for Flowing Fluids
    public enum OptimizationLevel {
        AGGRESSIVE,
        BALANCED,
        MINIMAL
    }

    private static OptimizationLevel flowingFluidsOptimizationLevel = OptimizationLevel.BALANCED;

    public static OptimizationLevel getFlowingFluidsOptimizationLevel() {
        return flowingFluidsOptimizationLevel;
    }

    public static void setFlowingFluidsOptimizationLevel(OptimizationLevel level) {
        flowingFluidsOptimizationLevel = level;
        LOGGER.info("Flowing Fluids optimization level set to: {}", level);
    }

    // Method to load configuration from the config spec
    public static void loadConfig() {
        if (flowingFluidsOptimizationLevelConfig != null && flowingFluidsOptimizationLevelConfig.get() != null) {
            String levelStr = flowingFluidsOptimizationLevelConfig.get().toUpperCase();
            try {
                flowingFluidsOptimizationLevel = OptimizationLevel.valueOf(levelStr);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Invalid optimization level '{}', defaulting to BALANCED", levelStr);
                flowingFluidsOptimizationLevel = OptimizationLevel.BALANCED;
            }
        } else {
            flowingFluidsOptimizationLevel = OptimizationLevel.BALANCED;
        }
        LOGGER.info("Loaded Flowing Fluids optimization configuration: {}", flowingFluidsOptimizationLevel);
    }

    static {
        BUILDER.push("Performance Settings");
        
        maxUpdatesPerTick = BUILDER
            .comment("Maximum fluid updates to process per tick")
            .defineInRange("maxUpdatesPerTick", 1000, 100, 10000);
        
        maxQueueSize = BUILDER
            .comment("Maximum number of fluid updates to queue")
            .defineInRange("maxQueueSize", 5000, 1000, 50000);
        
        targetTPS = BUILDER
            .comment("Target server TPS for performance adjustment")
            .defineInRange("targetTPS", 20.0, 10.0, 20.0);
        
        minTPS = BUILDER
            .comment("Minimum TPS before reducing update rate")
            .defineInRange("minTPS", 15.0, 5.0, 20.0);
        
        BUILDER.pop();
        BUILDER.push("Priority Settings");
        
        playerUpdateDistance = BUILDER
            .comment("Distance in blocks to check for nearby players for priority")
            .defineInRange("playerUpdateDistance", 16, 4, 64);
        
        prioritizeNearPlayers = BUILDER
            .comment("Give higher priority to fluid updates near players")
            .define("prioritizeNearPlayers", true);
        
        prioritizeSourceBlocks = BUILDER
            .comment("Give highest priority to source blocks")
            .define("prioritizeSourceBlocks", true);
        
        BUILDER.pop();
        BUILDER.push("Logging Settings");
        
        enableDebugLogging = BUILDER
            .comment("Enable debug logging")
            .define("enableDebugLogging", false);
        
        enablePerformanceLogging = BUILDER
            .comment("Enable performance logging")
            .define("enablePerformanceLogging", true);
        
        BUILDER.pop();
        BUILDER.push("Optimization Settings");
        
        flowingFluidsOptimizationLevelConfig = BUILDER
            .comment("Optimization level for Flowing Fluids (AGGRESSIVE, BALANCED, MINIMAL)")
            .define("flowingFluidsOptimizationLevel", "BALANCED");
        
        BUILDER.pop();
        
        SPEC = BUILDER.build();
    }
    
    /**
     * Register the configuration
     */
    public static void register() {
        LOGGER.info("Fluid Optimizer configuration ready - registration handled by mod loading system");
    }
    
    /**
     * Reload configuration
     */
    public static void reload() {
        LOGGER.info("Fluid Optimizer configuration reloaded");
    }
}
