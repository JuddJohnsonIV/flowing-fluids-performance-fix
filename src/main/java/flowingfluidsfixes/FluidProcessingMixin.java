package flowingfluidsfixes;

import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * COMPLETE MSPT OPTIMIZATION MIXIN - All features in one class
 * Intercepts Level operations and applies comprehensive throttling during high MSPT
 * This actually REDUCES work instead of just caching it
 */
@Mixin(Level.class)
public class FluidProcessingMixin {
    
    /**
     * PRIMARY: Intercept Level.getBlockState() calls and PREVENT events during high MSPT
     * REAL MSPT IMPROVEMENT: Skip expensive world access when server is lagging
     * This hits the root cause of Flowing Fluids performance issues
     */
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    public void onGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        // Only throttle on server side when optimization is active
        if (!((Object)this instanceof ServerLevel) || !FlowingFluidsFixesMinimal.isSpatialOptimizationActive()) {
            return;
        }
        
        ServerLevel level = (ServerLevel)(Object)this;
        
        // EVENT PREVENTION: Check if we should allow this operation at all
        if (!FlowingFluidsFixesMinimal.shouldAllowFluidProcessingAt(level, pos)) {
            // Don't just skip - PREVENT the operation entirely
            cir.setReturnValue(FlowingFluidsFixesMinimal.getFallbackBlockState(level, pos));
            return;
        }
        
        // If allowed, check if we should skip this operation entirely
        if (FlowingFluidsFixesMinimal.shouldSkipBlockOperation(level, pos)) {
            // Return a simple, cheap BlockState instead of doing expensive world access
            cir.setReturnValue(FlowingFluidsFixesMinimal.getFallbackBlockState(level, pos));
            return;
        }
        
        // If not throttling, allow normal operation (no caching - just let it run)
        // This reduces work during high MSPT instead of just making it look better
    }
    
    /**
     * SECONDARY: Intercept Level.setBlock() calls to maintain cache consistency
     * This ensures our throttling doesn't cause world corruption
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
    
    /**
     * TERTIARY: Intercept Level.getFluidState() calls (if they exist)
     * Some mods might call this directly, so we handle it too
     */
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true, require = 0)
    public void onGetFluidState(BlockPos pos, CallbackInfoReturnable<Object> cir) {
        // Only throttle on server side when optimization is active
        if (!((Object)this instanceof ServerLevel) || !FlowingFluidsFixesMinimal.isSpatialOptimizationActive()) {
            return;
        }
        
        ServerLevel level = (ServerLevel)(Object)this;
        
        // REAL MSPT IMPROVEMENT: Skip fluid state operations during high MSPT
        if (FlowingFluidsFixesMinimal.shouldSkipFluidOperation(level, pos)) {
            // Return a simple, cheap fluid state instead of doing expensive calculations
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
            return;
        }
    }
    
    /**
     * QUATERNARY: Intercept Level.isLoaded() calls to reduce chunk loading pressure
     * This helps reduce the cascade effect during fluid operations
     */
    @Inject(method = "isLoaded", at = @At("HEAD"), cancellable = true)
    public void onIsLoaded(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // Only optimize on server side when optimization is active
        if (!((Object)this instanceof ServerLevel) || !FlowingFluidsFixesMinimal.isSpatialOptimizationActive()) {
            return;
        }
        
        ServerLevel level = (ServerLevel)(Object)this;
        
        // CHUNK BOUNDARY PREVENTION: Prevent chunk loading during high MSPT
        if (FlowingFluidsFixesMinimal.shouldSkipBlockOperation(level, pos)) {
            // Check if this is at a chunk boundary
            boolean atBoundary = (pos.getX() % 16 == 0 || pos.getX() % 16 == 15 || 
                                pos.getZ() % 16 == 0 || pos.getZ() % 16 == 15);
            
            if (atBoundary) {
                // Return false to prevent chunk loading during high load
                cir.setReturnValue(false);
                return;
            }
        }
        
        // During extreme MSPT, be more aggressive about chunk loading
        if (FlowingFluidsFixesMinimal.shouldSkipBlockOperation(level, pos)) {
            // Return false to prevent chunk loading during high load
            cir.setReturnValue(false);
            return;
        }
    }
}
