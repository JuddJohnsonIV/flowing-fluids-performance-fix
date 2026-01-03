package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Debug Logging System for Flowing Fluids Integration
 * Step 10: Create debug logging for Flowing Fluids interactions
 * Captures detailed information on fluid updates and optimizations for troubleshooting
 */
public class FlowingFluidsDebugLogger {
    private static final Logger LOGGER = LogManager.getLogger(FlowingFluidsDebugLogger.class);
    
    // Debug logging state
    private static volatile boolean debugEnabled = false;
    private static volatile boolean fileLoggingEnabled = false;
    private static PrintWriter fileWriter = null;
    
    // Statistics tracking
    private static final AtomicLong totalUpdatesLogged = new AtomicLong(0);
    private static final AtomicLong optimizedUpdatesLogged = new AtomicLong(0);
    private static final AtomicLong skippedUpdatesLogged = new AtomicLong(0);
    private static final AtomicLong deferredUpdatesLogged = new AtomicLong(0);
    
    // Recent update tracking for pattern analysis
    private static final Map<BlockPos, UpdateRecord> recentUpdates = new ConcurrentHashMap<>();
    private static final int MAX_RECENT_UPDATES = 1000;
    
    // Timestamp formatter
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    /**
     * Initialize the debug logger
     */
    public static void initialize() {
        debugEnabled = FlowingFluidsOptimizationConfig.enableDetailedLogging.get();
        
        if (FlowingFluidsOptimizationConfig.enableFlowingFluidsDebug.get()) {
            enableFileLogging();
        }
        
        LOGGER.info("Flowing Fluids Debug Logger initialized (enabled: {}, file logging: {})", 
                   debugEnabled, fileLoggingEnabled);
    }
    
    /**
     * Enable file-based debug logging
     */
    public static void enableFileLogging() {
        try {
            File logDir = new File("logs/flowingfluids");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            String filename = "flowingfluids_debug_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".log";
            File logFile = new File(logDir, filename);
            
            fileWriter = new PrintWriter(new FileWriter(logFile, true));
            fileLoggingEnabled = true;
            
            writeToFile("=== Flowing Fluids Debug Log Started ===");
            writeToFile("Timestamp: " + LocalDateTime.now());
            writeToFile("Optimization Level: " + FlowingFluidsOptimizationConfig.optimizationLevel.get());
            writeToFile("=========================================\n");
            
            LOGGER.info("File logging enabled: {}", logFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Failed to enable file logging: {}", e.getMessage());
            fileLoggingEnabled = false;
        }
    }
    
    /**
     * Disable file-based debug logging
     */
    public static void disableFileLogging() {
        if (fileWriter != null) {
            writeToFile("\n=== Flowing Fluids Debug Log Ended ===");
            writeToFile("Total Updates Logged: " + totalUpdatesLogged.get());
            fileWriter.close();
            fileWriter = null;
        }
        fileLoggingEnabled = false;
        LOGGER.info("File logging disabled");
    }
    
    /**
     * Log a fluid update event
     */
    public static void logFluidUpdate(ServerLevel level, BlockPos pos, FluidState state, 
                                      UpdateType type, int delay, String reason) {
        if (!debugEnabled) return;
        
        totalUpdatesLogged.incrementAndGet();
        
        switch (type) {
            case OPTIMIZED -> optimizedUpdatesLogged.incrementAndGet();
            case SKIPPED -> skippedUpdatesLogged.incrementAndGet();
            case DEFERRED -> deferredUpdatesLogged.incrementAndGet();
            case NORMAL, CRITICAL -> {} // No specific counter for these
        }
        
        // Build log message
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String message = String.format("[%s] %s at %s - Fluid: %s (amount: %d), Delay: %d, Reason: %s",
            timestamp, type, pos, state.getType().toString(), state.getAmount(), delay, reason);
        
        // Log to console if detailed logging is enabled
        if (FlowingFluidsOptimizationConfig.enableDetailedLogging.get()) {
            LOGGER.debug(message);
        }
        
        // Log to file if enabled
        if (fileLoggingEnabled) {
            writeToFile(message);
        }
        
        // Track recent updates
        trackUpdate(pos, type, delay);
    }
    
    /**
     * Log optimization decision
     */
    public static void logOptimizationDecision(BlockPos pos, String decision, 
                                               double distanceToPlayer, double tps) {
        if (!debugEnabled) return;
        
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String message = String.format("[%s] DECISION at %s: %s (distance: %.1f, TPS: %.2f)",
            timestamp, pos, decision, distanceToPlayer, tps);
        
        if (FlowingFluidsOptimizationConfig.enableDetailedLogging.get()) {
            LOGGER.debug(message);
        }
        
        if (fileLoggingEnabled) {
            writeToFile(message);
        }
    }
    
    /**
     * Log emergency mode event
     */
    public static void logEmergencyEvent(String event, double tps, 
                                         EmergencyPerformanceMode.EmergencyLevel level) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String message = String.format("[%s] EMERGENCY: %s (TPS: %.2f, Level: %s)",
            timestamp, event, tps, level);
        
        LOGGER.warn(message);
        
        if (fileLoggingEnabled) {
            writeToFile("!!! " + message);
        }
    }
    
