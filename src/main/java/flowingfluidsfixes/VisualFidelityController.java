package flowingfluidsfixes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Visual Fidelity Controller for Flowing Fluids Optimizations
 * Step 48: Implement visual fidelity toggle for optimizations
 * Allows users to trade visual accuracy for performance gains
 */
public class VisualFidelityController {
    private static final Logger LOGGER = LogManager.getLogger(VisualFidelityController.class);
    
    // Visual fidelity levels
    public enum FidelityLevel {
        MAXIMUM,    // Full visual quality, minimal optimization
        HIGH,       // High quality with some optimization
        BALANCED,   // Balance between quality and performance
        LOW,        // Prioritize performance over visuals
        MINIMAL     // Maximum performance, minimal visual updates
    }
    
    // Current settings
    private static volatile FidelityLevel currentLevel = FidelityLevel.BALANCED;
    private static volatile boolean animationEnabled = true;
    private static volatile boolean particlesEnabled = true;
    private static volatile boolean distantFluidUpdates = true;
    private static volatile int visualUpdateDistance = 64;
    
    /**
     * Set visual fidelity level
     */
    public static void setFidelityLevel(FidelityLevel level) {
        currentLevel = level;
        applyFidelitySettings(level);
        LOGGER.info("Visual fidelity set to: {}", level);
    }
    
    /**
     * Apply settings based on fidelity level
     */
    private static void applyFidelitySettings(FidelityLevel level) {
        switch (level) {
            case MAXIMUM -> {
                animationEnabled = true;
                particlesEnabled = true;
                distantFluidUpdates = true;
                visualUpdateDistance = 128;
            }
            case HIGH -> {
                animationEnabled = true;
                particlesEnabled = true;
                distantFluidUpdates = true;
                visualUpdateDistance = 96;
            }
            case BALANCED -> {
                animationEnabled = true;
                particlesEnabled = true;
                distantFluidUpdates = true;
                visualUpdateDistance = 64;
            }
            case LOW -> {
                animationEnabled = true;
                particlesEnabled = false;
                distantFluidUpdates = false;
                visualUpdateDistance = 32;
            }
            case MINIMAL -> {
                animationEnabled = false;
                particlesEnabled = false;
                distantFluidUpdates = false;
                visualUpdateDistance = 16;
            }
        }
    }
    
    /**
     * Get current fidelity level
     */
    public static FidelityLevel getCurrentLevel() {
        return currentLevel;
    }
    
    /**
     * Check if animations are enabled
     */
    public static boolean isAnimationEnabled() {
        return animationEnabled;
    }
    
    /**
     * Check if particles are enabled
     */
    public static boolean isParticlesEnabled() {
        return particlesEnabled;
    }
    
    /**
     * Check if distant fluid updates are enabled
     */
    public static boolean isDistantFluidUpdatesEnabled() {
        return distantFluidUpdates;
    }
    
    /**
     * Get visual update distance
     */
    public static int getVisualUpdateDistance() {
        return visualUpdateDistance;
    }
    
    /**
     * Check if position is within visual update range
     */
    public static boolean isWithinVisualRange(double distanceSq) {
        return distanceSq <= visualUpdateDistance * visualUpdateDistance;
    }
    
    /**
     * Get update frequency multiplier based on fidelity
     * Lower = more frequent updates (better visuals)
     */
    public static float getUpdateFrequencyMultiplier() {
        return switch (currentLevel) {
            case MAXIMUM -> 1.0f;
            case HIGH -> 1.2f;
            case BALANCED -> 1.5f;
            case LOW -> 2.0f;
            case MINIMAL -> 4.0f;
        };
    }
    
    /**
     * Get status summary
     */
    public static String getStatusSummary() {
        return String.format("Visual Fidelity: %s (anim=%s, particles=%s, distant=%s, range=%d)",
            currentLevel, animationEnabled, particlesEnabled, distantFluidUpdates, visualUpdateDistance);
    }
}
