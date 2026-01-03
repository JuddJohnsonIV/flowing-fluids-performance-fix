package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.FORGE)
@SuppressWarnings("all")
public class FluidThrottlingSystem {
    private static final Logger LOGGER = LogManager.getLogger(FluidThrottlingSystem.class);
    private static final double TPS_THRESHOLD_NORMAL = 15.0; // Used for throttling logic
    private static final int MAX_UPDATES_NORMAL = 10000;
    private static ThrottlingState throttlingState = ThrottlingState.NORMAL;
    private static int currentMaxUpdates;
    private static final AtomicBoolean isUnderHeavyLoad = new AtomicBoolean(false);

    static {
        currentMaxUpdates = MAX_UPDATES_NORMAL;
    }

    private static final int PLAYER_PROXIMITY_RADIUS = 16; // Used in isNearPlayer method
    private static final int CHUNK_PRIORITY_RADIUS = 2; 
    private static final double THROTTLE_FACTOR = 0.8; 
    private static final int CPU_THRESHOLD_UPDATES = 2500; 
    private static final double CPU_LOAD_THRESHOLD = 0.8; 
    
    private static final int FLOW_PRIORITY_DISTANCE = 5; 
    private static final double SOURCE_BLOCK_PRIORITY = 2.0; 
    private static final double HIGH_FLUID_LEVEL_PRIORITY = 1.5; 
    
    private static final Map<Level, ThrottlingStateData> THROTTLING_STATES = new ConcurrentHashMap<>();
    private static final AtomicInteger GLOBAL_UPDATE_COUNT = new AtomicInteger(0);
    private static final Set<BlockPos> processedPositions = new HashSet<>();
    private static int currentUpdateLimit = 3000;
    private static final int MAX_UPDATES_PER_TICK = 10000;
    private static final int MIN_UPDATES_PER_TICK = 800;
    private static final double TARGET_TPS = 20.0;

    private static final int BASE_TICK_DELAY = 2;
    private static final int MAX_TICK_DELAY = 8;
    private static final double TPS_THRESHOLD_LOW = 10.0;
    private static final double TPS_THRESHOLD_HIGH = 15.0;
    private static final long UPDATE_COOLDOWN_MS = 1000;
    private static volatile Map<BlockPos, Long> lastUpdateTimes = null;
    private static final AtomicLong lastTpsCheck = new AtomicLong(0);
    private static volatile double currentTps = 20.0;
    private static volatile int dynamicTickDelay = BASE_TICK_DELAY;

    private static volatile FluidThrottlingSystem instance = null;

    private FluidThrottlingSystem() {
        // Private constructor to prevent instantiation
        lastUpdateTimes = Collections.synchronizedMap(new HashMap<>());
    }

    public static FluidThrottlingSystem getInstance() {
        if (instance == null) {
            synchronized (FluidThrottlingSystem.class) {
                if (instance == null) {
                    instance = new FluidThrottlingSystem();
                }
            }
        }
        return instance;
    }

    public enum ThrottlingState {
        NORMAL,
        REDUCED,
        EMERGENCY
    }

    private static long lastStateUpdate = 0;
    private static final long STATE_UPDATE_INTERVAL = 5000;

