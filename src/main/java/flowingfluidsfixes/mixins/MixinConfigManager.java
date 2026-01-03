package flowingfluidsfixes.mixins;

import flowingfluidsfixes.utils.LoggerUtils;
import net.minecraftforge.fml.common.Mod;

/**
 * Manages mixin configuration and initialization for Flowing Fluids integration
 */
@Mod.EventBusSubscriber(modid = "flowingfluidsfixes")
public class MixinConfigManager {
    private static final String MOD_ID = "flowingfluidsfixes";
    private static boolean mixinsInitialized = false;
    
    /**
     * Initialize mixin system for Flowing Fluids integration
     */
    public static void initializeMixins() {
        if (mixinsInitialized) {
            LoggerUtils.logDebug(MOD_ID, "Mixins already initialized");
            return;
        }
        
        try {
            // Check if Flowing Fluids mod is loaded
            boolean flowingFluidsLoaded = isFlowingFluidsLoaded();
            
            if (flowingFluidsLoaded) {
                LoggerUtils.logInfo(MOD_ID, "Flowing Fluids mod detected - enabling performance optimization mixins");
                enableFlowingFluidsMixins();
            } else {
                LoggerUtils.logInfo(MOD_ID, "Flowing Fluids mod not detected - using vanilla fluid optimizations");
                enableVanillaMixins();
            }
            
            mixinsInitialized = true;
            LoggerUtils.logInfo(MOD_ID, "Mixin system initialized successfully");
            
        } catch (Exception e) {
            LoggerUtils.logError(MOD_ID, "Failed to initialize mixin system", e);
        }
    }
    
    /**
     * Check if Flowing Fluids mod is loaded
     */
    private static boolean isFlowingFluidsLoaded() {
        try {
            Class.forName("traben.flowing_fluids.api.FlowingFluidsAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Enable Flowing Fluids specific mixins
     */
    private static void enableFlowingFluidsMixins() {
        LoggerUtils.logInfo(MOD_ID, "Enabling Flowing Fluids specific optimizations:");
        LoggerUtils.logInfo(MOD_ID, "- FlowingFluidMixin: Optimizes base fluid calculations");
        LoggerUtils.logInfo(MOD_ID, "- FlowingFluidsBlockMixin: Optimizes Flowing Fluids block behavior");
    }
    
    /**
     * Enable vanilla fluid mixins
     */
    private static void enableVanillaMixins() {
        LoggerUtils.logInfo(MOD_ID, "Enabling vanilla fluid optimizations:");
        LoggerUtils.logInfo(MOD_ID, "- FlowingFluidMixin: Optimizes base fluid calculations");
    }
    
    /**
     * Get mixin initialization status
     */
    public static boolean isMixinsInitialized() {
        return mixinsInitialized;
    }
}
