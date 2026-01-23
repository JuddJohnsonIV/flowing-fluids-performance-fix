package flowingfluidsfixes.mixin;

import flowingfluidsfixes.FlowingFluidsFixesMinimal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hook into Fluid.randomTick() for global fluid random tick throttling
 * This is the critical missing piece for ocean drain lag prevention
 */
@Mixin(Fluid.class)
public class FluidRandomTickMixin {
    
    /**
     * Hook into fluid random tick processing to apply global throttling
     * This prevents ocean drains and large fluid cascades from destroying server performance
     */
    @Inject(
        method = "randomTick",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRandomTick(Level level, BlockPos pos, CallbackInfo ci) {
        // Check if this fluid random tick should be throttled based on server MSPT
        if (FlowingFluidsFixesMinimal.shouldThrottleFluidRandomTick(pos)) {
            ci.cancel(); // Skip this fluid random tick entirely
        }
        // If not throttled, allow normal fluid random tick processing
    }
}
