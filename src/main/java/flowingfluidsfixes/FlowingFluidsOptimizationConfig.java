package flowingfluidsfixes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Enhanced Configuration System for Flowing Fluids Integration
 * Provides configurable optimization levels and comprehensive settings
 */
@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.MOD)
public class FlowingFluidsOptimizationConfig {
    private static final Logger LOGGER = LogManager.getLogger(FlowingFluidsOptimizationConfig.class);
    
    public static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec COMMON_CONFIG;
    
    // Optimization Level Configuration
    public static ForgeConfigSpec.EnumValue<OptimizationLevel> optimizationLevel;
    
    // Flowing Fluids Integration Settings
    public static ForgeConfigSpec.BooleanValue enableFlowingFluidsIntegration;
    public static ForgeConfigSpec.BooleanValue prioritizeFlowingFluidsUpdates;
    public static ForgeConfigSpec.DoubleValue flowingFluidsPriorityMultiplier;
    
    // Performance Thresholds
    public static ForgeConfigSpec.DoubleValue emergencyModeTPSThreshold;
    public static ForgeConfigSpec.DoubleValue performanceModeTPSThreshold;
    public static ForgeConfigSpec.IntValue maxUpdatesPerTickAggressive;
    public static ForgeConfigSpec.IntValue maxUpdatesPerTickBalanced;
    public static ForgeConfigSpec.IntValue maxUpdatesPerTickMinimal;
    
    // Distance and Priority Settings
    public static ForgeConfigSpec.IntValue criticalUpdateDistance;
    public static ForgeConfigSpec.IntValue normalUpdateDistance;
    public static ForgeConfigSpec.IntValue playerProximityRadius;
    public static ForgeConfigSpec.BooleanValue enablePlayerProximityPriority;
    public static ForgeConfigSpec.BooleanValue enableEntityProximityPriority;
    public static ForgeConfigSpec.IntValue entityProximityRadius;
    
    // Flowing Fluids Specific Settings
    public static ForgeConfigSpec.BooleanValue preserveFiniteFluids;
    public static ForgeConfigSpec.BooleanValue enablePressureSystemOptimization;
    public static ForgeConfigSpec.BooleanValue enableBiomeAwareOptimization;
    public static ForgeConfigSpec.BooleanValue enableEdgeFlowOptimization;
    
    // Debug and Monitoring
    public static ForgeConfigSpec.BooleanValue enableDetailedLogging;
    public static ForgeConfigSpec.BooleanValue enablePerformanceMetrics;
    public static ForgeConfigSpec.BooleanValue enableFlowingFluidsDebug;
    
    // Advanced Settings
    public static ForgeConfigSpec.IntValue tickDelayMultiplier;
    public static ForgeConfigSpec.DoubleValue serverCapacityMultiplier;
    public static ForgeConfigSpec.BooleanValue enableAdaptiveOptimization;
    public static ForgeConfigSpec.BooleanValue enableEmergencyPerformanceMode;
    
    // Distance-based processing (like Flowing Fluids' fluid_processing_distance)
    public static ForgeConfigSpec.IntValue fluidProcessingDistanceChunks;
    public static ForgeConfigSpec.BooleanValue enableDistanceLimit;
    
    // Game time protection
    public static ForgeConfigSpec.BooleanValue enableGameTimeProtection;
    
    // Threading Settings - Offload fluid processing from main tick thread
    public static ForgeConfigSpec.BooleanValue enableAsyncProcessing;
    public static ForgeConfigSpec.IntValue fluidThreadPoolSize;
    public static ForgeConfigSpec.IntValue asyncQueueSize;
    
    static {
        setupConfig();
        COMMON_CONFIG = COMMON_BUILDER.build();
    }
    
