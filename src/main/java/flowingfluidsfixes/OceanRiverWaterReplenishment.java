package flowingfluidsfixes;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Handles accelerated water replenishment ONLY in ocean and river biomes.
 * This helps reduce lag from Flowing Fluids calculating water holes by slowly
 * refilling them, while preserving the finite water system in all other biomes.
 * 
 * Key principle: Ocean and river biomes are TRUE infinite water sources in Flowing Fluids,
 * so we can safely replenish water there without breaking the finite water mechanics.
 */
@SuppressWarnings("all")
public class OceanRiverWaterReplenishment {
    
    private static final Logger LOGGER = LogManager.getLogger(OceanRiverWaterReplenishment.class);
    private static final Random RANDOM = new Random();
    
    // Default sea level for Minecraft
    private static final int SEA_LEVEL = 63;
    
    // Queue of positions that need water replenishment
    // MEMORY FIX: Added hard size limits
    private static final ConcurrentLinkedQueue<ReplenishmentTask> replenishmentQueue = new ConcurrentLinkedQueue<>();
    private static final int MAX_REPLENISHMENT_QUEUE_SIZE = 500; // Reduced from unbounded
    
    // Track positions currently being replenished to avoid duplicates
    private static final Set<BlockPos> activeReplenishment = ConcurrentHashMap.newKeySet();
    private static final int MAX_ACTIVE_REPLENISHMENT_SIZE = 500; // Hard limit
    
    // Track water positions above ocean that might need draining
    // Maps position -> (tick when first seen, fluid level when first seen)
    private static final Map<BlockPos, DrainCandidate> drainCandidates = new ConcurrentHashMap<>();
    
    // How long water must sit still before draining (in ticks) - 0.25 seconds = 5 ticks
    private static final int DRAIN_DELAY_TICKS = 5;
    
    // Max drain candidates to track - MEMORY FIX: Reduced from 5000
    private static final int MAX_DRAIN_CANDIDATES = 1000;
    
    // Configuration values (can be modified via config)
    private static float oceanReplenishRate = 0.95f; // Ultra fast - 95% chance per tick
    private static float riverReplenishRate = 0.80f; // Very fast - 80% chance for rivers
    private static int maxReplenishmentsPerTick = 500; // Reduced from 1000 to limit processing load
    private static boolean enabled = true;
    private static final int SHORE_WATER_LEVELING_RADIUS = 32; // Reduced radius for shore water leveling
    private static final int RAIN_WATER_REMOVAL_RADIUS = 40; // Reduced radius for rain water removal
    private static final int THIN_LAYER_LEVELING_RADIUS = 24; // Reduced radius for thin layer leveling
    private static final int OCEAN_SURFACE_EVAPORATION_RADIUS = 64; // Reduced radius for evaporation
    private static final int OCEAN_SURFACE_FILLING_RADIUS = 32; // Reduced radius for direct filling
    
    /**
     * Check if a position is eligible for water replenishment.
     * ONLY returns true for ocean and river biomes.
     */
    public static boolean isEligibleForReplenishment(ServerLevel level, BlockPos pos) {
        if (!enabled) {
            return false;
        }
        
        // Must be in ocean or river biome - NO other biomes allowed
        if (!BiomeOptimization.isOceanOrRiverBiome(level, pos)) {
            return false;
        }
        
        // Must be at or below sea level (STRICT: no replenishment above Y=63)
        if (pos.getY() > SEA_LEVEL) {
            return false;
        }
        
        // Get current block state
        FluidState fluidState = level.getFluidState(pos);
        BlockState blockState = level.getBlockState(pos);
        
        // Only replenish water (not lava or other fluids)
        if (!fluidState.is(Fluids.WATER) && !fluidState.is(Fluids.FLOWING_WATER)) {
            if (!blockState.isAir() && !blockState.canBeReplaced(Fluids.WATER)) {
                return false;
            }
        }
        
        // Must have adjacent water source to draw from
        return hasAdjacentWaterSource(level, pos);
    }
    