    /**
     * Log API interaction
     */
    public static void logAPIInteraction(String method, boolean success, String details) {
        if (!debugEnabled) return;
        
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String message = String.format("[%s] API: %s - %s - %s",
            timestamp, method, success ? "SUCCESS" : "FAILED", details);
        
        if (FlowingFluidsOptimizationConfig.enableDetailedLogging.get()) {
            LOGGER.debug(message);
        }
        
        if (fileLoggingEnabled) {
            writeToFile(message);
        }
    }
    
    /**
     * Log performance snapshot
     */
    public static void logPerformanceSnapshot() {
        if (!debugEnabled) return;
        
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        StringBuilder snapshot = new StringBuilder();
        snapshot.append(String.format("\n[%s] === PERFORMANCE SNAPSHOT ===\n", timestamp));
        snapshot.append(String.format("TPS: %.2f\n", PerformanceMonitor.getAverageTPS()));
        snapshot.append(String.format("Emergency Level: %s\n", EmergencyPerformanceMode.getCurrentLevel()));
        snapshot.append(String.format("Updates Logged: %d (optimized: %d, skipped: %d, deferred: %d)\n",
            totalUpdatesLogged.get(), optimizedUpdatesLogged.get(), 
            skippedUpdatesLogged.get(), deferredUpdatesLogged.get()));
        snapshot.append(String.format("Deferred Queue: %d\n", FluidTickScheduler.getDeferredQueueSize()));
        snapshot.append(String.format("Adaptive Limit: %d\n", FluidTickScheduler.getAdaptiveMaxUpdatesPerTick()));
        snapshot.append(String.format("Multiplayer Sync Queue: %d\n", MultiplayerFluidSync.getSyncQueueSize()));
        snapshot.append("==============================\n");
        
        if (FlowingFluidsOptimizationConfig.enableDetailedLogging.get()) {
            LOGGER.info(snapshot.toString());
        }
        
        if (fileLoggingEnabled) {
            writeToFile(snapshot.toString());
        }
    }
    
    /**
     * Track update for pattern analysis
     */
    private static void trackUpdate(BlockPos pos, UpdateType type, int delay) {
        if (recentUpdates.size() >= MAX_RECENT_UPDATES) {
            // Clear oldest entries
            recentUpdates.clear();
        }
        recentUpdates.put(pos.immutable(), new UpdateRecord(type, delay, System.currentTimeMillis()));
    }
    
    /**
     * Write to file
     */
    private static void writeToFile(String message) {
        if (fileWriter != null) {
            fileWriter.println(message);
            fileWriter.flush();
        }
    }
    
    /**
     * Enable/disable debug logging
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        LOGGER.info("Debug logging {}", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Check if debug is enabled
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    /**
     * Get statistics summary
     */
    public static String getStatsSummary() {
        return String.format("Debug Stats: %d total (%d optimized, %d skipped, %d deferred)",
            totalUpdatesLogged.get(), optimizedUpdatesLogged.get(),
            skippedUpdatesLogged.get(), deferredUpdatesLogged.get());
    }
    
    /**
     * Reset statistics
     */
    public static void resetStats() {
        totalUpdatesLogged.set(0);
        optimizedUpdatesLogged.set(0);
        skippedUpdatesLogged.set(0);
        deferredUpdatesLogged.set(0);
        recentUpdates.clear();
        LOGGER.info("Debug statistics reset");
    }
    
    /**
     * Cleanup on shutdown
     */
    public static void shutdown() {
        disableFileLogging();
        recentUpdates.clear();
        LOGGER.info("Flowing Fluids Debug Logger shutdown");
    }
    
    /**
     * Update type enum
     */
    public enum UpdateType {
        NORMAL,     // Normal update processing
        OPTIMIZED,  // Update was optimized
        SKIPPED,    // Update was skipped
        DEFERRED,   // Update was deferred
        CRITICAL    // Critical update (immediate processing)
    }
    
    /**
     * Update record for tracking
     */
    private record UpdateRecord(UpdateType type, int delay, long timestamp) {}
}
