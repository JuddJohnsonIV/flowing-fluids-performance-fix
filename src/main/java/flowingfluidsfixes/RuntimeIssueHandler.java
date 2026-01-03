package flowingfluidsfixes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime Issue Handler for Flowing Fluids Integration
 * Step 13: Validate compatibility with different Minecraft versions
 * Step 14: Address any runtime issues or conflicts during testing
 */
public class RuntimeIssueHandler {
    private static final Logger LOGGER = LogManager.getLogger(RuntimeIssueHandler.class);
    
    // Issue tracking
    private static final List<RuntimeIssue> detectedIssues = new ArrayList<>();
    private static final AtomicInteger totalIssues = new AtomicInteger(0);
    private static final AtomicInteger resolvedIssues = new AtomicInteger(0);
    
    // Issue severity levels
    public enum IssueSeverity {
        INFO,       // Informational, no action needed
        WARNING,    // Potential problem, may need attention
        ERROR,      // Definite problem, needs resolution
        CRITICAL    // Severe problem, may cause crashes
    }
    
    // Issue categories
    public enum IssueCategory {
        API_COMPATIBILITY,      // Flowing Fluids API issues
        VERSION_MISMATCH,       // Minecraft/mod version issues
        PERFORMANCE,            // Performance degradation
        MEMORY,                 // Memory issues
        SYNCHRONIZATION,        // Multiplayer sync issues
        CONFIGURATION,          // Config-related issues
        MOD_CONFLICT,           // Conflicts with other mods
        UNKNOWN                 // Unknown issues
    }
    
    /**
     * Report a runtime issue
     */
    public static void reportIssue(IssueCategory category, IssueSeverity severity, 
                                   String description, String suggestedFix) {
        RuntimeIssue issue = new RuntimeIssue(
            System.currentTimeMillis(),
            category,
            severity,
            description,
            suggestedFix,
            false
        );
        
        synchronized (detectedIssues) {
            detectedIssues.add(issue);
        }
        totalIssues.incrementAndGet();
        
        // Log based on severity
        switch (severity) {
            case CRITICAL -> LOGGER.error("CRITICAL ISSUE: {} - {}", category, description);
            case ERROR -> LOGGER.error("ERROR: {} - {}", category, description);
            case WARNING -> LOGGER.warn("WARNING: {} - {}", category, description);
            case INFO -> LOGGER.info("INFO: {} - {}", category, description);
        }
        
        if (suggestedFix != null && !suggestedFix.isEmpty()) {
            LOGGER.info("  Suggested fix: {}", suggestedFix);
        }
        
        // Auto-resolve certain issues
        attemptAutoResolve(issue);
    }
    
    /**
     * Attempt to automatically resolve an issue
     */
    private static void attemptAutoResolve(RuntimeIssue issue) {
        boolean resolved = false;
        
        switch (issue.category) {
            case API_COMPATIBILITY -> {
                if (!FlowingFluidsAPIIntegration.isFlowingFluidsAvailable()) {
                    VanillaFluidFallback.initialize();
                    LOGGER.info("Auto-resolved: Enabled vanilla fallback mode");
                    resolved = true;
                }
            }
            case PERFORMANCE -> {
                if (issue.severity == IssueSeverity.CRITICAL) {
                    FluidOptimizationRollback.manualRollback("Auto-rollback due to critical performance issue");
                    LOGGER.info("Auto-resolved: Triggered optimization rollback");
                    resolved = true;
                }
            }
            case MEMORY -> {
                FluidMemoryOptimizer.forceCleanup();
                LOGGER.info("Auto-resolved: Forced memory cleanup");
                resolved = true;
            }
            case CONFIGURATION -> {
                // Log config issues but don't auto-resolve
                LOGGER.info("Configuration issue detected - manual review recommended");
            }
            default -> {
                // No auto-resolution for other categories
            }
        }
        
        if (resolved) {
            markIssueResolved(issue);
        }
    }
    
    /**
     * Mark an issue as resolved
     */
    private static void markIssueResolved(RuntimeIssue issue) {
        synchronized (detectedIssues) {
            int index = detectedIssues.indexOf(issue);
            if (index >= 0) {
                RuntimeIssue resolved = new RuntimeIssue(
                    issue.timestamp, issue.category, issue.severity,
                    issue.description, issue.suggestedFix, true
                );
                detectedIssues.set(index, resolved);
                resolvedIssues.incrementAndGet();
            }
        }
    }
    