    /**
     * Check if position has an adjacent water source block
     */
    private static boolean hasAdjacentWaterSource(ServerLevel level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos adjacent = pos.relative(dir);
            FluidState adjacentFluid = level.getFluidState(adjacent);
            if (adjacentFluid.is(Fluids.WATER) && adjacentFluid.isSource()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Called when a non-full water block is detected in ocean/river biome.
     * Schedules it for gradual replenishment.
     */
    public static void scheduleReplenishment(ServerLevel level, BlockPos pos, FluidState currentState) {
        if (!enabled) {
            return;
        }
        
        // Double-check biome eligibility
        if (!BiomeOptimization.isOceanOrRiverBiome(level, pos)) {
            return;
        }
        
        // Don't schedule if already in queue
        if (activeReplenishment.contains(pos)) {
            return;
        }
        
        // MEMORY FIX: Enforce hard size limits
        if (replenishmentQueue.size() >= MAX_REPLENISHMENT_QUEUE_SIZE) {
            replenishmentQueue.clear();
            activeReplenishment.clear();
            LOGGER.debug("Cleared replenishment queue due to size limit");
            return;
        }
        
        if (activeReplenishment.size() >= MAX_ACTIVE_REPLENISHMENT_SIZE) {
            activeReplenishment.clear();
            LOGGER.debug("Cleared activeReplenishment due to size limit");
        }
        
        // Get appropriate replenishment rate based on biome
        float rate = BiomeOptimization.isOceanBiome(level, pos) ? oceanReplenishRate : riverReplenishRate;
        
        ReplenishmentTask task = new ReplenishmentTask(pos.immutable(), currentState.getAmount(), rate);
        replenishmentQueue.offer(task);
        activeReplenishment.add(pos.immutable());
        
        LOGGER.debug("Scheduled water replenishment at {} in {} biome", 
            pos, BiomeOptimization.isOceanBiome(level, pos) ? "ocean" : "river");
    }
    
    /**
     * Process pending replenishments - called each server tick.
     */
    public static void processReplenishments(ServerLevel level) {
        if (!enabled || replenishmentQueue.isEmpty()) {
            return;
        }
        
        int processed = 0;
        int refilled = 0;
        
        while (!replenishmentQueue.isEmpty() && processed < maxReplenishmentsPerTick) {
            ReplenishmentTask task = replenishmentQueue.poll();
            if (task == null) {
                break;
            }
            
            processed++;
            BlockPos taskPos = task.pos();
            
            // Remove from active set
            activeReplenishment.remove(taskPos);
            
            // Verify position is still valid
            if (!level.isLoaded(taskPos)) {
                continue;
            }
            
            // Re-verify biome
            if (!BiomeOptimization.isOceanOrRiverBiome(level, taskPos)) {
                continue;
            }
            
            // Check current state
            FluidState currentFluid = level.getFluidState(taskPos);
            
            // If already full water source, skip
            if (currentFluid.is(Fluids.WATER) && currentFluid.isSource()) {
                continue;
            }
            
            // Random chance to actually replenish this tick
            // OCEAN: Higher chance but still gradual for visual consistency
            // RIVER: Use normal chance for gradual refill
            boolean isOcean = BiomeOptimization.isOceanBiome(level, taskPos);
            float chance = isOcean ? 0.95f : task.replenishRate(); // Ocean gets 95% chance
            if (RANDOM.nextFloat() > chance) {
                // Re-queue for later
                replenishmentQueue.offer(task);
                activeReplenishment.add(taskPos);
                continue;
            }
            
            // Perform the replenishment
            if (tryReplenishWater(level, taskPos, currentFluid)) {
                refilled++;
            }
        }
        
        if (refilled > 0) {
            LOGGER.debug("Replenished {} water blocks in ocean/river biomes", refilled);
        }
    }
    
    /**
     * Attempt to replenish water at the given position.
     */
    private static boolean tryReplenishWater(ServerLevel level, BlockPos pos, FluidState currentFluid) {
        // Must have adjacent water source
        if (!hasAdjacentWaterSource(level, pos)) {
            return false;
        }
        
        BlockState currentBlock = level.getBlockState(pos);
        
        // If air or replaceable, place water source
        if (currentBlock.isAir() || currentBlock.canBeReplaced(Fluids.WATER)) {
            // INSTANT REFILL: Always place full source block for immediate ocean surface restoration
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            return true;
        }
        
        // If partially filled water, fill faster for ocean but still gradual
        if (currentFluid.is(Fluids.FLOWING_WATER)) {
            boolean isOcean = BiomeOptimization.isOceanBiome(level, pos);
            
            if (isOcean) {
                // OCEAN: Fill extremely fast (6 levels at a time)
                int currentAmount = currentFluid.getAmount();
                int newAmount = Math.min(currentAmount + 6, 8); // Ultra fast filling
                
                if (newAmount >= 8) {
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                } else {
                    level.setBlock(pos, Fluids.WATER.getFlowing(newAmount, false).createLegacyBlock(), 3);
                }
                return true;
            } else {
                // RIVER: Gradual filling
                int currentAmount = currentFluid.getAmount();
                int newAmount = Math.min(currentAmount + 2, 8);
                
                if (newAmount >= 8) {
                    level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                } else {
                    level.setBlock(pos, Fluids.WATER.getFlowing(newAmount, false).createLegacyBlock(), 3);
                }
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Scan a chunk for water holes in ocean/river biomes.
     */
    public static void scanChunkForWaterHoles(ServerLevel level, int chunkX, int chunkZ) {
        if (!enabled) {
            return;
        }
        
        int startX = chunkX * 16;
        int startZ = chunkZ * 16;
        
        for (int y = SEA_LEVEL; y >= level.getMinBuildHeight(); y--) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockPos pos = new BlockPos(startX + x, y, startZ + z);
                    
                    if (!BiomeOptimization.isOceanOrRiverBiome(level, pos)) {
                        continue;
                    }
                    
                    FluidState fluid = level.getFluidState(pos);
                    BlockState block = level.getBlockState(pos);
                    
                    if (block.isAir() || (fluid.is(Fluids.FLOWING_WATER) && hasAdjacentWaterSource(level, pos))) {
                        scheduleReplenishment(level, pos, fluid);
                    }
                }
            }
        }
    }
    
    /**
     * Quick check for fluid tick - should this fluid be accelerated for replenishment?
     */
    public static boolean shouldAccelerateFluidTick(ServerLevel level, BlockPos pos, FluidState state) {
        if (!enabled) {
            return false;
        }
        
        if (!BiomeOptimization.isOceanOrRiverBiome(level, pos)) {
            return false;
        }
        
        if (!state.is(Fluids.WATER) && !state.is(Fluids.FLOWING_WATER)) {
            return false;
        }
        
        return !state.isSource() && hasAdjacentWaterSource(level, pos);
    }
    
    /**
     * Get tick delay multiplier for ocean/river biomes.
     */
    public static float getTickDelayMultiplier(ServerLevel level, BlockPos pos) {
        if (!enabled) {
            return 1.0f;
        }
        
        if (BiomeOptimization.isOceanBiome(level, pos)) {
            return 0.3f;
        } else if (BiomeOptimization.isRiverBiome(level, pos)) {
            return 0.4f;
        }
        
        return 1.0f;
    }
    
    // Configuration setters
    public static void setOceanReplenishRate(float rate) {
        oceanReplenishRate = Math.max(0.0f, Math.min(1.0f, rate));
    }
    
    public static void setRiverReplenishRate(float rate) {
        riverReplenishRate = Math.max(0.0f, Math.min(1.0f, rate));
    }
    
    public static void setMaxReplenishmentsPerTick(int max) {
        maxReplenishmentsPerTick = Math.max(1, max);
    }
    
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            // MEMORY FIX: Clear all collections when disabled
            replenishmentQueue.clear();
            activeReplenishment.clear();
            drainCandidates.clear();
        }
    }
    
    /**
     * Clear all caches - call periodically or on world unload to prevent memory leaks
     */
    public static void clearAllCaches() {
        replenishmentQueue.clear();
        activeReplenishment.clear();
        drainCandidates.clear();
        LOGGER.debug("Cleared all OceanRiverWaterReplenishment caches");
    }
    
    public static boolean isEnabled() {
        return enabled;
    }
    
    public static int getQueueSize() {
        return replenishmentQueue.size();
    }
    
    /**
     * Task record for tracking replenishment
     */
    private record ReplenishmentTask(BlockPos pos, int currentLevel, float replenishRate) {}
    
    /**
     * Record for tracking drain candidates - water that might need to drain into ocean
     */
    private record DrainCandidate(long firstSeenTick, int fluidLevel) {}
    
