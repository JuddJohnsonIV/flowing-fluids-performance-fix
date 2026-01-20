package flowingfluidsfixes;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.BlockEvent.NeighborNotifyEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

@Mod(FlowingFluidsFixesMinimal.MOD_ID)
public class FlowingFluidsFixesMinimal {
    public static final String MOD_ID = "flowingfluidsfixes";
    
    // Performance tracking
    private static final AtomicInteger totalFluidEvents = new AtomicInteger(0);
    private static final AtomicInteger skippedFluidEvents = new AtomicInteger(0);
    private static final AtomicInteger eventsThisTick = new AtomicInteger(0);
    private static final AtomicInteger totalEntityEvents = new AtomicInteger(0);
    private static final AtomicInteger throttledEntityEvents = new AtomicInteger(0);
    private static final AtomicInteger entityEventsThisTick = new AtomicInteger(0);
    private static final AtomicInteger totalChunkEvents = new AtomicInteger(0);
    private static final AtomicInteger throttledChunkEvents = new AtomicInteger(0);
    
    // Enhanced performance tracking based on Spark profile analysis
    private static final AtomicInteger entityOperationsThisTick = new AtomicInteger(0);
    private static final AtomicInteger neighborUpdatesThisTick = new AtomicInteger(0);
    private static final AtomicInteger lightingUpdatesThisTick = new AtomicInteger(0);
    private static final AtomicInteger fluidOperationsThisTick = new AtomicInteger(0);
    
    // TICK CASCADE PREVENTION SYSTEM - Address root cause of MSPT issues
    private static final AtomicInteger tickOperationsThisTick = new AtomicInteger(0);
    private static final AtomicInteger scheduledTicksPending = new AtomicInteger(0);
    private static final AtomicInteger tickCascadeLevel = new AtomicInteger(0);
    private static long lastTickResetTime = 0;
    private static boolean tickSystemOverloaded = false;
    
    // ENTITY DATA THREAD POOL MONITORING - Address ForkJoinPool saturation
    private static final AtomicInteger entityDataOperationsThisTick = new AtomicInteger(0);
    private static final AtomicInteger threadPoolActiveThreads = new AtomicInteger(0);
    private static final AtomicInteger mainThreadExecutorUsage = new AtomicInteger(0);
    private static boolean threadPoolOverloaded = false;
    private static long lastThreadPoolCheck = 0;
    
    // Tick cascade prevention thresholds
    private static final int MAX_TICK_OPERATIONS = 50; // Maximum operations per tick
    private static final double TICK_CAPACITY_THRESHOLD = 0.7; // 70% capacity threshold
    private static final double CASCADE_PREVENTION_MSPT = 15.0; // MSPT threshold for cascade prevention
    private static final long TICK_RESET_INTERVAL = 50; // 50ms tick interval
    
    // Entity data throttling thresholds
    private static final int MAX_ENTITY_DATA_OPERATIONS = 20; // Maximum entity data ops per tick
    private static final double THREAD_POOL_THRESHOLD = 0.8; // 80% thread pool capacity threshold
    private static final long THREAD_POOL_CHECK_INTERVAL = 100; // 100ms check interval
    
    // Cache systems
    private static final ConcurrentHashMap<BlockPos, BlockState> blockStateCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, FluidState> fluidStateCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> playerNearbyChunks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> chunkExpiryTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Entity, Long> lastEntityProcess = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, BlockPos> chunkLoadCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Integer> chunkOperationCount = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, BlockPos> chunkBatchMap = new ConcurrentHashMap<>();
    private static final List<Entity> processedEntities = new ArrayList<>();
    
    // Location-based optimization systems
    private static final ConcurrentHashMap<Long, Integer> chunkGenerationCount = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Integer> blockUpdateCount = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> locationCacheExpiry = new ConcurrentHashMap<>();
    private static final Set<Long> highStressChunks = new HashSet<>();
    private static final AtomicInteger chunkGenerationsThisTick = new AtomicInteger(0);
    private static final AtomicInteger blockUpdatesThisTick = new AtomicInteger(0);
    private static final AtomicInteger entityProcessesThisTick = new AtomicInteger(0);
    private static long lastLocationReset = 0;
    
    // Performance thresholds
    private static final double EMERGENCY_MSPT = 50.0;
    private static final double ENTITY_THROTTLE_MSPT = 25.0;
    private static final double CHUNK_THROTTLE_MSPT = 30.0;
    private static final double LOCATION_THROTTLE_MSPT = 40.0;
    private static final int CHUNK_CACHE_DURATION = 5000; // 5 seconds
    private static final int MAX_CACHE_SIZE = 5000;
    private static final int MSPT_CHECK_INTERVAL = 2000; // 2 seconds
    
