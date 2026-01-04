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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AGGRESSIVE OCEAN FLOW ACCELERATOR
 * 
 * Addresses the issue where massive ocean surfaces create subtle flow value differences
 * across large areas, making fluid movement to the center extremely slow.
 * 
 * This system:
 * 1. Detects large flat ocean areas with similar flow values
 * 2. Creates artificial flow gradients towards the center
 * 3. Accelerates fluid movement in ocean biomes
 * 4. Maintains realistic water behavior while improving performance
 */
@SuppressWarnings("all")
public class OceanFlowAccelerator {
    
    private static final Logger LOGGER = LogManager.getLogger(OceanFlowAccelerator.class);
    
    // Configuration for aggressive ocean flow acceleration
    private static final int OCEAN_FLOW_ACCELERATION_RADIUS = 64; // Large radius for ocean areas
    private static final int MIN_OCEAN_BLOCKS_FOR_ACCELERATION = 100; // Minimum blocks to trigger acceleration
    private static final double FLOW_ACCELERATION_MULTIPLIER = 3.0; // Triple the flow speed
    private static final double CENTER_PULL_STRENGTH = 0.15; // Additional pull towards center
    private static final int PROCESSING_INTERVAL = 4; // Process every 4 ticks to balance performance
    
    // Cache for ocean center points to avoid recalculation
    private static final ConcurrentHashMap<String, BlockPos> oceanCenterCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<BlockPos, Long> lastProcessedTime = new ConcurrentHashMap<>();
    
    // Performance tracking
    private static final AtomicInteger acceleratedFlowsThisTick = new AtomicInteger(0);
    private static final AtomicInteger oceanAreasDetected = new AtomicInteger(0);
    
    /**
     * Main processing method - accelerates fluid flow in large ocean areas
     */
    public static void processOceanFlowAcceleration(ServerLevel level) {
        if (!FlowingFluidsIntegration.isFlowingFluidsLoaded()) {
            return;
        }
        
        // Only process on interval to maintain performance
        if (level.getGameTime() % PROCESSING_INTERVAL != 0) {
            return;
        }
        
        acceleratedFlowsThisTick.set(0);
        oceanAreasDetected.set(0);
        
        // Process each player's vicinity for ocean flow acceleration
        for (var player : level.players()) {
            BlockPos playerPos = player.blockPosition();
            processOceanAreaAroundPlayer(level, playerPos);
        }
        
        if (oceanAreasDetected.get() > 0) {
            LOGGER.debug("Ocean flow acceleration: {} areas processed, {} flows accelerated", 
                oceanAreasDetected.get(), acceleratedFlowsThisTick.get());
        }
    }
    
    /**
     * Process ocean area around a player for flow acceleration
     */
    private static void processOceanAreaAroundPlayer(ServerLevel level, BlockPos playerPos) {
        // Check if we're in an ocean biome
        if (!BiomeOptimization.isOceanOrRiverBiome(level, playerPos)) {
            return;
        }
        
        // Find or calculate ocean center for this area
        String areaKey = getAreaKey(playerPos);
        BlockPos oceanCenter = oceanCenterCache.computeIfAbsent(areaKey, k -> findOceanCenter(level, playerPos));
        
        if (oceanCenter == null) {
            return; // No suitable ocean center found
        }
        
        oceanAreasDetected.incrementAndGet();
        
        // Process flow acceleration in radius around ocean center
        int processed = 0;
        int maxProcessing = 500; // Limit processing per area for performance
        
        for (int dx = -OCEAN_FLOW_ACCELERATION_RADIUS; dx <= OCEAN_FLOW_ACCELERATION_RADIUS && processed < maxProcessing; dx += 3) {
            for (int dz = -OCEAN_FLOW_ACCELERATION_RADIUS; dz <= OCEAN_FLOW_ACCELERATION_RADIUS && processed < maxProcessing; dz += 3) {
                for (int dy = -2; dy <= 2 && processed < maxProcessing; dy++) {
                    BlockPos checkPos = oceanCenter.offset(dx, dy, dz);
                    
                    if (!level.isLoaded(checkPos)) continue;
                    
                    // Check if this position needs flow acceleration
                    if (shouldAccelerateFlowAt(level, checkPos, oceanCenter)) {
                        accelerateFlowAtPosition(level, checkPos, oceanCenter);
                        processed++;
                        acceleratedFlowsThisTick.incrementAndGet();
                    }
                }
            }
        }
    }
    
    /**
     * Find the center of a large ocean area
     */
    private static BlockPos findOceanCenter(ServerLevel level, BlockPos startPos) {
        int oceanBlocks = 0;
        BlockPos centerSum = BlockPos.ZERO;
        int searchRadius = 32;
        
        // Sample ocean blocks to find center
        for (int dx = -searchRadius; dx <= searchRadius; dx += 2) {
            for (int dz = -searchRadius; dz <= searchRadius; dz += 2) {
                BlockPos checkPos = startPos.offset(dx, 0, dz);
                if (level.isLoaded(checkPos) && isOceanWaterBlock(level, checkPos)) {
                    centerSum = centerSum.offset(checkPos.getX(), checkPos.getY(), checkPos.getZ());
                    oceanBlocks++;
                }
            }
        }
        
        // Only return center if we found enough ocean blocks
        if (oceanBlocks >= MIN_OCEAN_BLOCKS_FOR_ACCELERATION) {
            return new BlockPos(
                centerSum.getX() / oceanBlocks,
                startPos.getY(),
                centerSum.getZ() / oceanBlocks
            );
        }
        
        return null;
    }
    
