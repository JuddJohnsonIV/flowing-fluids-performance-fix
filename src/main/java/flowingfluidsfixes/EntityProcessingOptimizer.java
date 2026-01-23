package flowingfluidsfixes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Entity Processing Optimizer - Limits entity count and applies distance-based culling
 * Reduces updateFluidHeightAndDoFluidPushing overhead during high MSPT
 */
public class EntityProcessingOptimizer {
    
    // Entity processing configuration
    private static final int MAX_ENTITIES_PER_CHUNK = 50; // Maximum entities to process per chunk
    private static final int MAX_ENTITIES_PER_AREA = 200; // Maximum entities in processing area
    private static final double ENTITY_PROCESSING_RADIUS = 64.0; // 64 blocks processing radius
    private static final double HIGH_MSPT_ENTITY_RADIUS = 32.0; // Reduced radius during high MSPT
    
    // Entity tracking and caching
    private static final ConcurrentHashMap<Long, EntityCacheEntry> entityCache = new ConcurrentHashMap<>();
    private static final AtomicLong lastEntityUpdate = new AtomicLong(0);
    private static final long ENTITY_CACHE_DURATION = 1000; // 1 second cache duration
    
    // Performance tracking
    private static final AtomicInteger entitiesProcessed = new AtomicInteger(0);
    private static final AtomicInteger entitiesSkipped = new AtomicInteger(0);
    private static final AtomicInteger entitiesCulled = new AtomicInteger(0);
    private static final AtomicInteger entityScans = new AtomicInteger(0);
    
    // MSPT-based thresholds
    private static final double ENTITY_LIMITING_MSPT = 25.0; // Start limiting at 25ms
    private static final double ENTITY_CULLING_MSPT = 40.0; // Aggressive culling at 40ms
    private static final double EMERGENCY_ENTITY_MSPT = 50.0; // Emergency mode at 50ms
    
    /**
     * Entity cache entry for tracking entity state
     */
    public static class EntityCacheEntry {
        public final Entity entity;
        public final BlockPos lastPosition;
        public final long timestamp;
        public final double distanceToFluid;
        public final boolean isInFluid;
        
        public EntityCacheEntry(Entity entity, BlockPos fluidPos) {
            this.entity = entity;
            this.lastPosition = entity.blockPosition();
            this.timestamp = System.currentTimeMillis();
            this.distanceToFluid = entity.distanceToSqr(fluidPos.getX(), fluidPos.getY(), fluidPos.getZ());
            this.isInFluid = entity.isInWater() || entity.isInLava(); // Simplified fluid check
        }
    }
    
    /**
     * Get entities that should be processed for fluid interactions
     * Applies MSPT-based limits and distance-based culling
     */
    public static List<Entity> getEntitiesForFluidProcessing(ServerLevel level, BlockPos fluidPos) {
        entityScans.incrementAndGet();
        
        double currentMSPT = FlowingFluidsFixesMinimal.cachedMSPT;
        double processingRadius = getProcessingRadius(currentMSPT);
        
        // Update entity cache if needed
        updateEntityCacheIfNeeded(level, fluidPos);
        
        // Get entities within processing radius
        List<Entity> nearbyEntities = getNearbyEntities(level, fluidPos, processingRadius);
        
        // Apply MSPT-based filtering
        List<Entity> filteredEntities = applyMSPTBasedFiltering(nearbyEntities, currentMSPT);
        
        // Apply distance-based culling
        List<Entity> culledEntities = applyDistanceBasedCulling(filteredEntities, fluidPos, currentMSPT);
        
        // Apply count limits
        List<Entity> limitedEntities = applyCountLimits(culledEntities, currentMSPT);
        
        // Update statistics
        entitiesProcessed.addAndGet(limitedEntities.size());
        entitiesSkipped.addAndGet(nearbyEntities.size() - limitedEntities.size());
        entitiesCulled.addAndGet(filteredEntities.size() - limitedEntities.size());
        
        return limitedEntities;
    }
    
    /**
     * Get processing radius based on current MSPT
     */
    private static double getProcessingRadius(double mspt) {
        if (mspt > EMERGENCY_ENTITY_MSPT) {
            return 16.0; // Emergency: 16 blocks
        } else if (mspt > ENTITY_CULLING_MSPT) {
            return 24.0; // Aggressive culling: 24 blocks
        } else if (mspt > ENTITY_LIMITING_MSPT) {
            return HIGH_MSPT_ENTITY_RADIUS; // High MSPT: 32 blocks
        } else {
            return ENTITY_PROCESSING_RADIUS; // Normal: 64 blocks
        }
    }
    
