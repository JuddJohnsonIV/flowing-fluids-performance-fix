package flowingfluidsfixes;

import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

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

    private static final List<Long> tickTimes = Collections.synchronizedList(new ArrayList<>());
    private static final List<Double> tpsValues = Collections.synchronizedList(new ArrayList<>());
    private static final List<Double> cpuUsages = Collections.synchronizedList(new ArrayList<>());
    private static final List<Integer> fluidUpdateCounts = Collections.synchronizedList(new ArrayList<>());
    private static volatile double averageTPS = 20.0;
    private static volatile double averageCPUUsage = 0.0;
    private static volatile long averageTickTimeNanos = 0L;
    private static volatile boolean cpuOptimizationMode = false;
    private static volatile int fluidUpdateCount = 0;
    private static volatile int averageFluidUpdates = 0;
    private static volatile int currentFluidUpdateLimit = MAX_FLUID_UPDATES_PER_TICK;
    private static volatile boolean serverOverloaded = false;
    private static volatile boolean normalPerformance = true;
    private static long lastCPUCheck = 0;
    private static long lastTick = 0;

    // Metrics for Flowing Fluids optimizations
    private static long flowingFluidsUpdates = 0;
    private static long flowingFluidsSkippedUpdates = 0;
    private static long flowingFluidsDelayedUpdates = 0;
    private static double flowingFluidsTpsImpact = 0.0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (lastTick != 0) {
            long tickDuration = currentTime - lastTick;
            double tps = tickDuration > 0 ? 1000.0 / tickDuration : 20.0;
            tpsValues.add(tps);
            if (tpsValues.size() > TPS_WINDOW_SIZE) {
                tpsValues.remove(0);
            }

            long tickTimeNanos = tickDuration * 1_000_000;
            tickTimes.add(tickTimeNanos);
            if (tickTimes.size() > TICK_TIME_WINDOW_SIZE) {
                tickTimes.remove(0);
            }

            updateAverages();
            updatePerformanceState();
            updateFluidUpdateLimit();
        }
        lastTick = currentTime;

        if (currentTime - lastCPUCheck >= CPU_CHECK_INTERVAL * 50L) {
            lastCPUCheck = currentTime;
            updateCPUUsage();
        }

        fluidUpdateCount = 0;
    }

    private static synchronized void updateAverages() {
        if (!tpsValues.isEmpty()) {
            averageTPS = tpsValues.stream().mapToDouble(Double::doubleValue).average().orElse(20.0);
        }

        if (!tickTimes.isEmpty()) {
            averageTickTimeNanos = (long) tickTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        }

        if (!fluidUpdateCounts.isEmpty()) {
            averageFluidUpdates = (int) fluidUpdateCounts.stream().mapToInt(Integer::intValue).average().orElse(0);
        }

        cpuOptimizationMode = averageCPUUsage > CPU_OPTIMIZATION_THRESHOLD || averageTPS < TPS_EMERGENCY_THRESHOLD || averageTickTimeNanos > TICK_TIME_EMERGENCY_THRESHOLD;
    }

    private static synchronized void updatePerformanceState() {
        boolean wasNormal = normalPerformance;
        boolean wasOverloaded = serverOverloaded;

        normalPerformance = averageTPS >= TPS_NORMAL_THRESHOLD && 
                           averageTickTimeNanos <= TICK_TIME_NORMAL_THRESHOLD &&
                           averageCPUUsage <= CPU_USAGE_THRESHOLD;

        serverOverloaded = averageFluidUpdates > SERVER_OVERLOAD_THRESHOLD ||
                          averageTPS < TPS_EMERGENCY_THRESHOLD ||
                          averageTickTimeNanos > TICK_TIME_EMERGENCY_THRESHOLD;

        // Ensure recovery from overloaded state when TPS improves significantly
        if (serverOverloaded && wasOverloaded && averageTPS > TPS_NORMAL_THRESHOLD) {
            serverOverloaded = false;
            LOGGER.info("Forced recovery from overloaded state due to improved TPS: {}", averageTPS);
        }

        if (wasNormal && !normalPerformance) {
            LOGGER.warn("Performance degradation detected - TPS: {}, TickTime: {}ns, CPU: {}%", 
                       String.format("%.2f", averageTPS), averageTickTimeNanos, String.format("%.1f", averageCPUUsage));
        } else if (!wasNormal && normalPerformance) {
            LOGGER.info("Performance returned to normal - TPS: {}", String.format("%.2f", averageTPS));
        }

        if (!wasOverloaded && serverOverloaded) {
            LOGGER.error("Server overload detected! Fluid updates: {}, TPS: {}", 
                        averageFluidUpdates, String.format("%.2f", averageTPS));
        } else if (wasOverloaded && !serverOverloaded) {
            LOGGER.info("Server recovered from overload state");
        }
    }

    private static synchronized void updateFluidUpdateLimit() {
        int previousLimit = currentFluidUpdateLimit;

        if (serverOverloaded || cpuOptimizationMode) {
            currentFluidUpdateLimit = MIN_FLUID_UPDATES_PER_TICK * 4; // Increased to allow more updates even under load
        } else if (!normalPerformance) {
            double tpsRatio = Math.min(1.0, averageTPS / 20.0);
            currentFluidUpdateLimit = (int) (MIN_FLUID_UPDATES_PER_TICK * 2 + 
                (FLUID_UPDATE_THRESHOLD - MIN_FLUID_UPDATES_PER_TICK) * tpsRatio);
        } else if (averageFluidUpdates > FLUID_UPDATE_THRESHOLD) {
            currentFluidUpdateLimit = Math.max(MIN_FLUID_UPDATES_PER_TICK * 3, 
                currentFluidUpdateLimit - (currentFluidUpdateLimit / 10));
        } else {
            double tpsRatio = Math.min(1.0, averageTPS / 20.0);
            currentFluidUpdateLimit = (int) (MIN_FLUID_UPDATES_PER_TICK * 2 + 
                (MAX_FLUID_UPDATES_PER_TICK - MIN_FLUID_UPDATES_PER_TICK) * tpsRatio);
        }

        currentFluidUpdateLimit = Math.max(MIN_FLUID_UPDATES_PER_TICK * 3, 
            Math.min(MAX_FLUID_UPDATES_PER_TICK, currentFluidUpdateLimit));

        if (Math.abs(previousLimit - currentFluidUpdateLimit) > 1000) {
            LOGGER.debug("Fluid update limit adjusted: {} -> {}", previousLimit, currentFluidUpdateLimit);
        }
    }

    private static synchronized void updateCPUUsage() {
        double cpuUsage = 0.0; // Placeholder since actual CPU usage check is not available
        cpuUsages.add(cpuUsage);
        if (cpuUsages.size() > CPU_USAGE_WINDOW_SIZE) {
            cpuUsages.remove(0);
        }

        averageCPUUsage = cpuUsages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public static double getAverageTPS() {
        return averageTPS;
    }

    public static double getAverageCPUUsage() {
        return averageCPUUsage;
    }

    public static long getAverageTickTime() {
        return averageTickTimeNanos;
    }

    public static int getAverageFluidUpdates() {
        return averageFluidUpdates;
    }

    public static boolean isCPUOptimizationMode() {
        return cpuOptimizationMode;
    }

    public static void recordFluidUpdate() {
        fluidUpdateCount++;
        fluidUpdateCounts.add(fluidUpdateCount);
        if (fluidUpdateCounts.size() > FLUID_UPDATE_WINDOW_SIZE) {
            fluidUpdateCounts.remove(0);
        }
    }

    public static Map<String, Object> getPerformanceStats(Level level) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("averageTPS", averageTPS);
        stats.put("averageTickTimeNanos", averageTickTimeNanos);
        stats.put("averageCPUUsage", averageCPUUsage);
        stats.put("averageFluidUpdates", averageFluidUpdates);
        stats.put("cpuOptimizationMode", cpuOptimizationMode);
        stats.put("currentFluidUpdateLimit", currentFluidUpdateLimit);
        stats.put("serverOverloaded", serverOverloaded);
        stats.put("normalPerformance", normalPerformance);
        return stats;
    }

    public static int getCurrentFluidUpdateLimit() {
        return currentFluidUpdateLimit;
    }

    public static boolean isServerOverloaded() {
        return serverOverloaded;
    }

    public static boolean isNormalPerformance() {
        return normalPerformance;
    }

    public static boolean shouldThrottleFluidUpdates() {
        return fluidUpdateCount >= currentFluidUpdateLimit;
    }

    public static boolean canProcessFluidUpdate() {
        if (fluidUpdateCount >= currentFluidUpdateLimit) {
            if (fluidUpdateCount == currentFluidUpdateLimit) {
                LOGGER.debug("Fluid update limit reached for this tick: {}", currentFluidUpdateLimit);
            }
            return false;
        }
        return true;
    }

    public static double getShortTermAverageTPS() {
        if (tpsValues.size() < TPS_HISTORY_SIZE) {
            return averageTPS;
        }
        List<Double> recentTps = tpsValues.subList(
            Math.max(0, tpsValues.size() - TPS_HISTORY_SIZE), 
            tpsValues.size()
        );
        return recentTps.stream().mapToDouble(Double::doubleValue).average().orElse(averageTPS);
    }

    public static void incrementFlowingFluidsUpdates() {
        flowingFluidsUpdates++;
    }

    public static void incrementFlowingFluidsSkippedUpdates() {
        flowingFluidsSkippedUpdates++;
    }

    public static void incrementFlowingFluidsDelayedUpdates() {
        flowingFluidsDelayedUpdates++;
    }

    public static void updateFlowingFluidsTpsImpact(double tpsImpact) {
        flowingFluidsTpsImpact = tpsImpact;
    }

    public static long getFlowingFluidsUpdates() {
        return flowingFluidsUpdates;
    }

    public static long getFlowingFluidsSkippedUpdates() {
        return flowingFluidsSkippedUpdates;
    }

    public static long getFlowingFluidsDelayedUpdates() {
        return flowingFluidsDelayedUpdates;
    }

    public static double getFlowingFluidsTpsImpact() {
        return flowingFluidsTpsImpact;
    }

    // Method to log performance metrics for Flowing Fluids
    public static void logFlowingFluidsPerformanceMetrics() {
        LOGGER.info("Flowing Fluids Performance Metrics: Updates={}, Skipped={}, Delayed={}, TPS Impact={}", 
            flowingFluidsUpdates, flowingFluidsSkippedUpdates, flowingFluidsDelayedUpdates, flowingFluidsTpsImpact);
    }

    // Reset metrics for Flowing Fluids
    public static void resetFlowingFluidsMetrics() {
        flowingFluidsUpdates = 0;
        flowingFluidsSkippedUpdates = 0;
        flowingFluidsDelayedUpdates = 0;
        flowingFluidsTpsImpact = 0.0;
    }
}
