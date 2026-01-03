package flowingfluidsfixes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Modular Architecture for Optimization Algorithms
 * Step 29: Design modular architecture to easily swap or update optimization strategies
 */
public class ModularOptimizationArchitecture {
    private static final Logger LOGGER = LogManager.getLogger(ModularOptimizationArchitecture.class);
    
    // Registered optimization modules
    private static final Map<String, OptimizationModule> modules = new HashMap<>();
    private static final Map<String, Boolean> moduleEnabled = new HashMap<>();
    
    // Module categories
    public enum ModuleCategory {
        TICK_SCHEDULING,
        DISTANCE_OPTIMIZATION,
        MEMORY_MANAGEMENT,
        EMERGENCY_HANDLING,
        MULTIPLAYER_SYNC,
        PREDICTIVE,
        VISUAL_FIDELITY
    }
    
    static {
        registerDefaultModules();
    }
    
    private static void registerDefaultModules() {
        // Register all optimization modules
        registerModule("tick_scheduler", ModuleCategory.TICK_SCHEDULING,
            "Enhanced Tick Scheduler", "Priority-based fluid tick scheduling",
            () -> FluidTickScheduler.getAdaptiveMaxUpdatesPerTick() > 0);
        
        registerModule("aggressive_optimizer", ModuleCategory.DISTANCE_OPTIMIZATION,
            "Aggressive Optimizer", "Distance-based update skipping and delays",
            () -> true);
        
        registerModule("chunk_manager", ModuleCategory.DISTANCE_OPTIMIZATION,
            "Chunk-Based Manager", "Sleep/wake system for fluid chunks",
            () -> true);
        
        registerModule("memory_optimizer", ModuleCategory.MEMORY_MANAGEMENT,
            "Memory Optimizer", "Cache cleanup and memory management",
            () -> true);
        
        registerModule("emergency_mode", ModuleCategory.EMERGENCY_HANDLING,
            "Emergency Mode", "Graduated response to low TPS",
            () -> !EmergencyPerformanceMode.isEmergencyMode());
        
        registerModule("multiplayer_sync", ModuleCategory.MULTIPLAYER_SYNC,
            "Multiplayer Sync", "Batched synchronization for multiplayer",
            () -> true);
        
        registerModule("predictive_algorithm", ModuleCategory.PREDICTIVE,
            "Predictive Algorithm", "Pre-schedule updates based on predictions",
            () -> true);
        
        registerModule("visual_fidelity", ModuleCategory.VISUAL_FIDELITY,
            "Visual Fidelity", "Visual quality vs performance trade-offs",
            () -> true);
        
        registerModule("adaptive_feedback", ModuleCategory.TICK_SCHEDULING,
            "Adaptive Feedback", "Dynamic parameter adjustment",
            () -> AdaptiveFeedbackLoop.isFeedbackEnabled());
        
        registerModule("rollback_system", ModuleCategory.EMERGENCY_HANDLING,
            "Rollback System", "Auto-rollback on instability",
            () -> !FluidOptimizationRollback.isRollbackActive());
        
        LOGGER.info("Registered {} optimization modules", modules.size());
    }
    
    /**
     * Register an optimization module
     */
    public static void registerModule(String id, ModuleCategory category, String name,
                                      String description, Supplier<Boolean> healthCheck) {
        modules.put(id, new OptimizationModule(id, category, name, description, healthCheck));
        moduleEnabled.put(id, true);
    }
    
    /**
     * Enable a module
     */
    public static void enableModule(String id) {
        if (modules.containsKey(id)) {
            moduleEnabled.put(id, true);
            LOGGER.info("Enabled module: {}", id);
        }
    }
    
    /**
     * Disable a module
     */
    public static void disableModule(String id) {
        if (modules.containsKey(id)) {
            moduleEnabled.put(id, false);
            LOGGER.info("Disabled module: {}", id);
        }
    }
    
    /**
     * Check if module is enabled
     */
    public static boolean isModuleEnabled(String id) {
        return moduleEnabled.getOrDefault(id, false);
    }
    
    /**
     * Get module health status
     */
    public static boolean isModuleHealthy(String id) {
        OptimizationModule module = modules.get(id);
        if (module == null || !moduleEnabled.getOrDefault(id, false)) {
            return false;
        }
        try {
            return module.healthCheck.get();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get all modules in a category
     */
    public static Map<String, OptimizationModule> getModulesByCategory(ModuleCategory category) {
        Map<String, OptimizationModule> result = new HashMap<>();
        for (Map.Entry<String, OptimizationModule> entry : modules.entrySet()) {
            if (entry.getValue().category == category) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
    
    /**
     * Get system health report
     */
    public static String getHealthReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Module Health Report ===\n");
        
        int healthy = 0, unhealthy = 0, disabled = 0;
        
        for (Map.Entry<String, OptimizationModule> entry : modules.entrySet()) {
            String id = entry.getKey();
            OptimizationModule module = entry.getValue();
            
            String status;
            if (!moduleEnabled.getOrDefault(id, false)) {
                status = "DISABLED";
                disabled++;
            } else if (isModuleHealthy(id)) {
                status = "HEALTHY";
                healthy++;
            } else {
                status = "UNHEALTHY";
                unhealthy++;
            }
            
            report.append(String.format("  [%s] %s (%s): %s\n",
                status, module.name, module.category, module.description));
        }
        
        report.append(String.format("\nSummary: %d healthy, %d unhealthy, %d disabled\n",
            healthy, unhealthy, disabled));
        
        return report.toString();
    }
    
    /**
     * Get module count
     */
    public static int getModuleCount() {
        return modules.size();
    }
    
    /**
     * Get enabled module count
     */
    public static int getEnabledModuleCount() {
        return (int) moduleEnabled.values().stream().filter(b -> b).count();
    }
    
    /**
     * Optimization module record
     */
    public record OptimizationModule(
        String id,
        ModuleCategory category,
        String name,
        String description,
        Supplier<Boolean> healthCheck
    ) {}
}