    private static void setupConfig() {
        COMMON_BUILDER.comment("Flowing Fluids Integration Optimization Configuration")
                      .push("optimization_level");
        
        // Main optimization level setting
        optimizationLevel = COMMON_BUILDER
            .comment("Optimization level for Flowing Fluids integration. " +
                     "AGGRESSIVE: Maximum performance, may affect fluid behavior. " +
                     "BALANCED: Good performance with maintained fluid mechanics. " +
                     "MINIMAL: Light optimization, preserves all fluid behavior.")
            .defineEnum("optimizationLevel", OptimizationLevel.BALANCED);
        
        COMMON_BUILDER.pop();
        COMMON_BUILDER.push("flowing_fluids_integration");
        
        // Flowing Fluids integration settings
        enableFlowingFluidsIntegration = COMMON_BUILDER
            .comment("Enable enhanced integration with Flowing Fluids mod")
            .define("enableFlowingFluidsIntegration", true);
        
        prioritizeFlowingFluidsUpdates = COMMON_BUILDER
            .comment("Give higher priority to Flowing Fluids mod updates")
            .define("prioritizeFlowingFluidsUpdates", true);
        
        flowingFluidsPriorityMultiplier = COMMON_BUILDER
            .comment("Priority multiplier for Flowing Fluids updates (higher = more priority)")
            .defineInRange("flowingFluidsPriorityMultiplier", 1.5, 0.5, 3.0);
        
        COMMON_BUILDER.pop();
        COMMON_BUILDER.push("performance_thresholds");
        
        // Performance thresholds based on optimization level
        emergencyModeTPSThreshold = COMMON_BUILDER
            .comment("TPS threshold below which emergency mode is activated")
            .defineInRange("emergencyModeTPSThreshold", 10.0, 5.0, 15.0);
        
        performanceModeTPSThreshold = COMMON_BUILDER
            .comment("TPS threshold below which performance mode is activated")
            .defineInRange("performanceModeTPSThreshold", 15.0, 10.0, 20.0);
        
        maxUpdatesPerTickAggressive = COMMON_BUILDER
            .comment("Maximum fluid updates per tick in AGGRESSIVE mode")
            .defineInRange("maxUpdatesPerTickAggressive", 50, 25, 250);
        
        maxUpdatesPerTickBalanced = COMMON_BUILDER
            .comment("Maximum fluid updates per tick in BALANCED mode")
            .defineInRange("maxUpdatesPerTickBalanced", 125, 50, 500);
        
        maxUpdatesPerTickMinimal = COMMON_BUILDER
            .comment("Maximum fluid updates per tick in MINIMAL mode")
            .defineInRange("maxUpdatesPerTickMinimal", 250, 100, 1000);
        
        COMMON_BUILDER.pop();
        COMMON_BUILDER.push("distance_and_priority");
        
        // Distance and priority settings
        criticalUpdateDistance = COMMON_BUILDER
            .comment("Distance considered critical for immediate fluid updates")
            .defineInRange("criticalUpdateDistance", 16, 8, 32);
        
        normalUpdateDistance = COMMON_BUILDER
            .comment("Distance for normal fluid update processing")
            .defineInRange("normalUpdateDistance", 64, 32, 128);
        
        playerProximityRadius = COMMON_BUILDER
            .comment("Radius around players where fluid updates are prioritized")
            .defineInRange("playerProximityRadius", 32, 16, 64);
        
        enablePlayerProximityPriority = COMMON_BUILDER
            .comment("Enable player proximity-based fluid update prioritization")
            .define("enablePlayerProximityPriority", true);
        
        enableEntityProximityPriority = COMMON_BUILDER
            .comment("Enable fluid update prioritization near entities to prevent lag in entity behavior")
            .define("enableEntityProximityPriority", true);
        
        entityProximityRadius = COMMON_BUILDER
            .comment("Radius around entities where fluid updates are prioritized to prevent lag")
            .defineInRange("entityProximityRadius", 16, 8, 32);
        
        COMMON_BUILDER.pop();
        COMMON_BUILDER.push("flowing_fluids_specific");
        
        // Flowing Fluids specific optimizations
        preserveFiniteFluids = COMMON_BUILDER
            .comment("Preserve finite fluid behavior in Flowing Fluids mod")
            .define("preserveFiniteFluids", true);
        
        enablePressureSystemOptimization = COMMON_BUILDER
            .comment("Enable optimization for Flowing Fluids pressure systems")
            .define("enablePressureSystemOptimization", true);
        
        enableBiomeAwareOptimization = COMMON_BUILDER
            .comment("Enable biome-aware optimization for special fluid behaviors")
            .define("enableBiomeAwareOptimization", true);
        
        enableEdgeFlowOptimization = COMMON_BUILDER
            .comment("Enable optimization for edge flow behavior")
            .define("enableEdgeFlowOptimization", true);
        
        COMMON_BUILDER.pop();
        COMMON_BUILDER.push("debug_and_monitoring");
        
        // Debug and monitoring settings
        enableDetailedLogging = COMMON_BUILDER
            .comment("Enable detailed logging for Flowing Fluids integration")
            .define("enableDetailedLogging", false);
        
        enablePerformanceMetrics = COMMON_BUILDER
            .comment("Enable performance metrics collection")
            .define("enablePerformanceMetrics", true);
        
        enableFlowingFluidsDebug = COMMON_BUILDER
            .comment("Enable Flowing Fluids specific debug information")
            .define("enableFlowingFluidsDebug", false);
        
        COMMON_BUILDER.pop();
        COMMON_BUILDER.push("advanced_settings");
        
        // Advanced settings
        tickDelayMultiplier = COMMON_BUILDER
            .comment("Multiplier for fluid tick delays based on optimization level")
            .defineInRange("tickDelayMultiplier", 1, 1, 5);
        
        serverCapacityMultiplier = COMMON_BUILDER
            .comment("Multiplier for performance settings based on server capacity")
            .defineInRange("serverCapacityMultiplier", 1.0, 0.5, 3.0);
        
        enableAdaptiveOptimization = COMMON_BUILDER
            .comment("Enable adaptive optimization based on server performance")
            .define("enableAdaptiveOptimization", true);
        
        enableEmergencyPerformanceMode = COMMON_BUILDER
            .comment("Enable emergency performance mode during severe lag")
            .define("enableEmergencyPerformanceMode", true);
        
        COMMON_BUILDER.pop();
        COMMON_BUILDER.push("distance_processing");
        
        // Distance-based processing settings (like Flowing Fluids' fluid_processing_distance)
        enableDistanceLimit = COMMON_BUILDER
            .comment("Enable distance-based fluid processing limit. " +
                     "Fluids beyond this distance from players will NOT be processed at all. " +
                     "This is the most effective optimization for protecting mob AI and game time.")
            .define("enableDistanceLimit", true);
        
        fluidProcessingDistanceChunks = COMMON_BUILDER
            .comment("Distance in chunks (16 blocks each) for fluid processing. " +
                     "Similar to Flowing Fluids' fluid_processing_distance setting. " +
                     "Lower values = better performance but less visible fluid updates. " +
                     "6 chunks (96 blocks) is optimized for performance at high flow rates.")
            .defineInRange("fluidProcessingDistanceChunks", 6, 2, 24);
        
        enableGameTimeProtection = COMMON_BUILDER
            .comment("Enable game time protection. " +
                     "Halts fluid processing when the day/night cycle falls behind real time. " +
                     "This prevents the 'slow days' feeling caused by server lag.")
            .define("enableGameTimeProtection", true);
        
        COMMON_BUILDER.pop();
        COMMON_BUILDER.push("threading");
        
        // Threading settings - offload fluid processing from main tick thread
        enableAsyncProcessing = COMMON_BUILDER
            .comment("Enable async fluid processing on separate threads. " +
                     "This offloads fluid calculations from the main tick thread, " +
                     "preventing mob pathfinding and other game systems from being affected by fluid updates.")
            .define("enableAsyncProcessing", true);
        
        fluidThreadPoolSize = COMMON_BUILDER
            .comment("Number of threads dedicated to fluid processing. " +
                     "Default is half of available CPU cores. " +
                     "Increase for heavy fluid worlds, decrease if you have CPU constraints.")
            .defineInRange("fluidThreadPoolSize", Math.max(2, Runtime.getRuntime().availableProcessors() / 2), 1, 16);
        
        asyncQueueSize = COMMON_BUILDER
            .comment("Maximum number of fluid updates to queue for async processing")
            .defineInRange("asyncQueueSize", 5000, 1000, 20000);
        
        COMMON_BUILDER.pop();
    }
    