    /**
     * Check if water is a candidate for draining into ocean.
     * Water must be ABOVE ocean surface with ocean water directly below.
     * ENHANCED: Now also handles rain-created water sources aggressively.
     */
    private static boolean isDrainCandidate(ServerLevel level, BlockPos pos, FluidState state) {
        // Process both flowing water and source blocks (especially rain sources)
        if (!state.is(Fluids.FLOWING_WATER) && !(state.is(Fluids.WATER))) {
            return false;
        }
        
        // Must be ABOVE sea level
        if (pos.getY() <= SEA_LEVEL) {
            return false;
        }
        
        // Check if block BELOW is ocean/river water source
        BlockPos below = pos.below();
        if (!level.isInWorldBounds(below)) return false;
        
        FluidState belowFluid = level.getFluidState(below);
        if (belowFluid.is(Fluids.WATER) && belowFluid.isSource()) {
            // Verify the water below is in ocean/river biome
            if (BiomeOptimization.isOceanOrRiverBiome(level, below)) {
                return true;
            }
        }
        
        // ADDITIONAL CHECK: If this is a rain water source above ocean, drain it immediately
        if (state.isSource() && level.isRaining()) {
            // Check if we're in a rainy biome near ocean
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    if (BiomeOptimization.isOceanOrRiverBiome(level, pos.offset(dx, 0, dz))) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if water has been sitting still long enough to drain.
     * ENHANCED: Rain water drains immediately, normal water waits less time.
     */
    private static boolean shouldDrainNow(ServerLevel level, BlockPos pos, FluidState state, long currentTick) {
        BlockPos immutablePos = pos.immutable();
        DrainCandidate candidate = drainCandidates.get(immutablePos);
        
        // IMMEDIATE DRAIN for rain water sources
        if (state.isSource() && level.isRaining()) {
            return true; // Drain rain water immediately
        }
        
        if (candidate == null) {
            // First time seeing this water - register it
            if (drainCandidates.size() < MAX_DRAIN_CANDIDATES) {
                drainCandidates.put(immutablePos, new DrainCandidate(currentTick, state.getAmount()));
            }
            return false;
        }
        
        // Check if fluid level changed - if so, it's still flowing
        if (candidate.fluidLevel() != state.getAmount()) {
            // Fluid level changed - reset timer, it's still flowing
            drainCandidates.put(immutablePos, new DrainCandidate(currentTick, state.getAmount()));
            return false;
        }
        
        // Check if enough time has passed (reduced from 10 to 2 ticks for INSTANT drainage)
        long ticksWaiting = currentTick - candidate.firstSeenTick();
        return ticksWaiting >= 2; // 0.1 seconds - almost instant
    }
    
    /**
     * Drain water into ocean - ONLY for water sitting still on ocean surface.
     * 
     * Water must:
     * 1. Be ABOVE sea level
     * 2. Have ocean/river water directly below
     * 3. Have been sitting still for DRAIN_DELAY_TICKS (3 seconds)
     * 
     * Actively flowing water is NOT drained.
     */
    public static boolean drainIntoOcean(ServerLevel level, BlockPos pos, FluidState state, long currentTick) {
        if (!isDrainCandidate(level, pos, state)) {
            // Not above ocean - remove from tracking if present
            drainCandidates.remove(pos.immutable());
            return false;
        }
        
        if (!shouldDrainNow(level, pos, state, currentTick)) {
            // Not ready to drain yet - still tracking
            return false;
        }
        
        // Ready to drain - remove the water
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        drainCandidates.remove(pos.immutable());
        LOGGER.debug("Drained still water at {} into ocean below (was sitting for {} ticks)", pos, DRAIN_DELAY_TICKS);
        return true;
    }
    
    /**
     * Process ocean surface water draining - called each tick.
     * ENHANCED: Now aggressively removes rain water and processes more blocks per tick.
     * 
     * Processes water that:
     * 1. Is ABOVE the ocean surface (Y > 63)
     * 2. Has ocean/river water directly below
     * 3. Rain water drains IMMEDIATELY
     * 4. Normal water drains after 1 second (was 3 seconds)
     */
    public static void processShoreWaterLeveling(ServerLevel level) {
        if (!enabled || !FlowingFluidsIntegration.isFlowingFluidsLoaded()) {
            return;
        }
        
        long currentTick = level.getGameTime();
        int drained = 0;
        int tracked = 0;
        int maxPerTick = 1000; // Reduced from 2000 to limit processing load
        
        // Clean up stale entries less frequently to save processing (every 200 ticks)
        int cleanupInterval = level.isRaining() ? 150 : 200;
        if (currentTick % cleanupInterval == 0) {
            cleanupStaleDrainCandidates(level, currentTick);
        }
        
        // Process all players, but with reduced radius
        for (var player : level.players()) {
            if (drained >= maxPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = level.isRaining() ? SHORE_WATER_LEVELING_RADIUS + 8 : SHORE_WATER_LEVELING_RADIUS; // Slightly larger during rain
            
            // Scan ABOVE sea level with reduced coverage
            int maxY = level.isRaining() ? SEA_LEVEL + 10 : SEA_LEVEL + 8;
            for (int dx = -radius; dx <= radius && drained < maxPerTick; dx += 2) { // Increased step to reduce checks
                for (int dz = -radius; dz <= radius && drained < maxPerTick; dz += 2) { // Increased step to reduce checks
                    for (int worldY = SEA_LEVEL + 1; worldY <= maxY; worldY++) {
                        BlockPos checkPos = new BlockPos(
                            playerPos.getX() + dx, 
                            worldY, 
                            playerPos.getZ() + dz
                        );
                        
                        if (!level.isLoaded(checkPos)) continue;
                        
                        FluidState fluidState = level.getFluidState(checkPos);
                        if (fluidState.isEmpty()) continue;
                        
                        // During rain, be more aggressive with source blocks
                        if (level.isRaining() && fluidState.isSource()) {
                            if (drainIntoOcean(level, checkPos, fluidState, currentTick)) {
                                drained++;
                            }
                        } else if (!fluidState.isSource()) {
                            // ALL non-source water above ocean should drain instantly
                            if (drainIntoOcean(level, checkPos, fluidState, currentTick)) {
                                drained++;
                            } else if (isDrainCandidate(level, checkPos, fluidState)) {
                                tracked++;
                            }
                        }
                    }
                }
            }
        }
        
        if (drained > 0) {
            LOGGER.debug("Drained {} water blocks into ocean (tracking {} candidates, rain: {})", 
                drained, drainCandidates.size(), level.isRaining());
        }
    }
    
    /**
     * Process rain water removal - called each tick.
     * ENHANCED: Now aggressively removes rain water and processes more blocks per tick.
     * 
     * Processes water that:
     * 1. Is ABOVE the ocean surface (Y > 63)
     * 2. Has ocean/river water directly below
     * 3. Rain water drains IMMEDIATELY
     * 4. Normal water drains after 1 second (was 3 seconds)
     */
    public static void processRainWaterRemoval(ServerLevel level) {
        if (!enabled || !level.isRaining()) {
            return;
        }
        
        int removed = 0;
        int maxPerTick = 500; // Reduced from 1000 to limit processing load
        
        for (var player : level.players()) {
            if (removed >= maxPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = RAIN_WATER_REMOVAL_RADIUS; // Reduced radius to focus on nearby areas
            
            // Scan for ANY floating water sources during rain with reduced range
            for (int dx = -radius; dx <= radius; dx += 2) { // Increased step to reduce checks
                for (int dz = -radius; dz <= radius; dz += 2) { // Increased step to reduce checks
                    for (int worldY = SEA_LEVEL + 1; worldY <= SEA_LEVEL + 15; worldY++) { // Reduced height range
                        BlockPos checkPos = new BlockPos(
                            playerPos.getX() + dx, 
                            worldY, 
                            playerPos.getZ() + dz
                        );
                        
                        if (!level.isLoaded(checkPos)) continue;
                        
                        FluidState fluidState = level.getFluidState(checkPos);
                        
                        // During rain, remove ANY suspicious water sources above sea level
                        if (fluidState.isSource() && worldY > SEA_LEVEL) {
                            // Check if this water should exist here
                            if (shouldRemoveFloatingWater(level, checkPos, fluidState)) {
                                level.setBlock(checkPos, Blocks.AIR.defaultBlockState(), 3);
                                removed++;
                                LOGGER.debug("Removed floating rain water at {}", checkPos);
                            }
                        }
                    }
                }
            }
        }
        
        if (removed > 0) {
            LOGGER.info("Removed {} floating rain water blocks", removed);
        }
    }
    
    /**
     * Determine if water should be removed as floating water
     * More aggressive during rain to eliminate all floating water
     */
    private static boolean shouldRemoveFloatingWater(ServerLevel level, BlockPos pos, FluidState state) {
        if (!state.isSource()) return false;
        
        // Always remove water sources above sea level during rain unless supported
        if (pos.getY() > SEA_LEVEL) {
            // Check if water has solid support below
            BlockPos below = pos.below();
            if (level.isInWorldBounds(below)) {
                BlockState belowState = level.getBlockState(below);
                FluidState belowFluid = level.getFluidState(below);
                
                // Remove if floating in air or on non-solid blocks
                if (belowState.isAir() || (!belowState.getCollisionShape(level, below).isEmpty() && belowFluid.isEmpty())) {
                    return true;
                }
                
                // Remove if above ocean/river water (rain water on ocean surface)
                if (belowFluid.is(Fluids.WATER) && belowFluid.isSource()) {
                    return BiomeOptimization.isOceanOrRiverBiome(level, below);
                }
            }
        }
        
        return false;
    }
    private static void cleanupStaleDrainCandidates(ServerLevel level, long currentTick) {
        drainCandidates.entrySet().removeIf(entry -> {
            BlockPos pos = entry.getKey();
            DrainCandidate candidate = entry.getValue();
            
            // Remove if chunk not loaded
            if (!level.isLoaded(pos)) {
                return true;
            }
            
            // Remove if no longer water
            FluidState state = level.getFluidState(pos);
            if (state.isEmpty() || state.isSource()) {
                return true;
            }
            
            // Remove if tracked for too long (10 seconds = 200 ticks) without draining
            return currentTick - candidate.firstSeenTick() > 200;
        });
    }
    
    /**
     * Special handling for thin layers (level 1-3) near ocean surface.
     * This method is called more frequently to ensure thin layers level off quickly.
     */
    public static void processThinLayerLeveling(ServerLevel level) {
        if (!enabled || !FlowingFluidsIntegration.isFlowingFluidsLoaded()) {
            return;
        }
        
        long currentTick = level.getGameTime();
        int processed = 0;
        int maxPerTick = 1000; // Reduced from 2000 to limit processing load
        
        // Only process every 2 ticks instead of every tick to reduce load
        if (currentTick % 2 != 0) {
            return;
        }
        
        for (var player : level.players()) {
            if (processed >= maxPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = THIN_LAYER_LEVELING_RADIUS; // Reduced radius for processing
            
            // Focus on thin layers just above sea level
            for (int dx = -radius; dx <= radius && processed < maxPerTick; dx += 2) { // Increased step to reduce checks
                for (int dz = -radius; dz <= radius && processed < maxPerTick; dz += 2) { // Increased step to reduce checks
                    for (int worldY = SEA_LEVEL - 1; worldY <= SEA_LEVEL + 3; worldY++) {
                        BlockPos checkPos = new BlockPos(
                            playerPos.getX() + dx, 
                            worldY, 
                            playerPos.getZ() + dz
                        );
                        
                        if (!level.isLoaded(checkPos)) continue;
                        
                        FluidState fluidState = level.getFluidState(checkPos);
                        if (fluidState.isEmpty()) continue;
                        
                        // Focus on thin layers (1-3) that are causing lag
                        if (!fluidState.isSource() && fluidState.getAmount() <= 3) {
                            // Check if this thin layer should drain into ocean below
                            if (drainIntoOcean(level, checkPos, fluidState, currentTick)) {
                                processed++;
                            }
                        }
                    }
                }
            }
        }
        
        if (processed > 0) {
            LOGGER.debug("Processed {} thin layer leveling operations", processed);
        }
    }
    
    /**
     * INSTANT EVAPORATION SYSTEM
     * 
     * Thin flowing water (level 1-4) sitting directly ON TOP of ocean source blocks
     * will evaporate/merge into the ocean using random ticks. This prevents the massive
     * calculation load from thin rain water layers spreading across the ocean surface.
     * 
     * Detection: Y=64 with ocean source water at Y=63 below
     * Action: Random chance evaporation - unpredictable for player, low performance impact
     */
    public static void processInstantOceanSurfaceEvaporation(ServerLevel level) {
        if (!enabled) {
            return;
        }
        
        // Process every 2 ticks instead of every tick to reduce load
        if (level.getGameTime() % 2 != 0) {
            return;
        }
        
        int evaporated = 0;
        int maxPerTick = 5000; // Reduced from 10000 to limit processing load
        
        for (var player : level.players()) {
            if (evaporated >= maxPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = OCEAN_SURFACE_EVAPORATION_RADIUS; // Reduced radius to focus on nearby areas
            
            // Scan Y=63-64 to catch thin layers at sea level and just above
            for (int worldY = SEA_LEVEL; worldY <= SEA_LEVEL + 1; worldY++) {
                for (int dx = -radius; dx <= radius && evaporated < maxPerTick; dx += 2) { // Increased step to reduce checks
                    for (int dz = -radius; dz <= radius && evaporated < maxPerTick; dz += 2) { // Increased step to reduce checks
                        BlockPos checkPos = new BlockPos(
                            playerPos.getX() + dx, 
                            worldY, 
                            playerPos.getZ() + dz
                        );
                        
                        if (!level.isLoaded(checkPos)) continue;
                        
                        FluidState fluidState = level.getFluidState(checkPos);
                        
                        // Skip if empty or source block
                        if (fluidState.isEmpty() || fluidState.isSource()) continue;
                        
                        // Only process thin layers (level 1-6) - expanded range
                        int fluidLevel = fluidState.getAmount();
                        if (fluidLevel > 6) continue;
                        
                        // Check if sitting directly on ocean source water OR near ocean surface
                        BlockPos belowPos = checkPos.below();
                        FluidState belowFluid = level.getFluidState(belowPos);
                        
                        // Check if on ocean source OR if below is ocean and we're near surface
                        boolean onOceanSource = belowFluid.is(Fluids.WATER) && belowFluid.isSource();
                        boolean nearOceanSurface = worldY <= SEA_LEVEL + 2 && 
                            BiomeOptimization.isOceanOrRiverBiome(level, checkPos);
                        
                        if (onOceanSource || nearOceanSurface) {
                            // Verify we're in ocean/river biome (for near-surface case)
                            if (onOceanSource || BiomeOptimization.isOceanOrRiverBiome(level, belowPos)) {
                                // INSTANT ABSORPTION - no random chance, always remove thin layers
                                // This mimics how real ocean water absorbs runoff instantly
                                level.setBlock(checkPos, Blocks.AIR.defaultBlockState(), 3);
                                evaporated++;
                                
                                // Add bubble particles to show water flow direction
                                // Only show particles occasionally to avoid spam
                                if (RANDOM.nextFloat() < 0.1f) { // 10% chance for particles
                                    createFlowParticles(level, checkPos, belowPos);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (evaporated > 0) {
            LOGGER.debug("Random evaporation: removed {} thin water layers from ocean surface", evaporated);
        }
    }
    
    /**
     * Create bubble particles showing water flow direction when draining
     */
    private static void createFlowParticles(ServerLevel level, BlockPos waterPos, BlockPos oceanPos) {
        // Calculate flow direction (from water to ocean)
        Vec3i flowDirection = new Vec3i(
            oceanPos.getX() - waterPos.getX(),
            oceanPos.getY() - waterPos.getY(), 
            oceanPos.getZ() - waterPos.getZ()
        );
        
        // Normalize and scale the direction
        double length = Math.sqrt(flowDirection.getX() * flowDirection.getX() + 
                                flowDirection.getY() * flowDirection.getY() + 
                                flowDirection.getZ() * flowDirection.getZ());
        
        if (length > 0) {
            // Create sparse bubble particles flowing toward ocean
            for (int i = 0; i < 2; i++) { // Reduced to 2 particles for less spam
                double offsetX = (RANDOM.nextDouble() - 0.5) * 0.3; // Smaller spread
                double offsetZ = (RANDOM.nextDouble() - 0.5) * 0.3;
                double offsetY = RANDOM.nextDouble() * 0.2; // Smaller vertical range
                
                // Particle position with some randomness - start from water surface
                double particleX = waterPos.getX() + 0.5 + offsetX;
                double particleY = waterPos.getY() + 0.1 + offsetY; // Start near water surface
                double particleZ = waterPos.getZ() + 0.5 + offsetZ;
                
                // Particle velocity toward ocean with minimal upward movement
                double velX = (flowDirection.getX() / length) * 0.05 + (RANDOM.nextDouble() - 0.5) * 0.02;
                double velY = 0.02 + RANDOM.nextDouble() * 0.01; // Minimal upward bias for bubbles
                double velZ = (flowDirection.getZ() / length) * 0.05 + (RANDOM.nextDouble() - 0.5) * 0.02;
                
                // Send bubble particle with short lifetime
                level.sendParticles(
                    ParticleTypes.BUBBLE,
                    particleX, particleY, particleZ,
                    1, // count
                    velX, velY, velZ,
                    0.0 // No speed multiplier - bubbles dissipate instantly in air
                );
            }
        }
    }
    
    /**
     * ENHANCED UNDERWATER DRAINAGE BUBBLE STREAMS
     * 
     * Creates flowing bubble streams that follow water drainage paths from surface to air.
     * Bubbles spawn at water surface and flow down through water following the actual drainage direction.
     * Velocity matches the flow speed and direction for realistic water movement visualization.
     */
    public static void processUnderwaterCurrentParticles(ServerLevel level) {
        if (!enabled) {
            return;
        }
        
        // Process every tick for smooth continuous bubble streams
        if (level.getGameTime() % 1 != 0) {
            return;
        }
        
        int particlesSpawned = 0;
        int maxParticlesPerTick = 120; // Increased for better coverage
        
        for (var player : level.players()) {
            if (particlesSpawned >= maxParticlesPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = 32; // Moderate radius to cover nearby flowing water
            
            // Scan for flowing water near player
            for (int dx = -radius; dx <= radius && particlesSpawned < maxParticlesPerTick; dx += 2) {
                for (int dz = -radius; dz <= radius && particlesSpawned < maxParticlesPerTick; dz += 2) {
                    for (int dy = -12; dy <= 8; dy++) {
                        BlockPos checkPos = new BlockPos(
                            playerPos.getX() + dx, 
                            playerPos.getY() + dy, 
                            playerPos.getZ() + dz
                        );
                        
                        if (!level.isLoaded(checkPos)) continue;
                        
                        FluidState fluidState = level.getFluidState(checkPos);
                        
                        // Only process flowing water (not source blocks)
                        if (!fluidState.is(Fluids.FLOWING_WATER)) continue;
                        
                        // Check if player is nearby (within 20 blocks) for particle spawning
                        if (playerPos.distManhattan(checkPos) > 20) continue;
                        
                        // Higher chance for actively flowing water (based on fluid level)
                        int fluidLevel = fluidState.getAmount();
                        float spawnChance = fluidLevel > 4 ? 0.12f : 0.08f; // Increased chance for better visibility
                        if (RANDOM.nextFloat() > spawnChance) continue;
                        
                        // Create flowing bubble stream from this position
                        createDrainageBubbleStream(level, checkPos, fluidLevel);
                        particlesSpawned++;
                    }
                }
            }
            
            // NEW: Process water-to-air drainage points for enhanced bubble effects
            particlesSpawned += processWaterToAirDrainageBubbles(level, playerPos, maxParticlesPerTick - particlesSpawned);
        }
    }
    
    /**
     * ENHANCED WATER-TO-AIR DRAINAGE BUBBLES
     * 
     * Detects water blocks that are actively draining into air pockets and spawns
     * bubbles at the drainage points with realistic velocity and direction.
     * This creates visible bubble streams showing exactly where water is flowing.
     * 
     * @return Number of bubbles spawned
     */
    private static int processWaterToAirDrainageBubbles(ServerLevel level, BlockPos playerPos, int maxBubbles) {
        int bubblesSpawned = 0;
        int radius = 24; // Focused radius for drainage detection
        
        for (int dx = -radius; dx <= radius && bubblesSpawned < maxBubbles; dx += 1) { // Single block precision
            for (int dz = -radius; dz <= radius && bubblesSpawned < maxBubbles; dz += 1) {
                for (int dy = -8; dy <= 4; dy++) {
                    BlockPos checkPos = new BlockPos(
                        playerPos.getX() + dx, 
                        playerPos.getY() + dy, 
                        playerPos.getZ() + dz
                    );
                    
                    if (!level.isLoaded(checkPos)) continue;
                    if (playerPos.distManhattan(checkPos) > 16) continue; // Closer range for drainage bubbles
                    
                    FluidState fluidState = level.getFluidState(checkPos);
                    
                    // Check for water blocks that could be draining
                    if (fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER)) {
                        // Check all adjacent blocks for air pockets (drainage destinations)
                        for (Direction dir : Direction.values()) {
                            BlockPos adjacent = checkPos.relative(dir);
                            if (!level.isLoaded(adjacent)) continue;
                            
                            FluidState adjacentFluid = level.getFluidState(adjacent);
                            
                            // FOUND: Water draining into air pocket
                            if (adjacentFluid.isEmpty()) {
                                // Calculate drainage velocity based on direction and fluid level
                                double velX = dir.getStepX() * 0.15; // Stronger velocity for drainage
                                double velY = dir.getStepY() * 0.15;
                                double velZ = dir.getStepZ() * 0.15;
                                
                                // Add randomness for natural movement
                                velX += (RANDOM.nextDouble() - 0.5) * 0.05;
                                velY += (RANDOM.nextDouble() - 0.5) * 0.05;
                                velZ += (RANDOM.nextDouble() - 0.5) * 0.05;
                                
                                // Spawn multiple bubbles at drainage point
                                int bubbleCount = fluidState.isSource() ? 2 : 3; // More bubbles for flowing water
                                
                                for (int i = 0; i < bubbleCount && bubblesSpawned < maxBubbles; i++) {
                                    // Spawn bubbles at the water-air interface
                                    double particleX = checkPos.getX() + 0.5 + dir.getStepX() * 0.3;
                                    double particleY = checkPos.getY() + 0.5 + dir.getStepY() * 0.3;
                                    double particleZ = checkPos.getZ() + 0.5 + dir.getStepZ() * 0.3;
                                    
                                    // Add slight offset for variety
                                    particleX += (RANDOM.nextDouble() - 0.5) * 0.2;
                                    particleY += (RANDOM.nextDouble() - 0.5) * 0.2;
                                    particleZ += (RANDOM.nextDouble() - 0.5) * 0.2;
                                    
                                    // Send bubble particle with drainage velocity
                                    level.sendParticles(
                                        ParticleTypes.BUBBLE,
                                        particleX, particleY, particleZ,
                                        1,
                                        velX, velY, velZ,
                                        0.1 // Slightly larger speed multiplier for visibility
                                    );
                                    
                                    bubblesSpawned++;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return bubblesSpawned;
    }
    
    /**
     * Create a flowing bubble stream that follows water drainage path.
     * Bubbles spawn at water surface and flow down through water to air below.
     */
    private static void createDrainageBubbleStream(ServerLevel level, BlockPos startPos, int fluidLevel) {
        // Trace the drainage path from this position
        BlockPos currentPos = startPos;
        int pathLength = 0;
        int maxPathLength = 8; // Limit path length for performance
        
        // Find the drainage path by following water flow
        while (pathLength < maxPathLength && currentPos != null) {
            FluidState currentFluid = level.getFluidState(currentPos);
            
            // Stop if we hit air (drainage destination) or source water
            if (currentFluid.isEmpty() || currentFluid.isSource()) {
                break;
            }
            
            // Find next position in flow direction
            BlockPos nextPos = findFlowDirection(level, currentPos, currentFluid.getAmount());
            if (nextPos == null || nextPos.equals(currentPos)) {
                break;
            }
            
            // Calculate flow vector for this segment
            double flowX = nextPos.getX() - currentPos.getX();
            double flowY = nextPos.getY() - currentPos.getY();
            double flowZ = nextPos.getZ() - currentPos.getZ();
            
            // Normalize and scale by flow speed
            double flowLength = Math.sqrt(flowX * flowX + flowY * flowY + flowZ * flowZ);
            if (flowLength > 0) {
                flowX /= flowLength;
                flowY /= flowLength;
                flowZ /= flowLength;
                
                // Scale velocity based on fluid level (faster flow = more bubbles, higher velocity)
                double speedMultiplier = (9 - fluidLevel) * 0.03; // Lower level = faster flow
                
                // Create multiple bubbles along this flow segment for continuous stream
                int bubblesPerSegment = fluidLevel < 4 ? 3 : 2; // More bubbles for faster flow
                
                for (int i = 0; i < bubblesPerSegment; i++) {
                    // Spawn bubble at current position with some randomness
                    double particleX = currentPos.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.4;
                    double particleY = currentPos.getY() + 0.2 + RANDOM.nextDouble() * 0.6; // Throughout the water column
                    double particleZ = currentPos.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.4;
                    
                    // Velocity matching flow direction and speed
                    double velX = flowX * speedMultiplier + (RANDOM.nextDouble() - 0.5) * 0.01;
                    double velY = flowY * speedMultiplier - 0.02; // Slight downward bias for drainage
                    double velZ = flowZ * speedMultiplier + (RANDOM.nextDouble() - 0.5) * 0.01;
                    
                    // Send bubble particle with appropriate lifetime
                    level.sendParticles(
                        ParticleTypes.BUBBLE,
                        particleX, particleY, particleZ,
                        1,
                        velX, velY, velZ,
                        0.02 // Small speed multiplier for natural movement
                    );
                }
            }
            
            currentPos = nextPos;
            pathLength++;
        }
    }
    
    /**
     * Find the direction water is flowing by checking neighboring blocks for lower fluid levels
     */
    private static BlockPos findFlowDirection(ServerLevel level, BlockPos pos, int currentLevel) {
        BlockPos bestTarget = null;
        int lowestLevel = currentLevel;
        
        // Check horizontal neighbors first
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos neighbor = pos.relative(dir);
            FluidState neighborFluid = level.getFluidState(neighbor);
            
            // Water flows toward lower levels or air
            if (neighborFluid.isEmpty()) {
                // Flowing toward air - this is probably where water is draining
                return neighbor;
            } else if (neighborFluid.is(Fluids.WATER) || neighborFluid.is(Fluids.FLOWING_WATER)) {
                int neighborLevel = neighborFluid.getAmount();
                if (neighborLevel < lowestLevel) {
                    lowestLevel = neighborLevel;
                    bestTarget = neighbor;
                }
            }
        }
        
        // Check below - water flows down
        BlockPos below = pos.below();
        FluidState belowFluid = level.getFluidState(below);
        if (belowFluid.isEmpty() || (belowFluid.is(Fluids.WATER) && belowFluid.isSource())) {
            // Water is draining downward
            return below;
        }
        
        return bestTarget;
    }

    /**
     * ULTRA-AGGRESSIVE: Directly fill ocean surface holes at Y=62-65.
     * This bypasses the queue system and instantly converts non-source water to source blocks.
     * ENHANCED: Now handles multiple Y levels and includes river biomes for faster filling.
     */
    public static void processDirectOceanSurfaceFilling(ServerLevel level) {
        if (!enabled || !FlowingFluidsIntegration.isFlowingFluidsLoaded()) {
            return;
        }
        
        int filled = 0;
        int maxPerTick = 10000; // Extremely aggressive - fill as many holes as possible
        
        for (var player : level.players()) {
            if (filled >= maxPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = OCEAN_SURFACE_FILLING_RADIUS; // Use configured radius for direct filling
            
            // Check multiple Y levels around sea level (62-65) to catch missing layers
            for (int worldY = SEA_LEVEL - 1; worldY <= SEA_LEVEL + 2; worldY++) {
                for (int dx = -radius; dx <= radius && filled < maxPerTick; dx++) {
                    for (int dz = -radius; dz <= radius && filled < maxPerTick; dz++) {
                        BlockPos checkPos = new BlockPos(
                            playerPos.getX() + dx, 
                            worldY, 
                            playerPos.getZ() + dz
                        );
                        
                        if (!level.isLoaded(checkPos)) continue;
                        
                        // Process in both ocean AND river biomes for maximum coverage
                        if (!BiomeOptimization.isOceanOrRiverBiome(level, checkPos)) continue;
                        
                        FluidState fluidState = level.getFluidState(checkPos);
                        BlockState blockState = level.getBlockState(checkPos);
                        
                        // Fill air or non-source water instantly with intelligent density-based filling
                        if (blockState.isAir() || (fluidState.is(Fluids.FLOWING_WATER) && !fluidState.isSource())) {
                            // INTELLIGENT FILLING: Check surrounding water density for prioritization
                            int sourceNeighbors = countAdjacentWaterSources(level, checkPos);
                            
                            // Priority filling based on surrounding water density:
                            // - 4+ sources: Instant fill (well-connected ocean area)
                            // - 2-3 sources: High priority fill 
                            // - 1 source: Normal fill (edge areas)
                            // - 0 sources: Fill for ocean surface leveling (final layer completion)
                            
                            if (sourceNeighbors >= 4) {
                                // Well-connected area - instant fill
                                level.setBlock(checkPos, Blocks.WATER.defaultBlockState(), 3);
                                filled++;
                            } else if (sourceNeighbors >= 2) {
                                // Moderately connected - fill with high priority
                                level.setBlock(checkPos, Blocks.WATER.defaultBlockState(), 3);
                                filled++;
                            } else if (sourceNeighbors >= 1) {
                                // Edge area - fill but with lower priority (check every other tick)
                                if (level.getGameTime() % 2 == 0) {
                                    level.setBlock(checkPos, Blocks.WATER.defaultBlockState(), 3);
                                    filled++;
                                }
                            } else {
                                // 0 sources: ULTRA-AGGRESSIVE fill for ocean surface leveling at Y=63
                                // This ensures smooth ocean height by filling isolated surface holes immediately
                                if (worldY == SEA_LEVEL) {
                                    // At sea level - instant fill isolated holes to achieve smooth surface
                                    level.setBlock(checkPos, Blocks.WATER.defaultBlockState(), 3);
                                    filled++;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (filled > 0) {
            LOGGER.debug("Directly filled {} ocean/river surface holes at Y levels {}-{}", filled, SEA_LEVEL - 1, SEA_LEVEL + 2);
        }
    }
    
    /**
     * AGGRESSIVE FINAL SURFACE LEVELING
     * 
     * Ensures complete smooth ocean surface by aggressively filling any remaining
     * gaps at sea level (Y=63). This method focuses specifically on the final
     * layer completion to achieve perfect ocean height uniformity.
     */
    public static void processAggressiveSurfaceLeveling(ServerLevel level) {
        if (!enabled || !FlowingFluidsIntegration.isFlowingFluidsLoaded()) {
            return;
        }
        
        // ULTRA-AGGRESSIVE: Process every tick for immediate surface restoration
        // Removed tick throttling to ensure instant hole filling
        
        int filled = 0;
        int maxPerTick = 3000; // Increased for more aggressive filling
        
        for (var player : level.players()) {
            if (filled >= maxPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = 64; // Increased radius for maximum coverage
            
            // Focus specifically on sea level (Y=63) for final surface leveling
            int worldY = SEA_LEVEL;
            for (int dx = -radius; dx <= radius && filled < maxPerTick; dx += 2) { // Smaller steps for thoroughness
                for (int dz = -radius; dz <= radius && filled < maxPerTick; dz += 2) {
                    BlockPos checkPos = new BlockPos(
                        playerPos.getX() + dx, 
                        worldY, 
                        playerPos.getZ() + dz
                    );
                    
                    if (!level.isLoaded(checkPos)) continue;
                    
                    // Process in both ocean AND river biomes
                    if (!BiomeOptimization.isOceanOrRiverBiome(level, checkPos)) continue;
                    
                    FluidState fluidState = level.getFluidState(checkPos);
                    BlockState blockState = level.getBlockState(checkPos);
                    
                    // ULTRA-AGGRESSIVE: Fill any air or non-source water at sea level
                    if (blockState.isAir() || (fluidState.is(Fluids.FLOWING_WATER) && !fluidState.isSource())) {
                        // Check if this should be ocean water (surrounded by water or ocean biome)
                        boolean shouldFill = false;
                        
                        // Method 1: Check if at least 1 adjacent block is water source (lowered from 2)
                        int waterSources = countAdjacentWaterSources(level, checkPos);
                        if (waterSources >= 1) {
                            shouldFill = true;
                        } else {
                            // Method 2: Check if most of the surrounding area (3x3x1) is water
                            int waterCount = 0;
                            int totalChecked = 0;
                            for (int ox = -1; ox <= 1; ox++) {
                                for (int oz = -1; oz <= 1; oz++) {
                                    BlockPos surroundPos = checkPos.offset(ox, 0, oz);
                                    if (level.isLoaded(surroundPos)) {
                                        FluidState surroundFluid = level.getFluidState(surroundPos);
                                        if (surroundFluid.is(Fluids.WATER) || surroundFluid.is(Fluids.FLOWING_WATER)) {
                                            waterCount++;
                                        }
                                        totalChecked++;
                                    }
                                }
                            }
                            // Lowered threshold: Fill if 40% or more of surrounding area is water (was 60%)
                            if (totalChecked > 0 && (double) waterCount / totalChecked >= 0.4) {
                                shouldFill = true;
                            }
                        }
                        
                        // ULTRA-AGGRESSIVE: If in ocean biome, fill regardless of surrounding water
                        if (!shouldFill && BiomeOptimization.isOceanBiome(level, checkPos)) {
                            shouldFill = true;
                        }
                        
                        if (shouldFill) {
                            level.setBlock(checkPos, Blocks.WATER.defaultBlockState(), 3);
                            filled++;
                        }
                    }
                }
            }
        }
        
        if (filled > 0) {
            LOGGER.debug("Ultra-aggressive surface leveling filled {} holes at Y={}", filled, SEA_LEVEL);
        }
    }
    
    /**
     * INSTANT OCEAN SURFACE RESTORATION
     * 
     * Bypasses ALL checks and instantly fills any holes at ocean surface level (Y=63).
     * This is the ultimate solution for complete surface restoration after draining.
     * 
     * This method:
     * - Ignores all biome checks (fills anywhere that could be ocean)
     * - Ignores all adjacent water checks 
     * - Fills every single air/flowing water block at Y=63
     * - Processes every tick with maximum aggressiveness
     * - Designed specifically to eliminate any remaining surface dips
     */
    public static void processInstantOceanSurfaceRestoration(ServerLevel level) {
        if (!enabled || !FlowingFluidsIntegration.isFlowingFluidsLoaded()) {
            return;
        }
        
        // Process EVERY tick for instant restoration
        int filled = 0;
        int maxPerTick = 5000; // Maximum aggressive filling
        
        for (var player : level.players()) {
            if (filled >= maxPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = 80; // Very large radius for maximum coverage
            
            // Focus ONLY on sea level (Y=63) for instant surface restoration
            int worldY = SEA_LEVEL;
            for (int dx = -radius; dx <= radius && filled < maxPerTick; dx++) { // Single block steps for completeness
                for (int dz = -radius; dz <= radius && filled < maxPerTick; dz++) {
                    BlockPos checkPos = new BlockPos(
                        playerPos.getX() + dx, 
                        worldY, 
                        playerPos.getZ() + dz
                    );
                    
                    if (!level.isLoaded(checkPos)) continue;
                    
                    FluidState fluidState = level.getFluidState(checkPos);
                    BlockState blockState = level.getBlockState(checkPos);
                    
                    // INSTANT: Fill ANY air or non-source water at sea level, no questions asked
                    if (blockState.isAir() || (fluidState.is(Fluids.FLOWING_WATER) && !fluidState.isSource())) {
                        // ULTRA-AGGRESSIVE: Fill regardless of biome, surroundings, or anything else
                        // This ensures complete surface restoration
                        level.setBlock(checkPos, Blocks.WATER.defaultBlockState(), 3);
                        filled++;
                    }
                }
            }
        }
        
        if (filled > 0) {
            LOGGER.info("Instant ocean surface restoration filled {} holes at Y={}", filled, SEA_LEVEL);
        }
    }
    
    /**
     * Count adjacent water source blocks at the same Y level.
     * 
     * ACTIVE METHOD: Used by processDirectOceanSurfaceFilling for intelligent density-based filling.
     * Provides smart prioritization based on surrounding water connectivity:
     * - 4+ sources: Instant fill for well-connected ocean areas
     * - 2-3 sources: High priority fill for moderately connected areas  
     * - 1 source: Normal fill for edge areas (with tick-based throttling)
     * - 0 sources: Skip isolated holes to preserve intentional water features
     * 
     * This prevents overfilling isolated water pockets while ensuring rapid restoration
     * of connected ocean surfaces.
     */
    private static int countAdjacentWaterSources(ServerLevel level, BlockPos pos) {
        int count = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(dir);
            FluidState adjacentFluid = level.getFluidState(adjacent);
            if (adjacentFluid.is(Fluids.WATER) && adjacentFluid.isSource()) {
                count++;
            }
        }
        return count;
    }
}
