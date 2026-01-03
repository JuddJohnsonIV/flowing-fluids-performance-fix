package flowingfluidsfixes;

import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.FORGE)
@SuppressWarnings("all")
public class EmergencyPerformanceMode {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmergencyPerformanceMode.class);
    
    // Emergency thresholds - configurable via optimization level
    // FIXED: Raised from 2.0 to 8.0 - 2.0 was too low and would only activate when server is nearly frozen
    private static double emergencyTPSThreshold = 8.0;
    private static int emergencyModeDuration = 60;
    private static int maxUpdatesPerTick = 1000;
    
    // State tracking
    private static boolean isEmergencyMode = false;
    private static long emergencyModeStartTick = 0;
    private static int emergencyActivationCount = 0;
    private static long lastEmergencyEnd = 0;
    
    // Graduated response levels
    public enum EmergencyLevel {
        NONE,      // Normal operation
        CAUTION,   // TPS dropping, increase monitoring
        WARNING,   // TPS critical, reduce fluid updates
        EMERGENCY  // TPS critical, drastic measures
    }
    private static volatile EmergencyLevel currentLevel = EmergencyLevel.NONE;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        
        // Update thresholds from config
        updateThresholdsFromConfig();
        
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        Level level = server.overworld();
        double currentTPS = PerformanceMonitor.getAverageTPS();
        
        // Graduated response based on TPS
        EmergencyLevel newLevel = calculateEmergencyLevel(currentTPS);
        
        if (newLevel != currentLevel) {
            handleLevelTransition(currentLevel, newLevel, currentTPS);
            currentLevel = newLevel;
        }
        
        // Handle emergency mode activation/deactivation
        if (currentTPS < emergencyTPSThreshold && !isEmergencyMode) {
            activateEmergencyMode(level.getGameTime());
        } else if (isEmergencyMode) {
            long ticksInEmergency = level.getGameTime() - emergencyModeStartTick;
            if (ticksInEmergency > emergencyModeDuration || currentTPS > 10.0) {
                deactivateEmergencyMode(currentTPS);
            }
        }
    }
    
    /**
     * Update thresholds based on optimization level config
     */
    private static void updateThresholdsFromConfig() {
        var level = FlowingFluidsOptimizationConfig.optimizationLevel.get();
        switch (level) {
            case AGGRESSIVE -> {
                emergencyTPSThreshold = 10.0;  // Activate earlier
                emergencyModeDuration = 40;    // Shorter recovery check
                maxUpdatesPerTick = 250;       // More restrictive
            }
            case BALANCED -> {
                emergencyTPSThreshold = 8.0;   // FIXED: Was 2.0, now matches TPS_EMERGENCY_THRESHOLD
                emergencyModeDuration = 60;
                maxUpdatesPerTick = 1000;      // FIXED: Reduced from 2000
            }
            case MINIMAL -> {
                emergencyTPSThreshold = 5.0;   // FIXED: Was 1.0, still conservative
                emergencyModeDuration = 100;   // Longer before recovery
                maxUpdatesPerTick = 2000;      // FIXED: Reduced from 5000
            }
            default -> {
                // Default to BALANCED settings
                emergencyTPSThreshold = 8.0;
                emergencyModeDuration = 60;
                maxUpdatesPerTick = 1000;
            }
        }
    }
    
    /**
     * Calculate emergency level based on TPS
     */
    private static EmergencyLevel calculateEmergencyLevel(double tps) {
        if (tps >= 18.0) return EmergencyLevel.NONE;
        if (tps >= 12.0) return EmergencyLevel.CAUTION;
        if (tps >= 5.0) return EmergencyLevel.WARNING;
        return EmergencyLevel.EMERGENCY;
    }
    
    /**
     * Handle transition between emergency levels
     */
    private static void handleLevelTransition(EmergencyLevel oldLevel, EmergencyLevel newLevel, double tps) {
        if (newLevel.ordinal() > oldLevel.ordinal()) {
            // Escalating
            LOGGER.warn("Emergency level escalated: {} -> {} (TPS: {})", oldLevel, newLevel, String.format("%.2f", tps));
        } else {
            // De-escalating
            LOGGER.info("Emergency level improved: {} -> {} (TPS: {})", oldLevel, newLevel, String.format("%.2f", tps));
        }
    }
    
    /**
     * Activate emergency mode
     */
    private static void activateEmergencyMode(long gameTime) {
        setEmergencyMode(true);
        emergencyModeStartTick = gameTime;
        emergencyActivationCount++;
        LOGGER.error("EMERGENCY MODE ACTIVATED - Fluid updates drastically reduced (activation #{})", emergencyActivationCount);
    }
    
    /**
     * Deactivate emergency mode
     */
    private static void deactivateEmergencyMode(double currentTPS) {
        setEmergencyMode(false);
        lastEmergencyEnd = System.currentTimeMillis();
        LOGGER.info("Emergency mode deactivated - TPS recovered to {} or duration exceeded", String.format("%.2f", currentTPS));
    }

    public static boolean isEmergencyMode() {
        return isEmergencyMode;
    }

    public static void setEmergencyMode(boolean mode) {
        isEmergencyMode = mode;
    }

    public static int getMaxUpdatesPerTick() {
        // Return appropriate limit based on emergency level
        return switch (currentLevel) {
            case EMERGENCY -> Math.min(100, maxUpdatesPerTick / 20);  // Drastic reduction
            case WARNING -> Math.min(500, maxUpdatesPerTick / 4);     // Significant reduction
            case CAUTION -> Math.min(1000, maxUpdatesPerTick / 2);    // Moderate reduction
            case NONE -> maxUpdatesPerTick;                            // Normal operation
        };
    }
    
    /**
     * Get current emergency level
     */
    public static EmergencyLevel getCurrentLevel() {
        return currentLevel;
    }
    
    /**
     * Get number of times emergency mode has been activated
     */
    public static int getEmergencyActivationCount() {
        return emergencyActivationCount;
    }
    
    /**
     * Get time since last emergency ended (ms)
     */
    public static long getTimeSinceLastEmergency() {
        if (lastEmergencyEnd == 0) return Long.MAX_VALUE;
        return System.currentTimeMillis() - lastEmergencyEnd;
    }
    
    /**
     * Get emergency status summary
     */
    public static String getStatusSummary() {
        return String.format("Emergency Status: Level=%s, Active=%s, Activations=%d, MaxUpdates=%d",
            currentLevel, isEmergencyMode, emergencyActivationCount, getMaxUpdatesPerTick());
    }

    public static void setMaxUpdatesPerTick(int updates) {
        maxUpdatesPerTick = updates;
    }
}
