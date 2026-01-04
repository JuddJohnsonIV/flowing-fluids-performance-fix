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
    
    // Configuration constants
    private static final int OCEAN_FLOW_ACCELERATION_RADIUS = 64; // Radius to search for ocean areas
    private static final int MIN_OCEAN_BLOCKS_FOR_ACCELERATION = 100; // Minimum ocean blocks to trigger acceleration
    private static final double FLOW_ACCELERATION_MULTIPLIER = 5.0; // Increased from 3.0 for more aggressive acceleration
    private static final double CENTER_PULL_STRENGTH = 0.3; // Increased from 0.15 for stronger center pulling
    private static final int PROCESSING_INTERVAL = 2; // Process every 2 ticks instead of 4 for more frequent updates
    
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
        
        // Reset per-tick counters
        acceleratedFlowsThisTick.set(0);
        oceanAreasDetected.set(0);
        
        // Get all players in the overworld
        var players = level.getPlayers(player -> 
            player.level() == level && 
            player.isAlive() && 
            !player.isSpectator()
        );
        
        if (players.isEmpty()) {
            return; // No players to optimize around
        }
        
        // Process ocean areas around each player
        for (var player : players) {
            try {
                processOceanAreaAroundPlayer(level, player.blockPosition());
            } catch (Exception e) {
                LOGGER.error("Error processing ocean flow acceleration for player at {}: {}", 
                    player.blockPosition(), e.getMessage());
                // Continue with other players
            }
        }
        
        // Log performance metrics
        if (acceleratedFlowsThisTick.get() > 0) {
            LOGGER.debug("Ocean flow acceleration: {} flows accelerated, {} ocean areas processed", 
                acceleratedFlowsThisTick.get(), oceanAreasDetected.get());
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
        
        // Find or calculate ocean hole center for this area
        String areaKey = getAreaKey(playerPos);
        BlockPos holeCenter = oceanCenterCache.computeIfAbsent(areaKey, k -> findOceanHoleCenter(level, playerPos));
        
        if (holeCenter == null) {
            return; // No suitable ocean hole found
        }
        
        // Find the center of flowing fluids that should be pulled toward the hole
        BlockPos flowCenter = findFlowingFluidCenter(level, holeCenter);
        
        if (flowCenter == null) {
            return; // No flowing fluids found to accelerate
        }
        
        oceanAreasDetected.incrementAndGet();
        
        // Process flow acceleration from flowing fluid center toward hole center
        int processed = 0;
        int maxProcessing = 200; // Reduced for better performance
        
        LOGGER.debug("Processing flow acceleration from flow center {} to hole center {}", 
            flowCenter, holeCenter);
        
        // Process in expanding circles from flow center toward hole center
        double distance = Math.sqrt(
            Math.pow(holeCenter.getX() - flowCenter.getX(), 2) + 
            Math.pow(holeCenter.getZ() - flowCenter.getZ(), 2)
        );
        
        if (distance < 2.0) {
            return; // Already at hole center
        }
        
        // Process positions along the path from flow center to hole center
        int steps = Math.min((int)distance * 2, maxProcessing);
        for (int step = 0; step < steps; step++) {
            double progress = (double) step / steps;
            
            // Interpolate position from flow center to hole center
            BlockPos checkPos = new BlockPos(
                (int)(flowCenter.getX() + (holeCenter.getX() - flowCenter.getX()) * progress),
                flowCenter.getY(),
                (int)(flowCenter.getZ() + (holeCenter.getZ() - flowCenter.getZ()) * progress)
            );
            
            if (!level.isLoaded(checkPos)) continue;
            
            // Check if this position needs flow acceleration toward the hole
            if (shouldAccelerateFlowAt(level, checkPos, holeCenter)) {
                accelerateFlowAtPosition(level, checkPos, holeCenter);
                processed++;
                acceleratedFlowsThisTick.incrementAndGet();
            }
            
            // Also process nearby positions in a small radius for better coverage
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue; // Skip center position
                    
                    BlockPos nearbyPos = checkPos.offset(dx, 0, dz);
                    if (level.isLoaded(nearbyPos) && shouldAccelerateFlowAt(level, nearbyPos, holeCenter)) {
                        accelerateFlowAtPosition(level, nearbyPos, holeCenter);
                        processed++;
                        acceleratedFlowsThisTick.incrementAndGet();
                    }
                }
            }
        }
    }
    
    /**
     * Find the center of ocean surface holes/depressions that need filling
     * This targets actual problem areas rather than geographical ocean centers
     */
    private static BlockPos findOceanHoleCenter(ServerLevel level, BlockPos startPos) {
        int holeBlocks = 0;
        BlockPos holeCenterSum = BlockPos.ZERO;
        int searchRadius = 32;
        
        // Sample for ocean surface holes (non-source water or air at sea level)
        for (int dx = -searchRadius; dx <= searchRadius; dx += 2) {
            for (int dz = -searchRadius; dz <= searchRadius; dz += 2) {
                BlockPos checkPos = startPos.offset(dx, 0, dz);
                if (level.isLoaded(checkPos) && isOceanSurfaceHole(level, checkPos)) {
                    holeCenterSum = holeCenterSum.offset(checkPos.getX(), checkPos.getY(), checkPos.getZ());
                    holeBlocks++;
                }
            }
        }
        
        // Only return center if we found enough hole blocks to indicate a real hole
        if (holeBlocks >= MIN_OCEAN_BLOCKS_FOR_ACCELERATION / 2) {
            BlockPos center = new BlockPos(
                holeCenterSum.getX() / holeBlocks,
                startPos.getY(),
                holeCenterSum.getZ() / holeBlocks
            );
            LOGGER.debug("Found ocean hole center at {} with {} hole blocks", center, holeBlocks);
            return center;
        }
        
        return null; // No significant hole found
    }
    
    /**
     * Find the center of flowing fluids that should be pulled toward holes
     * This identifies areas with flowing water that need acceleration
     */
    private static BlockPos findFlowingFluidCenter(ServerLevel level, BlockPos holeCenter) {
        int flowingBlocks = 0;
        BlockPos flowCenterSum = BlockPos.ZERO;
        int searchRadius = 32; // Reduced radius for better performance
        
        // Sample for flowing water blocks around the hole
        for (int dx = -searchRadius; dx <= searchRadius; dx += 4) { // Increased step size
            for (int dz = -searchRadius; dz <= searchRadius; dz += 4) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos checkPos = holeCenter.offset(dx, dy, dz);
                    if (level.isLoaded(checkPos) && isFlowingWaterBlock(level, checkPos)) {
                        // Calculate distance from hole - prioritize closer flowing fluids
                        double distance = Math.sqrt(dx * dx + dz * dz + dy * dy);
                        if (distance <= 24) { // Reduced radius for better focus
                            flowCenterSum = flowCenterSum.offset(checkPos.getX(), checkPos.getY(), checkPos.getZ());
                            flowingBlocks++;
                        }
                    }
                }
            }
        }
        
        // Only return center if we found flowing fluids
        if (flowingBlocks >= 8) { // Reduced threshold
            BlockPos center = new BlockPos(
                flowCenterSum.getX() / flowingBlocks,
                flowCenterSum.getY() / flowingBlocks,
                flowCenterSum.getZ() / flowingBlocks
            );
            LOGGER.debug("Found flowing fluid center at {} with {} flowing blocks near hole {}", 
                center, flowingBlocks, holeCenter);
            return center;
        }
        
        return null; // No flowing fluids found
    }
    
    /**
     * Check if flow should be accelerated at this position
     */
    private static boolean shouldAccelerateFlowAt(ServerLevel level, BlockPos pos, BlockPos holeCenter) {
        // Must be ocean water
        if (!isOceanWaterBlock(level, pos)) {
            return false;
        }
        
        // Check if recently processed (avoid duplicate work)
        long currentTime = level.getGameTime();
        Long lastProcessed = lastProcessedTime.get(pos);
        if (lastProcessed != null && currentTime - lastProcessed < 5) { // Reduced from 10 to 5 seconds for more frequent processing
            return false;
        }
        
        // Check if flow values are too similar (indicating slow movement)
        FluidState fluidState = level.getFluidState(pos);
        if (!fluidState.is(Fluids.FLOWING_WATER) || fluidState.isSource()) {
            return false; // Only accelerate flowing water, not source blocks
        }
        
        // VERY AGGRESSIVE: Check surrounding flow values for similarity - even lower threshold
        int similarFlowCount = countSimilarFlowNeighbors(level, pos);
        if (similarFlowCount >= 2) { // Reduced from 3 to 2 for maximum acceleration
            return true;
        }
        
        // AGGRESSIVE: Always accelerate if we're in a long gradient chain
        if (isInLongGradient(level, pos)) {
            return true;
        }
        
        // AGGRESSIVE: Always accelerate if flow level is very low (stagnant)
        int currentLevel = fluidState.getAmount();
        if (currentLevel <= 3) { // Increased from 2 to 3
            return true;
        }
        
        // NEW: Accelerate if we're in a dent (low point surrounded by higher flow)
        if (isInDent(level, pos)) {
            return true;
        }
        
        // NEW: Accelerate if we're far from hole center (needs more pull)
        double distanceToHole = pos.distSqr(holeCenter);
        if (distanceToHole > 400) { // More than 20 blocks away
            return true;
        }
        
        return false;
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
     * Check if this position is in a dent (low point surrounded by higher flow)
     * This identifies the center of stagnant areas that need aggressive ripple effects
     */
    private static boolean isInDent(ServerLevel level, BlockPos pos) {
        int currentLevel = level.getFluidState(pos).getAmount();
        int higherNeighbors = 0;
        
        // Check all horizontal neighbors
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            if (level.isLoaded(neighborPos)) {
                FluidState neighborFluid = level.getFluidState(neighborPos);
                if (neighborFluid.is(Fluids.FLOWING_WATER)) {
                    int neighborLevel = neighborFluid.getAmount();
                    // If neighbor has significantly higher flow, this might be a dent
                    if (neighborLevel > currentLevel + 1) {
                        higherNeighbors++;
                    }
                }
            }
        }
        
        // Consider it a dent if surrounded by higher flow on 2+ sides
        return higherNeighbors >= 2;
    }
    
    /**
     * Check if this position is part of a long gradient chain
     * This detects the smooth gradient bug where fluids stop moving
     */
    private static boolean isInLongGradient(ServerLevel level, BlockPos pos) {
        int currentLevel = level.getFluidState(pos).getAmount();
        int gradientLength = 0;
        
        // Check in all horizontal directions for gradient chains
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int chainLength = measureGradientChain(level, pos, dir, currentLevel);
            gradientLength = Math.max(gradientLength, chainLength);
        }
        
        // If we have a gradient chain of 4+ blocks, it's a long gradient
        return gradientLength >= 4;
    }
    
    /**
     * Measure the length of a gradient chain in a specific direction
     */
    private static int measureGradientChain(ServerLevel level, BlockPos start, Direction dir, int startLevel) {
        int chainLength = 0;
        BlockPos current = start;
        int expectedLevel = startLevel;
        
        // Follow the gradient for up to 8 blocks
        for (int i = 0; i < 8; i++) {
            current = current.relative(dir);
            if (!level.isLoaded(current)) break;
            
            FluidState fluid = level.getFluidState(current);
            if (!fluid.is(Fluids.FLOWING_WATER)) break;
            
            int levelDiff = Math.abs(fluid.getAmount() - expectedLevel);
            
            // Check if this follows the expected gradient pattern (levels change by 0-2)
            if (levelDiff <= 2) {
                chainLength++;
                expectedLevel = fluid.getAmount(); // Update expected level for next block
            } else {
                break; // Gradient broken
            }
        }
        
        return chainLength;
    }
    
    /**
     * Accelerate flow at a specific position towards the ocean hole center
     */
    private static void accelerateFlowAtPosition(ServerLevel level, BlockPos pos, BlockPos holeCenter) {
        FluidState currentFluid = level.getFluidState(pos);
        if (!currentFluid.is(Fluids.FLOWING_WATER)) {
            return;
        }
        
        // Calculate direction towards hole center
        double dx = holeCenter.getX() - pos.getX();
        double dz = holeCenter.getZ() - pos.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        
        if (distance < 1.0) return; // Already at center
        
        // Normalize direction
        dx /= distance;
        dz /= distance;
        
        // Calculate new flow level with aggressive ripple effects and center pull
        int currentLevel = currentFluid.getAmount();
        int newLevel = currentLevel;
        
        // VERY AGGRESSIVE: Apply ripple effect - create dramatic flow changes
        if (currentLevel > 1) {
            // Create ripple effect by dramatically changing flow levels
            boolean isInDentArea = isInDent(level, pos);
            boolean isInGradient = isInLongGradient(level, pos);
            
            if (isInDentArea) {
                // Dents need maximum disruption - create strong ripple
                newLevel = Math.max(1, currentLevel - 4); // Reduce by 4 levels
                LOGGER.debug("Creating ripple effect at dent {} from level {} to {}", pos, currentLevel, newLevel);
            } else if (isInGradient) {
                // Gradients need strong disruption to break smooth flow
                newLevel = Math.max(1, currentLevel - 3); // Reduce by 3 levels
                LOGGER.debug("Breaking gradient at {} from level {} to {}", pos, currentLevel, newLevel);
            } else {
                // General aggressive acceleration
                newLevel = Math.max(1, currentLevel - 2); // Reduce by 2 levels
            }
        }
        
        // Apply center pull - extremely aggressive pulling toward center
        double pullStrength = CENTER_PULL_STRENGTH;
        if (distance > 48) {
            pullStrength *= 5.0; // 5x pull strength for very distant areas
        } else if (distance > 32) {
            pullStrength *= 4.0; // 4x pull strength for distant areas
        } else if (distance > 16) {
            pullStrength *= 3.0; // 3x pull strength for medium distance
        } else if (distance > 8) {
            pullStrength *= 2.0; // 2x pull strength for close distance
        }
        
        // Calculate flow reduction based on center pull
        int flowReduction = (int)(currentLevel * pullStrength);
        
        // Apply directional flow towards center with maximum disruption
        if (dx > 0.1) { // Flow east
            newLevel = Math.max(1, newLevel - Math.max(3, flowReduction)); // Minimum reduction of 3
        } else if (dx < -0.1) { // Flow west
            newLevel = Math.max(1, newLevel - Math.max(3, flowReduction)); // Minimum reduction of 3
        }
        
        if (dz > 0.1) { // Flow south
            newLevel = Math.max(1, newLevel - Math.max(3, flowReduction)); // Minimum reduction of 3
        } else if (dz < -0.1) { // Flow north
            newLevel = Math.max(1, newLevel - Math.max(3, flowReduction)); // Minimum reduction of 3
        }
        
        // NEW: Create ripple effect on neighbors to propagate the disturbance
        if (newLevel != currentLevel) {
            createRippleEffect(level, pos, newLevel, holeCenter);
        }
        
        // Update the block if flow level changed
        if (newLevel != currentLevel && newLevel > 0 && newLevel < 8) {
            BlockState newState = Blocks.WATER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 8 - newLevel);
            
            // Apply the new state and log the change
            level.setBlock(pos, newState, 3); // Update with block flags for proper propagation
            lastProcessedTime.put(pos, level.getGameTime());
            
            LOGGER.debug("Accelerated flow at {} from level {} to {} towards hole center {}", 
                pos, currentLevel, newLevel, holeCenter);
        }
    }
    
    /**
     * Create ripple effect on neighboring blocks to propagate flow disturbances
     * This simulates how water ripples when it moves, breaking up stagnant areas
     */
    private static void createRippleEffect(ServerLevel level, BlockPos centerPos, int centerNewLevel, BlockPos holeCenter) {
        // Create ripple on immediate neighbors to propagate the disturbance
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = centerPos.relative(dir);
            if (level.isLoaded(neighborPos)) {
                FluidState neighborFluid = level.getFluidState(neighborPos);
                if (neighborFluid.is(Fluids.FLOWING_WATER) && !neighborFluid.isSource()) {
                    int neighborCurrentLevel = neighborFluid.getAmount();
                    
                    // Calculate ripple strength based on distance from center
                    double rippleStrength = 0.5; // 50% of the center's change
                    int levelChange = (int)((centerNewLevel - neighborCurrentLevel) * rippleStrength);
                    
                    // Apply ripple if it creates a significant change
                    if (Math.abs(levelChange) >= 1) {
                        int rippleNewLevel = Math.max(1, Math.min(7, neighborCurrentLevel + levelChange));
                        
                        // Apply ripple to neighbor
                        BlockState rippleState = Blocks.WATER.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 8 - rippleNewLevel);
                        
                        level.setBlock(neighborPos, rippleState, 3);
                        lastProcessedTime.put(neighborPos, level.getGameTime());
                        
                        LOGGER.debug("Applied ripple at {} from level {} to {}", neighborPos, neighborCurrentLevel, rippleNewLevel);
                        
                        // Create secondary ripple on diagonal neighbors for wider propagation
                        createSecondaryRipple(level, neighborPos, holeCenter);
                    }
                }
            }
        }
    }
    
    /**
     * Create secondary ripple on diagonal neighbors for wider disturbance propagation
     */
    private static void createSecondaryRipple(ServerLevel level, BlockPos centerPos, BlockPos holeCenter) {
        // Check diagonal positions for secondary ripple
        int[][] diagonals = {{1,1}, {1,-1}, {-1,1}, {-1,-1}};
        
        for (int[] diagonal : diagonals) {
            BlockPos diagonalPos = centerPos.offset(diagonal[0], 0, diagonal[1]);
            if (level.isLoaded(diagonalPos)) {
                FluidState diagonalFluid = level.getFluidState(diagonalPos);
                if (diagonalFluid.is(Fluids.FLOWING_WATER) && !diagonalFluid.isSource()) {
                    int diagonalCurrentLevel = diagonalFluid.getAmount();
                    
                    // Secondary ripple is weaker (25% of primary)
                    double secondaryStrength = 0.25;
                    int levelChange = (int)(secondaryStrength * 2); // Small change to create disturbance
                    
                    if (Math.abs(levelChange) >= 1) {
                        int secondaryNewLevel = Math.max(1, Math.min(7, diagonalCurrentLevel + levelChange));
                        
                        BlockState secondaryState = Blocks.WATER.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 8 - secondaryNewLevel);
                        
                        level.setBlock(diagonalPos, secondaryState, 3);
                        lastProcessedTime.put(diagonalPos, level.getGameTime());
                        
                        LOGGER.debug("Applied secondary ripple at {} from level {} to {}", diagonalPos, diagonalCurrentLevel, secondaryNewLevel);
                    }
                }
            }
        }
    }
    
    /**
     * Check if a position contains flowing water (non-source)
     */
    private static boolean isFlowingWaterBlock(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        
        // Must be in ocean biome
        if (!BiomeOptimization.isOceanOrRiverBiome(level, pos)) {
            return false;
        }
        
        FluidState fluidState = level.getFluidState(pos);
        
        // Must be flowing water (not source)
        return fluidState.is(Fluids.FLOWING_WATER) && !fluidState.isSource();
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
     * Check if a position is an ocean surface hole that needs filling
     * This includes air blocks or non-source water at sea level in ocean biomes
     */
    private static boolean isOceanSurfaceHole(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        
        // Must be in ocean biome
        if (!BiomeOptimization.isOceanOrRiverBiome(level, pos)) {
            return false;
        }
        
        // Check if at or near sea level (62-65)
        int y = pos.getY();
        if (y < 62 || y > 65) {
            return false;
        }
        
        FluidState fluidState = level.getFluidState(pos);
        BlockState blockState = level.getBlockState(pos);
        
        // Consider it a hole if:
        // 1. Air block where water should be
        // 2. Non-source flowing water (indicating incomplete filling)
        // 3. Very shallow flowing water (level 1-3)
        
        if (blockState.isAir()) {
            return true; // Air hole at surface
        }
        
        if (fluidState.is(Fluids.FLOWING_WATER) && !fluidState.isSource()) {
            int fluidLevel = fluidState.getAmount();
            return fluidLevel <= 3; // Shallow flowing water indicates hole
        }
        
        return false; // Not a hole
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
     * Clean up old cached data to prevent memory leaks
     */
    public static void cleanup() {
        long currentTime = System.currentTimeMillis();
        long cutoffTime = currentTime - (5 * 60 * 1000); // 5 minutes ago
        
        // Clean up old processing timestamps
        lastProcessedTime.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
        
        // Clean up old ocean center caches periodically
        if (currentTime % (10 * 60 * 1000) < 1000) { // Every 10 minutes
            oceanCenterCache.clear();
            LOGGER.debug("Cleared ocean center cache to prevent memory leaks");
        }
    }
    
    /**
     * Get performance statistics
     */
    public static String getPerformanceStats() {
        return String.format("Ocean Flow Accelerator Stats: %d flows accelerated, %d ocean areas processed, %d cached centers",
            acceleratedFlowsThisTick.get(),
            oceanAreasDetected.get(),
            oceanCenterCache.size()
        );
    }
    
    /**
     * Clear caches to prevent memory leaks
     */
    public static void clearCaches() {
        oceanCenterCache.clear();
        lastProcessedTime.clear();
        LOGGER.info("Ocean flow accelerator caches cleared");
    }
}
