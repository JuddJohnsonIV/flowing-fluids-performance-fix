package flowingfluidsfixes;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tick Time Protection System - Prevents fluid processing from slowing game time
 * 
 * The Problem:
 * - Minecraft runs at 20 TPS (50ms per tick)
 * - If fluid processing takes too long, ticks take longer than 50ms
 * - This causes TPS to drop below 20, which slows game time progression
 * - Daytime appears to slow down because game time advances per tick
 * 
 * The Solution:
 * - Monitor actual tick time at the START of each tick
 * - If previous tick exceeded safe thresholds, HALT all fluid processing
 * - Gradually resume fluid processing when tick time normalizes
 * - Prioritize game time progression over fluid visual accuracy
 */
@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TickTimeProtection {
    private static final Logger LOGGER = LogManager.getLogger(TickTimeProtection.class);
    
    // Tick time thresholds (in milliseconds)
    // Target: 50ms per tick = 20 TPS
    private static final long TICK_TIME_TARGET_MS = 50;
    private static final long TICK_TIME_WARNING_MS = 60;    // 10ms over = warning
    private static final long TICK_TIME_CRITICAL_MS = 85;   // 35ms over = critical
    private static final long TICK_TIME_EMERGENCY_MS = 100; // 50ms over = emergency halt
    
    // Tick timing tracking
    private static volatile long lastTickStartTime = 0;
    private static volatile long lastTickDuration = 0;
    private static volatile long averageTickDuration = 50;
    private static final int TICK_HISTORY_SIZE = 20;
    private static final long[] tickHistory = new long[TICK_HISTORY_SIZE];
    private static volatile int tickHistoryIndex = 0;
    
    // Protection state
    public enum ProtectionState {
        NORMAL,     // Tick time is healthy, allow full fluid processing
        WARNING,    // Tick time slightly elevated, reduce fluid processing
        CRITICAL,   // Tick time high, minimal fluid processing
        EMERGENCY   // Tick time critical, HALT ALL fluid processing
    }
    private static volatile ProtectionState currentState = ProtectionState.NORMAL;
    
    // Fluid processing control
    private static volatile boolean fluidProcessingHalted = false;
    private static volatile int fluidProcessingReduction = 0; // 0-100%
    
    // Statistics
    private static final AtomicLong totalTicksMonitored = new AtomicLong(0);
    private static final AtomicLong ticksOverTarget = new AtomicLong(0);
    private static final AtomicLong emergencyHalts = new AtomicLong(0);
    private static final AtomicInteger consecutiveSlowTicks = new AtomicInteger(0);
    
    // Recovery tracking
    private static volatile int ticksAtNormalSpeed = 0;
    private static final int RECOVERY_THRESHOLD = 10; // Need 10 normal ticks to fully recover
    
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        
        // Calculate duration of previous tick
        if (lastTickStartTime > 0) {
            lastTickDuration = currentTime - lastTickStartTime;
            updateTickHistory(lastTickDuration);
            analyzeTickTime();
        }
        
        lastTickStartTime = currentTime;
        totalTicksMonitored.incrementAndGet();
    }
    
    /**
     * Update tick duration history for averaging
     */
    private static void updateTickHistory(long duration) {
        tickHistory[tickHistoryIndex] = duration;
        tickHistoryIndex = (tickHistoryIndex + 1) % TICK_HISTORY_SIZE;
        
        // Calculate average
        long sum = 0;
        int count = 0;
        for (long tickTime : tickHistory) {
            if (tickTime > 0) {
                sum += tickTime;
                count++;
            }
        }
        if (count > 0) {
            averageTickDuration = sum / count;
        }
    }
    
    /**
     * Analyze tick time and adjust protection state
     */
    private static void analyzeTickTime() {
        ProtectionState oldState = currentState;
        
        // Track slow ticks
        if (lastTickDuration > TICK_TIME_TARGET_MS) {
            ticksOverTarget.incrementAndGet();
            consecutiveSlowTicks.incrementAndGet();
            ticksAtNormalSpeed = 0;
        } else {
            consecutiveSlowTicks.set(0);
            ticksAtNormalSpeed++;
        }
        
        // Determine and apply protection state
        ProtectionState newState = determineProtectionState();
        applyProtectionState(newState);
        
        // Log state changes
        if (oldState != currentState) {
            logStateChange(currentState);
        }
    }
    
    /**
     * Determine the appropriate protection state based on tick metrics
     */
    private static ProtectionState determineProtectionState() {
        int slowTicks = consecutiveSlowTicks.get();
        
        // Check conditions in order of severity
        if (lastTickDuration >= TICK_TIME_EMERGENCY_MS || slowTicks >= 5) {
            return ProtectionState.EMERGENCY;
        }
        if (lastTickDuration >= TICK_TIME_CRITICAL_MS || slowTicks >= 3) {
            return ProtectionState.CRITICAL;
        }
        if (lastTickDuration >= TICK_TIME_WARNING_MS || averageTickDuration > TICK_TIME_TARGET_MS) {
            return ProtectionState.WARNING;
        }
        if (ticksAtNormalSpeed >= RECOVERY_THRESHOLD) {
            return ProtectionState.NORMAL;
        }
        // Gradual recovery - maintain current state
        return currentState;
    }
    
    /**
     * Apply the protection state and update related flags
     */
    private static void applyProtectionState(ProtectionState newState) {
        switch (newState) {
            case EMERGENCY -> {
                currentState = ProtectionState.EMERGENCY;
                fluidProcessingHalted = true;
                fluidProcessingReduction = 100;
                emergencyHalts.incrementAndGet();
            }
            case CRITICAL -> {
                currentState = ProtectionState.CRITICAL;
                fluidProcessingHalted = false;
                fluidProcessingReduction = 90;
            }
            case WARNING -> {
                currentState = ProtectionState.WARNING;
                fluidProcessingHalted = false;
                fluidProcessingReduction = 50;
            }
            case NORMAL -> {
                currentState = ProtectionState.NORMAL;
                fluidProcessingHalted = false;
                fluidProcessingReduction = 0;
            }
            default -> {
                // Gradual recovery
                fluidProcessingReduction = Math.max(0, fluidProcessingReduction - 10);
                if (fluidProcessingReduction == 0) {
                    currentState = ProtectionState.NORMAL;
                }
            }
        }
    }
    
    /**
     * Log state change with appropriate severity
     */
    private static void logStateChange(ProtectionState state) {
        switch (state) {
            case EMERGENCY -> LOGGER.error("TICK TIME EMERGENCY - Halting all fluid processing! Last tick: {}ms, Average: {}ms",
                lastTickDuration, averageTickDuration);
            case CRITICAL -> LOGGER.warn("Tick time critical - Reducing fluid processing by 90%. Last tick: {}ms",
                lastTickDuration);
            case WARNING -> LOGGER.info("Tick time elevated - Reducing fluid processing by 50%. Last tick: {}ms",
                lastTickDuration);
            case NORMAL -> LOGGER.info("Tick time normalized - Resuming normal fluid processing. Average: {}ms",
                averageTickDuration);
        }
    }
    
    /**
     * Check if fluid processing should be allowed
     * Called by FluidTickScheduler before each fluid update
     */
    public static boolean shouldAllowFluidProcessing() {
        return !fluidProcessingHalted;
    }
    
    /**
     * Get the fluid processing multiplier (0.0 to 1.0)
     * 1.0 = full processing, 0.0 = no processing
     */
    public static double getFluidProcessingMultiplier() {
        return (100 - fluidProcessingReduction) / 100.0;
    }
    
    /**
     * Get current protection state
     */
    public static ProtectionState getProtectionState() {
        return currentState;
    }
    
    /**
     * Get last tick duration in milliseconds
     */
    public static long getLastTickDuration() {
        return lastTickDuration;
    }
    
    /**
     * Get average tick duration in milliseconds
     */
    public static long getAverageTickDuration() {
        return averageTickDuration;
    }
    
    /**
     * Check if tick time is within target (50ms)
     */
    public static boolean isTickTimeHealthy() {
        return currentState == ProtectionState.NORMAL && averageTickDuration <= TICK_TIME_TARGET_MS;
    }
    
    /**
     * Get the maximum fluid time budget based on current tick protection state
     * This dynamically adjusts based on how much headroom we have in the tick
     */
    public static long getDynamicFluidTimeBudgetMs() {
        // Calculate remaining time in tick budget
        long remainingBudget = TICK_TIME_TARGET_MS - (System.currentTimeMillis() - lastTickStartTime);
        
        // Never use more than 20% of remaining budget for fluids
        long maxFluidTime = Math.max(1, remainingBudget / 5);
        
        // Apply state-based reduction
        return switch (currentState) {
            case EMERGENCY -> 0; // No time for fluids
            case CRITICAL -> Math.min(2, maxFluidTime);  // 2ms max
            case WARNING -> Math.min(3, maxFluidTime);   // 3ms max
            case NORMAL -> Math.min(5, maxFluidTime);    // 5ms max
        };
    }
    
    /**
     * Get status summary
     */
    public static String getStatusSummary() {
        return String.format("TickProtection: state=%s, lastTick=%dms, avgTick=%dms, reduction=%d%%, halts=%d",
            currentState, lastTickDuration, averageTickDuration, 
            fluidProcessingReduction, emergencyHalts.get());
    }
    
    /**
     * Get detailed statistics
     */
    public static String getDetailedStats() {
        long total = totalTicksMonitored.get();
        long overTarget = ticksOverTarget.get();
        double overPercent = total > 0 ? (overTarget * 100.0 / total) : 0;
        
        return String.format("TickTimeProtection Stats: %d total ticks, %d over target (%.1f%%), %d emergency halts, state=%s",
            total, overTarget, overPercent, emergencyHalts.get(), currentState);
    }
    
    /**
     * Force reset to normal state (for debugging/commands)
     */
    public static void forceReset() {
        currentState = ProtectionState.NORMAL;
        fluidProcessingHalted = false;
        fluidProcessingReduction = 0;
        consecutiveSlowTicks.set(0);
        ticksAtNormalSpeed = RECOVERY_THRESHOLD;
        LOGGER.info("Tick time protection force reset to NORMAL state");
    }
}