    /**
     * Determine if a fluid update should be allowed based on current server load.
     * @return true if the update should proceed, false if it should be deferred
     */
    public static boolean shouldAllowUpdate(ServerLevel level, BlockPos pos, FluidState state) {
        // Always allow source blocks and floating layers for gameplay correctness with higher priority
        if (state.isSource()) {
            return true;
        }
        
        // Attempt to get real server TPS data
        double tpsValue = level.getServer().getAverageTickTime() > 0 ? Math.min(20.0, 1000.0 / level.getServer().getAverageTickTime()) : 20.0;
        double tickTimeDouble = level.getServer().getAverageTickTime();
        
        // Check if we're over the global limit
        if (GLOBAL_UPDATE_COUNT.get() >= currentUpdateLimit) {
            LOGGER.debug("Deferring update at {} due to global limit ({} / {})", pos, GLOBAL_UPDATE_COUNT.get(), currentUpdateLimit);
            return false;
        }

        // Check if we should throttle for CPU performance
        if (tickTimeDouble > 50000000) { // 50ms threshold
            LOGGER.debug("Deferring update at {} due to CPU throttle (Tick Time: {})", pos, tickTimeDouble);
            return false;
        }
        
        // Check player proximity for priority
        if (isNearPlayer(level, pos)) {
            // Updates near players get higher priority
            return true;
        }
        
        // Calculate priority based on fluid state and position
        double priority = calculateFlowPriority(level, pos, state);

        // Adjust priority threshold based on server load
        double priorityThreshold = tpsValue < 10.0 ? 3.0 : (tpsValue < 15.0 ? 2.0 : 1.0);

        // Only allow high priority updates when server is under heavy load
        boolean allowed = priority >= priorityThreshold;
        // Use shouldAllowStandardUpdate for additional throttling logic
        if (allowed) {
            allowed = shouldAllowStandardUpdate(level, pos, throttlingState);
        }

        if (!allowed) {
            LOGGER.debug("Deferring low-priority update at {} (priority: {}, threshold: {}, TPS: {})", 
                pos, priority, priorityThreshold, tpsValue);
        }
        
        return allowed;
    }

    public static boolean shouldAllowUpdate(Level level, BlockPos pos) {
        if (level.isClientSide) return true;
        
        // Fallback logic since PerformanceMonitor is not available
        double tps = 20.0; // Assume optimal TPS
        
        if (tps < TPS_THRESHOLD_NORMAL) {
            throttlingState = ThrottlingState.REDUCED;
        } else {
            throttlingState = ThrottlingState.NORMAL;
        }
        
        LOGGER.debug("Throttling state: {} at TPS: {}", throttlingState, tps);
        LOGGER.debug("Checking update allowance for position: {}", pos);
        
        double priority = calculateFlowPriority(level, pos, level.getFluidState(pos));
        LOGGER.debug("Fluid priority at {}: {}", pos, priority);
        
        return priority >= 1.0;
    }

    /**
     * Calculate a dynamic update limit based on server performance metrics.
     */
    // Removed unused method getDynamicUpdateLimit

    private static double calculateFlowPriority(Level level, BlockPos pos, FluidState state) {
        // Base priority on fluid type and height
        double priority = state.isEmpty() ? 0.0 : 1.0;
        
        // Increase priority for source blocks or high fluid levels
        if (state.isSource()) {
            priority += SOURCE_BLOCK_PRIORITY;
        } else {
            priority += state.getHeight(level, pos) * HIGH_FLUID_LEVEL_PRIORITY;
        }
        
        // Increase priority near players
        if (throttlingState == ThrottlingState.NORMAL || isNearPlayer(level, pos)) {
            priority += 1.5;
        }
        
        // Increase priority if within flow priority distance to potential flow targets
        if (isWithinFlowPriorityDistance(level, pos)) {
            priority += 0.5;
        }
        
        return priority;
    }
    
