package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Multiplayer Synchronization System for Flowing Fluids
 * Ensures fluid updates are properly synchronized between server and clients
 * to prevent desync issues in multiplayer environments.
 */
public class MultiplayerFluidSync {
    private static final Logger LOGGER = LogManager.getLogger(MultiplayerFluidSync.class);
    
    // Sync tracking
    private static final Map<ChunkPos, Set<BlockPos>> pendingSyncUpdates = new ConcurrentHashMap<>();
    private static final Map<ServerPlayer, Long> playerLastSyncTime = new ConcurrentHashMap<>();
    
    // Sync configuration
    private static final int SYNC_BATCH_SIZE = 50;           // Max updates per sync packet
    private static final int SYNC_INTERVAL_TICKS = 5;        // Sync every 5 ticks
    private static final int MAX_SYNC_DISTANCE = 64;         // Max distance for sync updates
    private static final int PRIORITY_SYNC_DISTANCE = 16;    // Priority sync distance
    
    // Performance tracking
    private static final AtomicLong totalSyncUpdates = new AtomicLong(0);
    private static final AtomicLong batchedSyncUpdates = new AtomicLong(0);
    private static final AtomicInteger currentSyncQueueSize = new AtomicInteger(0);
    
    private static int tickCounter = 0;
    
    /**
     * Queue a fluid update for synchronization to nearby players
     */
    public static void queueFluidSync(ServerLevel level, BlockPos pos, FluidState state) {
        if (level == null || pos == null) return;
        
        ChunkPos chunkPos = new ChunkPos(pos);
        pendingSyncUpdates.computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet())
                         .add(pos.immutable());
        
        currentSyncQueueSize.incrementAndGet();
        
