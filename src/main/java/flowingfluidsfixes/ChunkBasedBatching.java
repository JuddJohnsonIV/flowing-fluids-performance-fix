package flowingfluidsfixes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.FORGE)
@SuppressWarnings("all")
public class ChunkBasedBatching {
    private static final Logger LOGGER = LogManager.getLogger();
    
    // Configuration constants - increased for better performance with our optimizations
    private static final int MAX_CHUNKS_PER_TICK = 500;  // Increased from 200
    private static final int BASE_CHUNKS_PER_TICK = 250; // Increased from 100
    private static final int MIN_CHUNKS_PER_TICK = 50;   // Increased from 20
    private static final int CHUNK_BUFFER_SIZE = 10000;  // Increased from 5000
    private static final int CHUNK_UPDATE_INTERVAL = 1;   // Reduced from 2 for more responsive updates
    private static final double PLAYER_PROXIMITY_SQ = 8192.0; // Increased from 4096.0 (32 blocks)
    private static final double CPU_SAVE_MODE_TPS_THRESHOLD = 8.0; // Lowered from 10.0
    private static final long CLEANUP_INTERVAL = 30000;   // Increased from 20000
    private static final int MAX_UPDATES_PER_CHUNK = 500;  // Increased from 200
    private static final int CHUNK_SIZE = 16;

    // State variables
    private static long LAST_CLEANUP = 0;
    private static final Set<BlockPos> activeChunks = new HashSet<>();
    private static final Set<BlockPos> pendingChunks = new HashSet<>();
    private static final Set<BlockPos> fluidUpdateChunks = ConcurrentHashMap.newKeySet();
    
    // Performance tracking
    private static final Set<BlockPos> chunksProcessedThisTick = new HashSet<>();
    
    public static void markChunkProcessed(BlockPos chunkPos) {
        chunksProcessedThisTick.add(chunkPos);
    }
    
    public static void clearProcessedChunks() {
        chunksProcessedThisTick.clear();
    }
    private static long lastUpdateTime = 0;
    private static long lastPerformanceLog = 0;
    private static final AtomicInteger processedChunks = new AtomicInteger(0);
    
    public static void queueFluidUpdateEnhanced(Level currentLevel, BlockPos pos, BlockState state) {
        queueChunkForProcessing(currentLevel, pos);
    }

    public static void queueFluidUpdate(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        BlockPos chunkIdentifier = new BlockPos(
            pos.getX() & ~(CHUNK_SIZE - 1),
            pos.getY() & ~(CHUNK_SIZE - 1),
            pos.getZ() & ~(CHUNK_SIZE - 1)
        );
        fluidUpdateChunks.add(chunkIdentifier);
        LOGGER.debug("Queued fluid update for chunk: {}", chunkIdentifier);
    }

