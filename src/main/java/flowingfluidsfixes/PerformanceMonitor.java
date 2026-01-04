package flowingfluidsfixes;

import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.FORGE)
@SuppressWarnings("all")
public class PerformanceMonitor {
    private static final Logger LOGGER = LogManager.getLogger(PerformanceMonitor.class);
    private static final int TPS_WINDOW_SIZE = 100;
    private static final int CPU_USAGE_WINDOW_SIZE = 50;
    private static final int CPU_CHECK_INTERVAL = 10;
    private static final double CPU_USAGE_THRESHOLD = 80.0;
    private static final double CPU_OPTIMIZATION_THRESHOLD = 90.0;
    private static final int FLUID_UPDATE_WINDOW_SIZE = 20;
    // Adjusted from 12000 to 15000 - increased target for faster edge water flow
    private static final int MAX_FLUID_UPDATES_PER_TICK = 15000;
    // Keep MIN_FLUID_UPDATES_PER_TICK at 1200 for aggressive edge water flow during overload
    private static final int MIN_FLUID_UPDATES_PER_TICK = 1200;
    // Adjusted from 6000 to 8000 - increased threshold to allow more edge water processing
    private static final int FLUID_UPDATE_THRESHOLD = 8000;
    private static final int SERVER_OVERLOAD_THRESHOLD = 200;
    private static final int TPS_HISTORY_SIZE = 5; // Used for short-term TPS averaging
    private static final double TPS_EMERGENCY_THRESHOLD = 8.0;
    private static final double TPS_NORMAL_THRESHOLD = 15.0;
    private static final int TICK_TIME_WINDOW_SIZE = 100;
    private static final long TICK_TIME_EMERGENCY_THRESHOLD = 125_000_000;
    private static final long TICK_TIME_NORMAL_THRESHOLD = 66_000_000;

    // LOCK-FREE PERFORMANCE TRACKING - Use atomic operations instead of synchronized blocks
    private static final AtomicLong totalTicksMonitored = new AtomicLong(0);
    private static final AtomicLong ticksOverTarget = new AtomicLong(0);
    private static final AtomicLong emergencyHalts = new AtomicLong(0);
    private static final AtomicInteger consecutiveSlowTicks = new AtomicInteger(0);

    // Lock-free rolling averages using atomic references
    private static final AtomicReference<Double> averageTPS = new AtomicReference<>(20.0);
    private static final AtomicReference<Double> averageCPUUsage = new AtomicReference<>(0.0);
    private static final AtomicReference<Long> averageTickTimeNanos = new AtomicReference<>(0L);
    private static final AtomicBoolean normalPerformance = new AtomicBoolean(true);
    private static final AtomicBoolean serverOverloaded = new AtomicBoolean(false);
    private static final AtomicBoolean cpuOptimizationMode = new AtomicBoolean(false);
    private static final AtomicInteger currentFluidUpdateLimit = new AtomicInteger(MAX_FLUID_UPDATES_PER_TICK);
    private static final AtomicInteger fluidUpdateCount = new AtomicInteger(0);
    private static long lastCPUCheck = 0;
    private static long lastTick = 0;

    // LOCK-FREE ROLLING AVERAGES - Use circular buffers with atomic indices
    private static final int WINDOW_SIZE = 100;
    private static final double[] tpsWindow = new double[WINDOW_SIZE];
    private static final double[] cpuWindow = new double[WINDOW_SIZE];
    private static final long[] tickTimeWindow = new long[WINDOW_SIZE];
    private static final AtomicInteger windowIndex = new AtomicInteger(0);
    private static final AtomicInteger windowCount = new AtomicInteger(0);

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (lastTick != 0) {
            long tickDuration = currentTime - lastTick;
            double tps = tickDuration > 0 ? 1000.0 / tickDuration : 20.0;
            updateMetrics(tickDuration, tps, 0.0, fluidUpdateCount.get());
        }
        lastTick = currentTime;

        if (currentTime - lastCPUCheck >= CPU_CHECK_INTERVAL * 50L) {
            lastCPUCheck = currentTime;
            updateCPUUsage();
        }