    /**
     * Check for common runtime issues
     */
    public static void performRuntimeCheck() {
        LOGGER.info("Performing runtime compatibility check...");
        
        // Check API availability
        if (!FlowingFluidsAPIIntegration.isFlowingFluidsAvailable()) {
            reportIssue(IssueCategory.API_COMPATIBILITY, IssueSeverity.INFO,
                "Flowing Fluids mod not detected",
                "Install Flowing Fluids mod or use vanilla fallback mode");
        }
        
        // Check version compatibility
        FlowingFluidsVersionDetector.detectVersion();
        if (FlowingFluidsVersionDetector.isInstalled()) {
            int major = FlowingFluidsVersionDetector.getMajorVersion();
            int minor = FlowingFluidsVersionDetector.getMinorVersion();
            
            if (major == 0 && minor < 5) {
                reportIssue(IssueCategory.VERSION_MISMATCH, IssueSeverity.WARNING,
                    "Flowing Fluids version is below recommended (0.5.0)",
                    "Update to Flowing Fluids 0.6.0 or later for best compatibility");
            }
        }
        
        // Check memory status
        Runtime runtime = Runtime.getRuntime();
        double memoryUsage = (double)(runtime.totalMemory() - runtime.freeMemory()) / runtime.maxMemory();
        if (memoryUsage > 0.9) {
            reportIssue(IssueCategory.MEMORY, IssueSeverity.WARNING,
                "High memory usage detected (" + String.format("%.1f%%", memoryUsage * 100) + ")",
                "Consider increasing Java heap size or reducing render distance");
        }
        
        // Check TPS
        double tps = PerformanceMonitor.getAverageTPS();
        if (tps < 10.0) {
            reportIssue(IssueCategory.PERFORMANCE, IssueSeverity.ERROR,
                "Low TPS detected (" + String.format("%.1f", tps) + ")",
                "Consider using AGGRESSIVE optimization level");
        } else if (tps < 15.0) {
            reportIssue(IssueCategory.PERFORMANCE, IssueSeverity.WARNING,
                "Below optimal TPS (" + String.format("%.1f", tps) + ")",
                "Monitor performance and consider optimization adjustments");
        }
        
        // Check Create mod compatibility
        if (CreateModCompatibility.isCreateModLoaded()) {
            LOGGER.info("Create mod detected - compatibility layer active");
        }
        
        LOGGER.info("Runtime check complete. Issues found: {}", totalIssues.get());
    }
    
    /**
     * Get list of unresolved issues
     */
    public static List<RuntimeIssue> getUnresolvedIssues() {
        List<RuntimeIssue> unresolved = new ArrayList<>();
        synchronized (detectedIssues) {
            for (RuntimeIssue issue : detectedIssues) {
                if (!issue.resolved) {
                    unresolved.add(issue);
                }
            }
        }
        return unresolved;
    }
    
    /**
     * Get issue statistics
     */
    public static String getIssueStats() {
        int critical = 0, errors = 0, warnings = 0, info = 0;
        synchronized (detectedIssues) {
            for (RuntimeIssue issue : detectedIssues) {
                if (!issue.resolved) {
                    switch (issue.severity) {
                        case CRITICAL -> critical++;
                        case ERROR -> errors++;
                        case WARNING -> warnings++;
                        case INFO -> info++;
                    }
                }
            }
        }
        return String.format("Issues: %d total (%d resolved), Active: %d critical, %d errors, %d warnings, %d info",
            totalIssues.get(), resolvedIssues.get(), critical, errors, warnings, info);
    }
    
    /**
     * Clear all issues
     */
    public static void clearIssues() {
        synchronized (detectedIssues) {
            detectedIssues.clear();
        }
        totalIssues.set(0);
        resolvedIssues.set(0);
        LOGGER.info("All runtime issues cleared");
    }
    
    /**
     * Runtime issue record
     */
    public record RuntimeIssue(
        long timestamp,
        IssueCategory category,
        IssueSeverity severity,
        String description,
        String suggestedFix,
        boolean resolved
    ) {}
}
