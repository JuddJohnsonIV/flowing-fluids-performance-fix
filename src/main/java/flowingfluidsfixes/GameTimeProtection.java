package flowingfluidsfixes;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import java.util.concurrent.atomic.AtomicLong;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GameTimeProtection {
    private static final Logger LOGGER = LogManager.getLogger(GameTimeProtection.class);
    
    private static final long MS_PER_TICK = 50;
    
    private static final long DRIFT_WARNING_TICKS = 5;
    private static final long DRIFT_CRITICAL_TICKS = 10;
    private static final long DRIFT_EMERGENCY_TICKS = 20;
    
    private static volatile long startRealTimeMs = 0;
    private static volatile long startGameTime = 0;
    private static volatile long expectedGameTime = 0;
    private static volatile long actualGameTime = 0;
    private static volatile long currentDriftTicks = 0;
    
    public enum DriftState {
        SYNCED,
        WARNING,
        CRITICAL,
        EMERGENCY
    }
    
    private static volatile DriftState currentDriftState = DriftState.SYNCED;
    
    private static volatile AtomicLong timeDriftEvents = new AtomicLong(0);
    private static volatile AtomicLong emergencyHalts = new AtomicLong(0);
    private static volatile int consecutiveDriftTicks = 0;
    private static volatile long maxDriftObserved = 0;
    
    private static volatile boolean fluidProcessingAllowed = true;
    private static volatile double fluidProcessingMultiplier = 1.0;
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld == null) return;
            
            long currentRealTime = System.currentTimeMillis();
            long currentGameTime = overworld.getGameTime();
            
            if (startRealTimeMs == 0) {
                startRealTimeMs = currentRealTime;
                startGameTime = currentGameTime;
                expectedGameTime = currentGameTime;
                actualGameTime = currentGameTime;
                return;
            }
            
            long realTimeElapsedMs = currentRealTime - startRealTimeMs;
            long expectedTicksElapsed = realTimeElapsedMs / MS_PER_TICK;
            expectedGameTime = startGameTime + expectedTicksElapsed;
            actualGameTime = currentGameTime;
            
            currentDriftTicks = expectedGameTime - actualGameTime;
            
            if (currentDriftTicks > maxDriftObserved) {
                maxDriftObserved = currentDriftTicks;
            }
            
            updateDriftState();
            
            FluidProcessingDistanceLimit.updatePlayerCache(overworld);
        }
    }
    
    private static void updateDriftState() {
        DriftState oldState = currentDriftState;
        
        if (currentDriftTicks >= DRIFT_EMERGENCY_TICKS) {
            currentDriftState = DriftState.EMERGENCY;
            fluidProcessingAllowed = false;
            fluidProcessingMultiplier = 0.0;
            emergencyHalts.incrementAndGet();
            consecutiveDriftTicks++;
        } else if (currentDriftTicks >= DRIFT_CRITICAL_TICKS) {
            currentDriftState = DriftState.CRITICAL;
            fluidProcessingAllowed = true;
            fluidProcessingMultiplier = 0.1;
            consecutiveDriftTicks++;
        } else if (currentDriftTicks >= DRIFT_WARNING_TICKS) {
            currentDriftState = DriftState.WARNING;
            fluidProcessingAllowed = true;
            fluidProcessingMultiplier = 0.5;
            consecutiveDriftTicks++;
        } else {
            currentDriftState = DriftState.SYNCED;
            fluidProcessingAllowed = true;
            fluidProcessingMultiplier = 1.0;
            consecutiveDriftTicks = 0;
        }
        
        if (oldState != currentDriftState) {
            if (currentDriftState.ordinal() > oldState.ordinal()) {
                timeDriftEvents.incrementAndGet();
                logDriftIncrease(currentDriftState);
            } else {
                logDriftDecrease(currentDriftState);
            }
        }
    }
    
    private static void logDriftIncrease(DriftState newState) {
        switch (newState) {
            case EMERGENCY -> LOGGER.error(
                "GAME TIME EMERGENCY - Halting fluids! Drift: {} ticks ({} ms behind)",
                currentDriftTicks, currentDriftTicks * MS_PER_TICK);
            case CRITICAL -> LOGGER.warn(
                "Game time critical - Reducing fluids to 10%. Drift: {} ticks",
                currentDriftTicks);
            case WARNING -> LOGGER.info(
                "Game time drifting - Reducing fluids to 50%. Drift: {} ticks",
                currentDriftTicks);
            default -> { }
        }
    }
    
    private static void logDriftDecrease(DriftState newState) {
        if (newState == DriftState.SYNCED) {
            LOGGER.info("Game time synchronized - Resuming normal fluid processing");
        }
    }
    
    public static boolean shouldAllowFluidProcessing() {
        return fluidProcessingAllowed;
    }
    
    public static double getFluidProcessingMultiplier() {
        return fluidProcessingMultiplier;
    }
    
    public static DriftState getDriftState() {
        return currentDriftState;
    }
    
    public static long getCurrentDriftTicks() {
        return currentDriftTicks;
    }
    
    public static long getCurrentDriftMs() {
        return currentDriftTicks * MS_PER_TICK;
    }
    
    public static boolean isGameTimeSynced() {
        return currentDriftState == DriftState.SYNCED;
    }
    
    public static String getStatusSummary() {
        return String.format(
            "GameTime: state=%s, drift=%d ticks (%dms), multiplier=%.0f%%, halts=%d",
            currentDriftState, currentDriftTicks, currentDriftTicks * MS_PER_TICK,
            fluidProcessingMultiplier * 100, emergencyHalts.get());
    }
    
    public static String getDetailedStats() {
        return String.format(
            "GameTimeProtection: expected=%d, actual=%d, drift=%d, maxDrift=%d, driftEvents=%d, emergencyHalts=%d, consecutiveDriftTicks=%d",
            expectedGameTime, actualGameTime, currentDriftTicks, maxDriftObserved,
            timeDriftEvents.get(), emergencyHalts.get(), consecutiveDriftTicks);
    }
    
    public static void reset() {
        startRealTimeMs = 0;
        startGameTime = 0;
        expectedGameTime = 0;
        actualGameTime = 0;
        currentDriftTicks = 0;
        currentDriftState = DriftState.SYNCED;
        fluidProcessingAllowed = true;
        fluidProcessingMultiplier = 1.0;
        LOGGER.info("Game time protection reset");
    }
    
    public static void resetStats() {
        timeDriftEvents.set(0);
        emergencyHalts.set(0);
        maxDriftObserved = 0;
        consecutiveDriftTicks = 0;
    }
}