    /**
     * Update entity cache if it's stale
     */
    private static void updateEntityCacheIfNeeded(ServerLevel level, BlockPos fluidPos) {
        long currentTime = System.currentTimeMillis();
        long lastUpdate = lastEntityUpdate.get();
        
        if (currentTime - lastUpdate > ENTITY_CACHE_DURATION && 
            lastEntityUpdate.compareAndSet(lastUpdate, currentTime)) {
            
            performEntityCacheUpdate(level, fluidPos);
        }
    }
    
    /**
     * Perform entity cache update
     */
    private static void performEntityCacheUpdate(ServerLevel level, BlockPos fluidPos) {
        // Clear expired entries
        cleanExpiredEntityEntries();
        
        // Get current entities in the area
        ChunkPos chunkPos = new ChunkPos(fluidPos);
        List<Entity> nearbyEntities = level.getEntities(null, 
            new net.minecraft.world.phys.AABB(
                fluidPos.getX() - ENTITY_PROCESSING_RADIUS,
                fluidPos.getY() - ENTITY_PROCESSING_RADIUS,
                fluidPos.getZ() - ENTITY_PROCESSING_RADIUS,
                fluidPos.getX() + ENTITY_PROCESSING_RADIUS,
                fluidPos.getY() + ENTITY_PROCESSING_RADIUS,
                fluidPos.getZ() + ENTITY_PROCESSING_RADIUS
            )
        );
        
        // Update cache with current entities
        entityCache.clear();
        for (Entity entity : nearbyEntities) {
            long entityKey = getEntityKey(entity);
            entityCache.put(entityKey, new EntityCacheEntry(entity, fluidPos));
        }
    }
    
    /**
     * Get nearby entities from cache or level
     */
    private static List<Entity> getNearbyEntities(ServerLevel level, BlockPos fluidPos, double radius) {
        // Use cached entities if available
        if (!entityCache.isEmpty()) {
            return entityCache.values().stream()
                .filter(entry -> entry.distanceToFluid <= radius * radius)
                .map(entry -> entry.entity)
                .collect(Collectors.toList());
        }
        
        // Fallback to direct entity access
        return level.getEntities(null, 
            new net.minecraft.world.phys.AABB(
                fluidPos.getX() - radius,
                fluidPos.getY() - radius,
                fluidPos.getZ() - radius,
                fluidPos.getX() + radius,
                fluidPos.getY() + radius,
                fluidPos.getZ() + radius
            )
        );
    }
    
    /**
     * Apply MSPT-based filtering
     */
    private static List<Entity> applyMSPTBasedFiltering(List<Entity> entities, double mspt) {
        if (mspt <= ENTITY_LIMITING_MSPT) {
            return entities; // No filtering during low MSPT
        }
        
        return entities.stream()
            .filter(entity -> shouldProcessEntityDuringHighMSPT(entity, mspt))
            .collect(Collectors.toList());
    }
    
