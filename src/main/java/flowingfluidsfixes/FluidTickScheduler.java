package flowingfluidsfixes;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Enhanced Fluid Tick Scheduler for Flowing Fluids integration
 * Prioritizes critical updates and optimizes performance based on server load and player proximity
 */
public class FluidTickScheduler {
    private static final Logger LOGGER = LogManager.getLogger(FluidTickScheduler.class);
    
    // Performance tuning constants - now configurable
    private static int CRITICAL_DISTANCE = 16; // Distance considered critical for immediate updates
    private static int NORMAL_DISTANCE = 64;   // Distance for normal processing
    private static final int MAX_DELAY_MULTIPLIER = 10; // Maximum delay scaling
    private static int BASE_DELAY = 5;         // Base delay for non-critical updates
    private static final int MAX_PROPAGATION_RADIUS = 32; // Maximum radius for fluid update propagation to prevent lag from massive areas
    
    // Performance tracking
    private static final AtomicInteger totalScheduled = new AtomicInteger(0);
    private static final AtomicInteger criticalUpdates = new AtomicInteger(0);
    private static final AtomicInteger deferredUpdates = new AtomicInteger(0);
    private static final AtomicLong totalProcessingTimeNanos = new AtomicLong(0);
    
    // Priority queue for deferred updates (sorted by priority then scheduled time)
    private static final PriorityQueue<PrioritizedFluidTick> deferredQueue = new PriorityQueue<>(
        Comparator.comparingInt(PrioritizedFluidTick::priority)
                  .thenComparingLong(PrioritizedFluidTick::scheduledTime)
    );
    
    // Track recently processed positions to avoid duplicate scheduling
    private static final Map<BlockPos, Long> recentlyScheduled = new ConcurrentHashMap<>();
    private static final int DEDUP_COOLDOWN_TICKS = 2;
    
    // Track origin of fluid changes to limit propagation radius
    private static final Map<BlockPos, BlockPos> fluidChangeOrigins = new ConcurrentHashMap<>();
    
    // Adaptive throttling based on server performance
    private static volatile int adaptiveMaxUpdatesPerTick = 200; // Reduced from 800 for aggressive throttling
    private static volatile double lastKnownTPS = 20.0;
    
    // TIME BUDGET SYSTEM - Dynamic limit on fluid processing time per tick
    // This ensures mob pathfinding and AI always have enough CPU time
    // Now uses EntityProtectionSystem for dynamic budgets based on entity load
    private static final long DEFAULT_FLUID_TIME_PER_TICK_MS = 4; // Default 4ms per tick for fluids (out of 50ms)
    private static volatile long currentMaxFluidTimeNanos = DEFAULT_FLUID_TIME_PER_TICK_MS * 1_000_000;
    
    // Entity protection statistics
    private static final AtomicInteger updatesBlockedForEntities = new AtomicInteger(0);
    private static volatile long fluidTimeUsedThisTick = 0;
    private static final AtomicInteger updatesSkippedDueToTimeBudget = new AtomicInteger(0);
    private static final AtomicLong totalTimeBudgetExceeded = new AtomicLong(0);
    
    // Distance limit and game time protection statistics
    private static final AtomicLong updatesSkippedByDistance = new AtomicLong(0);
    private static final AtomicLong updatesSkippedByGameTime = new AtomicLong(0);
    
    // FLOW STABILITY CACHE - Cache stable flow patterns to avoid recalculation
    // Key: BlockPos, Value: StableFlowEntry with cached flow state
    private static final Map<BlockPos, StableFlowEntry> stableFlowCache = new ConcurrentHashMap<>();
    private static final int STABLE_FLOW_CACHE_MAX_SIZE = 50000;
    private static final int STABILITY_THRESHOLD = 2; // Updates without change = stable (faster detection)
    
    // EDGE FLUID TRACKING - Track edge fluids (level 1-2) that rarely flow
    private static final Map<BlockPos, EdgeFluidEntry> edgeFluidCache = new ConcurrentHashMap<>();
    private static final int EDGE_SKIP_THRESHOLD = 1; // Skip after just 1 stable check (aggressive)
    private static final int DORMANT_THRESHOLD = 3; // Go dormant after 3 stable checks (faster dormancy)
    private static final long DORMANT_RECHECK_INTERVAL = 400; // Only recheck dormant fluids every 20 seconds
    private static final AtomicInteger edgeFluidsSkipped = new AtomicInteger(0);
    private static final AtomicInteger dormantEdgeFluids = new AtomicInteger(0);
    
    // HEAVY FLOW ZONES - Cache areas with constant heavy flow (waterfalls, etc.)
    private static final Map<Long, HeavyFlowZone> heavyFlowZones = new ConcurrentHashMap<>();
    private static final AtomicInteger heavyFlowCacheHits = new AtomicInteger(0);
    
    // Server tick tracking
    private static volatile long currentServerTick = 0;
    
    /**
     * Reset time budget at the start of each server tick
     * Call this from the server tick event handler
     */
    public static void resetTickTimeBudget() {
        if (fluidTimeUsedThisTick >= currentMaxFluidTimeNanos) {
            totalTimeBudgetExceeded.incrementAndGet();
        }
        fluidTimeUsedThisTick = 0;
        currentServerTick++;
        
        // Periodic cache cleanup every 200 ticks (10 seconds)
        if (currentServerTick % 200 == 0) {
            cleanupCaches();
        }
    }
    
    /**
     * Clean up expired cache entries to prevent memory bloat
     */
    private static void cleanupCaches() {
        long cutoffTick = currentServerTick - 100; // Expire entries older than 5 seconds
        
        // Clean stable flow cache
        stableFlowCache.entrySet().removeIf(e -> e.getValue().lastUpdateTick < cutoffTick);
        
        // Clean edge fluid cache
        edgeFluidCache.entrySet().removeIf(e -> e.getValue().lastCheckTick < cutoffTick);
        
        // Clean heavy flow zones
        heavyFlowZones.entrySet().removeIf(e -> e.getValue().lastUpdateTick < cutoffTick);
        
        // Clean recently scheduled
        long timeCutoff = System.currentTimeMillis() - 2000;
        recentlyScheduled.entrySet().removeIf(e -> e.getValue() < timeCutoff);
        
        // Clean fluid change origins
        fluidChangeOrigins.entrySet().removeIf(e -> recentlyScheduled.getOrDefault(e.getKey(), 0L) < timeCutoff);
        
        LOGGER.debug("Cache cleanup: stableFlow={}, edgeFluid={}, heavyZones={}",
            stableFlowCache.size(), edgeFluidCache.size(), heavyFlowZones.size());
    }
    
    /**
     * Get time budget usage statistics
     */
    public static String getTimeBudgetStats() {
        return String.format("TimeBudget: %dms limit (entity-aware), %d times exceeded, %d deferred, %d blocked for entities",
            EntityProtectionSystem.getMaxFluidTimeMs(), totalTimeBudgetExceeded.get(), 
            updatesSkippedDueToTimeBudget.get(), updatesBlockedForEntities.get());
    }
    
