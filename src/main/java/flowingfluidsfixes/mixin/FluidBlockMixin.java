package flowingfluidsfixes.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin for LiquidBlock to monitor fluid events for metrics only.
 * 
 * CRITICAL FIX: We NO LONGER cancel ticks - this was breaking Flowing Fluids.
 * Performance optimization should be done via Flowing Fluids' native config settings.
 */
@Mixin(net.minecraft.world.level.block.LiquidBlock.class)
public class FluidBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = false)
    private void onTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        // Only record metrics - DO NOT cancel or interfere with Flowing Fluids
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty()) {
            flowingfluidsfixes.PerformanceMonitor.recordFluidUpdate();
        }
        // Let Flowing Fluids handle the actual fluid logic
    }
}
