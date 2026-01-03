package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluid;

@SuppressWarnings("all")
public class DeferredFluidTick {
    private final Fluid fluid;
    private final BlockPos pos;
    private final long scheduledTime;
    private final int priority;

    public DeferredFluidTick(Fluid fluid, BlockPos pos, long scheduledTime, int priority) {
        this.fluid = fluid;
        this.pos = pos;
        this.scheduledTime = scheduledTime;
        this.priority = priority;
    }

    public Fluid getFluid() {
        return fluid;
    }

    public BlockPos getPos() {
        return pos;
    }

    public long getScheduledTime() {
        return scheduledTime;
    }

    public int getPriority() {
        return priority;
    }
}
