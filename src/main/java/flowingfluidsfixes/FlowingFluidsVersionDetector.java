package flowingfluidsfixes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Version Detection System for Flowing Fluids Mod
 * Step 20: Develop a system to detect and adapt to Flowing Fluids mod versions
 * Ensures backward and forward compatibility
 */
public class FlowingFluidsVersionDetector {
    private static final Logger LOGGER = LogManager.getLogger(FlowingFluidsVersionDetector.class);
    
    // Version information
    private static volatile String detectedVersion = "unknown";
    private static volatile int majorVersion = 0;
    private static volatile int minorVersion = 0;
    private static volatile int patchVersion = 0;
    private static volatile boolean versionDetected = false;
    
    // API compatibility flags
    private static volatile boolean hasFiniteFluidAPI = false;
    private static volatile boolean hasPressureSystemAPI = false;
    private static volatile boolean hasBiomeIntegrationAPI = false;
    private static volatile boolean hasCreateCompatAPI = false;
    
    // Known version features
    private static final String MIN_SUPPORTED_VERSION = "0.5.0";
    private static final String RECOMMENDED_VERSION = "0.6.0";
    
    /**
     * Detect Flowing Fluids version and capabilities
     */
    public static void detectVersion() {
        if (versionDetected) return;
        
        try {
            // Try to get version from API class
            Class<?> apiClass = Class.forName("traben.flowing_fluids.api.FlowingFluidsAPI");
            
            // Try VERSION field first
            try {
                Field versionField = apiClass.getField("VERSION");
                Object versionValue = versionField.get(null);
                if (versionValue != null) {
                    detectedVersion = versionValue.toString();
                    parseVersion(detectedVersion);
                }
            } catch (NoSuchFieldException e) {
                LOGGER.debug("VERSION field not found, trying alternative detection");
            }
            
            // If version still unknown, try to detect from mod info
            if ("unknown".equals(detectedVersion)) {
                detectVersionFromModInfo();
            }
            
            // Detect available API features
            detectAPICapabilities(apiClass);
            
            versionDetected = true;
            
            LOGGER.info("Flowing Fluids version detected: {} (major={}, minor={}, patch={})",
                       detectedVersion, majorVersion, minorVersion, patchVersion);
            LOGGER.info("API Capabilities: finite={}, pressure={}, biome={}, create={}",
                       hasFiniteFluidAPI, hasPressureSystemAPI, hasBiomeIntegrationAPI, hasCreateCompatAPI);
            
            // Check version compatibility
            checkVersionCompatibility();
            
        } catch (ClassNotFoundException e) {
            LOGGER.info("Flowing Fluids mod not detected - using fallback mode");
            detectedVersion = "not_installed";
            versionDetected = true;
        } catch (Exception e) {
            LOGGER.warn("Error detecting Flowing Fluids version: {}", e.getMessage());
            versionDetected = true;
        }
    }
    
    /**
     * Parse version string into components
     */
    private static void parseVersion(String version) {
        try {
            // Remove any prefix like "1.20.1-"
            String cleanVersion = version;
            if (version.contains("-")) {
                String[] parts = version.split("-");
                if (parts.length > 1) {
                    cleanVersion = parts[parts.length - 1];
                }
            }
            
            // Parse major.minor.patch
            String[] versionParts = cleanVersion.split("\\.");
            if (versionParts.length >= 1) {
                majorVersion = Integer.parseInt(versionParts[0]);
            }
            if (versionParts.length >= 2) {
                minorVersion = Integer.parseInt(versionParts[1]);
            }
            if (versionParts.length >= 3) {
                patchVersion = Integer.parseInt(versionParts[2].replaceAll("[^0-9]", ""));
            }
        } catch (NumberFormatException e) {
            LOGGER.debug("Could not parse version components from: {}", version);
        }
    }
    
    /**
     * Try to detect version from mod info
     */
    private static void detectVersionFromModInfo() {
        try {
            // Try to get version from mod container
            var modList = net.minecraftforge.fml.ModList.get();
            if (modList != null) {
                var modContainer = modList.getModContainerById("flowing_fluids");
                if (modContainer.isPresent()) {
                    detectedVersion = modContainer.get().getModInfo().getVersion().toString();
                    parseVersion(detectedVersion);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not detect version from mod info: {}", e.getMessage());
        }
    }
    
    /**
     * Detect available API capabilities
     */
    private static void detectAPICapabilities(Class<?> apiClass) {
        // Check for finite fluid API
        hasFiniteFluidAPI = hasMethod(apiClass, "doesModifyThisFluid");
        
        // Check for pressure system API
        hasPressureSystemAPI = hasMethod(apiClass, "isModCurrentlyMovingFluids");
        
        // Check for biome integration API
        hasBiomeIntegrationAPI = hasMethod(apiClass, "doesBiomeInfiniteWaterRefill");
        
        // Check for Create compatibility API (v0.6+)
        hasCreateCompatAPI = majorVersion >= 0 && minorVersion >= 6;
    }
    
    /**
     * Check if a method exists in the class
     */
    private static boolean hasMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check version compatibility and log warnings
     */
    private static void checkVersionCompatibility() {
        if ("not_installed".equals(detectedVersion)) {
            return;
        }
        
        // Check minimum version
        if (isVersionLessThan(majorVersion, minorVersion, patchVersion, 0, 5, 0)) {
            LOGGER.warn("Flowing Fluids version {} is below minimum supported version {}",
                       detectedVersion, MIN_SUPPORTED_VERSION);
            LOGGER.warn("Some optimization features may not work correctly");
        }
        
        // Check recommended version
        if (isVersionLessThan(majorVersion, minorVersion, patchVersion, 0, 6, 0)) {
            LOGGER.info("Consider upgrading to Flowing Fluids {} for best compatibility",
                       RECOMMENDED_VERSION);
        }
    }
    
    /**
     * Compare version numbers
     */
    private static boolean isVersionLessThan(int maj, int min, int patch, 
                                             int targetMaj, int targetMin, int targetPatch) {
        if (maj < targetMaj) return true;
        if (maj > targetMaj) return false;
        if (min < targetMin) return true;
        if (min > targetMin) return false;
        return patch < targetPatch;
    }
    
    // Getters
    
    public static String getDetectedVersion() {
        return detectedVersion;
    }
    
    public static int getMajorVersion() {
        return majorVersion;
    }
    
    public static int getMinorVersion() {
        return minorVersion;
    }
    
    public static int getPatchVersion() {
        return patchVersion;
    }
    
    public static boolean isVersionDetected() {
        return versionDetected;
    }
    
    public static boolean hasFiniteFluidAPI() {
        return hasFiniteFluidAPI;
    }
    
    public static boolean hasPressureSystemAPI() {
        return hasPressureSystemAPI;
    }
    
    public static boolean hasBiomeIntegrationAPI() {
        return hasBiomeIntegrationAPI;
    }
    
    public static boolean hasCreateCompatAPI() {
        return hasCreateCompatAPI;
    }
    
    public static boolean isInstalled() {
        return !"not_installed".equals(detectedVersion) && !"unknown".equals(detectedVersion);
    }
    
    /**
     * Get version summary
     */
    public static String getVersionSummary() {
        return String.format("Flowing Fluids: %s (APIs: finite=%s, pressure=%s, biome=%s, create=%s)",
            detectedVersion, hasFiniteFluidAPI, hasPressureSystemAPI, 
            hasBiomeIntegrationAPI, hasCreateCompatAPI);
    }
}