    /**
     * Check if we've exceeded the time budget for this tick
     * Uses EntityProtectionSystem for dynamic budget based on entity load
     */
    private static boolean isTimeBudgetExceeded() {
        // Get dynamic time budget from EntityProtectionSystem
        currentMaxFluidTimeNanos = EntityProtectionSystem.getMaxFluidTimeNanos();
        return fluidTimeUsedThisTick >= currentMaxFluidTimeNanos;
    }
    
    /**
     * Enhanced fluid tick scheduling with Flowing Fluids prioritization
     * Implements adaptive throttling based on server TPS, player proximity, AND time budget
     * 
     * KEY OPTIMIZATION: Edge fluids (barely flowing) are aggressively skipped to protect mob pathfinding
     */
    public static void scheduleFluidTick(ServerLevel level, BlockPos pos, FluidState state, int delay) {
        long startTime = System.nanoTime();
        
        // GAME TIME PROTECTION: Primary check - halt if day/night cycle is falling behind
        // This is the MOST IMPORTANT check - players notice slow days immediately
        if (FlowingFluidsOptimizationConfig.enableGameTimeProtection.get() && 
            !GameTimeProtection.shouldAllowFluidProcessing()) {
            updatesSkippedByGameTime.incrementAndGet();
            // Don't even defer - just skip to let game time catch up
            return;
        }
        
        // DISTANCE LIMIT: Skip fluids far from all players (like Flowing Fluids' fluid_processing_distance)
        // This is a HARD CUTOFF - distant fluids are not processed at all
        if (FlowingFluidsOptimizationConfig.enableDistanceLimit.get() && 
            !FluidProcessingDistanceLimit.isWithinProcessingRange(pos)) {
            updatesSkippedByDistance.incrementAndGet();
            // Don't defer distant fluids - they'll be processed when players get closer
            return;
        }
        
        // PROPAGATION RADIUS LIMIT: Check if this update is within the allowed radius from the origin
        if (!isWithinPropagationRadius(pos)) {
            updatesSkippedByDistance.incrementAndGet();
            return; // Skip updates that are too far from the origin to prevent massive calculation areas
        }
        
        // TICK TIME PROTECTION: Secondary check - halt if tick time is critical
        // This prevents daytime slowdown by ensuring game time advances normally
        if (!TickTimeProtection.shouldAllowFluidProcessing()) {
            // Emergency halt - defer ALL updates to prevent game time slowdown
            deferUpdate(level, pos, state, delay, UpdatePriority.LOW);
            return; // Don't even track time - we're in emergency mode
        }
        
        // FAST PATH: Check if this is a stable/cached flow that doesn't need processing
        if (isStableCachedFlow(pos, state)) {
            // CRITICAL: For Flowing Fluids, verify this cached flow can't still drain
            if (FlowingFluidsIntegration.isFlowingFluidsLoaded()) {
                // Double-check that this fluid truly can't flow anywhere
                if (canFluidFlowAnywhere(level, pos, state)) {
                    // It can still flow - remove from cache and process normally
                    stableFlowCache.remove(pos);
                    LOGGER.debug("Flowing Fluids: Cached flow at {} can still flow - processing", pos);
                } else {
                    // Truly stable - safe to skip
                    heavyFlowCacheHits.incrementAndGet();
                    fluidTimeUsedThisTick += System.nanoTime() - startTime;
                    return;
                }
            } else {
                // Vanilla behavior - safe to skip cached flows
                heavyFlowCacheHits.incrementAndGet();
                fluidTimeUsedThisTick += System.nanoTime() - startTime;
                return;
            }
        }
        
        // STABLE SOURCE BLOCK CHECK: Skip source blocks in equilibrium (lakes, pools, oceans)
        // This is a major optimization - stable water bodies don't need constant recalculation
        if (state.isSource() && isStableSourceBlock(level, pos, state)) {
            // CRITICAL: For Flowing Fluids, verify this source can't still spread
            if (FlowingFluidsIntegration.isFlowingFluidsLoaded()) {
                // Check if this source can still spread to adjacent blocks
                if (canSourceBlockSpread(level, pos, state)) {
                    // It can spread - remove from cache and process normally
                    stableSourceBlockCache.remove(pos);
                    LOGGER.debug("Flowing Fluids: Source at {} can still spread - processing", pos);
                } else {
                    // Truly stable - safe to skip
                    heavyFlowCacheHits.incrementAndGet();
                    fluidTimeUsedThisTick += System.nanoTime() - startTime;
                    return;
                }
            } else {
                // Vanilla behavior - safe to skip stable sources
                heavyFlowCacheHits.incrementAndGet();
                fluidTimeUsedThisTick += System.nanoTime() - startTime;
                return; // Skip - stable source block in equilibrium
            }
        }
        
        // ENTITY PROTECTION CHECK: Skip fluid processing if entities nearby need CPU time
        if (!EntityProtectionSystem.shouldAllowFluidProcessing(level, pos)) {
            updatesBlockedForEntities.incrementAndGet();
            // Defer this update instead of dropping it
            deferUpdate(level, pos, state, delay, UpdatePriority.LOW);
            fluidTimeUsedThisTick += System.nanoTime() - startTime;
            return; // Skip to protect entities
        }
        
        // EDGE FLUID SKIP: Aggressively skip edge fluids (level 1-2) that rarely flow
        if (shouldSkipEdgeFluid(level, pos, state)) {
            edgeFluidsSkipped.incrementAndGet();
            updatePerformanceCounters(UpdatePriority.SKIP);
            fluidTimeUsedThisTick += System.nanoTime() - startTime;
            return; // Skip entirely - major CPU savings for mobs
        }
        
        // TIME BUDGET CHECK - If we've used our time budget, defer ALL non-critical updates
        // This ensures mobs and other game systems have enough CPU time
        if (isTimeBudgetExceeded()) {
            UpdatePriority quickPriority = getQuickPriority(level, pos, state);
            if (quickPriority != UpdatePriority.CRITICAL) {
                // Defer to next tick - don't even spend time calculating
                deferUpdate(level, pos, state, delay, quickPriority);
                updatesSkippedDueToTimeBudget.incrementAndGet();
                return;
            }
        }
        
        // Update configuration values from config system
        updateConfigurationFromSettings();
        updateAdaptiveThrottling();
        
        // Deduplication check - avoid scheduling same position multiple times
        if (isRecentlyScheduled(pos)) {
            return;
        }
        
        totalScheduled.incrementAndGet();
        
        // Determine update priority based on FLOW LIKELIHOOD
        UpdatePriority priority = determineUpdatePriority(level, pos, state);
        
        // SKIP priority = edge fluids that won't flow further - don't process at all
        if (priority == UpdatePriority.SKIP) {
            updatePerformanceCounters(priority);
            fluidTimeUsedThisTick += System.nanoTime() - startTime;
            return; // Skip entirely - saves CPU for mobs
        }
        
        // Check if we should defer this update based on server load
        if (shouldDeferUpdate(priority)) {
            deferUpdate(level, pos, state, delay, priority);
            fluidTimeUsedThisTick += System.nanoTime() - startTime;
            return;
        }
        
        int adjustedDelay = calculateOptimizedDelay(level, pos, state, delay, priority);
        
        // Log the scheduling decision for debugging
        logSchedulingDecision(pos, priority, delay, adjustedDelay);
        
        // Apply the scheduled tick with optimized delay
        level.scheduleTick(pos, state.getType(), adjustedDelay);
        markAsScheduled(pos);
        
        // Cache this flow state for future optimization
        cacheFlowState(pos, state, priority);
        
        // Track heavy flow zones for zone-based optimization
        trackHeavyFlowZone(level, pos, state);
        
        // OCEAN/RIVER REPLENISHMENT: Schedule faster refill for non-source water in ocean/river biomes
        // This helps reduce lag from Flowing Fluids calculating water holes by slowly refilling them
        // IMPORTANT: Only affects ocean and river biomes - preserves finite water system elsewhere
        if (OceanRiverWaterReplenishment.shouldAccelerateFluidTick(level, pos, state)) {
            OceanRiverWaterReplenishment.scheduleReplenishment(level, pos, state);
        }
        
        // Update performance counters
        updatePerformanceCounters(priority);
        
        long elapsed = System.nanoTime() - startTime;
        totalProcessingTimeNanos.addAndGet(elapsed);
        fluidTimeUsedThisTick += elapsed;
    }
    
