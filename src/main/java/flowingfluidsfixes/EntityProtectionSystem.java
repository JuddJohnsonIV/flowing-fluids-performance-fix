package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Entity Protection System - Ensures mobs and entities are not affected by fluid processing
 * 
 * This system:
 * 1. Tracks entity count and AI activity in the world
 * 2. Dynamically adjusts fluid processing budget based on entity load
 * 3. Pauses fluid processing entirely when entities need more CPU time
 * 4. Prioritizes entity-rich chunks over fluid-heavy chunks
 * 5. Monitors pathfinding activity to detect AI strain
 */
public class EntityProtectionSystem {
    private static final Logger LOGGER = LogManager.getLogger(EntityProtectionSystem.class);
    
    // Entity tracking
    private static volatile int totalEntityCount = 0;
    private static volatile int mobCount = 0;
    private static volatile int pathfindingMobCount = 0;
    private static volatile int entitiesNearFluids = 0;
    
    // Time budget for entities (ensures they get enough CPU time)
    // Minecraft tick = 50ms, we want entities to have AT LEAST 30ms
    private static final long TICK_BUDGET_MS = 50; // Target tick time
    private static final long MIN_ENTITY_TIME_MS = 30; // Minimum time reserved for entities
    private static final long MAX_FLUID_TIME_WHEN_ENTITIES_HEAVY_MS = 2; // Only 2ms for fluids when many entities
    private static final long MAX_FLUID_TIME_NORMAL_MS = 4; // 4ms normally
    
    // Tracking actual entity processing time
    private static volatile long entityProcessingTimeThisTick = 0;
    private static volatile long lastTickStartTime = 0;
    
    // Entity load thresholds
    private static final int LIGHT_ENTITY_LOAD = 50;
    private static final int MODERATE_ENTITY_LOAD = 150;
    private static final int HEAVY_ENTITY_LOAD = 300;
    private static final int CRITICAL_ENTITY_LOAD = 500;
    
    // Pathfinding mob thresholds (pathfinding is CPU-intensive)
    private static final int PATHFINDING_LIGHT = 20;
    private static final int PATHFINDING_MODERATE = 50;
    private static final int PATHFINDING_HEAVY = 100;
    
    // Entity protection state
    public enum ProtectionLevel {
        NONE,       // Normal fluid processing
        LOW,        // Slight reduction in fluid processing
        MODERATE,   // Significant reduction in fluid processing
        HIGH,       // Major reduction - entities take priority
        CRITICAL    // Almost no fluid processing - entities are starving
    }
    private static volatile ProtectionLevel currentProtectionLevel = ProtectionLevel.NONE;
    
    // Tracking metrics
    private static final AtomicInteger fluidUpdatesBlockedForEntities = new AtomicInteger(0);
    private static final AtomicLong entityProtectionActivations = new AtomicLong(0);
    private static volatile long lastEntityScan = 0;
    private static final int ENTITY_SCAN_INTERVAL_TICKS = 10; // Scan every 10 ticks (0.5 seconds)
    
    // Chunk-based entity tracking for smart fluid throttling
    private static final Map<Long, ChunkEntityInfo> chunkEntityCounts = new ConcurrentHashMap<>();
    
    // Fluid processing near entities tracking
    private static int ENTITY_FLUID_PROTECTION_RADIUS = 8; // Protect entities within 8 blocks of fluid processing, will be updated from config
    
    /**
     * Update protection radius from configuration
     */
    public static void updateProtectionRadiusFromConfig() {
        try {
            // Assuming entityProximityRadius is accessible after config load
            // This might need adjustment based on actual config field name
            ENTITY_FLUID_PROTECTION_RADIUS = FlowingFluidsOptimizationConfig.entityProximityRadius.get();
            LOGGER.info("Updated entity fluid protection radius to {} blocks from config", ENTITY_FLUID_PROTECTION_RADIUS);
        } catch (Exception e) {
            LOGGER.error("Failed to update entity protection radius from config: {}", e.getMessage());
            ENTITY_FLUID_PROTECTION_RADIUS = 8; // Default fallback
        }
    }
    
    /**
     * Scan entities in the world to determine load
     */
    private static void scanEntities(ServerLevel level) {
        int entities = 0;
        int mobs = 0;
        int pathfinders = 0;
        int nearFluids = 0;
        
        // Clear old chunk data
        chunkEntityCounts.clear();
        
        for (Entity entity : level.getAllEntities()) {
            entities++;
            
            if (entity instanceof Mob) {
                mobs++;
                
                if (entity instanceof PathfinderMob) {
                    pathfinders++;
                }
                
                // Check if entity is near fluids
                BlockPos entityPos = entity.blockPosition();
                if (isNearFluid(level, entityPos)) {
                    nearFluids++;
                }
                
                // Track per-chunk entity counts
                ChunkPos chunkPos = new ChunkPos(entityPos);
                long chunkKey = chunkPos.toLong();
                chunkEntityCounts.compute(chunkKey, (k, v) -> {
                    if (v == null) {
                        return new ChunkEntityInfo(1, entity instanceof PathfinderMob ? 1 : 0);
                    }
                    return new ChunkEntityInfo(
                        v.entityCount + 1,
                        v.pathfinderCount + (entity instanceof PathfinderMob ? 1 : 0)
                    );
                });
            }
        }
        
        totalEntityCount = entities;
        mobCount = mobs;
        pathfindingMobCount = pathfinders;
        entitiesNearFluids = nearFluids;
    }
    
