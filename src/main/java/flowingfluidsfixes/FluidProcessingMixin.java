package flowingfluidsfixes;

import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept core fluid processing methods and apply caching optimizations
 * This provides the method interception needed for effective fluid state caching
 */
@Mixin(Level.class)
public class FluidProcessingMixin {
    
    /**
     * Intercept getBlockState() calls to use cached values when possible
     * This is one of the most expensive operations in fluid processing
     */
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    public void onGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        // Only apply caching on server side and when optimization is active
        if (!((Object)this instanceof ServerLevel) || !FlowingFluidsFixesMinimal.isSpatialOptimizationActive()) {
            return;
        }
        
        ServerLevel level = (ServerLevel)(Object)this;
        
        // Use cached BlockState to avoid expensive LevelChunk/PalettedContainer operations
        BlockState cachedState = FlowingFluidsFixesMinimal.getCachedBlockState(level, pos);
        cir.setReturnValue(cachedState);
    }
    
    /**
     * Intercept getFluidState() calls to use cached values when possible
     * This prevents redundant fluid state calculations
     */
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    public void onGetFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        // Only apply caching on server side and when optimization is active
        if (!((Object)this instanceof ServerLevel) || !FlowingFluidsFixesMinimal.isSpatialOptimizationActive()) {
            return;
        }
        
        ServerLevel level = (ServerLevel)(Object)this;
        
        // Use cached FluidState to avoid expensive calculations
        FluidState cachedState = FlowingFluidsFixesMinimal.getCachedFluidState(level, pos);
        cir.setReturnValue(cachedState);
    }
    
    /**
     * Intercept setBlock() calls to invalidate cache when blocks change
     * This ensures cache consistency when the world changes
     */
    @Inject(method = "setBlock", at = @At("HEAD"))
    public void onSetBlock(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        // Only invalidate cache on server side and when optimization is active
        if (!((Object)this instanceof ServerLevel) || !FlowingFluidsFixesMinimal.isSpatialOptimizationActive()) {
            return;
        }
        
        // Invalidate cached state for this position to maintain consistency
        FlowingFluidsFixesMinimal.invalidateFluidCache(pos);
        
        // Also invalidate nearby positions since block changes can affect neighbors
        FlowingFluidsFixesMinimal.invalidateFluidCacheArea(pos, 2);
    }
}