    /**
     * Check if flow should be accelerated at this position
     */
    private static boolean shouldAccelerateFlowAt(ServerLevel level, BlockPos pos, BlockPos oceanCenter) {
        // Must be ocean water
        if (!isOceanWaterBlock(level, pos)) {
            return false;
        }
        
        // Check if recently processed (avoid duplicate work)
        long currentTime = level.getGameTime();
        Long lastProcessed = lastProcessedTime.get(pos);
        if (lastProcessed != null && currentTime - lastProcessed < 20) { // 20 second cooldown
            return false;
        }
        
        // Check if flow values are too similar (indicating slow movement)
        FluidState fluidState = level.getFluidState(pos);
        if (!fluidState.is(Fluids.FLOWING_WATER) || fluidState.isSource()) {
            return false; // Only accelerate flowing water, not source blocks
        }
        
        // Check surrounding flow values for similarity
        int similarFlowCount = countSimilarFlowNeighbors(level, pos);
        return similarFlowCount >= 4; // Accelerate if most neighbors have similar flow
    }
    
    /**
     * Count neighbors with similar flow values
     */
    private static int countSimilarFlowNeighbors(ServerLevel level, BlockPos pos) {
        int currentLevel = level.getFluidState(pos).getAmount();
        int similarCount = 0;
        
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            if (level.isLoaded(neighborPos)) {
                FluidState neighborFluid = level.getFluidState(neighborPos);
                if (neighborFluid.is(Fluids.FLOWING_WATER)) {
                    int neighborLevel = neighborFluid.getAmount();
                    // Consider similar if within 1-2 levels
                    if (Math.abs(currentLevel - neighborLevel) <= 2) {
                        similarCount++;
                    }
                }
            }
        }
        
        return similarCount;
    }
    
    /**
     * Accelerate flow at a specific position towards the ocean center
     */
    private static void accelerateFlowAtPosition(ServerLevel level, BlockPos pos, BlockPos oceanCenter) {
        FluidState currentFluid = level.getFluidState(pos);
        if (!currentFluid.is(Fluids.FLOWING_WATER)) {
            return;
        }
        
        // Calculate direction towards ocean center
        double dx = oceanCenter.getX() - pos.getX();
        double dz = oceanCenter.getZ() - pos.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        if (distance < 1.0) return; // Already at center
        
        // Normalize direction
        dx /= distance;
        dz /= distance;
        
        // Calculate new flow level with acceleration
        int currentLevel = currentFluid.getAmount();
        int newLevel = currentLevel;
        
        // Apply center pull - increase flow towards center
        if (dx > 0.1) { // Flow east
            newLevel = Math.max(1, currentLevel - 1);
        } else if (dx < -0.1) { // Flow west
            newLevel = Math.max(1, currentLevel - 1);
        }
        
        if (dz > 0.1) { // Flow south
            newLevel = Math.max(1, currentLevel - 1);
        } else if (dz < -0.1) { // Flow north
            newLevel = Math.max(1, currentLevel - 1);
        }
        
        // Apply acceleration multiplier for more aggressive flow
        if (distance > 16) { // Far from center, accelerate more
            newLevel = Math.max(1, (int)(newLevel * FLOW_ACCELERATION_MULTIPLIER));
        }
        
        // Update the block if flow level changed
        if (newLevel != currentLevel && newLevel > 0 && newLevel < 8) {
            BlockState newState = Blocks.WATER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 8 - newLevel);
            
            level.setBlock(pos, newState, 3); // Update with block flags for proper propagation
            lastProcessedTime.put(pos, level.getGameTime());
        }
    }
    
    /**
     * Check if a position contains ocean water
     */
    private static boolean isOceanWaterBlock(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        
        FluidState fluidState = level.getFluidState(pos);
        BlockState blockState = level.getBlockState(pos);
        
        // Must be water (source or flowing) in ocean biome
        boolean isWater = fluidState.is(Fluids.WATER) || fluidState.is(Fluids.FLOWING_WATER);
        boolean isInOcean = BiomeOptimization.isOceanOrRiverBiome(level, pos);
        
        return isWater && isInOcean;
    }
    
    /**
     * Generate area key for caching ocean centers
     */
    private static String getAreaKey(BlockPos pos) {
        // Divide position into chunks to cache ocean centers by area
        int chunkX = pos.getX() / 64;
        int chunkZ = pos.getZ() / 64;
        return "ocean_" + chunkX + "_" + chunkZ;
    }
    
    /**
     * Clear caches to prevent memory leaks
     */
    public static void clearCaches() {
        oceanCenterCache.clear();
        lastProcessedTime.clear();
        LOGGER.info("Ocean flow accelerator caches cleared");
    }
    
    /**
     * Get performance statistics
     */
    public static String getPerformanceStats() {
        return String.format("OceanFlowAccelerator: Areas=%d, Accelerated=%d, CentersCached=%d", 
            oceanAreasDetected.get(), acceleratedFlowsThisTick.get(), oceanCenterCache.size());
    }
}
