package flowingfluidsfixes.mixins;

import flowingfluidsfixes.FlowingFluidsCalculationOptimizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test for mixin system functionality
 */
public class BasicMixinTest {
    
    @Test
    void testFlowingFluidsCalculationOptimizerBasicFunctionality() {
        // Test optimizer exists and basic methods work
        assertNotNull(FlowingFluidsCalculationOptimizer.class);
        
        // Test stats method works
        String stats = FlowingFluidsCalculationOptimizer.getOptimizationStats();
        assertNotNull(stats);
        assertTrue(stats.contains("FluidOpt")); // Updated to match new shorter format
    }
    
    @Test
    void testCacheManagement() {
        // Test cache clearing
        FlowingFluidsCalculationOptimizer.clearCache();
        
        // Test tick counter reset
        FlowingFluidsCalculationOptimizer.resetTickCounter();
        
        // Verify operations complete without errors
        assertDoesNotThrow(() -> FlowingFluidsCalculationOptimizer.getOptimizationStats());
    }
}