    /**
     * Check if entity should be processed during high MSPT
     */
    private static boolean shouldProcessEntityDuringHighMSPT(Entity entity, double mspt) {
        // Always process players
        if (entity instanceof net.minecraft.server.level.ServerPlayer) {
            return true;
        }
        
        // Prioritize living entities during high MSPT
        if (mspt > ENTITY_CULLING_MSPT && !(entity instanceof LivingEntity)) {
            return false;
        }
        
        // Skip item entities during very high MSPT
        if (mspt > EMERGENCY_ENTITY_MSPT && entity instanceof ItemEntity) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Apply distance-based culling
     */
    private static List<Entity> applyDistanceBasedCulling(List<Entity> entities, BlockPos fluidPos, double mspt) {
        if (mspt <= ENTITY_LIMITING_MSPT) {
            return entities; // No culling during low MSPT
        }
        
        double maxDistance = getMaxProcessingDistance(mspt);
        double maxDistanceSq = maxDistance * maxDistance;
        
        return entities.stream()
            .filter(entity -> entity.distanceToSqr(fluidPos.getX(), fluidPos.getY(), fluidPos.getZ()) <= maxDistanceSq)
            .collect(Collectors.toList());
    }
    
    /**
     * Get maximum processing distance based on MSPT
     */
    private static double getMaxProcessingDistance(double mspt) {
        if (mspt > EMERGENCY_ENTITY_MSPT) {
            return 16.0; // Emergency: very close entities only
        } else if (mspt > ENTITY_CULLING_MSPT) {
            return 32.0; // Aggressive culling: close entities only
        } else {
            return 48.0; // Moderate limiting: medium distance
        }
    }
    
    /**
     * Apply count limits
     */
    private static List<Entity> applyCountLimits(List<Entity> entities, double mspt) {
        int maxEntities = getMaxEntityCount(mspt);
        
        if (entities.size() <= maxEntities) {
            return entities;
        }
        
        // Sort by priority (players first, then living entities, then others)
        List<Entity> prioritized = entities.stream()
            .sorted((e1, e2) -> getEntityPriority(e1) - getEntityPriority(e2))
            .collect(Collectors.toList());
        
        // Return only the highest priority entities
        return prioritized.subList(0, maxEntities);
    }
    
    /**
     * Get maximum entity count based on MSPT
     */
    private static int getMaxEntityCount(double mspt) {
        if (mspt > EMERGENCY_ENTITY_MSPT) {
            return 10; // Emergency: very few entities
        } else if (mspt > ENTITY_CULLING_MSPT) {
            return 25; // Aggressive culling: few entities
        } else if (mspt > ENTITY_LIMITING_MSPT) {
            return 50; // Moderate limiting: limited entities
        } else {
            return MAX_ENTITIES_PER_AREA; // Normal: full limit
        }
    }
    
    /**
     * Get entity processing priority (lower number = higher priority)
     */
    private static int getEntityPriority(Entity entity) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer) {
            return 0; // Players: highest priority
        } else if (entity instanceof LivingEntity) {
            return 1; // Living entities: high priority
        } else if (entity instanceof ItemEntity) {
            return 3; // Items: low priority
        } else {
            return 2; // Other entities: medium priority
        }
    }
    
    /**
     * Clean expired entity cache entries
     */
    private static void cleanExpiredEntityEntries() {
        long currentTime = System.currentTimeMillis();
        
        entityCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().timestamp > ENTITY_CACHE_DURATION);
    }
    
    /**
     * Generate unique key for entity
     */
    private static long getEntityKey(Entity entity) {
        return ((long)entity.getId() & 0xFFFFFFFFL) << 32 | (entity.getType().hashCode() & 0xFFFFFFFFL);
    }
    
    /**
     * Check if entity processing should be enabled
     */
    public static boolean shouldProcessEntities(ServerLevel level) {
        double mspt = FlowingFluidsFixesMinimal.cachedMSPT;
        
        // Disable entity processing entirely during extreme MSPT
        if (mspt > EMERGENCY_ENTITY_MSPT + 10.0) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Get entity processing statistics
     */
    public static String getEntityProcessingStatistics() {
        return String.format(
            "EntityProcessing: %d processed, %d skipped, %d culled, %d scans | Cache: %d entries",
            entitiesProcessed.get(),
            entitiesSkipped.get(),
            entitiesCulled.get(),
            entityScans.get(),
            entityCache.size()
        );
    }
    
    /**
     * Get entity processing efficiency
     */
    public static double getEntityProcessingEfficiency() {
        int total = entitiesProcessed.get() + entitiesSkipped.get();
        return total > 0 ? (entitiesProcessed.get() * 100.0 / total) : 0.0;
    }
    
    /**
     * Clear all entity caches
     */
    public static void clearAllCaches() {
        entityCache.clear();
        lastEntityUpdate.set(0);
        
        // Reset statistics
        entitiesProcessed.set(0);
        entitiesSkipped.set(0);
        entitiesCulled.set(0);
        entityScans.set(0);
    }
    
    /**
     * Get current entity processing limits
     */
    public static String getCurrentLimits() {
        double mspt = FlowingFluidsFixesMinimal.cachedMSPT;
        double radius = getProcessingRadius(mspt);
        int maxCount = getMaxEntityCount(mspt);
        
        return String.format("Entity Limits: Radius=%.1f, MaxCount=%d (MSPT=%.1f)", radius, maxCount, mspt);
    }
}
