package flowingfluidsfixes.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("all")
public class FluidTickScheduler {
    private static final Logger LOGGER = LogManager.getLogger(FluidTickScheduler.class);
    private static final FluidTickScheduler INSTANCE = new FluidTickScheduler();

    public static FluidTickScheduler getInstance() {
        return INSTANCE;
    }

    public void scheduleFluidTick(ServerLevel level, BlockPos pos, Fluid fluid, int delay) {
        // Implementation for scheduling fluid ticks
        LOGGER.debug("Scheduling fluid tick at {} for fluid {} with delay {}", pos, fluid, delay);
        level.scheduleTick(pos, fluid, delay);
    }
    
    public void scheduleStabilityCheck(ServerLevel level, BlockPos pos, FluidState state) {
        // Implementation for scheduling stability checks
        LOGGER.debug("Scheduling stability check at {} for state {}", pos, state);
        level.scheduleTick(pos, state.getType(), 1);
    }
    
    public void scheduleTick(ServerLevel level, BlockPos pos, Fluid fluid) {
        // Implementation for scheduling ticks
        scheduleFluidTick(level, pos, fluid, 1);
        LOGGER.debug("Scheduled tick for fluid at {}", pos);
    }
    
    public void scheduleTick(ServerLevel level, BlockPos pos, FluidState state) {
        // Implementation for scheduling ticks with FluidState
        scheduleFluidTick(level, pos, state.getType(), 1);
        LOGGER.debug("Scheduled tick for fluid state at {}", pos);
    }
}