    /**
     * Check if this is a stable cached flow that doesn't need recalculation
     * Heavy flow areas (waterfalls, constant sources) are cached for efficiency
     */
    private static boolean isStableCachedFlow(BlockPos pos, FluidState state) {
        StableFlowEntry cached = stableFlowCache.get(pos);
        if (cached == null) return false;
        
        // Check if the flow state matches cached state - return true if stable
        return cached.fluidLevel == state.getAmount() && 
               cached.isSource == state.isSource() &&
               cached.stabilityCount >= STABILITY_THRESHOLD;
    }
    
    // Cache for stable source blocks - tracks source blocks in equilibrium
    private static final Map<BlockPos, Long> stableSourceBlockCache = new ConcurrentHashMap<>();
    private static final int STABLE_SOURCE_CACHE_MAX_SIZE = 30000;
    
    /**
     * Check if a source block is stable (surrounded by other sources or solid blocks)
     * Stable source blocks in lakes/pools/oceans don't need constant recalculation
     */
    private static boolean isStableSourceBlock(ServerLevel level, BlockPos pos, FluidState state) {
        if (!state.isSource()) return false;
        
        // Check if we've already confirmed this source is stable recently
        Long lastCheck = stableSourceBlockCache.get(pos);
        if (lastCheck != null) {
            long ticksSinceCheck = currentServerTick - lastCheck;
            if (ticksSinceCheck < 100) { // Recheck every 5 seconds (100 ticks)
                return true; // Already confirmed stable
            }
        }
        
        // Check all horizontal neighbors - must be sources or solid blocks
        int stableNeighbors = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (!level.isInWorldBounds(neighbor)) {
                stableNeighbors++; // World edge counts as stable
                continue;
            }
            
            FluidState neighborFluid = level.getFluidState(neighbor);
            if (neighborFluid.isSource() && neighborFluid.getType().isSame(state.getType())) {
                stableNeighbors++; // Adjacent source of same type
            } else if (isBlockSolid(level, neighbor)) {
                stableNeighbors++; // Solid block
            }
        }
        
        // Check below - must be solid or source
        BlockPos below = pos.below();
        boolean belowStable;
        if (level.isInWorldBounds(below)) {
            FluidState belowFluid = level.getFluidState(below);
            belowStable = isBlockSolid(level, below) || 
                         (belowFluid.isSource() && belowFluid.getType().isSame(state.getType()));
        } else {
            belowStable = true; // Below world = stable
        }
        
        // Stable if all 4 horizontal neighbors are stable AND below is stable
        if (stableNeighbors >= 4 && belowStable) {
            // Cache this stable source block
            if (stableSourceBlockCache.size() < STABLE_SOURCE_CACHE_MAX_SIZE) {
                stableSourceBlockCache.put(pos.immutable(), currentServerTick);
            }
            return true;
        }
        