        fluidUpdateCount.set(0);
    }

    /**
     * Update performance metrics without synchronization
     */
    public static void updateMetrics(long tickTime, double tps, double cpuUsage, int fluidUpdateCount) {
        // Update rolling averages using lock-free circular buffer
        int index = windowIndex.getAndIncrement() % WINDOW_SIZE;
        int count = Math.min(windowCount.incrementAndGet(), WINDOW_SIZE);

        tpsWindow[index] = tps;
        cpuWindow[index] = cpuUsage;
        tickTimeWindow[index] = tickTime;

        // Calculate new averages
        if (count > 0) {
            double newTPS = 0.0;
            double newCPU = 0.0;
            long newTickTime = 0L;

            for (int i = 0; i < count; i++) {
                newTPS += tpsWindow[i];
                newCPU += cpuWindow[i];
                newTickTime += tickTimeWindow[i];
            }

            averageTPS.set(newTPS / count);
            averageCPUUsage.set(newCPU / count);
            averageTickTimeNanos.set(newTickTime / count);
        }

        // Update performance state
        updatePerformanceState();
        updateFluidUpdateLimit();
    }

    /**
     * Update performance state without synchronization
     */
    private static void updatePerformanceState() {
        double currentTPS = averageTPS.get();
        long currentTickTime = averageTickTimeNanos.get();
        double currentCPU = averageCPUUsage.get();

        boolean wasNormal = normalPerformance.get();
        boolean wasOverloaded = serverOverloaded.get();

        // Update state flags atomically
        normalPerformance.set(currentTPS >= TPS_NORMAL_THRESHOLD &&
                currentTickTime <= TICK_TIME_NORMAL_THRESHOLD &&
                currentCPU <= CPU_USAGE_THRESHOLD);

        serverOverloaded.set(currentTPS < TPS_EMERGENCY_THRESHOLD ||
                currentTickTime > TICK_TIME_EMERGENCY_THRESHOLD);

        cpuOptimizationMode.set(currentCPU > CPU_OPTIMIZATION_THRESHOLD ||
                currentTPS < TPS_EMERGENCY_THRESHOLD ||
                currentTickTime > TICK_TIME_EMERGENCY_THRESHOLD);

        // Log state changes
        if (wasNormal && !normalPerformance.get()) {
            LOGGER.warn("Performance degraded: TPS={}, TickTime={}ns, CPU={}%",
                    String.format("%.2f", currentTPS), currentTickTime, String.format("%.1f", currentCPU * 100));
        } else if (!wasNormal && normalPerformance.get()) {
            LOGGER.info("Performance recovered: TPS={}, TickTime={}ns, CPU={}%",
                    String.format("%.2f", currentTPS), currentTickTime, String.format("%.1f", currentCPU * 100));
        }
    }

    /**
     * Update fluid update limit without synchronization
     */
    private static void updateFluidUpdateLimit() {
        int previousLimit = currentFluidUpdateLimit.get();

        if (serverOverloaded.get() || cpuOptimizationMode.get()) {
            currentFluidUpdateLimit.set(MIN_FLUID_UPDATES_PER_TICK);
        } else if (normalPerformance.get()) {
            currentFluidUpdateLimit.set(MAX_FLUID_UPDATES_PER_TICK);
        } else {
            currentFluidUpdateLimit.set(MIN_FLUID_UPDATES_PER_TICK +
                    (MAX_FLUID_UPDATES_PER_TICK - MIN_FLUID_UPDATES_PER_TICK) / 2);
        }

        if (previousLimit != currentFluidUpdateLimit.get()) {
            LOGGER.info("Fluid update limit changed: {} -> {}", previousLimit, currentFluidUpdateLimit.get());
        }
    }

    /**
     * Update CPU usage without synchronization
     */
    private static void updateCPUUsage() {
        double cpuUsage = 0.0; // Placeholder since actual CPU usage check is not available
        int index = windowIndex.get() % WINDOW_SIZE;
        cpuWindow[index] = cpuUsage;
    }

    /**
     * Get performance metrics without synchronization
     */
    public static double getAverageTPS() {
        return averageTPS.get();
    }

    public static double getAverageCPUUsage() {
        return averageCPUUsage.get();
    }

    public static long getAverageTickTime() {
        return averageTickTimeNanos.get();
    }

    public static int getCurrentFluidUpdateLimit() {
        return currentFluidUpdateLimit.get();
    }

    public static boolean isServerOverloaded() {
        return serverOverloaded.get();
    }

    public static boolean isNormalPerformance() {
        return normalPerformance.get();
    }

    public static boolean shouldThrottleFluidUpdates() {
        return fluidUpdateCount.get() >= currentFluidUpdateLimit.get();
    }

    public static boolean canProcessFluidUpdate() {
        if (fluidUpdateCount.get() >= currentFluidUpdateLimit.get()) {
            if (fluidUpdateCount.get() == currentFluidUpdateLimit.get()) {
                LOGGER.debug("Fluid update limit reached for this tick: {}", currentFluidUpdateLimit.get());
            }
            return false;
        }
        return true;
    }
    
    /**
     * Increment fluid update counter for tracking
     */
    public static void incrementFluidUpdateCount() {
        fluidUpdateCount.incrementAndGet();
    }
    
    /**
     * Get current fluid update count for this tick
     */
    public static int getFluidUpdateCount() {
        return fluidUpdateCount.get();
    }


}
