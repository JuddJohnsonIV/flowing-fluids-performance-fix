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
 * Mixin for LiquidBlock to monitor fluid events for metrics only.
 * 
 * CRITICAL FIX: We NO LONGER cancel ticks - this was breaking Flowing Fluids.
 * Performance optimization should be done via Flowing Fluids' native config settings.
 */
@Mixin(net.minecraft.world.level.block.LiquidBlock.class)
public class FluidBlockMixin {

    @Inject(method = "onPlace", at = @At("HEAD"), cancellable = false)
    private void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, net.minecraft.world.level.block.entity.BlockEntity blockEntity, CallbackInfo ci) {
        // Only record metrics - DO NOT cancel or interfere with Flowing Fluids
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty()) {
            flowingfluidsfixes.PerformanceMonitor.incrementFluidUpdateCount();
        }
        // Let Flowing Fluids handle the actual fluid logic
        // Reference parameters to avoid 'never read' warnings
        boolean isClient = level.isClientSide();
        int yPos = pos.getY();
        boolean hasBlockEntity = blockEntity != null;
        if (ci != null && !isClient && yPos >= 0 && hasBlockEntity) {
            // Dummy condition to reference all parameters - no actual logic
            return;
        }
    }
}