        // Immediately sync critical updates to nearby players
        if (isCriticalUpdate(level, pos, state)) {
            syncCriticalUpdate(level, pos, state);
        }
    }
    
    /**
     * Process pending sync updates - call from server tick
     */
    public static void processPendingSync(ServerLevel level) {
        tickCounter++;
        
        if (tickCounter % SYNC_INTERVAL_TICKS != 0) {
            return; // Not time for sync yet
        }
        
        if (pendingSyncUpdates.isEmpty()) {
            return;
        }
        
        // Group updates by player based on proximity
        Map<ServerPlayer, List<BlockPos>> playerUpdates = new HashMap<>();
        
        for (ServerPlayer player : level.players()) {
            List<BlockPos> updates = getUpdatesForPlayer(level, player);
            if (!updates.isEmpty()) {
                playerUpdates.put(player, updates);
            }
        }
        
        // Send batched updates to each player
        for (Map.Entry<ServerPlayer, List<BlockPos>> entry : playerUpdates.entrySet()) {
            sendBatchedUpdates(level, entry.getKey(), entry.getValue());
        }
        
        // Clear processed updates
        cleanupProcessedUpdates();
    }
    
    /**
     * Get updates relevant to a specific player
     */
    private static List<BlockPos> getUpdatesForPlayer(ServerLevel level, ServerPlayer player) {
        List<BlockPos> relevantUpdates = new ArrayList<>();
        BlockPos playerPos = player.blockPosition();
        
        for (Map.Entry<ChunkPos, Set<BlockPos>> entry : pendingSyncUpdates.entrySet()) {
            ChunkPos chunkPos = entry.getKey();
            
            // Check if chunk is within player's sync distance
            double chunkDistanceSq = getChunkDistanceSq(playerPos, chunkPos);
            if (chunkDistanceSq > MAX_SYNC_DISTANCE * MAX_SYNC_DISTANCE) {
                continue;
            }
            
            // Add updates from this chunk, prioritizing close ones
            Set<BlockPos> chunkUpdates = entry.getValue();
            for (BlockPos updatePos : chunkUpdates) {
                double distanceSq = player.distanceToSqr(updatePos.getX(), updatePos.getY(), updatePos.getZ());
                
                // Priority sync for close updates
                if (distanceSq <= PRIORITY_SYNC_DISTANCE * PRIORITY_SYNC_DISTANCE) {
                    relevantUpdates.add(0, updatePos); // Add to front
                } else if (distanceSq <= MAX_SYNC_DISTANCE * MAX_SYNC_DISTANCE) {
                    relevantUpdates.add(updatePos);
                }
                
                // Limit batch size
                if (relevantUpdates.size() >= SYNC_BATCH_SIZE) {
                    break;
                }
            }
        }
        
        return relevantUpdates;
    }
    
    /**
     * Send batched fluid updates to a player
     */
    private static void sendBatchedUpdates(ServerLevel level, ServerPlayer player, List<BlockPos> updates) {
        if (updates.isEmpty()) return;
        
        int sentCount = 0;
        for (BlockPos pos : updates) {
            if (sentCount >= SYNC_BATCH_SIZE) break;
            
            // Send block update to client
            // This triggers the client to re-fetch the block state
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            
            sentCount++;
            totalSyncUpdates.incrementAndGet();
        }
        
        batchedSyncUpdates.addAndGet(sentCount);
        playerLastSyncTime.put(player, System.currentTimeMillis());
        
        LOGGER.debug("Sent {} fluid sync updates to player {}", sentCount, player.getName().getString());
    }
    
    /**
     * Sync a critical update immediately to nearby players
     */
    private static void syncCriticalUpdate(ServerLevel level, BlockPos pos, FluidState state) {
        for (ServerPlayer player : level.players()) {
            double distanceSq = player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
            if (distanceSq <= PRIORITY_SYNC_DISTANCE * PRIORITY_SYNC_DISTANCE) {
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                totalSyncUpdates.incrementAndGet();
            }
        }
    }
    
    /**
     * Check if this is a critical update that needs immediate sync
     */
    private static boolean isCriticalUpdate(ServerLevel level, BlockPos pos, FluidState state) {
        // Source blocks and high-level fluids are critical
        if (state.isSource() || state.getAmount() >= 7) {
            return true;
        }
        
        // Fluids near players are critical
        for (ServerPlayer player : level.players()) {
            double distanceSq = player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
            if (distanceSq <= PRIORITY_SYNC_DISTANCE * PRIORITY_SYNC_DISTANCE) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Clean up processed updates from the queue
     */
    private static void cleanupProcessedUpdates() {
        // Remove empty chunk entries
        pendingSyncUpdates.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        // Update queue size
        int newSize = pendingSyncUpdates.values().stream()
            .mapToInt(Set::size)
            .sum();
        currentSyncQueueSize.set(newSize);
    }
    
    /**
     * Calculate squared distance from player position to chunk center
     */
    private static double getChunkDistanceSq(BlockPos playerPos, ChunkPos chunkPos) {
        int chunkCenterX = chunkPos.getMinBlockX() + 8;
        int chunkCenterZ = chunkPos.getMinBlockZ() + 8;
        double dx = playerPos.getX() - chunkCenterX;
        double dz = playerPos.getZ() - chunkCenterZ;
        return dx * dx + dz * dz;
    }
    
    /**
     * Handle player disconnect - clean up their sync data
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        playerLastSyncTime.remove(player);
        LOGGER.debug("Cleaned up sync data for disconnected player {}", player.getName().getString());
    }
    
    /**
     * Get sync statistics
     */
    public static String getSyncStats() {
        return String.format("Multiplayer Sync: %d total syncs, %d batched, %d pending, %d players tracked",
            totalSyncUpdates.get(), batchedSyncUpdates.get(), 
            currentSyncQueueSize.get(), playerLastSyncTime.size());
    }
    
    /**
     * Get current sync queue size
     */
    public static int getSyncQueueSize() {
        return currentSyncQueueSize.get();
    }
    
    /**
     * Reset sync statistics
     */
    public static void resetStats() {
        totalSyncUpdates.set(0);
        batchedSyncUpdates.set(0);
        LOGGER.info("Multiplayer sync statistics reset");
    }
    
    /**
     * Clear all pending sync updates
     */
    public static void clearPendingSync() {
        pendingSyncUpdates.clear();
        currentSyncQueueSize.set(0);
        LOGGER.info("Cleared all pending sync updates");
    }
}