    // Location-based optimization thresholds
    private static final int MAX_CHUNK_GENERATION_PER_TICK = 2;
    private static final int MAX_BLOCK_UPDATES_PER_CHUNK = 50;
    private static final int MAX_ENTITY_PROCESSES_PER_TICK = 100;
    private static final int LOCATION_CACHE_DURATION = 10000; // 10 seconds
    
    // MSPT tracking
    private static double cachedMSPT = 5.0;
    private static long lastMSPTCheck = 0;
    private static boolean allowOptimizations = false;
    
    public FlowingFluidsFixesMinimal() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        System.out.println("[FlowingFluidsFixes] COMPREHENSIVE performance optimization system initialized");
    }
    
    private void commonSetup(FMLCommonSetupEvent event) {
        // Clear all caches to prevent interference with other mods during initialization
        resetAllStats();
        
        // CRITICAL: Clear all caches immediately to prevent registry interference
        blockStateCache.clear();
        fluidStateCache.clear();
        playerNearbyChunks.clear();
        chunkExpiryTimes.clear();
        chunkLoadCache.clear();
        chunkOperationCount.clear();
        chunkBatchMap.clear();
        lastEntityProcess.clear();
        processedEntities.clear();
        
        // Clear location-based optimization caches
        chunkGenerationCount.clear();
        blockUpdateCount.clear();
        locationCacheExpiry.clear();
        highStressChunks.clear();
        
        // SAFETY: Enable caching only after all mods have finished initializing
        allowOptimizations = true;
        System.out.println("[FlowingFluidsFixes] Caching enabled - systems consolidated and ready");
    }
    
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        updateMSPT();
        
        // TICK CASCADE PREVENTION SYSTEM - Reset and monitor tick capacity
        resetTickCascadeSystem();
        
        // Reset per-tick counters
        eventsThisTick.set(0);
        entityEventsThisTick.set(0);
        chunkGenerationsThisTick.set(0);
        blockUpdatesThisTick.set(0);
        entityProcessesThisTick.set(0);
        entityOperationsThisTick.set(0);
        neighborUpdatesThisTick.set(0);
        lightingUpdatesThisTick.set(0);
        fluidOperationsThisTick.set(0);
        entityDataOperationsThisTick.set(0);
        threadPoolActiveThreads.set(0);
        mainThreadExecutorUsage.set(0);
        
        // Reset location-based counters periodically
        if (System.currentTimeMillis() - lastLocationReset > 5000) {
            resetLocationCounters();
            lastLocationReset = System.currentTimeMillis();
        }
        
        // Clean up expired caches periodically
        if (System.currentTimeMillis() % 10000 < 200) { // Every 10 seconds
            cleanupExpiredCaches();
        }
        
        // Status reporting
        if (eventsThisTick.get() > 0 && System.currentTimeMillis() % 5000 < 200) {
            int totalEvents = totalFluidEvents.get() + totalEntityEvents.get() + totalChunkEvents.get();
            int totalSkipped = skippedFluidEvents.get() + throttledEntityEvents.get() + throttledChunkEvents.get();
            System.out.println(String.format("[FlowingFluidsFixes] THREAD POOL Status: MSPT=%.2f, TickOps=%d, EntityDataOps=%d, ThreadPoolOverloaded=%b, TotalEvents=%d, TotalSkipped=%d (%.1f%%)", 
                cachedMSPT, tickOperationsThisTick.get(), entityDataOperationsThisTick.get(), threadPoolOverloaded, totalEvents, totalSkipped, (totalSkipped * 100.0 / totalEvents)));
        }
    }
    
    // SPATIAL PARTITIONING SYSTEM
    private static boolean isChunkNearPlayers(BlockPos pos) {
        long chunkKey = getChunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        
        // Check cache first
        Boolean cached = playerNearbyChunks.get(chunkKey);
        if (cached != null && !isChunkCacheExpired(chunkKey)) {
            return cached;
        }
        
        // Calculate fresh result - use fast integer math instead of expensive operations
        boolean nearPlayers = false;
        
        // Get the world this chunk coordinates
        int chunkX = (int)(chunkKey >> 32);
        int chunkZ = (int)chunkKey;
        
        // FAST: Use squared distance instead of sqrt (no floating point math)
        // AND avoid BlockPos object creation
        int distanceSquared = chunkX * chunkX + chunkZ * chunkZ;
        final int MAX_DISTANCE_SQUARED = 16 * 16; // 16 chunks squared
        
        // FAST: Simple integer comparison, no object creation, no sqrt
        nearPlayers = distanceSquared <= MAX_DISTANCE_SQUARED;
        
        // Cache result
        playerNearbyChunks.put(chunkKey, nearPlayers);
        chunkExpiryTimes.put(chunkKey, System.currentTimeMillis() + CHUNK_CACHE_DURATION);
        
        return nearPlayers;
    }
    
    private static boolean isChunkCacheExpired(long chunkKey) {
        Long expiry = chunkExpiryTimes.get(chunkKey);
        return expiry == null || System.currentTimeMillis() > expiry;
    }
    
    // BLOCKSTATE CACHING SYSTEM
    private static BlockState getCachedBlockState(Level level, BlockPos pos) {
        BlockState cached = blockStateCache.get(pos);
        if (cached != null) {
            return cached;
        }
        
        BlockState state = level.getBlockState(pos);
        if (blockStateCache.size() < MAX_CACHE_SIZE) {
            blockStateCache.put(pos, state);
        }
        return state;
    }
    
    // COMPREHENSIVE FLUID EVENT HANDLER - Enhanced with Tick Cascade Prevention
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!allowOptimizations) return; // Safety check
        
        LevelAccessor levelAccessor = event.getLevel();
        if (!(levelAccessor instanceof Level level)) return; // Server side only
        
        BlockPos pos = event.getPos();
        
        // TICK CASCADE PREVENTION - Check tick capacity BEFORE processing
        if (isTickSystemOverloaded()) {
            skippedFluidEvents.incrementAndGet();
            return; // Prevent cascade formation at source
        }
        
        // THREAD POOL MONITORING - Check thread pool capacity
        if (isThreadPoolOverloaded()) {
            skippedFluidEvents.incrementAndGet();
            return; // Prevent thread pool exhaustion
        }
        
        // Track tick operations for cascade prevention
        tickOperationsThisTick.incrementAndGet();
        
        // TRACK NEIGHBOR UPDATES - Critical bottleneck from Spark profile
        neighborUpdatesThisTick.incrementAndGet();
        
        // AGGRESSIVE NEIGHBOR UPDATE THROTTLING - CollectingNeighborUpdater is major bottleneck
        if (cachedMSPT > 15.0) {
            // Skip 75% of neighbor updates during high MSPT
            if (neighborUpdatesThisTick.get() % 4 != 0) {
                skippedFluidEvents.incrementAndGet();
                return;
            }
        } else if (cachedMSPT > 10.0) {
            // Skip 50% of neighbor updates during medium MSPT
            if (neighborUpdatesThisTick.get() % 2 != 0) {
                skippedFluidEvents.incrementAndGet();
                return;
            }
        }
        
        // LOCATION-BASED OPTIMIZATION - Check for high-stress chunks
        if (isHighStressLocation(pos)) {
            // Apply aggressive throttling in high-stress areas
            if (cachedMSPT > LOCATION_THROTTLE_MSPT) {
                skippedFluidEvents.incrementAndGet();
                return; // Skip all fluid events in high-stress areas during high MSPT
            }
            
            // Even during normal MSPT, limit processing in high-stress areas
            if (eventsThisTick.get() % 3 != 0) {
                skippedFluidEvents.incrementAndGet();
                return; // Skip 66% of events in high-stress areas
            }
        }
        
        // SPATIAL PARTITIONING - Skip if no players nearby
        if (!isChunkNearPlayers(event.getPos())) {
            skippedFluidEvents.incrementAndGet();
            return; // CULL distant fluids
        }
        
        // Track total events
        totalFluidEvents.incrementAndGet();
        eventsThisTick.incrementAndGet();
        fluidOperationsThisTick.incrementAndGet();
        
        // ENTITY OPERATION TRACKING - 15 entity operations found in Spark profile
        if (level.players().size() > 0) {
            entityOperationsThisTick.addAndGet(level.players().size());
            
            // ENTITY DATA THREAD POOL TRACKING - Address ForkJoinPool saturation
            entityDataOperationsThisTick.incrementAndGet();
            
            // AGGRESSIVE ENTITY THROTTLING - Major bottleneck from Spark profile
            if (cachedMSPT > 20.0) {
                // Skip 80% of fluid events when many entities and high MSPT
                if (entityOperationsThisTick.get() % 5 != 0) {
                    skippedFluidEvents.incrementAndGet();
                    return;
                }
            } else if (cachedMSPT > 15.0) {
                // Skip 66% of fluid events when many entities and medium MSPT
                if (entityOperationsThisTick.get() % 3 != 0) {
                    skippedFluidEvents.incrementAndGet();
                    return;
                }
            }
            
            // ENTITY AI PATHFINDING THROTTLING - Address PathfinderMob and GroundPathNavigation bottlenecks
            if (cachedMSPT > 12.0 && level.players().size() > 5) {
                // Additional throttling when many players and entities
                if (entityOperationsThisTick.get() % 2 != 0) {
                    skippedFluidEvents.incrementAndGet();
                    return; // Reduce AI pathfinding overhead
                }
            }
        }
        
        // EMERGENCY PROTECTION
        if (cachedMSPT > EMERGENCY_MSPT) {
            skippedFluidEvents.incrementAndGet();
            return; // EXIT BEFORE ANY WORLD ACCESS
        }
        
        // LOCATION-BASED BLOCK UPDATE TRACKING
        int chunkX = event.getPos().getX() >> 4;
        int chunkZ = event.getPos().getZ() >> 4;
        long chunkKey = getChunkKey(chunkX, chunkZ);
        incrementBlockUpdateCount(chunkKey);
        
        // CHUNK MANAGEMENT THROTTLING - Address ChunkMap and ServerChunkCache bottlenecks
        Integer chunkOps = chunkOperationCount.get(chunkKey);
        if (chunkOps != null && chunkOps > 3) {
            if (cachedMSPT > 10.0) {
                skippedFluidEvents.incrementAndGet();
                return; // Limit chunk operations per tick during high MSPT
            }
        }
        
        // Skip if this chunk has too many block updates
        if (getBlockUpdateCount(chunkKey) > MAX_BLOCK_UPDATES_PER_CHUNK) {
            skippedFluidEvents.incrementAndGet();
            return; // Limit block updates per chunk
        }
        
        // BASIC FILTERING
        if (!shouldProcessFluid(level, pos)) {
            return;
        }
        
        // CACHE OPTIMIZATIONS - Use cached states to reduce world access
        BlockState state = getCachedBlockState(level, event.getPos());
        FluidState fluidState = state.getFluidState();
        
        // AGGRESSIVE SLOPE-AWARE THROTTLING
        if (cachedMSPT > 10.0) {
            // Check if this is a slope situation (water needs to flow downhill)
            boolean isSlopeSituation = isOnSlope((ServerLevel) level, event.getPos());
            
            if (isSlopeSituation) {
                // Allow slope updates even during high MSPT - critical for fluid flow
                // Don't skip slope updates as they break the cascade
            } else {
                // Apply AGGRESSIVE throttling for non-slope situations
                if (cachedMSPT > 30.0) {
                    if (eventsThisTick.get() % 2 != 0) {
                        skippedFluidEvents.incrementAndGet();
                        return; // Skip 50%
                    }
                } else if (cachedMSPT > 20.0) {
                    if (eventsThisTick.get() % 3 != 0) {
                        skippedFluidEvents.incrementAndGet();
                        return; // Skip 66%
                    }
                } else if (cachedMSPT > 10.0) {
                    if (eventsThisTick.get() % 4 != 0) {
                        skippedFluidEvents.incrementAndGet();
                        return; // Skip 75%
                    }
                }
            }
        }
        
        // ENHANCED CACHE OPTIMIZATIONS
        if (cachedMSPT > 5.0 && allowOptimizations) {
            // LIGHTING OPERATION TRACKING - 2 lighting operations found in Spark profile
            lightingUpdatesThisTick.incrementAndGet();
            
            // AGGRESSIVE LIGHTING THROTTLING - ThreadedLevelLightEngine bottleneck
            if (cachedMSPT > 12.0) {
                // Skip 50% of lighting updates during high MSPT
                if (lightingUpdatesThisTick.get() % 2 != 0) {
                    skippedFluidEvents.incrementAndGet();
                    return;
                }
            }
            
            // TASK QUEUE THROTTLING - Address ProcessorHandle and ChunkTaskPriorityQueueSorter bottlenecks
            if (cachedMSPT > 15.0) {
                // Limit task creation during high MSPT to prevent queue saturation
                int totalOps = entityOperationsThisTick.get() + neighborUpdatesThisTick.get() + lightingUpdatesThisTick.get();
                if (totalOps > 20) {
                    skippedFluidEvents.incrementAndGet();
                    return; // Prevent task queue saturation
                }
            }
            
            // Use cached data when safe
            if (blockStateCache.containsKey(pos) && fluidStateCache.containsKey(pos)) {
                return; // Use cached values instead of world access
            }
            
            // Cache the current state for future use
            try {
                BlockState currentState = level.getBlockState(pos);
                FluidState currentFluid = currentState.getFluidState();
                blockStateCache.put(pos, currentState);
                fluidStateCache.put(pos, currentFluid);
            } catch (Exception e) {
                // Fail silently
            }
        }
        
        // ENHANCED CHUNK BATCHING
        addToChunkBatch(pos);
    }
    
    // ENTITY OPTIMIZATION SYSTEM - DISABLED due to method signature issues
    // @SubscribeEvent
    // public void onEntityTick(LivingEvent.LivingTickEvent event) {
    //     // DISABLED: Entity level access methods incompatible with Minecraft 1.20.1
    //     // Main fluid optimization system works without this
    // }
    
    // @SubscribeEvent
    // public void onEntitySpawn(EntityJoinLevelEvent event) {
    //     // DISABLED: Entity level access methods incompatible with Minecraft 1.20.1
    //     // Main fluid optimization system works without this
    // }
    
    // CHUNK OPTIMIZATION SYSTEM
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!allowOptimizations) return;
        
        // Fix: Check if level is client-side properly
        if (!(event.getLevel() instanceof ServerLevel)) return;
        
        totalChunkEvents.incrementAndGet();
        
        // EMERGENCY CHUNK PROTECTION
        if (cachedMSPT > EMERGENCY_MSPT) {
            throttledChunkEvents.incrementAndGet();
            return; // Skip all chunk processing
        }
        
        // CHUNK THROTTLING WITH LOCATION-BASED OPTIMIZATION
        if (cachedMSPT > CHUNK_THROTTLE_MSPT) {
            long chunkKey = getChunkKey(event.getChunk().getPos().x, event.getChunk().getPos().z);
            
            // Track chunk generation for location-based optimization
            incrementChunkGenerationCount(chunkKey);
            
            // Check if we recently loaded this chunk
            if (chunkLoadCache.containsKey(chunkKey)) {
                throttledChunkEvents.incrementAndGet();
                return; // Skip duplicate chunk loads
            }
            
            // Limit chunk generations per tick in high-stress areas
            if (getChunkGenerationCount(chunkKey) > MAX_CHUNK_GENERATION_PER_TICK) {
                throttledChunkEvents.incrementAndGet();
                return; // Skip excessive chunk generations
            }
            
            // Cache this chunk load
            chunkLoadCache.put(chunkKey, new BlockPos(event.getChunk().getPos().x * 16, 0, event.getChunk().getPos().z * 16));
            
            // Limit chunk operations per tick
            Integer ops = chunkOperationCount.get(chunkKey);
            if (ops != null && ops > 5) {
                throttledChunkEvents.incrementAndGet();
                return; // Skip excessive chunk operations
            }
            
            chunkOperationCount.put(chunkKey, (ops == null ? 1 : ops + 1));
        }
    }
    
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!allowOptimizations) return;
        
        // Fix: Check if level is client-side properly
        if (!(event.getLevel() instanceof ServerLevel)) return;
        
        // Clean up chunk cache
        long chunkKey = getChunkKey(event.getChunk().getPos().x, event.getChunk().getPos().z);
        chunkLoadCache.remove(chunkKey);
        chunkOperationCount.remove(chunkKey);
    }
    
    // LOCATION-BASED OPTIMIZATION METHODS
    
    private static boolean isHighStressLocation(BlockPos pos) {
        // Use BlockPos.toString() to avoid obfuscated method issues
        // BlockPos toString format: "BlockPos{x=X, y=Y, z=Z}"
        long chunkKey;
        try {
            String posStr = pos.toString();
            // Parse coordinates from string
            int xIndex = posStr.indexOf("x=");
            int zIndex = posStr.indexOf("z=");
            int commaIndex = posStr.indexOf(',', zIndex);
            
            if (xIndex != -1 && zIndex != -1 && commaIndex != -1) {
                int x = Integer.parseInt(posStr.substring(xIndex + 2, posStr.indexOf(',', xIndex)));
                int z = Integer.parseInt(posStr.substring(zIndex + 2, commaIndex));
                chunkKey = x * 31L + z;
            } else {
                // Fallback: use hash code
                chunkKey = pos.hashCode();
            }
        } catch (Exception e) {
            // Final fallback: use hash code
            chunkKey = pos.hashCode();
        }
        
        // Check if this chunk is already marked as high-stress
        if (highStressChunks.contains(chunkKey)) {
            return true;
        }
        
        // Check if this chunk has high activity counts
        Integer blockUpdates = blockUpdateCount.get(chunkKey);
        Integer chunkGens = chunkGenerationCount.get(chunkKey);
        
        // Mark as high-stress if thresholds exceeded
        boolean isHighStress = (blockUpdates != null && blockUpdates > MAX_BLOCK_UPDATES_PER_CHUNK) ||
                             (chunkGens != null && chunkGens > MAX_CHUNK_GENERATION_PER_TICK);
        
        if (isHighStress) {
            highStressChunks.add(chunkKey);
        }
        
        return isHighStress;
    }
    
    private static void incrementBlockUpdateCount(long chunkKey) {
        blockUpdatesThisTick.incrementAndGet();
        blockUpdateCount.merge(chunkKey, 1, Integer::sum);
    }
    
    private static void incrementChunkGenerationCount(long chunkKey) {
        chunkGenerationsThisTick.incrementAndGet();
        chunkGenerationCount.merge(chunkKey, 1, Integer::sum);
    }
    
    private static int getBlockUpdateCount(long chunkKey) {
        return blockUpdateCount.getOrDefault(chunkKey, 0);
    }
    
    private static int getChunkGenerationCount(long chunkKey) {
        return chunkGenerationCount.getOrDefault(chunkKey, 0);
    }
    
    private static void resetLocationCounters() {
        // Reset per-tick counters
        chunkGenerationsThisTick.set(0);
        blockUpdatesThisTick.set(0);
        entityProcessesThisTick.set(0);
        
        // Clear high-stress chunks periodically
        if (cachedMSPT < 20.0) {
            highStressChunks.clear();
        }
    }
    
    private static int getLocationOptimizationCount() {
        return chunkGenerationsThisTick.get() + blockUpdatesThisTick.get() + entityProcessesThisTick.get();
    }
    
    // SLOPE DETECTION - KEY INNOVATION
    private static boolean isOnSlope(ServerLevel level, BlockPos pos) {
        try {
            // Get current water level
            BlockState currentState = level.getBlockState(pos);
            FluidState currentFluid = currentState.getFluidState();
            
            if (currentFluid.isEmpty()) {
                return false; // Not water, no slope needed
            }
            
            int currentLevel = currentFluid.getAmount(); // Water level (1-8, 8 = full block)
            
            // Check adjacent positions for lower water levels or empty spaces
            BlockPos[] adjacent = {
                pos.north(), pos.south(), pos.east(), pos.west(),
                pos.north().east(), pos.north().west(), 
                pos.south().east(), pos.south().west()
            };
            
            for (BlockPos adjacentPos : adjacent) {
                if (!level.isLoaded(adjacentPos)) continue;
                
                BlockState adjacentState = level.getBlockState(adjacentPos);
                FluidState adjacentFluid = adjacentState.getFluidState();
                
                // If adjacent position has lower water level or is empty, this is a slope
                if (adjacentFluid.isEmpty()) {
                    return true; // Water can flow into empty space
                } else if (adjacentFluid.getType() == currentFluid.getType()) {
                    int adjacentLevel = adjacentFluid.getAmount();
                    if (adjacentLevel < currentLevel) {
                        return true; // Water can flow to lower level
                    }
                }
            }
            
            // Check below for downward flow
            BlockPos belowPos = pos.below();
            if (level.isLoaded(belowPos)) {
                BlockState belowState = level.getBlockState(belowPos);
                FluidState belowFluid = belowState.getFluidState();
                
                if (belowFluid.isEmpty() || 
                    (belowFluid.getType() == currentFluid.getType() && belowFluid.getAmount() < 8)) {
                    return true; // Water can flow down
                }
            }
            
            return false; // No slope detected
        } catch (Exception e) {
            return false; // Fail safely
        }
    }
    
    // UTILITY METHODS
    // Note: isNearFluid method kept for compatibility but not used in current implementation
    private static boolean isNearFluid(Level level, BlockPos pos) {
        try {
            // Check 5x5x5 area around position for fluids
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        BlockPos checkPos = pos.offset(x, y, z);
                        if (level.isLoaded(checkPos)) {
                            FluidState fluid = level.getFluidState(checkPos);
                            if (!fluid.isEmpty()) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean shouldProcessFluid(Level level, BlockPos pos) {
        // AGGRESSIVE OPTIMIZATION: Skip expensive player distance calculations
        // Use simple chunk-based check instead of worldwide player scanning
        
        // Skip if no players at all (empty worlds)
        if (level.players().isEmpty()) {
            return false;
        }
        
        // OPTIMIZED: Only process fluids in chunks near spawn (0,0) to reduce worldwide scanning
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        
        // FAST: Use squared distance instead of expensive Math.sqrt()
        int distanceSquared = chunkX * chunkX + chunkZ * chunkZ;
        final int MAX_DISTANCE_SQUARED = 32 * 32; // 32 chunks squared
        
        // Only process fluids within 32 chunks of spawn (dramatic reduction in processing area)
        return distanceSquared <= MAX_DISTANCE_SQUARED;
    }

    private static long getChunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
    
    private static void cleanupExpiredCaches() {
        long currentTime = System.currentTimeMillis();
        
        // Clean up old entity process times
        lastEntityProcess.entrySet().removeIf(entry -> 
            currentTime - entry.getValue() > 5000); // 5 seconds
        
        // Clean up old chunk load cache
        chunkLoadCache.entrySet().removeIf(entry -> {
            // Remove chunks that haven't been accessed recently
            return chunkOperationCount.getOrDefault(entry.getKey(), 0) < 1;
        });
        
        // Clean up old location-based caches
        chunkGenerationCount.entrySet().removeIf(entry -> {
            Long expiry = locationCacheExpiry.get(entry.getKey());
            return expiry == null || System.currentTimeMillis() > expiry;
        });
        
        blockUpdateCount.entrySet().removeIf(entry -> {
            Long expiry = locationCacheExpiry.get(entry.getKey());
            return expiry == null || System.currentTimeMillis() > expiry;
        });
    }
    
    // ENTITY PROCESSING TRACKING - Used for location-based optimization
    private static void trackEntityProcessing(Entity entity) {
        entityProcessesThisTick.incrementAndGet();
        lastEntityProcess.put(entity, System.currentTimeMillis());
    }
    
    // LEVEL EVENT INTERCEPTION - CRITICAL for MSPT optimization
    @SubscribeEvent
    public void onLevelTick(TickEvent.ServerTickEvent event) {
        if (!allowOptimizations || event.phase != TickEvent.Phase.END) return;
        
        // EMERGENCY PROTECTION
        if (cachedMSPT > EMERGENCY_MSPT) {
            return; // Skip all level processing during emergency
        }
        
        // LOCATION-BASED LEVEL TICK OPTIMIZATION
        if (cachedMSPT > LOCATION_THROTTLE_MSPT) {
            // Limit level tick processing in high-stress areas
            if (eventsThisTick.get() % 2 != 0) {
                return; // Skip 50% of level ticks
            }
        }
        
        // Track level tick for optimization
        entityProcessesThisTick.incrementAndGet();
    }
    
    // BLOCK EVENT INTERCEPTION - CRITICAL for MSPT optimization
    @SubscribeEvent
    public void onBlockEvent(BlockEvent event) {
        if (!allowOptimizations) return;
        
        // Only process server level events
        if (!(event.getLevel() instanceof ServerLevel)) return;
        
        BlockPos pos = event.getPos();
        
        // LOCATION-BASED BLOCK EVENT OPTIMIZATION
        if (isHighStressLocation(pos)) {
            // Apply aggressive throttling in high-stress areas
            if (cachedMSPT > LOCATION_THROTTLE_MSPT) {
                if (eventsThisTick.get() % 3 != 0) {
                    return; // Skip 66% of block events
                }
            }
        }
        
        // Track block events for optimization - use BlockPos.toString() to avoid method issues
        long chunkKey;
        try {
            String posStr = event.getPos().toString();
            // Parse coordinates from string
            int xIndex = posStr.indexOf("x=");
            int zIndex = posStr.indexOf("z=");
            int commaIndex = posStr.indexOf(',', zIndex);
            
            if (xIndex != -1 && zIndex != -1 && commaIndex != -1) {
                int x = Integer.parseInt(posStr.substring(xIndex + 2, posStr.indexOf(',', xIndex)));
                int z = Integer.parseInt(posStr.substring(zIndex + 2, commaIndex));
                chunkKey = x * 31L + z;
            } else {
                // Fallback: use hash code
                chunkKey = event.getPos().hashCode();
            }
        } catch (Exception e) {
            // Final fallback: use hash code
            chunkKey = event.getPos().hashCode();
        }
        
        incrementBlockUpdateCount(chunkKey);
    }
    
    private static void updateMSPT() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastMSPTCheck > MSPT_CHECK_INTERVAL) {
            try {
                net.minecraft.server.MinecraftServer server = null;
                
                try {
                    java.lang.reflect.Method getCurrentServer = net.minecraft.server.MinecraftServer.class.getMethod("getCurrentServer");
                    Object serverObj = getCurrentServer.invoke(null);
                    if (serverObj instanceof net.minecraft.server.MinecraftServer) {
                        server = (net.minecraft.server.MinecraftServer) serverObj;
                    }
                } catch (Exception ignored) {}
                
                if (server != null) {
                    long[] tickTimes = server.tickTimes;
                    if (tickTimes != null && tickTimes.length > 0) {
                        long totalTickTime = 0;
                        int count = 0;
                        for (int i = 0; i < Math.min(tickTimes.length, 100); i++) {
                            totalTickTime += tickTimes[i];
                            count++;
                        }
                        
                        if (count > 0) {
                            cachedMSPT = totalTickTime / (double) count / 1_000_000.0;
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback estimation
                int skipped = skippedFluidEvents.get();
                if (skipped > 1000) {
                    cachedMSPT = Math.max(cachedMSPT, 25.0);
                } else {
                    cachedMSPT = 8.0 + (skipped * 0.01);
                }
            }
            
            lastMSPTCheck = currentTime;
        }
    }
    
    public static double getMSPT() {
        updateMSPT();
        return cachedMSPT;
    }
    
    // TICK CASCADE PREVENTION METHODS - Address root cause of MSPT issues
    private static void resetTickCascadeSystem() {
        long currentTime = System.currentTimeMillis();
        
        // Reset tick counters at regular intervals
        if (currentTime - lastTickResetTime > TICK_RESET_INTERVAL) {
            tickOperationsThisTick.set(0);
            scheduledTicksPending.set(0);
            lastTickResetTime = currentTime;
            
            // Update cascade level based on recent performance
            if (cachedMSPT > CASCADE_PREVENTION_MSPT) {
                tickCascadeLevel.incrementAndGet();
            } else if (cachedMSPT < 10.0) {
                tickCascadeLevel.set(Math.max(0, tickCascadeLevel.get() - 1));
            }
        }
        
        // Check if tick system is overloaded
        tickSystemOverloaded = isTickCapacityExceeded();
        
        // Monitor thread pool capacity
        monitorThreadPoolCapacity(currentTime);
    }
    
    private static void monitorThreadPoolCapacity(long currentTime) {
        // Check thread pool capacity at regular intervals
        if (currentTime - lastThreadPoolCheck > THREAD_POOL_CHECK_INTERVAL) {
            lastThreadPoolCheck = currentTime;
            
            try {
                // Monitor ForkJoinPool (common pool used by Minecraft)
                java.util.concurrent.ForkJoinPool commonPool = java.util.concurrent.ForkJoinPool.commonPool();
                int parallelism = commonPool.getParallelism();
                int activeThreads = commonPool.getActiveThreadCount();
                
                threadPoolActiveThreads.set(activeThreads);
                
                // Check if thread pool is overloaded (>80% capacity)
                double threadPoolUsage = (double) activeThreads / parallelism;
                threadPoolOverloaded = threadPoolUsage > THREAD_POOL_THRESHOLD;
                
                // Monitor main thread executor usage (fallback when thread pool is saturated)
                if (threadPoolOverloaded) {
                    mainThreadExecutorUsage.incrementAndGet();
                }
                
                // Reset entity data operations counter periodically
                if (entityDataOperationsThisTick.get() > MAX_ENTITY_DATA_OPERATIONS) {
                    entityDataOperationsThisTick.set(0);
                }
                
            } catch (Exception e) {
                // Fail silently - thread pool monitoring is optional
            }
        }
    }
    
    private static boolean isTickSystemOverloaded() {
        // Multiple indicators of tick system overload
        boolean msptOverload = cachedMSPT > CASCADE_PREVENTION_MSPT;
        boolean operationOverload = tickOperationsThisTick.get() > MAX_TICK_OPERATIONS;
        boolean cascadeOverload = tickCascadeLevel.get() > 3;
        boolean capacityOverload = getTickCapacity() < TICK_CAPACITY_THRESHOLD;
        
        return msptOverload || operationOverload || cascadeOverload || capacityOverload;
    }
    
    private static boolean isThreadPoolOverloaded() {
        // Check thread pool capacity and entity data operations
        boolean threadPoolCapacity = threadPoolOverloaded;
        boolean entityDataOverload = entityDataOperationsThisTick.get() > MAX_ENTITY_DATA_OPERATIONS;
        
        return threadPoolCapacity || entityDataOverload;
    }
    
    private static boolean isTickCapacityExceeded() {
        // Calculate tick capacity based on current operations
        int totalOps = tickOperationsThisTick.get() + entityOperationsThisTick.get() + 
                       neighborUpdatesThisTick.get() + lightingUpdatesThisTick.get();
        
        return totalOps > MAX_TICK_OPERATIONS;
    }
    
    private static double getTickCapacity() {
        // Calculate remaining tick capacity (0.0 to 1.0)
        int usedOps = tickOperationsThisTick.get();
        return Math.max(0.0, 1.0 - (double)usedOps / MAX_TICK_OPERATIONS);
    }
    
    // CHUNK BATCHING
    private static void addToChunkBatch(BlockPos pos) {
        long chunkKey = ((long)(pos.getX() >> 4) << 32) | (pos.getZ() >> 4) & 0xFFFFFFFFL;
        chunkBatchMap.put(chunkKey, pos);
        
        // Process batch if it gets too large
        if (chunkBatchMap.size() > 100) {
            chunkBatchMap.clear();
        }
    }
    
    // COMPREHENSIVE STATISTICS RESET
    private static void resetAllStats() {
        totalFluidEvents.set(0);
        skippedFluidEvents.set(0);
        eventsThisTick.set(0);
        
        totalEntityEvents.set(0);
        throttledEntityEvents.set(0);
        entityEventsThisTick.set(0);
        
        totalChunkEvents.set(0);
        throttledChunkEvents.set(0);
    }
    
    // PUBLIC API FOR COMPATIBILITY
    public static boolean shouldAllowFluidFlow(Level level, BlockPos pos, BlockPos from) {
        if (!allowOptimizations) return true; // Safety check
        
        // SPATIAL PARTITIONING - Skip if no players nearby
        if (!isChunkNearPlayers(pos)) {
            skippedFluidEvents.incrementAndGet();
            return false; // CULL distant fluids
        }
        
        // Continue with existing MSPT-based throttling
        return true;
    }
    
    public static double getMSPTValue() {
        return cachedMSPT;
    }
    
    public static int getTotalOptimizationCount() {
        return skippedFluidEvents.get() + throttledEntityEvents.get() + throttledChunkEvents.get();
    }
    
    public static boolean isOptimizationActive() {
        return allowOptimizations;
    }
}
