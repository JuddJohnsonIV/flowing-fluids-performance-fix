package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * Represents a fluid update that needs to be processed.
 * Implements Comparable to support priority-based processing.
 */
@SuppressWarnings("all")
public class FluidUpdate implements Comparable<FluidUpdate> {
    public final Level level;
    public final BlockPos pos;
    public final FluidState state;
    public final BlockState blockState;
    public final int priority; // Added for priority-based processing
    public final long timestamp;

    public FluidUpdate(Level level, BlockPos pos, FluidState state, BlockState blockState, int priority) {
        this.level = level;
        this.pos = pos.immutable();
        this.state = state;
        this.blockState = blockState;
        this.priority = priority;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public int compareTo(FluidUpdate other) {
        // Higher priority first
        int priorityCompare = Integer.compare(other.priority, this.priority);
        if (priorityCompare != 0) return priorityCompare;
        
        // Older updates first
        return Long.compare(this.timestamp, other.timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FluidUpdate)) return false;
        FluidUpdate that = (FluidUpdate) o;
        return pos.equals(that.pos) && state.getType() == that.state.getType();
    }

    @Override
    public int hashCode() {
        return 31 * pos.hashCode() + state.getType().hashCode();
    }
}