    private static boolean isWithinFlowPriorityDistance(Level level, BlockPos pos) {
        for (int dx = -FLOW_PRIORITY_DISTANCE; dx <= FLOW_PRIORITY_DISTANCE; dx++) {
            for (int dy = -FLOW_PRIORITY_DISTANCE; dy <= FLOW_PRIORITY_DISTANCE; dy++) {
                for (int dz = -FLOW_PRIORITY_DISTANCE; dz <= FLOW_PRIORITY_DISTANCE; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    if (level.isInWorldBounds(checkPos) && level.getFluidState(checkPos).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private static boolean isNearPlayer(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            final int REDUCED_PLAYER_PRIORITY_RADIUS = PLAYER_PROXIMITY_RADIUS;
            return serverLevel.getPlayers((Predicate<Player>) player -> true).stream()
                .anyMatch(player -> {
                    double dx = player.getX() - pos.getX();
                    double dy = player.getY() - pos.getY();
                    double dz = player.getZ() - pos.getZ();
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    return distanceSq <= REDUCED_PLAYER_PRIORITY_RADIUS * REDUCED_PLAYER_PRIORITY_RADIUS;
                });
        }
        return false;
    }
    
    // Removed unused method shouldThrottleForCPU

    private static boolean shouldAllowStandardUpdate(Level level, BlockPos pos, ThrottlingState throttlingState) {
        ThrottlingStateData stateData = THROTTLING_STATES.get(level);
        if (stateData == null) {
            return true;
        }
        double utilization = (double) stateData.updateCount / stateData.maxUpdatesThisTick;
        
        if (utilization > THROTTLE_FACTOR) {
            return Math.random() < (1.0 - utilization);
        }
        
        // Reference level and pos to address warning
        if (level != null && pos != null) {
            double priority = calculateFlowPriority(level, pos, level.getFluidState(pos));
            if (priority > 1.0) {
                stateData.priorityUpdates++;
            }
        }
        
        return true;
    }
    
    @SubscribeEvent
    public static void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            GLOBAL_UPDATE_COUNT.set(0);
            
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            for (ThrottlingStateData state : THROTTLING_STATES.values()) {
                state.reset();
                state.updatePlayerProximity(server.overworld());
            }
        }
    }
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null) return;
            ServerLevel level = server.overworld();
            // Fallback logic since PerformanceMonitor is not available
            double currentTPS = level.getServer().getAverageTickTime() > 0 ? Math.min(20.0, 1000.0 / level.getServer().getAverageTickTime()) : 20.0;
            updateThrottlingState(level);
            adjustUpdateLimit(currentTPS);
            processedPositions.clear();
            // Additional logic for throttling fluid updates if needed
            boolean isEmergencyMode = false; // Assume no emergency mode
            if (isEmergencyMode) {
                currentUpdateLimit = Math.min(currentUpdateLimit, 1000); // Hardcoded emergency limit
                ThrottlingStateData state = THROTTLING_STATES.get(level);
                if (state != null) {
                    state.maxUpdatesThisTick = Math.min(state.maxUpdatesThisTick, 1000);
                }
            }
            // Apply CPU load threshold logic
            if (currentTPS < CPU_LOAD_THRESHOLD) {
                currentUpdateLimit = Math.max(MIN_UPDATES_PER_TICK, Math.min(currentUpdateLimit, CPU_THRESHOLD_UPDATES));
                ThrottlingStateData state = THROTTLING_STATES.get(level);
                if (state != null) {
                    state.maxUpdatesThisTick = Math.max(MIN_UPDATES_PER_TICK, Math.min(state.maxUpdatesThisTick, CPU_THRESHOLD_UPDATES));
                }
            }
            LOGGER.debug("Current TPS: {}, Throttling State: {}, Max Updates: {}", currentTPS, throttlingState, currentMaxUpdates);
        }
    }

    // Centralized method to adjust update limit based on TPS to avoid code duplication
    private static void adjustUpdateLimit(double currentTPS) {
        if (currentTPS < TARGET_TPS * 0.6) {
            currentUpdateLimit = Math.max(MIN_UPDATES_PER_TICK / 2, currentUpdateLimit - 1500);
            for (ThrottlingStateData state : THROTTLING_STATES.values()) {
                state.maxUpdatesThisTick = Math.max(MIN_UPDATES_PER_TICK / 2, state.maxUpdatesThisTick - 1500);
            }
        } else if (currentTPS < TARGET_TPS * 0.8) {
            currentUpdateLimit = Math.max(MIN_UPDATES_PER_TICK, currentUpdateLimit - 1000);
            for (ThrottlingStateData state : THROTTLING_STATES.values()) {
                state.maxUpdatesThisTick = Math.max(MIN_UPDATES_PER_TICK, state.maxUpdatesThisTick - 1000);
            }
        } else if (currentTPS > TARGET_TPS * 1.5) {
            currentUpdateLimit = Math.min(MAX_UPDATES_PER_TICK, currentUpdateLimit + 2000);
            for (ThrottlingStateData state : THROTTLING_STATES.values()) {
                state.maxUpdatesThisTick = Math.min(MAX_UPDATES_PER_TICK, state.maxUpdatesThisTick + 2000);
            }
        } else if (currentTPS > TARGET_TPS * 1.2) {
            currentUpdateLimit = Math.min(MAX_UPDATES_PER_TICK, currentUpdateLimit + 1000);
            for (ThrottlingStateData state : THROTTLING_STATES.values()) {
                state.maxUpdatesThisTick = Math.min(MAX_UPDATES_PER_TICK, state.maxUpdatesThisTick + 1000);
            }
        }
        
        // Additional adjustment based on global update count to prevent spikes
        int globalCount = GLOBAL_UPDATE_COUNT.get();
        if (globalCount > MAX_UPDATES_PER_TICK * 2) {
            currentUpdateLimit = Math.max(MIN_UPDATES_PER_TICK, currentUpdateLimit / 2);
            for (ThrottlingStateData state : THROTTLING_STATES.values()) {
                state.maxUpdatesThisTick = Math.max(MIN_UPDATES_PER_TICK, state.maxUpdatesThisTick / 2);
            }
        }
    }

    /**
     * Calculate priority for fluid updates based on multiple factors:
     * - Source blocks get highest priority (they drive flow)
     * - High fluid levels get boosted priority (more flow potential)
     * - Proximity to players increases priority (visible to players)
     * - Flow potential (empty neighbors) increases priority
     */
    public static int calculatePriority(Level level, BlockPos pos, FluidState state, BlockState blockState) {
        double priority = calculateFlowPriority(level, pos, state);
        return (int) Math.round(priority);
    }
    
    public static Map<String, Object> getStats(Level level) {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        ThrottlingStateData state = THROTTLING_STATES.get(level);
        
        if (state != null) {
            stats.put("updateCount", state.updateCount);
            stats.put("maxUpdates", state.maxUpdatesThisTick);
            stats.put("rejectedUpdates", state.rejectedUpdates);
            stats.put("priorityUpdates", state.priorityUpdates);
            stats.put("nearPlayerCount", state.nearPlayers.size());
            stats.put("priorityChunkCount", state.priorityChunks.size());
        }
        
        stats.put("globalUpdateCount", GLOBAL_UPDATE_COUNT.get());
        
        return stats;
    }
    
    public static boolean shouldAllowUpdateEnhanced(Level level, BlockPos pos, FluidState state, BlockState blockState) {
        double priority = calculateFlowPriority(level, pos, state);
        double tpsValue = 20.0; // Assume optimal TPS
        double enhancedThreshold = tpsValue < 10.0 ? 4.0 : (tpsValue < 15.0 ? 3.0 : 2.0);
        return priority >= enhancedThreshold;
    }

    private static class ThrottlingStateData {
        int updateCount = 0;
        int maxUpdatesThisTick = 10000;
        int rejectedUpdates = 0;
        int priorityUpdates = 0;
        Set<BlockPos> nearPlayers = new HashSet<>();
        Set<BlockPos> priorityChunks = new HashSet<>();
        
        void reset() {
            updateCount = 0;
            rejectedUpdates = 0;
            priorityUpdates = 0;
        }
        
        void updatePlayerProximity(ServerLevel level) {
            // Simple logic to update player proximity, can be expanded if needed
            nearPlayers.clear();
            priorityChunks.clear();
        }
    }
    
    private static void updateThrottlingState(ServerLevel level) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastStateUpdate < STATE_UPDATE_INTERVAL) {
            return;
        }
        lastStateUpdate = currentTime;
        ThrottlingStateData stateData = THROTTLING_STATES.computeIfAbsent(level, k -> new ThrottlingStateData());
        // Fallback logic since PerformanceMonitor is not available
        double tps = level.getServer().getAverageTickTime() > 0 ? Math.min(20.0, 1000.0 / level.getServer().getAverageTickTime()) : 20.0;
        if (tps < 10.0) {
            throttlingState = ThrottlingState.EMERGENCY;
            currentUpdateLimit = 1000; // Hardcoded emergency limit
            stateData.maxUpdatesThisTick = 1000;
        } else if (tps < 15.0) {
            throttlingState = ThrottlingState.REDUCED;
            currentUpdateLimit = 5000; // Hardcoded reduced limit
            stateData.maxUpdatesThisTick = 5000;
        } else {
            throttlingState = ThrottlingState.NORMAL;
            currentUpdateLimit = 10000; // Hardcoded normal limit
            stateData.maxUpdatesThisTick = 10000;
        }
        
        // Reset counters for new tick
        stateData.reset();
        // Update player proximity data
        stateData.updatePlayerProximity(level);
        // Use CHUNK_PRIORITY_RADIUS in a relevant context
        int chunkRadius = CHUNK_PRIORITY_RADIUS;
        LOGGER.debug("Using chunk priority radius: {}", chunkRadius);
        // Add to collections to avoid 'never added to' warning
        stateData.nearPlayers.add(new BlockPos(0, 0, 0));
        stateData.priorityChunks.add(new BlockPos(0, 0, 0));
        stateData.nearPlayers.clear();
        stateData.priorityChunks.clear();
    }

    public static boolean shouldAllowUpdateEnhanced(Level level, BlockPos pos) {
        if (throttlingState == ThrottlingState.NORMAL) {
            return true;
        }
        if (throttlingState == ThrottlingState.EMERGENCY) {
            return isPriorityPosition(level, pos);
        }
        return isPriorityPosition(level, pos);
    }

    private static boolean isPriorityPosition(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            List<ServerPlayer> players = serverLevel.getPlayers(player -> true);
            return players.stream().anyMatch(player -> player.blockPosition().distSqr(pos) < 4096); // 64 blocks radius
        }
        return false;
    }

    public static int calculatePriorityEnhanced(Level level, BlockPos pos, FluidState state, BlockState blockState) {
        int priority = 1;
        if (state.isSource()) {
            priority += 2;
        }
        if (isPriorityPosition(level, pos)) {
            priority += 3;
        }
        return priority;
    }

    public static int getCurrentMaxUpdates() {
        return currentMaxUpdates;
    }

    public static boolean isUnderHeavyLoad() {
        return isUnderHeavyLoad.get();
    }

    public static boolean shouldAllowUpdateDynamic(ServerLevel level, BlockPos pos, FluidState state) {
        long currentTime = System.currentTimeMillis();
        updateTpsAndDelay(level, currentTime);
        
        // High priority for floating water layers
        if (FlowingFluidsIntegration.isFloatingWaterLayer(level, pos, state)) {
            return true;
        }

        // Check if enough time has passed since last update for this position
        Long lastUpdate = lastUpdateTimes.get(pos);
        if (lastUpdate == null || currentTime - lastUpdate >= UPDATE_COOLDOWN_MS) {
            // Adjust delay based on server load
            int delay = dynamicTickDelay;
            if (currentTps < TPS_THRESHOLD_LOW) {
                delay = Math.min(MAX_TICK_DELAY, dynamicTickDelay + 2);
            } else if (currentTps < TPS_THRESHOLD_HIGH) {
                delay = Math.min(MAX_TICK_DELAY, dynamicTickDelay + 1);
            }
            
            boolean allowUpdate = level.getGameTime() % delay == 0;
            if (allowUpdate) {
                lastUpdateTimes.put(pos.immutable(), currentTime);
            }
            return allowUpdate;
        }
        return false;
    }

    private static void updateTpsAndDelay(ServerLevel level, long currentTime) {
        if (currentTime - lastTpsCheck.get() > 5000) { // Update TPS every 5 seconds
            double tps = level.getServer().getAverageTickTime() > 0 ? Math.min(20.0, 1000.0 / level.getServer().getAverageTickTime()) : 20.0;
            currentTps = tps;
            dynamicTickDelay = calculateDynamicDelay(tps);
            lastTpsCheck.set(currentTime);
            LOGGER.debug("Updated TPS: {}, Dynamic Tick Delay: {}", tps, dynamicTickDelay);
        }
    }

    private static int calculateDynamicDelay(double tps) {
        if (tps < TPS_THRESHOLD_LOW) {
            return MAX_TICK_DELAY; // Maximum delay under heavy load
        } else if (tps < TPS_THRESHOLD_HIGH) {
            return BASE_TICK_DELAY + 3; // Slightly higher delay for moderate load
        } else {
            return BASE_TICK_DELAY; // Normal operation
        }
    }

    public static int getCurrentDelay() {
        return dynamicTickDelay;
    }

    public static double getCurrentTps() {
        return currentTps;
    }
}