        // Not stable - remove from cache if present
        stableSourceBlockCache.remove(pos);
        return false;
    }
    
    /**
     * Check if a block is solid (blocks fluid flow)
     * Uses non-deprecated method to check if block prevents fluid passage
     */
    private static boolean isBlockSolid(ServerLevel level, BlockPos pos) {
        var blockState = level.getBlockState(pos);
        // A block is "solid" for fluid purposes if it's not air, not a fluid, and has collision
        // Using getCollisionShape check instead of deprecated blocksMotion()
        return !blockState.isAir() && blockState.getFluidState().isEmpty() && 
               !blockState.getCollisionShape(level, pos).isEmpty();
    }
    
    /**
     * Aggressively skip edge fluids (level 1-2) that are unlikely to flow further
     * Enhanced with DORMANT state - thin layers stop calculating after being stable
     * and only wake up when neighbors change
     */
    private static boolean shouldSkipEdgeFluid(ServerLevel level, BlockPos pos, FluidState state) {
        if (state.isEmpty() || state.isSource()) return false;
        
        int fluidLevel = state.getAmount();
        
        // Only skip edge fluids (level 1-2)
        if (fluidLevel > 2) return false;
        
        // Calculate current neighbor hash to detect changes
        int currentNeighborHash = calculateNeighborHash(level, pos, state);
        
        // Check edge fluid cache
        EdgeFluidEntry edgeEntry = edgeFluidCache.get(pos);
        if (edgeEntry != null) {
            // DORMANT CHECK: If this fluid is dormant, only wake if neighbors changed
            if (edgeEntry.isDormant) {
                // Check if neighbors have changed
                if (currentNeighborHash == edgeEntry.lastNeighborHash) {
                    // Neighbors haven't changed - keep dormant
                    // Only do periodic sanity check every DORMANT_RECHECK_INTERVAL ticks
                    if (currentServerTick - edgeEntry.lastCheckTick < DORMANT_RECHECK_INTERVAL) {
                        return true; // SKIP - still dormant, no neighbor changes
                    }
                }
                // Neighbors changed or time for periodic check - wake up
                dormantEdgeFluids.decrementAndGet();
                LOGGER.debug("Waking dormant edge fluid at {} - neighbor changed or periodic check", pos);
            }
            
            // If stable for multiple checks but not dormant yet
            if (edgeEntry.stableCount >= EDGE_SKIP_THRESHOLD && !edgeEntry.isDormant) {
                // Check if level is same as before (no smoothing happening)
                if (edgeEntry.fluidLevel == fluidLevel && 
                    currentNeighborHash == edgeEntry.lastNeighborHash) {
                    // Level unchanged and neighbors unchanged - increment stable count
                    int newStableCount = edgeEntry.stableCount + 1;
                    
                    // Check if should go dormant
                    if (newStableCount >= DORMANT_THRESHOLD) {
                        // GO DORMANT - stop calculating this thin layer
                        edgeFluidCache.put(pos.immutable(), new EdgeFluidEntry(
                            fluidLevel, newStableCount, currentServerTick,
                            edgeEntry.canFlowDown, edgeEntry.canFlowHorizontal,
                            true, currentServerTick, currentNeighborHash
                        ));
                        dormantEdgeFluids.incrementAndGet();
                        LOGGER.debug("Edge fluid at {} going DORMANT after {} stable checks", pos, newStableCount);
                        return true; // SKIP - now dormant
                    }
                    
                    // Not dormant yet but stable - update cache and skip
                    edgeFluidCache.put(pos.immutable(), new EdgeFluidEntry(
                        fluidLevel, newStableCount, currentServerTick,
                        edgeEntry.canFlowDown, edgeEntry.canFlowHorizontal,
                        false, 0, currentNeighborHash
                    ));
                    return true; // SKIP - stable but not dormant yet
                }
            }
        }
        
        // Check if this edge fluid can actually flow anywhere
        boolean canFlowDown = canFluidFlowDownward(level, pos, state);
        boolean canFlowHorizontal = canFluidFlowHorizontally(level, pos, state);
        
        // Update edge fluid cache
        if (!canFlowDown && !canFlowHorizontal) {
            // This edge fluid is blocked - cache it for skipping
            int newStableCount = (edgeEntry != null) ? edgeEntry.stableCount + 1 : 1;
            edgeFluidCache.put(pos.immutable(), new EdgeFluidEntry(
                fluidLevel, newStableCount, currentServerTick, false, false,
                false, 0, currentNeighborHash
            ));
            
            // Skip if it's been stable long enough
            return newStableCount >= EDGE_SKIP_THRESHOLD;
        } else {
            // Edge fluid can flow - reset stable count since it might change
            edgeFluidCache.put(pos.immutable(), new EdgeFluidEntry(
                fluidLevel, 0, currentServerTick, canFlowDown, canFlowHorizontal,
                false, 0, currentNeighborHash
            ));
            return false;
        }
    }
    
    /**
     * Calculate a hash of neighbor fluid states to detect changes
     * This allows dormant thin layers to wake up when neighbors change
     */
    private static int calculateNeighborHash(ServerLevel level, BlockPos pos, FluidState state) {
        int hash = 17;
        
        // Include current fluid's own state in the hash for complete tracking
        hash = 31 * hash + state.getAmount();
        hash = 31 * hash + (state.isSource() ? 1 : 0);
        
        // Hash all 6 neighbors
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (level.isInWorldBounds(neighbor)) {
                FluidState neighborState = level.getFluidState(neighbor);
                hash = 31 * hash + neighborState.getAmount();
                hash = 31 * hash + (neighborState.isEmpty() ? 0 : neighborState.getType().hashCode() % 100);
            }
        }
        
        // Also include the block below (important for flow direction)
        BlockPos below = pos.below();
        if (level.isInWorldBounds(below)) {
            hash = 31 * hash + (level.getBlockState(below).isAir() ? 1 : 0);
        }
        
        return hash;
    }
    
    /**
     * Notify that a fluid has changed at a position
     * This wakes up any dormant edge fluids nearby
     */
    public static void notifyFluidChanged(ServerLevel level, BlockPos pos) {
        // Set this position as an origin for fluid changes
        fluidChangeOrigins.put(pos.immutable(), pos.immutable());
        
        // Wake up dormant edge fluids in adjacent positions
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            EdgeFluidEntry entry = edgeFluidCache.get(neighbor);
            if (entry != null && entry.isDormant) {
                // Wake it up by marking for recheck
                edgeFluidCache.put(neighbor.immutable(), new EdgeFluidEntry(
                    entry.fluidLevel, entry.stableCount, 0, // Reset lastCheckTick to force recheck
                    entry.canFlowDown, entry.canFlowHorizontal,
                    false, 0, 0 // No longer dormant
                ));
                dormantEdgeFluids.decrementAndGet();
            }
            // Propagate origin to neighbors to track the source of changes
            fluidChangeOrigins.put(neighbor.immutable(), pos.immutable());
        }
    }
    
    /**
     * Get count of dormant edge fluids for monitoring
     */
    public static int getDormantEdgeFluidsCount() {
        return dormantEdgeFluids.get();
    }
    
    /**
     * Check if fluid can flow horizontally to any adjacent block
     */
    private static boolean canFluidFlowHorizontally(ServerLevel level, BlockPos pos, FluidState state) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(dir);
            if (!level.isInWorldBounds(adjacent)) continue;
            
            FluidState adjState = level.getFluidState(adjacent);
            if (adjState.isEmpty()) return true; // Can flow into empty space
            if (adjState.getType().isSame(state.getType()) && adjState.getAmount() < state.getAmount()) {
                return true; // Can flow into lower fluid
            }
        }
        return false;
    }
    
    /**
     * Check if fluid can flow anywhere (downward or horizontally)
     * Critical for determining if a fluid is truly stable
     */
    private static boolean canFluidFlowAnywhere(ServerLevel level, BlockPos pos, FluidState state) {
        if (state.isEmpty()) return false;
        
        // Check downward flow
        if (canFluidFlowDownward(level, pos, state)) {
            return true;
        }
        
        // Check horizontal flow
        if (canFluidFlowHorizontally(level, pos, state)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a source block can spread to adjacent positions
     * Used for Flowing Fluids to determine if a source is truly stable
     */
    private static boolean canSourceBlockSpread(ServerLevel level, BlockPos pos, FluidState state) {
        // Check downward spread
        BlockPos below = pos.below();
        if (level.isInWorldBounds(below)) {
            FluidState belowFluid = level.getFluidState(below);
            if (belowFluid.isEmpty() || 
                (belowFluid.getType().isSame(state.getType()) && !belowFluid.isSource())) {
                return true;
            }
        }
        
        // Check horizontal spread
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (!level.isInWorldBounds(neighbor)) continue;
            
            var neighborBlock = level.getBlockState(neighbor);
            FluidState neighborFluid = neighborBlock.getFluidState();
            
            if (neighborBlock.isAir() || (neighborFluid.isEmpty() && !isBlockSolid(level, neighbor))) {
                return true;
            }
            
            if (neighborFluid.getType().isSame(state.getType()) && 
                !neighborFluid.isSource() && neighborFluid.getAmount() < 8) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Cache flow state for optimization
     */
    private static void cacheFlowState(BlockPos pos, FluidState state, UpdatePriority priority) {
        if (stableFlowCache.size() >= STABLE_FLOW_CACHE_MAX_SIZE) return;
        
        StableFlowEntry existing = stableFlowCache.get(pos);
        int stabilityCount = 1;
        
        if (existing != null) {
            // Check if state matches - if so, increment stability
            if (existing.fluidLevel == state.getAmount() && existing.isSource == state.isSource()) {
                stabilityCount = existing.stabilityCount + 1;
            }
        }
        
        stableFlowCache.put(pos.immutable(), new StableFlowEntry(
            state.getAmount(),
            state.isSource(),
            priority,
            stabilityCount,
            currentServerTick
        ));
    }
    
    /**
     * Quick priority check for time budget enforcement (minimal computation)
     * Uses state for flow-aware priority - source blocks and high-level fluids get higher priority
     */
    private static UpdatePriority getQuickPriority(ServerLevel level, BlockPos pos, FluidState state) {
        // Source blocks are always high priority
        if (state.isSource()) {
            double distSq = getDistanceToNearestPlayer(level, pos);
            if (distSq <= CRITICAL_DISTANCE * CRITICAL_DISTANCE) {
                return UpdatePriority.CRITICAL;
            }
            return UpdatePriority.HIGH;
        }
        
        // High-level fluids (5+) need faster processing - they're actively flowing
        int fluidLevel = state.getAmount();
        if (fluidLevel >= 5) {
            double distSq = getDistanceToNearestPlayer(level, pos);
            if (distSq <= CRITICAL_DISTANCE * CRITICAL_DISTANCE) {
                return UpdatePriority.CRITICAL;
            }
            return UpdatePriority.HIGH;
        }
        
        // Edge fluids (1-2) can be safely deferred
        if (fluidLevel <= 2) {
            return UpdatePriority.LOW;
        }
        
        // Medium level fluids - check player distance
        double distSq = getDistanceToNearestPlayer(level, pos);
        if (distSq <= CRITICAL_DISTANCE * CRITICAL_DISTANCE) {
            return UpdatePriority.CRITICAL;
        }
        return UpdatePriority.NORMAL;
    }
    
    /**
     * Process deferred fluid updates during quieter server periods
     * Call this from server tick handler when TPS is healthy
     */
    public static void processDeferredUpdates(ServerLevel level, int maxToProcess) {
        if (deferredQueue.isEmpty()) return;
        
        double currentTPS = PerformanceMonitor.getAverageTPS();
        if (currentTPS < 15.0) {
            // Server is struggling, don't process deferred updates
            return;
        }
        
        int processed = 0;
        while (!deferredQueue.isEmpty() && processed < maxToProcess) {
            PrioritizedFluidTick tick = deferredQueue.poll();
            if (tick != null && level.isInWorldBounds(tick.pos())) {
                FluidState currentState = level.getFluidState(tick.pos());
                if (!currentState.isEmpty()) {
                    level.scheduleTick(tick.pos(), currentState.getType(), tick.delay());
                    processed++;
                }
            }
        }
        
        if (processed > 0) {
            LOGGER.debug("Processed {} deferred fluid updates, {} remaining", processed, deferredQueue.size());
        }
    }
    
    /**
     * Update configuration values from the optimization config system
     */
    private static void updateConfigurationFromSettings() {
        if (FlowingFluidsOptimizationConfig.isFlowingFluidsIntegrationEnabled()) {
            var settings = FlowingFluidsOptimizationConfig.getCurrentOptimizationSettings();
            CRITICAL_DISTANCE = settings.criticalDistance();
            NORMAL_DISTANCE = settings.normalDistance();
            BASE_DELAY = settings.delayMultiplier();
        }
    }
    
    /**
     * Update adaptive throttling based on current server performance
     */
    private static void updateAdaptiveThrottling() {
        double currentTPS = PerformanceMonitor.getAverageTPS();
        lastKnownTPS = currentTPS;
        
        // Adaptive throttling: adjust max updates based on TPS, more aggressive
        if (currentTPS >= 19.0) {
            adaptiveMaxUpdatesPerTick = 1600; // High TPS - significantly more updates
        } else if (currentTPS >= 15.0) {
            adaptiveMaxUpdatesPerTick = 800;  // Normal TPS - increased limit
        } else if (currentTPS >= 8.0) { // Lowered threshold for moderate throttling
            adaptiveMaxUpdatesPerTick = 300;  // Low TPS - reduce updates more aggressively
        } else {
            adaptiveMaxUpdatesPerTick = 80;   // Critical TPS - minimal updates, more aggressive
        }
    }
    
    /**
     * Check if position was recently scheduled to avoid duplicates
     */
    private static boolean isRecentlyScheduled(BlockPos pos) {
        Long lastScheduled = recentlyScheduled.get(pos);
        if (lastScheduled == null) return false;
        
        long currentTime = System.currentTimeMillis();
        return (currentTime - lastScheduled) < (DEDUP_COOLDOWN_TICKS * 50); // 50ms per tick
    }
    
    /**
     * Mark position as recently scheduled
     */
    private static void markAsScheduled(BlockPos pos) {
        recentlyScheduled.put(pos.immutable(), System.currentTimeMillis());
        
        // Clean up old entries periodically
        if (recentlyScheduled.size() > 10000) {
            long cutoff = System.currentTimeMillis() - 1000; // 1 second
            recentlyScheduled.entrySet().removeIf(entry -> entry.getValue() < cutoff);
        }
    }
    
    /**
     * Determine if update should be deferred based on priority and server load
     */
    private static boolean shouldDeferUpdate(UpdatePriority priority) {
        // Never defer critical updates
        if (priority == UpdatePriority.CRITICAL) return false;
        
        // VERY AGGRESSIVE DEFERRAL: Defer almost everything under any load
        if (lastKnownTPS < 19.0) {
            // Only process HIGH priority updates, defer everything else
            return priority != UpdatePriority.HIGH;
        }
        
        // Check if we've hit the adaptive limit
        if (totalScheduled.get() > adaptiveMaxUpdatesPerTick) {
            return true;
        }
        
        return priority == UpdatePriority.LOW;
    }
    
    /**
     * Defer an update for later processing with world-aware priority adjustment
     * Uses level to check dimension, loaded chunks, and nearby activity for smarter deferral
     */
    private static void deferUpdate(ServerLevel level, BlockPos pos, FluidState state, int delay, UpdatePriority priority) {
        // World-aware priority adjustment
        int priorityValue = switch (priority) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 3;
            case SKIP -> 4;
        };
        
        // WORLD-AWARE DEFERRAL: Adjust priority based on world state
        // Check if chunk is loaded and has player activity
        if (level.isLoaded(pos)) {
            // Boost priority for chunks with nearby players
            double playerDist = getDistanceToNearestPlayer(level, pos);
            if (playerDist <= NORMAL_DISTANCE && priorityValue > 1) {
                priorityValue = Math.max(1, priorityValue - 1); // Boost priority
            }
            
            // Check if this is in an active area (redstone, entities)
            if (level.hasNeighborSignal(pos) && priorityValue > 0) {
                priorityValue = Math.max(0, priorityValue - 1); // Redstone-active areas get priority
            }
        } else {
            // Unloaded chunk - lower priority significantly
            priorityValue = Math.min(4, priorityValue + 1);
        }
        
        // Dimension-aware deferral: Nether fluids (lava) need faster processing
        if (level.dimension() == net.minecraft.world.level.Level.NETHER && 
            state.getType() == Fluids.LAVA) {
            priorityValue = Math.max(0, priorityValue - 1); // Boost lava priority in Nether
        }
        
        deferredQueue.offer(new PrioritizedFluidTick(
            pos.immutable(),
            state.getType(),
            delay,
            priorityValue,
            System.currentTimeMillis()
        ));
        
        deferredUpdates.incrementAndGet();
        FlowingFluidsPerformanceMonitor.recordFlowingFluidsUpdate(
            FlowingFluidsPerformanceMonitor.UpdateType.DEFERRED);
        
        LOGGER.debug("Deferred fluid tick at {} in {} with priority {} (adjusted)", 
            pos, level.dimension().location(), priority);
    }
    
    /**
     * Determines the priority of a fluid update based on FLOW LIKELIHOOD
     * High-level actively flowing fluids get priority, edge fluids get deprioritized
     * Integrates Flowing Fluids critical checks for enhanced mod compatibility
     */
    private static UpdatePriority determineUpdatePriority(ServerLevel level, BlockPos pos, FluidState state) {
        int fluidLevel = state.getAmount();
        double distanceToPlayer = getDistanceToNearestPlayer(level, pos);
        
        // FLOWING FLUIDS INTEGRATION: Check for critical Flowing Fluids updates first
        // This ensures pressure systems, edge flows, and finite fluids are properly prioritized
        if (FlowingFluidsIntegration.isFlowingFluidsLoaded() && isFlowingFluidsCritical(level, pos, state)) {
            // Flowing Fluids critical updates get boosted priority
            if (distanceToPlayer <= CRITICAL_DISTANCE) {
                return UpdatePriority.CRITICAL;
            }
            return UpdatePriority.HIGH;
        }
        
        // FLOW LIKELIHOOD SCORING:
        // - Source blocks (level 8): Always active, highest priority
        // - High level (6-7): Very likely to flow, high priority
        // - Medium level (4-5): Moderately likely to flow
        // - Low level (2-3): Less likely to flow, lower priority
        // - Edge level (1): Rarely flows further, lowest priority
        
        // Source blocks are always critical when near players
        if (state.isSource()) {
            if (distanceToPlayer <= CRITICAL_DISTANCE) {
                return UpdatePriority.CRITICAL;
            }
            return UpdatePriority.HIGH;
        }
        
        // Edge fluids (level 1-2) - LOWEST priority, rarely flow further
        if (fluidLevel <= 2) {
            // Only process if VERY close to player AND can actually flow
            if (distanceToPlayer <= 8 && canFluidFlowDownward(level, pos, state)) {
                return UpdatePriority.LOW;
            }
            // Skip edge fluids entirely if not near player
            return UpdatePriority.SKIP;
        }
        
        // Low level fluids (3-4) - low priority unless actively flowing
        if (fluidLevel <= 4) {
            if (distanceToPlayer <= CRITICAL_DISTANCE && canFluidFlow(level, pos, state)) {
                return UpdatePriority.NORMAL;
            }
            if (distanceToPlayer <= NORMAL_DISTANCE && canFluidFlowDownward(level, pos, state)) {
                return UpdatePriority.LOW;
            }
            return UpdatePriority.SKIP;
        }
        
        // High level fluids (5-7) - these are actively flowing, prioritize
        if (distanceToPlayer <= CRITICAL_DISTANCE) {
            return UpdatePriority.CRITICAL;
        }
        
        // Check if this high-level fluid can actually flow somewhere
        if (canFluidFlow(level, pos, state)) {
            if (distanceToPlayer <= NORMAL_DISTANCE) {
                return UpdatePriority.HIGH;
            }
            return UpdatePriority.NORMAL;
        }
        
        // High level but blocked - still process but lower priority
        return UpdatePriority.LOW;
    }
    
    /**
     * Check if fluid can flow downward (most important flow direction)
     */
    private static boolean canFluidFlowDownward(ServerLevel level, BlockPos pos, FluidState state) {
        BlockPos below = pos.below();
        if (!level.isInWorldBounds(below)) return false;
        
        FluidState belowState = level.getFluidState(below);
        // Can flow down if below is empty or has lower fluid
        return belowState.isEmpty() || 
               (belowState.getType().isSame(state.getType()) && belowState.getAmount() < 8);
    }
    
    /**
     * Calculates optimized delay based on priority, server conditions, and flow-aware analysis
     * Uses level/pos/state for intelligent delay calculation based on fluid dynamics
     */
    private static int calculateOptimizedDelay(ServerLevel level, BlockPos pos, FluidState state, 
                                            int originalDelay, UpdatePriority priority) {
        int baseDelay = switch (priority) {
            case CRITICAL -> Math.min(originalDelay, 1); // Process immediately
            case HIGH -> Math.min(originalDelay, 2);     // Fast processing
            case NORMAL -> Math.min(originalDelay * 2, BASE_DELAY); // Moderate delay
            case LOW -> Math.min(originalDelay * MAX_DELAY_MULTIPLIER, BASE_DELAY * 3); // Significant delay
            case SKIP -> originalDelay * 100; // Should not reach here
        };
        
        // FLOW-AWARE DELAY ADJUSTMENT: Analyze fluid dynamics for smarter delays
        
        // Fluids with pressure from above need faster processing
        if (hasFluidPressure(level, pos, state)) {
            baseDelay = Math.max(1, baseDelay / 2); // Halve delay for pressurized fluids
        }
        
        // Source blocks adjacent to empty spaces need immediate attention
        if (state.isSource() && canFluidFlow(level, pos, state)) {
            baseDelay = Math.min(baseDelay, 2);
        }
        
        // Waterfall detection: vertical flow chains get expedited
        if (canFluidFlowDownward(level, pos, state)) {
            BlockPos above = pos.above();
            if (level.isInWorldBounds(above)) {
                FluidState aboveState = level.getFluidState(above);
                if (!aboveState.isEmpty() && aboveState.getType().isSame(state.getType())) {
                    // Part of a waterfall - expedite
                    baseDelay = Math.max(1, baseDelay - 1);
                }
            }
        }
        
        // Dimension-specific adjustments
        if (level.dimension() == net.minecraft.world.level.Level.NETHER) {
            // Lava in Nether flows faster
            if (state.getType() == Fluids.LAVA) {
                baseDelay = Math.max(1, baseDelay - 1);
            }
        }
        
        // TPS-aware delay scaling
        if (lastKnownTPS < 10.0) {
            baseDelay = baseDelay * 2; // Double delay under heavy load
        } else if (lastKnownTPS >= 19.0) {
            baseDelay = Math.max(1, baseDelay - 1); // Reduce delay when server is healthy
        }
        
        return baseDelay;
    }
    
    /**
     * Check if this is a critical Flowing Fluids update
     * Used by determineUpdatePriority for enhanced Flowing Fluids integration
     */
    private static boolean isFlowingFluidsCritical(ServerLevel level, BlockPos pos, FluidState state) {
        // Check for pressure systems (Flowing Fluids feature)
        if (hasFluidPressure(level, pos, state)) {
            return true;
        }
        
        // Check for edge flow behavior in visible areas
        if (isEdgeFlowing(level, pos, state) && getDistanceToNearestPlayer(level, pos) <= NORMAL_DISTANCE) {
            return true;
        }
        
        // Check for finite fluid preservation
        return shouldPreserveFiniteFluid(level, pos, state);
    }
    
    /**
     * Enhanced flow detection with Flowing Fluids awareness
     */
    private static boolean canFluidFlow(ServerLevel level, BlockPos pos, FluidState state) {
        if (state.isSource()) {
            return true; // Source blocks always need checking
        }
        
        // Check horizontal flow possibilities
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(dir);
            FluidState adjacentState = level.getFluidState(adjacent);
            if (adjacentState.isEmpty() || 
                (adjacentState.getType().isSame(state.getType()) && adjacentState.getAmount() < state.getAmount())) {
                return true; // Flow possible
            }
        }
        
        // Check downward flow (critical for Flowing Fluids)
        BlockPos below = pos.below();
        if (level.isInWorldBounds(below)) {
            FluidState belowState = level.getFluidState(below);
            if (belowState.isEmpty() && state.getAmount() > 1) {
                return true; // Can flow downward
            }
        }
        
        return false;
    }
    
    /**
     * Check if fluid has pressure from above (Flowing Fluids feature)
     */
    private static boolean hasFluidPressure(ServerLevel level, BlockPos pos, FluidState state) {
        BlockPos above = pos.above();
        if (level.isInWorldBounds(above)) {
            FluidState aboveState = level.getFluidState(above);
            return !aboveState.isEmpty() && aboveState.getType().isSame(state.getType());
        }
        return false;
    }
    
    /**
     * Check if fluid is actively flowing to adjacent blocks
     */
    private static boolean isEdgeFlowing(ServerLevel level, BlockPos pos, FluidState state) {
        if (state.isEmpty() || state.getAmount() <= 1) {
            return false;
        }
        
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(dir);
            FluidState adjacentState = level.getFluidState(adjacent);
            if (adjacentState.isEmpty() || adjacentState.getAmount() < state.getAmount()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Check if finite fluid should be preserved (Flowing Fluids integration)
     */
    private static boolean shouldPreserveFiniteFluid(ServerLevel level, BlockPos pos, FluidState state) {
        if (!FlowingFluidsIntegration.isFlowingFluidsLoaded()) {
            return false;
        }
        
        // Preserve fluids in special biomes
        if (FlowingFluidsAPIIntegration.doesBiomeInfiniteWaterRefill(level, pos)) {
            return true;
        }
        
        // Preserve low-level fluids that might be part of finite systems
        return state.getAmount() <= 2 && state.getType() == Fluids.WATER;
    }
    
    /**
     * Calculate distance to nearest player
     */
    private static double getDistanceToNearestPlayer(ServerLevel level, BlockPos pos) {
        return level.players().stream()
            .mapToDouble(player -> player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
            .min()
            .orElse(Double.MAX_VALUE);
    }
    
    /**
     * Log scheduling decisions for debugging
     * Called after each scheduling decision to track fluid optimization behavior
     */
    private static void logSchedulingDecision(BlockPos pos, UpdatePriority priority, 
                                            int originalDelay, int adjustedDelay) {
        if (LOGGER.isDebugEnabled()) {
            String priorityStr = priority.name();
            String actionStr = adjustedDelay < originalDelay ? "expedited" : 
                              adjustedDelay > originalDelay ? "delayed" : "normal";
            LOGGER.debug("Fluid tick at {} priority: {}, action: {}, delay: {}->{}", 
                        pos, priorityStr, actionStr, originalDelay, adjustedDelay);
        }
    }
    
    /**
     * Update performance counters
     */
    private static void updatePerformanceCounters(UpdatePriority priority) {
        switch (priority) {
            case CRITICAL -> {
                criticalUpdates.incrementAndGet();
                FlowingFluidsPerformanceMonitor.recordFlowingFluidsUpdate(
                    FlowingFluidsPerformanceMonitor.UpdateType.CRITICAL);
            }
            case LOW -> {
                deferredUpdates.incrementAndGet();
                FlowingFluidsPerformanceMonitor.recordFlowingFluidsUpdate(
                    FlowingFluidsPerformanceMonitor.UpdateType.DEFERRED);
            }
            case HIGH, NORMAL -> {
                FlowingFluidsPerformanceMonitor.recordFlowingFluidsUpdate(
                    FlowingFluidsPerformanceMonitor.UpdateType.OPTIMIZED);
            }
            case SKIP -> {
                // SKIP updates are not processed at all - track as skipped
                FlowingFluidsPerformanceMonitor.recordFlowingFluidsUpdate(
                    FlowingFluidsPerformanceMonitor.UpdateType.SKIPPED);
            }
        }
    }
    
    /**
     * Get statistics for testing and monitoring
     */
    public static Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalScheduled", totalScheduled.get());
        stats.put("criticalUpdates", criticalUpdates.get());
        stats.put("deferredUpdates", deferredUpdates.get());
        stats.put("edgeFluidsSkipped", edgeFluidsSkipped.get());
        stats.put("heavyFlowCacheHits", heavyFlowCacheHits.get());
        stats.put("stableFlowCacheSize", stableFlowCache.size());
        stats.put("edgeFluidCacheSize", edgeFluidCache.size());
        stats.put("deferredQueueSize", deferredQueue.size());
        return stats;
    }
    
    /**
     * Get performance statistics
     */
    public static String getPerformanceStats() {
        int total = totalScheduled.get();
        int critical = criticalUpdates.get();
        int deferred = deferredUpdates.get();
        
        double criticalPercent = total > 0 ? (critical * 100.0 / total) : 0;
        double deferredPercent = total > 0 ? (deferred * 100.0 / total) : 0;
        
        return String.format("FluidTickScheduler: %d total, %.1f%% critical, %.1f%% deferred", 
                            total, criticalPercent, deferredPercent);
    }
    
    /**
     * Get comprehensive protection statistics
     */
    public static String getProtectionStats() {
        return String.format("Protection: gameTime=%d skipped, distance=%d skipped, entities=%d blocked, timeBudget=%d exceeded",
            updatesSkippedByGameTime.get(), updatesSkippedByDistance.get(),
            updatesBlockedForEntities.get(), updatesSkippedDueToTimeBudget.get());
    }
    
    /**
     * Get full status summary including all protection systems
     */
    public static String getFullStatusSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(getPerformanceStats()).append("\n");
        sb.append(getProtectionStats()).append("\n");
        sb.append(GameTimeProtection.getStatusSummary()).append("\n");
        sb.append(FluidProcessingDistanceLimit.getStatsSummary()).append("\n");
        sb.append(EntityProtectionSystem.getProtectionStats()).append("\n");
        sb.append(TickTimeProtection.getStatusSummary());
        return sb.toString();
    }
    
    /**
     * Reset performance counters
     */
    public static void resetCounters() {
        totalScheduled.set(0);
        criticalUpdates.set(0);
        deferredUpdates.set(0);
        totalProcessingTimeNanos.set(0);
        updatesSkippedByDistance.set(0);
        updatesSkippedByGameTime.set(0);
        recentlyScheduled.clear();
    }
    
    /**
     * Get the number of deferred updates waiting
     */
    public static int getDeferredQueueSize() {
        return deferredQueue.size();
    }
    
    /**
     * Get adaptive max updates per tick
     */
    public static int getAdaptiveMaxUpdatesPerTick() {
        return adaptiveMaxUpdatesPerTick;
    }
    
    /**
     * Get average processing time per update in microseconds
     */
    public static double getAverageProcessingTimeMicros() {
        long total = totalScheduled.get();
        if (total == 0) return 0;
        return (totalProcessingTimeNanos.get() / 1000.0) / total;
    }
    
    /**
     * Prioritized fluid tick record for deferred processing
     */
    private record PrioritizedFluidTick(
        BlockPos pos,
        net.minecraft.world.level.material.Fluid fluid,
        int delay,
        int priority,
        long scheduledTime
    ) {}
    
    /**
     * Update priority enumeration
     */
    private enum UpdatePriority {
        CRITICAL,  // Must be processed immediately (near players, critical systems)
        HIGH,      // Should be processed quickly (source blocks, high-level active flows)
        NORMAL,    // Standard processing (medium-level flows in visible areas)
        LOW,       // Can be significantly delayed (low-level flows, distant updates)
        SKIP       // Do not process at all (edge fluids level 1-2 that can't flow further)
    }
    
    /**
     * Cache entry for stable flow patterns - reduces recalculation for constant flows
     * Used for waterfalls, pipe outputs, and other heavy constant flow areas
     */
    private record StableFlowEntry(
        int fluidLevel,
        boolean isSource,
        UpdatePriority cachedPriority,
        int stabilityCount,      // How many ticks this has been stable
        long lastUpdateTick
    ) {}
    
    /**
     * Cache entry for edge fluids (level 1-2) that rarely flow
     * Enhanced with dormant state tracking - thin layers go dormant after being stable
     * and only wake up when a neighbor changes
     */
    private record EdgeFluidEntry(
        int fluidLevel,
        int stableCount,         // How many checks this has been stable
        long lastCheckTick,
        boolean canFlowDown,
        boolean canFlowHorizontal,
        boolean isDormant,       // True if this edge fluid is dormant (stopped calculating)
        long dormantSinceTick,   // When this fluid went dormant
        int lastNeighborHash     // Hash of neighbor states to detect changes
    ) {}
    
    /**
     * Heavy flow zone tracking for areas with constant massive water flow
     * Allows entire zones to be cached and processed efficiently
     */
    private record HeavyFlowZone(
        long chunkKey,           // Encoded chunk position
        int activeSourceCount,   // Number of active source blocks
        int heavyFlowCount,      // Number of heavy flow blocks
        double averageFlowLevel, // Average fluid level in zone
        long lastUpdateTick,
        boolean isWaterfall      // True if vertical flow dominates
    ) {}
    
    /**
     * Track heavy flow zones - areas with constant massive fluid flow
     * This allows entire zones to be cached and processed more efficiently
     */
    private static void trackHeavyFlowZone(ServerLevel level, BlockPos pos, FluidState state) {
        if (state.isEmpty()) return;
        
        // Encode chunk position as key
        long chunkKey = ((long) (pos.getX() >> 4) << 32) | (pos.getZ() >> 4 & 0xFFFFFFFFL);
        
        HeavyFlowZone existing = heavyFlowZones.get(chunkKey);
        
        // Determine if this is a waterfall (vertical flow)
        boolean isWaterfall = canFluidFlowDownward(level, pos, state) && hasFluidPressure(level, pos, state);
        
        if (existing != null) {
            // Update existing zone
            int newSourceCount = existing.activeSourceCount() + (state.isSource() ? 1 : 0);
            int newHeavyCount = existing.heavyFlowCount() + (state.getAmount() >= 6 ? 1 : 0);
            double newAvgLevel = (existing.averageFlowLevel() * existing.heavyFlowCount() + state.getAmount()) 
                                 / (existing.heavyFlowCount() + 1);
            boolean updatedWaterfall = existing.isWaterfall() || isWaterfall;
            
            heavyFlowZones.put(chunkKey, new HeavyFlowZone(
                chunkKey, newSourceCount, newHeavyCount, newAvgLevel,
                currentServerTick, updatedWaterfall
            ));
        } else {
            // Create new zone entry if this is heavy flow
            if (state.getAmount() >= 6 || state.isSource()) {
                heavyFlowZones.put(chunkKey, new HeavyFlowZone(
                    chunkKey,
                    state.isSource() ? 1 : 0,
                    state.getAmount() >= 6 ? 1 : 0,
                    state.getAmount(),
                    currentServerTick,
                    isWaterfall
                ));
            }
        }
        
        // Limit cache size
        if (heavyFlowZones.size() > 1000) {
            // Evict oldest entries
            long cutoff = currentServerTick - 50;
            heavyFlowZones.entrySet().removeIf(e -> e.getValue().lastUpdateTick() < cutoff);
        }
    }
    
    /**
     * Check if position is in a known heavy flow zone for optimization decisions
     */
    public static boolean isInHeavyFlowZone(BlockPos pos) {
        long chunkKey = ((long) (pos.getX() >> 4) << 32) | (pos.getZ() >> 4 & 0xFFFFFFFFL);
        HeavyFlowZone zone = heavyFlowZones.get(chunkKey);
        return zone != null && zone.heavyFlowCount() >= 5;
    }
    
    /**
     * Get cache statistics for monitoring
     */
    public static String getCacheStats() {
        return String.format("Caches: stableFlow=%d, edgeFluid=%d (dormant=%d), heavyZones=%d | Skipped: edge=%d, cacheHit=%d",
            stableFlowCache.size(), edgeFluidCache.size(), dormantEdgeFluids.get(), heavyFlowZones.size(),
            edgeFluidsSkipped.get(), heavyFlowCacheHits.get());
    }
    
    /**
     * Get edge fluid skip count for performance monitoring
     */
    public static int getEdgeFluidsSkipped() {
        return edgeFluidsSkipped.get();
    }
    
    /**
     * Get heavy flow cache hits for performance monitoring
     */
    public static int getHeavyFlowCacheHits() {
        return heavyFlowCacheHits.get();
    }
    
    /**
     * Clear all caches (useful for world unload or config changes)
     */
    public static void clearAllCaches() {
        stableFlowCache.clear();
        edgeFluidCache.clear();
        heavyFlowZones.clear();
        edgeFluidsSkipped.set(0);
        heavyFlowCacheHits.set(0);
    }
    
    /**
     * Check if a position is within the allowed propagation radius from its origin
     */
    private static boolean isWithinPropagationRadius(BlockPos pos) {
        BlockPos origin = fluidChangeOrigins.get(pos);
        if (origin == null) {
            // If no origin is set, allow processing (likely an initial change)
            fluidChangeOrigins.put(pos.immutable(), pos.immutable());
            return true;
        }
        
        double distanceSquared = pos.distSqr(origin);
        double maxRadiusSquared = MAX_PROPAGATION_RADIUS * MAX_PROPAGATION_RADIUS;
        return distanceSquared <= maxRadiusSquared;
    }
}