    /**
     * Get current optimization settings based on level
     */
    public static OptimizationSettings getCurrentOptimizationSettings() {
        return switch (optimizationLevel.get()) {
            case AGGRESSIVE -> getAggressiveSettings();
            case BALANCED -> getBalancedSettings();
            case MINIMAL -> getMinimalSettings();
        };
    }
    
    private static OptimizationSettings getAggressiveSettings() {
        return new OptimizationSettings(
            maxUpdatesPerTickAggressive.get(),
            3, // Higher delay multiplier
            16, // Smaller critical distance
            32, // Smaller normal distance
            true, // Enable all optimizations
            true, // Emergency mode
            0.5 // Lower priority threshold
        );
    }
    
    private static OptimizationSettings getBalancedSettings() {
        return new OptimizationSettings(
            maxUpdatesPerTickBalanced.get(),
            2, // Moderate delay multiplier
            criticalUpdateDistance.get(),
            normalUpdateDistance.get(),
            true, // Enable most optimizations
            true, // Emergency mode
            1.0 // Normal priority threshold
        );
    }
    
    private static OptimizationSettings getMinimalSettings() {
        return new OptimizationSettings(
            maxUpdatesPerTickMinimal.get(),
            1, // Minimal delay multiplier
            64, // Larger critical distance
            128, // Larger normal distance
            false, // Disable aggressive optimizations
            false, // No emergency mode
            2.0 // Higher priority threshold
        );
    }
    
