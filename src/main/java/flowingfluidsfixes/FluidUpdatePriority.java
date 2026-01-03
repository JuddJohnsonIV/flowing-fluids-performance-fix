package flowingfluidsfixes;

import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Defines priority levels for fluid updates.
 * Higher priority values are processed first.
 */
@SuppressWarnings("all")
public enum FluidUpdatePriority {
    // Higher values = higher priority
    SOURCE_BLOCK(100),       // Source blocks have highest priority
    FLOATING_LAYER(90),      // Floating water layers
    PLAYER_NEARBY(75),       // Fluids near players
    WATER(50),               // Regular water updates
    LAVA(40),                // Lava updates (less critical than water)
    NORMAL(0),               // Default priority
    LOW(-10);                // Low priority updates

    private final int priorityValue;

    FluidUpdatePriority(int priorityValue) {
        this.priorityValue = priorityValue;
    }

    public int getValue() {
        return priorityValue;
    }

    /**
     * Determine the appropriate priority for a fluid state
     */
    public static FluidUpdatePriority fromFluidState(FluidState state, boolean isNearPlayer) {
        if (state.isSource()) return SOURCE_BLOCK;
        if (isNearPlayer) return PLAYER_NEARBY;
        if (state.getType().isSame(Fluids.WATER)) return WATER;
        if (state.getType().isSame(Fluids.LAVA)) return LAVA;
        return NORMAL;
    }
}
