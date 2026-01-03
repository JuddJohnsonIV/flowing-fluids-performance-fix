package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

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
    private static final ConcurrentLinkedQueue<ReplenishmentTask> replenishmentQueue = new ConcurrentLinkedQueue<>();
    
    // Track positions currently being replenished to avoid duplicates
    private static final Set<BlockPos> activeReplenishment = ConcurrentHashMap.newKeySet();
    
    // Track water positions above ocean that might need draining
    // Maps position -> (tick when first seen, fluid level when first seen)
    private static final Map<BlockPos, DrainCandidate> drainCandidates = new ConcurrentHashMap<>();
    
    // How long water must sit still before draining (in ticks) - 1.5 seconds = 30 ticks
    private static final int DRAIN_DELAY_TICKS = 30;
    
    // Max drain candidates to track
    private static final int MAX_DRAIN_CANDIDATES = 5000;
    
    // Configuration values (can be modified via config)
    private static float oceanReplenishRate = 0.20f; // Reduced from 0.40f to allow natural leveling
    private static float riverReplenishRate = 0.15f; // Reduced from 0.30f to allow natural leveling
    private static int maxReplenishmentsPerTick = 50; // Reduced from 100 to reduce interference
    private static boolean enabled = true;
    
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
        
        // Must be at or below sea level
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
        
        // Get appropriate replenishment rate based on biome
        float rate = BiomeOptimization.isOceanBiome(level, pos) ? oceanReplenishRate : riverReplenishRate;
        
        ReplenishmentTask task = new ReplenishmentTask(pos, currentState.getAmount(), rate);
        replenishmentQueue.offer(task);
        activeReplenishment.add(pos);
        
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
            if (RANDOM.nextFloat() > task.replenishRate()) {
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
            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            return true;
        }
        
        // If partially filled water, increase level toward source
        if (currentFluid.is(Fluids.FLOWING_WATER)) {
            int currentAmount = currentFluid.getAmount();
            int newAmount = Math.min(currentAmount + 2, 8);
            
            if (newAmount >= 8) {
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
            } else {
                level.setBlock(pos, Fluids.WATER.getFlowing(newAmount, false).createLegacyBlock(), 3);
            }
            return true;
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
            replenishmentQueue.clear();
            activeReplenishment.clear();
        }
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
        
        // Check if enough time has passed (reduced from 20 to 10 ticks for faster drainage)
        long ticksWaiting = currentTick - candidate.firstSeenTick();
        return ticksWaiting >= 10; // 0.5 seconds instead of 1 second
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
        int maxPerTick = 800; // Increased from 500 for more aggressive thin layer removal
        
        // Clean up stale entries more frequently during rain (every 100 ticks)
        int cleanupInterval = level.isRaining() ? 100 : 200;
        if (currentTick % cleanupInterval == 0) {
            cleanupStaleDrainCandidates(level, currentTick);
        }
        
        // Process all players, but be extra aggressive during rain
        for (var player : level.players()) {
            if (drained >= maxPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = level.isRaining() ? 64 : 48; // Larger radius during rain
            
            // Scan ABOVE sea level with larger range during rain
            int maxY = level.isRaining() ? SEA_LEVEL + 15 : SEA_LEVEL + 8;
            for (int dx = -radius; dx <= radius && drained < maxPerTick; dx += 1) { // Reduced step from 2 to 1
                for (int dz = -radius; dz <= radius && drained < maxPerTick; dz += 1) { // Reduced step from 2 to 1
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
     * Enhanced rain water detection system to completely eliminate floating water
     * This method is called more frequently during rain to catch floating water immediately
     */
    public static void processRainWaterRemoval(ServerLevel level) {
        if (!enabled || !level.isRaining()) {
            return;
        }
        
        int removed = 0;
        int maxPerTick = 1000; // Very aggressive during rain
        
        for (var player : level.players()) {
            if (removed >= maxPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = 80; // Large radius to catch all floating water
            
            // Scan for ANY floating water sources during rain
            for (int dx = -radius; dx <= radius; dx += 1) {
                for (int dz = -radius; dz <= radius; dz += 1) {
                    for (int worldY = SEA_LEVEL + 1; worldY <= SEA_LEVEL + 20; worldY++) {
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
        int maxPerTick = 1200; // Very aggressive for thin layers
        
        // Only process every 4 ticks to balance performance
        if (currentTick % 4 != 0) {
            return;
        }
        
        for (var player : level.players()) {
            if (processed >= maxPerTick) break;
            
            BlockPos playerPos = player.blockPosition();
            int radius = 32; // Smaller radius but more frequent
            
            // Focus on thin layers just above sea level
            for (int dx = -radius; dx <= radius && processed < maxPerTick; dx += 1) {
                for (int dz = -radius; dz <= radius && processed < maxPerTick; dz += 1) {
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
     * Get drain candidate count for debugging
     */
    public static int getDrainCandidateCount() {
        return drainCandidates.size();
    }
}