    /**
     * Check if Flowing Fluids integration is enabled
     */
    public static boolean isFlowingFluidsIntegrationEnabled() {
        return enableFlowingFluidsIntegration.get() && FlowingFluidsIntegration.isFlowingFluidsLoaded();
    }
    
    /**
     * Get current maximum updates per tick based on optimization level
     */
    public static int getCurrentMaxUpdatesPerTick() {
        return getCurrentOptimizationSettings().maxUpdatesPerTick();
    }
    
    /**
     * Log current configuration
     */
    public static void logCurrentConfig() {
        if (enableDetailedLogging.get()) {
            LOGGER.info("=== Flowing Fluids Optimization Configuration ===");
            LOGGER.info("Optimization Level: {}", optimizationLevel.get());
            LOGGER.info("Flowing Fluids Integration: {}", enableFlowingFluidsIntegration.get());
            LOGGER.info("Max Updates Per Tick: {}", getCurrentMaxUpdatesPerTick());
            LOGGER.info("Critical Distance: {}", criticalUpdateDistance.get());
            LOGGER.info("Player Proximity Priority: {}", enablePlayerProximityPriority.get());
            LOGGER.info("Entity Proximity Priority: {}", enableEntityProximityPriority.get());
            LOGGER.info("Entity Proximity Radius: {}", entityProximityRadius.get());
            LOGGER.info("Adaptive Optimization: {}", enableAdaptiveOptimization.get());
            LOGGER.info("Emergency Performance Mode: {}", enableEmergencyPerformanceMode.get());
            LOGGER.info("Async Processing: {}", enableAsyncProcessing.get());
            LOGGER.info("Thread Pool Size: {}", fluidThreadPoolSize.get());
            LOGGER.info("Async Queue Size: {}", asyncQueueSize.get());
            LOGGER.info("Distance Limit Enabled: {}", enableDistanceLimit.get());
            LOGGER.info("Processing Distance: {} chunks", fluidProcessingDistanceChunks.get());
            LOGGER.info("Game Time Protection: {}", enableGameTimeProtection.get());
            LOGGER.info("===============================================");
        }
        
        // Apply threading configuration to FluidThreadingHandler
        applyThreadingConfig();
        
        // Apply distance limit configuration
        applyDistanceLimitConfig();
    }
    
    /**
     * Apply distance limit configuration
     */
    public static void applyDistanceLimitConfig() {
        if (enableDistanceLimit.get()) {
            FluidProcessingDistanceLimit.setProcessingDistanceChunks(fluidProcessingDistanceChunks.get());
        }
    }
    
    /**
     * Apply threading configuration to the FluidThreadingHandler
     */
    public static void applyThreadingConfig() {
        FluidThreadingHandler.setAsyncEnabled(enableAsyncProcessing.get());
        FluidThreadingHandler.setThreadPoolSize(fluidThreadPoolSize.get());
    }
    
    @SubscribeEvent
    public static void onConfigLoading(final ModConfigEvent.Loading configEvent) {
        if (configEvent.getConfig().getSpec() == COMMON_CONFIG) {
            LOGGER.info("Flowing Fluids optimization configuration loaded");
            logCurrentConfig();
            EntityProtectionSystem.updateProtectionRadiusFromConfig();
        }
    }
    
    @SubscribeEvent
    public static void onConfigReloading(final ModConfigEvent.Reloading configEvent) {
        if (configEvent.getConfig().getSpec() == COMMON_CONFIG) {
            LOGGER.info("Flowing Fluids optimization configuration reloaded");
            logCurrentConfig();
            EntityProtectionSystem.updateProtectionRadiusFromConfig();
        }
    }
    
    /**
     * Optimization levels enum
     */
    public enum OptimizationLevel {
        AGGRESSIVE("Maximum performance optimization"),
        BALANCED("Balanced performance and functionality"),
        MINIMAL("Light optimization, preserves all behavior");
        
        private final String description;
        
        OptimizationLevel(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Optimization settings record
     */
    public record OptimizationSettings(
        int maxUpdatesPerTick,
        int delayMultiplier,
        int criticalDistance,
        int normalDistance,
        boolean enableAggressiveOptimizations,
        boolean enableEmergencyMode,
        double priorityThreshold
    ) {
        public boolean isWithinCriticalDistance(double distance) {
            return distance <= criticalDistance * criticalDistance; // Compare squared distances
        }
        
        public boolean isWithinNormalDistance(double distance) {
            return distance <= normalDistance * normalDistance; // Compare squared distances
        }
    }
}
