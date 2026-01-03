package flowingfluidsfixes.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;

@SuppressWarnings("all")
public class FluidThrottlingSystem {
    private static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(FluidThrottlingSystem.class);
    private static final FluidThrottlingSystem INSTANCE = new FluidThrottlingSystem();

    public static FluidThrottlingSystem getInstance() {
        return INSTANCE;
    }

    public boolean shouldAllowUpdate(ServerLevel level, BlockPos pos, FluidState state) {
        // Implementation for throttling fluid updates
        LOGGER.debug("Checking if update is allowed at {} for state {}", pos, state);
        return true;
    }
}
