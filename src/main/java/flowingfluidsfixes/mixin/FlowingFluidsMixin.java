package flowingfluidsfixes.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that hooks into vanilla FlowingFluid.tick() for MONITORING only.
 * 
 * CRITICAL FIX: We NO LONGER cancel ticks - this was breaking Flowing Fluids.
 * Instead, we only monitor and record metrics. Flowing Fluids handles all fluid logic.
 * 
 * Performance optimization is now done via config recommendations, not tick interception.
 */
@Mixin(net.minecraft.world.level.material.FlowingFluid.class)
public abstract class FlowingFluidsMixin {
    
    /**
     * Monitor fluid ticks for performance metrics only.
     * 
     * FIXED: No longer cancels ticks - this was preventing Flowing Fluids from working.
     * We only record that a tick happened for our performance monitoring.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = false, remap = false)
    private void onTick(Level level, BlockPos pos, BlockState state, FluidState fluidState, CallbackInfo ci) {
        // Only record metrics - DO NOT cancel or interfere with Flowing Fluids
        if (!fluidState.isEmpty() && !level.isClientSide()) {
            flowingfluidsfixes.PerformanceMonitor.recordFluidUpdate();
        }
        // Let Flowing Fluids handle the actual fluid logic
    }
}