    /**
     * Check if a position is near flowing fluids
     */
    private static boolean isNearFluid(ServerLevel level, BlockPos pos) {
        // Quick check for fluids in a 3x3x3 area around the entity
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    if (!level.getFluidState(checkPos).isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Update protection level based on entity counts
     */
    private static void updateProtectionLevel() {
        ProtectionLevel oldLevel = currentProtectionLevel;
        
        // Calculate protection level based on multiple factors
        int entityScore = calculateEntityScore();
        
        if (entityScore >= 100) {
            currentProtectionLevel = ProtectionLevel.CRITICAL;
        } else if (entityScore >= 75) {
            currentProtectionLevel = ProtectionLevel.HIGH;
        } else if (entityScore >= 50) {
            currentProtectionLevel = ProtectionLevel.MODERATE;
        } else if (entityScore >= 25) {
            currentProtectionLevel = ProtectionLevel.LOW;
        } else {
            currentProtectionLevel = ProtectionLevel.NONE;
        }
        
        // Log level changes
        if (oldLevel != currentProtectionLevel) {
            if (currentProtectionLevel.ordinal() > oldLevel.ordinal()) {
                LOGGER.warn("Entity protection increased: {} -> {} (entities={}, mobs={}, pathfinders={})",
                    oldLevel, currentProtectionLevel, totalEntityCount, mobCount, pathfindingMobCount);
                entityProtectionActivations.incrementAndGet();
            } else {
                LOGGER.info("Entity protection decreased: {} -> {}", oldLevel, currentProtectionLevel);
            }
        }
    }
    
    /**
     * Calculate entity load score (0-100+)
     */
    private static int calculateEntityScore() {
        int score = 0;
        
        // Base entity count contribution (0-30)
        if (totalEntityCount >= CRITICAL_ENTITY_LOAD) {
            score += 30;
        } else if (totalEntityCount >= HEAVY_ENTITY_LOAD) {
            score += 20;
        } else if (totalEntityCount >= MODERATE_ENTITY_LOAD) {
            score += 10;
        } else if (totalEntityCount >= LIGHT_ENTITY_LOAD) {
            score += 5;
        }
        
        // Pathfinding mob contribution (0-40) - pathfinding is very CPU intensive
        if (pathfindingMobCount >= PATHFINDING_HEAVY) {
            score += 40;
        } else if (pathfindingMobCount >= PATHFINDING_MODERATE) {
            score += 25;
        } else if (pathfindingMobCount >= PATHFINDING_LIGHT) {
            score += 10;
        }
        
        // Entities near fluids contribution (0-30) - these need priority
        int nearFluidScore = Math.min(30, entitiesNearFluids * 3);
        score += nearFluidScore;
        
        // TPS contribution - if TPS is low, protect entities more
        double tps = PerformanceMonitor.getAverageTPS();
        if (tps < 10.0) {
            score += 30;
        } else if (tps < 15.0) {
            score += 15;
        } else if (tps < 18.0) {
            score += 5;
        }
        
        return score;
    }
    
    /**
     * Get the maximum fluid processing time allowed based on entity protection level
     * This is called by FluidTickScheduler to adjust its time budget
     * 
     * Uses MIN_ENTITY_TIME_MS and MAX_FLUID_TIME_WHEN_ENTITIES_HEAVY_MS for dynamic calculation
     */
    public static long getMaxFluidTimeMs() {
        // Calculate remaining time after reserving minimum for entities
        long remainingAfterEntities = TICK_BUDGET_MS - MIN_ENTITY_TIME_MS; // 50 - 30 = 20ms max for everything else
        
        // Base calculation on protection level
        long baseFluidTime = switch (currentProtectionLevel) {
            case CRITICAL -> 1;  // Only 1ms - entities are starving
            case HIGH -> MAX_FLUID_TIME_WHEN_ENTITIES_HEAVY_MS;  // 2ms - heavy entity load
            case MODERATE -> 3;  // 3ms - moderate entity load
            case LOW -> MAX_FLUID_TIME_NORMAL_MS;  // 4ms - slight reduction
            case NONE -> MAX_FLUID_TIME_NORMAL_MS; // 4ms - normal
        };
        
        // Dynamic adjustment: if we've used a lot of tick time already, reduce fluid time
        long elapsedThisTick = lastTickStartTime > 0 ? System.currentTimeMillis() - lastTickStartTime : 0;
        if (elapsedThisTick > MIN_ENTITY_TIME_MS) {
            // Already past entity budget - drastically reduce fluid time
            return Math.max(1, baseFluidTime / 2);
        }
        
        // Ensure we never exceed remaining budget
        return Math.min(baseFluidTime, remainingAfterEntities / 5); // Max 4ms (20/5)
    }
    
    /**
     * Called at the start of each server tick to track timing
     */
    public static void onTickStart() {
        lastTickStartTime = System.currentTimeMillis();
        entityProcessingTimeThisTick = 0;
    }
    
    /**
     * Record entity processing time for monitoring
     */
    public static void recordEntityProcessingTime(long timeMs) {
        entityProcessingTimeThisTick += timeMs;
    }
    
    /**
     * Get the entity processing time this tick
     */
    public static long getEntityProcessingTimeThisTick() {
        return entityProcessingTimeThisTick;
    }
    
    /**
     * Check if entities have enough time budget remaining this tick
     */
    public static boolean hasEntityTimeBudget() {
        long elapsed = lastTickStartTime > 0 ? System.currentTimeMillis() - lastTickStartTime : 0;
        return elapsed < MIN_ENTITY_TIME_MS;
    }
    
    /**
     * Get the maximum fluid processing time in nanoseconds
     */
    public static long getMaxFluidTimeNanos() {
        return getMaxFluidTimeMs() * 1_000_000L;
    }
    
    /**
     * Check if fluid processing should be allowed at a specific position
     * Returns false if there are too many entities nearby that need CPU time
     */
    public static boolean shouldAllowFluidProcessing(ServerLevel level, BlockPos pos) {
        // Always allow if no protection needed
        if (currentProtectionLevel == ProtectionLevel.NONE) {
            return true;
        }
        
        // Check chunk entity density
        ChunkPos chunkPos = new ChunkPos(pos);
        ChunkEntityInfo chunkInfo = chunkEntityCounts.get(chunkPos.toLong());
        
        if (chunkInfo != null) {
            LOGGER.debug("Checking fluid processing for chunk with {} entities, including {} pathfinders", chunkInfo.entityCount, chunkInfo.pathfinderCount);
            // If this chunk has many pathfinding mobs, restrict fluid processing
            if (chunkInfo.pathfinderCount >= 5 && currentProtectionLevel.ordinal() >= ProtectionLevel.MODERATE.ordinal()) {
                fluidUpdatesBlockedForEntities.incrementAndGet();
                return false;
            }
            
            // If this chunk has many entities overall
            if (chunkInfo.entityCount >= 10 && currentProtectionLevel == ProtectionLevel.CRITICAL) {
                fluidUpdatesBlockedForEntities.incrementAndGet();
                return false;
            }
        }
        
        // Check for entities very close to this fluid position
        if (currentProtectionLevel.ordinal() >= ProtectionLevel.HIGH.ordinal()) {
            List<Mob> nearbyMobs = level.getEntitiesOfClass(
                Mob.class,
                new AABB(pos).inflate(ENTITY_FLUID_PROTECTION_RADIUS)
            );
            
            if (!nearbyMobs.isEmpty()) {
                // Block fluid processing if mobs are very close
                fluidUpdatesBlockedForEntities.incrementAndGet();
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get fluid update multiplier based on protection level
     * Lower values = fewer fluid updates allowed
     */
    public static double getFluidUpdateMultiplier() {
        return switch (currentProtectionLevel) {
            case CRITICAL -> 0.1;  // Only 10% of normal updates
            case HIGH -> 0.25;     // 25% of normal updates
            case MODERATE -> 0.5;  // 50% of normal updates
            case LOW -> 0.75;      // 75% of normal updates
            case NONE -> 1.0;      // 100% - normal
        };
    }
    
    /**
     * Get current protection level
     */
    public static ProtectionLevel getProtectionLevel() {
        return currentProtectionLevel;
    }
    
    /**
     * Get entity statistics
     */
    public static String getEntityStats() {
        return String.format("Entities: total=%d, mobs=%d, pathfinders=%d, nearFluids=%d",
            totalEntityCount, mobCount, pathfindingMobCount, entitiesNearFluids);
    }
    
    /**
     * Get protection statistics
     */
    public static String getProtectionStats() {
        return String.format("EntityProtection: level=%s, blocked=%d, activations=%d, maxFluidTime=%dms",
            currentProtectionLevel, fluidUpdatesBlockedForEntities.get(), 
            entityProtectionActivations.get(), getMaxFluidTimeMs());
    }
    
    /**
     * Get full status summary
     */
    public static String getStatusSummary() {
        return String.format("%s | %s", getEntityStats(), getProtectionStats());
    }
    
    /**
     * Reset statistics
     */
    public static void resetStats() {
        fluidUpdatesBlockedForEntities.set(0);
        entityProtectionActivations.set(0);
    }
    
    /**
     * Chunk entity info record - used to track entity density per chunk for fluid throttling decisions
     */
    private record ChunkEntityInfo(int entityCount, int pathfinderCount) {}
    
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        if (level == null) return;
        long currentTick = level.getGameTime();
        
        // Periodic entity scan
        if (currentTick - lastEntityScan >= ENTITY_SCAN_INTERVAL_TICKS) {
            scanEntities(level);
            updateProtectionLevel();
            lastEntityScan = currentTick;
        }
    }
}
