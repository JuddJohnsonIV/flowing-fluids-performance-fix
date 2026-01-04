package flowingfluidsfixes;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig.Type;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main mod class for Flowing Fluids Fixes
 * Ultra-aggressive optimization with proper Flowing Fluids API integration
 * Works completely automatically - no commands needed
 */
@Mod("flowingfluidsfixes")
@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.MOD)
public class FlowingFluidsFixes {
    private static final Logger LOGGER = LogManager.getLogger(FlowingFluidsFixes.class);
    @SuppressWarnings("removal")
    private static final ModLoadingContext MOD_LOADING_CONTEXT = ModLoadingContext.get();

    /**
     * Main mod constructor - initializes all optimization systems
     */
    public FlowingFluidsFixes() {
        LOGGER.info("Flowing Fluids Fixes mod constructor starting");
        
        // Register configuration system
        MOD_LOADING_CONTEXT.registerConfig(Type.COMMON, FlowingFluidsOptimizationConfig.COMMON_CONFIG, "flowingfluidsfixes-optimization.toml");
        
        // Initialize Flowing Fluids integration
        FlowingFluidsIntegration.initializeIntegration();
        
        // Log current optimization configuration
        FlowingFluidsOptimizationConfig.logCurrentConfig();
        
        LOGGER.info("Flowing Fluids Fixes mod initialized with optimization level: {}", 
                   FlowingFluidsOptimizationConfig.optimizationLevel.get());
    }
    
    @SubscribeEvent
    public void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Common setup for Flowing Fluids Fixes");
        
        // Verify Flowing Fluids API connection
        if (FlowingFluidsAPIIntegration.isFlowingFluidsAvailable()) {
            LOGGER.info("✅ Flowing Fluids API connected successfully");
        } else {
            LOGGER.info("⚠️ Flowing Fluids API not available - using vanilla optimization");
        }
        
        // Additional setup can be added here if needed
    }
    
    /**
     * Log comprehensive performance statistics
     */
    public static void logStatus() {
        LOGGER.info("=== Flowing Fluids Integration Status ===");
        LOGGER.info("Fluid Optimization Status: {}", AggressiveFluidOptimizer.getStatus());
        LOGGER.info("Fluid Tick Scheduler: {}", FluidTickScheduler.getPerformanceStats());
        
        // Log Flowing Fluids specific performance if enabled
        if (FlowingFluidsOptimizationConfig.enablePerformanceMetrics.get()) {
            LOGGER.info("Flowing Fluids Performance: {}", 
                       FlowingFluidsPerformanceMonitor.getOptimizationEffectiveness());
        }
        
        // Log current optimization settings
        var settings = FlowingFluidsOptimizationConfig.getCurrentOptimizationSettings();
        LOGGER.info("Current Optimization Settings:");
        LOGGER.info("  Max Updates Per Tick: {}", settings.maxUpdatesPerTick());
        LOGGER.info("  Critical Distance: {} blocks", settings.criticalDistance());
        LOGGER.info("  Normal Distance: {} blocks", settings.normalDistance());
        LOGGER.info("  Delay Multiplier: {}x", settings.delayMultiplier());
        LOGGER.info("  Emergency Mode: {}", settings.enableEmergencyMode());
        LOGGER.info("========================================");
    }
    
    /**
     * Get comprehensive performance report
     */
    public static String getPerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Flowing Fluids Integration Performance Report ===\n");
        
        // Basic optimization status
        report.append("Basic Status:\n");
        report.append("  ").append(AggressiveFluidOptimizer.getStatus()).append("\n");
        report.append("  ").append(FluidTickScheduler.getPerformanceStats()).append("\n");
        
        // Flowing Fluids specific metrics
        if (FlowingFluidsOptimizationConfig.isFlowingFluidsIntegrationEnabled()) {
            report.append("\nFlowing Fluids Integration:\n");
            report.append(FlowingFluidsPerformanceMonitor.getOptimizationEffectiveness()).append("\n");
            
            var detailedReport = FlowingFluidsPerformanceMonitor.getPerformanceReport();
            report.append("  Total Updates: ").append(detailedReport.get("totalFlowingFluidsUpdates")).append("\n");
            report.append("  Optimization Rate: ").append(String.format("%.2f%%", (Double) detailedReport.get("optimizationRate"))).append("\n");
            report.append("  Deferral Rate: ").append(String.format("%.2f%%", (Double) detailedReport.get("deferralRate"))).append("\n");
            report.append("  Current TPS: ").append(String.format("%.2f", (Double) detailedReport.get("currentTPS"))).append("\n");
            
            if (detailedReport.containsKey("tpsImprovement")) {
                report.append("  TPS Improvement: ").append(String.format("%.2f%%", (Double) detailedReport.get("tpsImprovement"))).append("\n");
            }
        }
        
        // Server performance
        report.append("\nServer Performance:\n");
        report.append("  TPS: ").append(String.format("%.2f", PerformanceMonitor.getAverageTPS())).append("\n");
        report.append("  Current Fluid Updates: ").append(PerformanceMonitor.getFluidUpdateCount()).append("\n");
        report.append("  Server Overloaded: ").append(PerformanceMonitor.isServerOverloaded()).append("\n");
        
        report.append("================================================\n");
        return report.toString();
    }
}
