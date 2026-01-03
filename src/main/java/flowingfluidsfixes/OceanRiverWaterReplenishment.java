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
    
    // Configuration values (can be modified via config)
    private static float oceanReplenishRate = 0.15f;
    private static float riverReplenishRate = 0.10f;
    private static int maxReplenishmentsPerTick = 50;
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
}
