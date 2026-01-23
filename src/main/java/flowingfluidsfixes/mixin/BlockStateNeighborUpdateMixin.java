package flowingfluidsfixes.mixin;

import flowingfluidsfixes.FlowingFluidsFixesMinimal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to hook into BlockState.updateNeighbourShapes for neighbor update throttling
 * This prevents cascading neighbor update chains during high MSPT that can destroy server performance
 */
@Mixin(BlockState.class)
public class BlockStateNeighborUpdateMixin {
    
    /**
     * Hook into neighbor update processing to apply global throttling during high MSPT
     * This is critical for preventing fluid cascades from triggering massive neighbor update chains
     */
    @Inject(
        method = "updateNeighbourShapes",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onUpdateNeighbourShapes(Level level, BlockPos pos, int flags, CallbackInfo ci) {
        // Check if neighbor updates should be blocked based on server MSPT
        if (FlowingFluidsFixesMinimal.shouldBlockNeighborUpdates()) {
            ci.cancel(); // Skip this neighbor update entirely
        }
        // If not blocked, allow normal neighbor update processing
    }
}