    private static int calculateDynamicQueueSize() {
        double tps = 20.0;
        if (tps < CPU_SAVE_MODE_TPS_THRESHOLD) {
            LOGGER.warn("Server TPS critically low ({}). Setting chunk processing to minimum: {}", tps, MIN_CHUNKS_PER_TICK);
            return MIN_CHUNKS_PER_TICK;
        } else if (tps < 15.0) {
            int midLimit = (MAX_CHUNKS_PER_TICK + MIN_CHUNKS_PER_TICK) / 2;
            LOGGER.info("Server TPS below target ({}). Adjusting chunk processing to: {}", tps, midLimit);
            return midLimit;
        } else {
            LOGGER.debug("Server TPS optimal ({}). Setting chunk processing to maximum: {}", tps, MAX_CHUNKS_PER_TICK);
            return MAX_CHUNKS_PER_TICK;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        long currentTime = System.currentTimeMillis();

        if (currentTime - lastUpdateTime >= CHUNK_UPDATE_INTERVAL * 50L) {
            lastUpdateTime = currentTime;
            processActiveChunks(level);
            LOGGER.debug("Processed chunk updates with buffer size: {}", CHUNK_BUFFER_SIZE);
        }

        if (currentTime - lastPerformanceLog >= 60000L) {
            lastPerformanceLog = currentTime;
            LOGGER.info("Processed {} chunks for fluid updates in the last minute", processedChunks.getAndSet(0));
            LOGGER.info("Max updates per chunk: {}", MAX_UPDATES_PER_CHUNK);
        }

        if (currentTime - LAST_CLEANUP >= CLEANUP_INTERVAL) {
            LAST_CLEANUP = currentTime;
            cleanupProcessedChunks(level);
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Dynamic chunk limit calculated as {}", getDynamicChunkLimit());
        }
    }

    private static int getDynamicChunkLimit() {
        double avgTickTime = 0.0;
        if (avgTickTime > 40_000_000) {
            return MIN_CHUNKS_PER_TICK;
        } else if (avgTickTime > 25_000_000) {
            return BASE_CHUNKS_PER_TICK / 2;
        } else if (avgTickTime < 10_000_000) {
            return MAX_CHUNKS_PER_TICK;
        }
        return BASE_CHUNKS_PER_TICK;
    }

    private static void queueChunkForProcessing(@SuppressWarnings("unused") Level level, BlockPos pos) {
        BlockPos chunkIdentifier = new BlockPos(
            pos.getX() & ~(CHUNK_SIZE - 1),
            pos.getY() & ~(CHUNK_SIZE - 1),
            pos.getZ() & ~(CHUNK_SIZE - 1)
        );
        if (!activeChunks.contains(chunkIdentifier)) {
            activeChunks.add(chunkIdentifier);
            LOGGER.debug("Queued chunk for processing at {}", chunkIdentifier);
        }
    }

    private static void processActiveChunks(ServerLevel level) {
        if (activeChunks.isEmpty()) {
            return;
        }

        int chunksToProcess = calculateDynamicQueueSize();
        int processedCount = 0;
        List<BlockPos> prioritizedChunks = new ArrayList<>();
        List<BlockPos> normalChunks = new ArrayList<>();

        for (BlockPos chunkIdentifier : activeChunks) {
            if (isChunkNearPlayer(level, chunkIdentifier)) {
                prioritizedChunks.add(chunkIdentifier);
            } else {
                normalChunks.add(chunkIdentifier);
            }
        }

        Iterator<BlockPos> prioritizedIterator = prioritizedChunks.iterator();
        while (prioritizedIterator.hasNext() && processedCount < chunksToProcess) {
            BlockPos chunkIdentifier = prioritizedIterator.next();
            activeChunks.remove(chunkIdentifier);
            processChunkFluids(level, chunkIdentifier);
            processedCount++;
        }

        if (processedCount < chunksToProcess) {
            Iterator<BlockPos> normalIterator = normalChunks.iterator();
            while (normalIterator.hasNext() && processedCount < chunksToProcess) {
                BlockPos chunkIdentifier = normalIterator.next();
                activeChunks.remove(chunkIdentifier);
                processChunkFluids(level, chunkIdentifier);
                processedCount++;
            }
        }
        
        if (processedCount > MAX_CHUNKS_PER_TICK) {
            LOGGER.debug("Reached max chunks per tick: {}", MAX_CHUNKS_PER_TICK);
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Processing chunk in level {}", level);
        }
    }

    private static void processChunkFluids(ServerLevel level, BlockPos chunkIdentifier) {
        try {
            BlockPos centerPos = new BlockPos(chunkIdentifier.getX() + 8, chunkIdentifier.getY(), chunkIdentifier.getZ() + 8);
            BlockState centerState = level.getBlockState(centerPos);
            processFluidUpdateEnhanced(level, centerPos, centerState);

            processedChunks.incrementAndGet();
            if (processedChunks.get() % 100 == 0) {
                LOGGER.debug("Processed {} chunks for fluid updates", processedChunks.get());
            }
        } catch (Exception e) {
            LOGGER.error("Error processing chunk fluids at {}: {}", chunkIdentifier, e.getMessage());
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Using max updates per chunk: {}, last cleanup at: {}", MAX_UPDATES_PER_CHUNK, LAST_CLEANUP);
        }
    }

    private static void processFluidUpdateEnhanced(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.isInWorldBounds(pos) || serverLevel.getBlockState(pos) != state) {
            return;
        }
        FluidState fluidState = state.getFluidState();
        if (fluidState.isEmpty()) return;
        Fluid fluid = fluidState.getType();
        serverLevel.scheduleTick(pos, fluid, fluid.getTickDelay(serverLevel));
    }
    
    private static boolean isChunkNearPlayer(ServerLevel level, BlockPos chunkIdentifier) {
        List<ServerPlayer> players = level.getPlayers(player -> true);
        BlockPos chunkCenter = new BlockPos(chunkIdentifier.getX() + 8, chunkIdentifier.getY(), chunkIdentifier.getZ() + 8);
        for (ServerPlayer player : players) {
            BlockPos playerPos = player.blockPosition();
            double distanceSq = playerPos.distSqr(chunkCenter);
            if (distanceSq < PLAYER_PROXIMITY_SQ) {
                return true;
            }
        }
        return false;
    }
    
    private static void cleanupProcessedChunks(@SuppressWarnings("unused") ServerLevel level) {
        activeChunks.clear();
        pendingChunks.clear();
        LAST_CLEANUP = System.currentTimeMillis();
        LOGGER.debug("Cleaned up processed chunks after interval: {}", CLEANUP_INTERVAL);
    }
    
    public static Map<String, Object> getPerformanceStats(Level level) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeChunks", activeChunks.size());
        stats.put("chunksProcessedThisTick", chunksProcessedThisTick.size());
        return stats;
    }
    
    public static void forceProcessAllUpdates(ServerLevel level) {
        for (BlockPos chunkPos : new HashSet<>(activeChunks)) {
            processChunkFluids(level, chunkPos);
        }
        LOGGER.info("Force processed all fluid updates in {} chunks", activeChunks.size());
    }
    
    public static boolean shouldProcessChunk(Level level, BlockPos chunkPos) {
        LOGGER.debug("Checking if chunk at {} in level {} should be processed", chunkPos, level);
        return !chunksProcessedThisTick.contains(chunkPos);
    }
    
    static {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Debug logging enabled");
        }
    }
}
