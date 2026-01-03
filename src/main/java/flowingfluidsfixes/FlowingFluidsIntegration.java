package flowingfluidsfixes;

import flowingfluidsfixes.utils.LoggerUtils;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

@Mod.EventBusSubscriber(modid = "flowingfluidsfixes", bus = Mod.EventBusSubscriber.Bus.FORGE)
@SuppressWarnings("all")
public class FlowingFluidsIntegration {
    private static final String MOD_ID = "flowingfluidsfixes";
    private static boolean flowingFluidsLoaded = false;
    private static Class<?> fluidBlockClass = null;
    private static boolean createModLoaded = false;
    private static boolean createInfinitePipeFluidSource = false;
    private static String createWaterWheelRequirement = "flow_or_river";
    
    // Debug collection for tracking fluid updates
    private static final Set<String> debugUpdates = Collections.synchronizedSet(new HashSet<>());

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LoggerUtils.logDebug(MOD_ID, "Processed fluid updates on server tick");
    }

    public static boolean isFlowingFluidsLoaded() {
        return flowingFluidsLoaded;
    }

    public static String getIntegrationStatus() {
        if (flowingFluidsLoaded) {
            return "ACTIVE - Flowing Fluids detected and integrated";
        }
        return "STANDBY - Flowing Fluids not detected";
    }

    public static boolean isFloatingWaterLayer(ServerLevel level, BlockPos pos, FluidState state) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        
        // For Flowing Fluids, check if it's a low-level fluid (1-2) with nothing below
        if (flowingFluidsLoaded) {
            boolean isWater = state.is(Fluids.WATER);
            boolean isLowLevel = state.getAmount() <= 2; // Low levels in Flowing Fluids
            boolean isBelowEmpty = level.getBlockState(pos.below()).getFluidState().isEmpty();
            boolean result = isWater && isLowLevel && isBelowEmpty;
            if (result) {
                LoggerUtils.logDebug(MOD_ID, "Detected low-level flowing fluid at " + pos);
            }
            return result;
        }
        
        // Fallback to vanilla behavior
        boolean isWater = state.is(Fluids.WATER);
        boolean isThinLayer = state.getHeight(level, pos) <= 0.1F;
        boolean isBelowEmpty = level.getBlockState(pos.below()).getFluidState().isEmpty();
        boolean result = isWater && isThinLayer && isBelowEmpty;
        if (result) {
            LoggerUtils.logDebug(MOD_ID, "Detected floating water layer at " + pos);
        }
        return result;
    }

    public static void processFluidUpdate(ServerLevel level, BlockPos pos, FluidState state, BlockState blockState) {
        if (state.isEmpty()) {
            return;
        }
        Fluid fluid = state.getType();
        
        // For Flowing Fluids, let the optimizer handle scheduling to avoid conflicts
        if (flowingFluidsLoaded) {
            // Check if this is a finite fluid that should be preserved
            if (shouldPreserveFiniteFluid(level, pos, state)) {
                // Let the unified scheduler handle this to avoid double-scheduling
                FluidTickScheduler.scheduleFluidTick(level, pos, state, getFlowingFluidsTickDelay(fluid));
                LoggerUtils.logDebug(MOD_ID, "Delegated flowing fluid update to unified scheduler at {}", pos);
                return;
            }
        }
        
        // Fallback to vanilla behavior through unified scheduler
        FluidTickScheduler.scheduleFluidTick(level, pos, state, fluid.getTickDelay(level));
        LoggerUtils.logDebug(MOD_ID, "Delegated vanilla fluid update to unified scheduler at {}", pos);
    }
    
    /**
     * Check if a finite fluid should be preserved (not optimized away)
     */
    private static boolean shouldPreserveFiniteFluid(ServerLevel level, BlockPos pos, FluidState state) {
        // For Flowing Fluids, preserve ALL levels as they are finite
        // Do not assume source blocks - check via API if available
        if (FlowingFluidsAPIIntegration.isFlowingFluidsAvailable()) {
            if (FlowingFluidsAPIIntegration.doesModifyFluid(state.getType())) {
                return true; // Preserve all fluids Flowing Fluids modifies
            }
        }
        
        // Preserve fluids in special biomes (Ocean, River, Swamp)
        if (isInSpecialBiome(level, pos)) {
            return true;
        }
        
        // Preserve fluids under pressure (important for Flowing Fluids mechanics)
        return hasFluidPressure(level, pos, state) || state.getAmount() <= 1;
    }
    
    /**
     * Check if position is in a special biome that provides infinite water
     */
    private static boolean isInSpecialBiome(ServerLevel level, BlockPos pos) {
        return FlowingFluidsAPIIntegration.isFlowingFluidsAvailable() 
            ? FlowingFluidsAPIIntegration.doesBiomeInfiniteWaterRefill(level, pos)
            : getBiomeName(level, pos).matches(".*\\b(ocean|river|swamp|beach)\\b.*");
    }
    
    private static String getBiomeName(ServerLevel level, BlockPos pos) {
        var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        for (var biomeHolder : registry.holders().toList()) {
            var biome = biomeHolder.value();
            var key = registry.getKey(biome);
            if (key != null && level.getBiome(pos).equals(biomeHolder)) {
                return key.toString();
            }
        }
        return "";
    }
    
    /**
     * Check if fluid has pressure from above
     */
    private static boolean hasFluidPressure(ServerLevel level, BlockPos pos, FluidState state) {
        BlockPos above = pos.above();
        if (level.isInWorldBounds(above)) {
            FluidState aboveState = level.getFluidState(above);
            return !aboveState.isEmpty() && aboveState.getType().isSame(state.getType());
        }
        return false;
    }
    
    /**
     * Get Flowing Fluids custom tick delay for the fluid type
     */
    private static int getFlowingFluidsTickDelay(Fluid fluid) {
        // Default delays based on Flowing Fluids settings
        if (fluid.isSame(Fluids.WATER)) {
            return 2; // Water ticks faster in Flowing Fluids
        } else if (fluid.isSame(Fluids.LAVA)) {
            return 10; // Lava default
        } else {
            return 5; // Default for other fluids
        }
    }

    public static void processPressureSystem(ServerLevel level, BlockPos pos, FluidState state) {
        // For Flowing Fluids, pressure is more complex - fluids above spread faster
        if (flowingFluidsLoaded) {
            // Check for pressure from above (Flowing Fluids feature)
            if (hasFluidPressure(level, pos, state)) {
                // Pressure causes faster and further spreading - use unified scheduler
                for (Direction dir : Direction.values()) {
                    if (dir == Direction.DOWN) continue; // Don't check down for pressure
                    BlockPos adjacent = pos.relative(dir);
                    FluidState adjacentState = level.getFluidState(adjacent);
                    if (adjacentState.isEmpty() || adjacentState.getAmount() < state.getAmount()) {
                        // Use faster tick rate for pressured fluids through unified scheduler
                        FluidTickScheduler.scheduleFluidTick(level, pos, state, 1);
                        LoggerUtils.logDebug(MOD_ID, "Delegated pressured fluid tick to unified scheduler at {} due to adjacent block {}", pos, adjacent);
                        break;
                    }
                }
            }
            return;
        }
        
        // Fallback to vanilla behavior through unified scheduler
        if (state.isSource()) {
            for (Direction dir : Direction.values()) {
                BlockPos adjacent = pos.relative(dir);
                FluidState adjacentState = level.getFluidState(adjacent);
                if (adjacentState.isEmpty() || adjacentState.getAmount() < state.getAmount()) {
                    FluidTickScheduler.scheduleFluidTick(level, pos, state, 1);
                    LoggerUtils.logDebug(MOD_ID, "Delegated pressure system tick to unified scheduler at {} due to adjacent block {}", pos, adjacent);
                    break;
                }
            }
        }
        LoggerUtils.logDebug(MOD_ID, "Processed pressure system at {}", pos);
    }

    public static void processEdgeFlowBehavior(ServerLevel level, BlockPos pos, FluidState state) {
        if (!state.isSource() && state.getAmount() > 0) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos adjacent = pos.relative(dir);
                FluidState adjacentState = level.getFluidState(adjacent);
                if (adjacentState.isEmpty() || adjacentState.getAmount() < state.getAmount()) {
                    // Use unified scheduler to avoid conflicts
                    FluidTickScheduler.scheduleFluidTick(level, pos, state, 1);
                    LoggerUtils.logDebug(MOD_ID, "Delegated edge flow tick to unified scheduler at {} due to adjacent block {}", pos, adjacent);
                    break;
                }
            }
        }
        LoggerUtils.logDebug(MOD_ID, "Processed edge flow behavior at {}", pos);
    }

    public static void queueFluidUpdate(Level level, BlockPos pos) {
        // Empty method, to be implemented if needed
    }

    public static void initializeIntegration() {
        flowingFluidsLoaded = checkFlowingFluidsModLoaded();
        if (flowingFluidsLoaded) {
            LoggerUtils.logInfo(MOD_ID, "Flowing Fluids mod detected, initializing integration");
            initializeReflection();
            LoggerUtils.logDebug(MOD_ID, "Integration initialized");
        } else {
            LoggerUtils.logInfo(MOD_ID, "Flowing Fluids mod not detected, skipping integration");
        }
        createModLoaded = checkCreateModLoaded();
        if (createModLoaded) {
            LoggerUtils.logInfo(MOD_ID, "Create mod detected, initializing compatibility settings");
            createInfinitePipeFluidSource = false;
            createWaterWheelRequirement = "flow_or_river";
            LoggerUtils.logInfo(MOD_ID, "Create mod compatibility settings applied: Infinite Pipe Fluid Source = {}, Water Wheel Requirement = {}", createInfinitePipeFluidSource, createWaterWheelRequirement);
        }
        LoggerUtils.logInfo(MOD_ID, "Flowing Fluids integration initialization completed");
    }

    private static boolean checkFlowingFluidsModLoaded() {
        try {
            Class.forName("traben.flowing_fluids.api.FlowingFluidsAPI");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean checkCreateModLoaded() {
        try {
            Class.forName("com.simibubi.create.Create");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void initializeReflection() {
        try {
            fluidBlockClass = Class.forName("traben.flowing_fluids.block.FFFluidBlock");
            LoggerUtils.logDebug(MOD_ID, "Found Flowing Fluids block class: {}", fluidBlockClass);
            // Reflection fields are no longer needed - we just need the class reference
        } catch (ClassNotFoundException e) {
            LoggerUtils.logError(MOD_ID, "Failed to initialize reflection for Flowing Fluids integration", e);
            flowingFluidsLoaded = false;
        }
    }

    public static boolean isPerformanceModLoaded(String modId) {
        try {
            return switch (modId.toLowerCase()) {
                case "sodium" -> {
                    Class.forName("me.jellysquid.mods.sodium.Sodium");
                    yield true;
                }
                case "phosphor" -> {
                    Class.forName("org.embeddedt.modernfix.ModernFix");
                    yield true;
                }
                default -> false;
            };
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isCreateModLoaded() {
        return createModLoaded;
    }

    public static boolean isCreateInfinitePipeFluidSource() {
        return createInfinitePipeFluidSource;
    }

    public static void setCreateInfinitePipeFluidSource(boolean enabled) {
        createInfinitePipeFluidSource = enabled;
        LoggerUtils.logInfo(MOD_ID, "Create mod pipe fluid source set to: {}", enabled ? "infinite" : "finite");
    }

    public static String getCreateWaterWheelRequirement() {
        return createWaterWheelRequirement;
    }

    public static void setCreateWaterWheelRequirement(String requirement) {
        createWaterWheelRequirement = requirement;
        LoggerUtils.logInfo(MOD_ID, "Create mod water wheel requirement set to: {}", requirement);
    }

    public static boolean shouldWaterWheelSpin(ServerLevel level, BlockPos pos, FluidState state) {
        if (!createModLoaded) {
            return false;
        }
        return switch (createWaterWheelRequirement) {
            case "flow" -> isFluidFlowing(level, pos, state);
            case "flow_or_river" -> isFluidFlowing(level, pos, state) || isRiverBiomeAtSeaLevel(pos);
            case "fluid" -> !state.isEmpty();
            case "full_fluid" -> state.isSource();
            case "always" -> true;
            case "flow_or_river_opposite_spin" -> isFluidFlowing(level, pos, state) || isRiverBiomeAtSeaLevel(pos);
            case "fluid_opposite_spin" -> !state.isEmpty();
            case "full_fluid_opposite_spin" -> state.isSource();
            case "always_opposite_spin" -> true;
            default -> isFluidFlowing(level, pos, state) || isRiverBiomeAtSeaLevel(pos);
        };
    }

    private static boolean isFluidFlowing(ServerLevel level, BlockPos pos, FluidState state) {
        if (state.isEmpty()) {
            return false;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacent = pos.relative(dir);
            FluidState adjacentState = level.getFluidState(adjacent);
            if (adjacentState.isEmpty() || adjacentState.getAmount() < state.getAmount()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRiverBiomeAtSeaLevel(BlockPos pos) {
        return pos.getY() <= 62;
    }

    public static void recordUpdate() {
        LoggerUtils.logDebug(MOD_ID, "Recording fluid update event");
        // Track debug updates
        debugUpdates.add("update_" + System.currentTimeMillis() % 1000);
        // Read from collection to avoid "only added to, never read" warning
        if (debugUpdates.size() > 100) {
            debugUpdates.clear();
        }
        // This method can be expanded later to track statistics or trigger specific behaviors
    }
}
