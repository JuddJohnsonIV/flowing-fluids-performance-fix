# Flowing Fluids Integration Documentation

## Overview

This document describes the comprehensive integration between the Flowing Fluids Fixes mod and the Flowing Fluids mod by Traben. The integration provides performance optimization while maintaining full compatibility with Flowing Fluids' finite fluid mechanics.

## Step 15: Documentation of All Changes and Configurations

### Core Components

#### 1. FluidTickScheduler.java
- **Purpose**: Enhanced fluid tick scheduling with priority-based processing
- **Features**:
  - Adaptive throttling based on TPS (50-1000 updates/tick)
  - Priority queue for deferred updates
  - Deduplication to prevent duplicate scheduling
  - Processing time tracking
- **Configuration**: Controlled by `FlowingFluidsOptimizationConfig`

#### 2. AggressiveFluidOptimizer.java
- **Purpose**: Aggressive fluid update optimization
- **Features**:
  - Distance-based update skipping
  - Configurable delay multipliers
  - Per-tick update limits
  - Flowing Fluids-aware optimization (less aggressive)
- **Optimization Levels**:
  - AGGRESSIVE: Max 15 updates/tick, 24-block distance
  - BALANCED: Max 25 updates/tick, 32-block distance
  - MINIMAL: Max 50 updates/tick, 48-block distance

#### 3. FlowingFluidsAPIIntegration.java
- **Purpose**: Direct integration with Flowing Fluids API
- **Features**:
  - Dynamic update rate control (1x-8x multiplier)
  - Optimized tick delay calculation
  - Pressure system and biome-aware handling
  - API availability detection

#### 4. EmergencyPerformanceMode.java
- **Purpose**: Emergency response to server performance issues
- **Features**:
  - Graduated response levels (NONE, CAUTION, WARNING, EMERGENCY)
  - Configurable TPS thresholds per optimization level
  - Automatic recovery when performance improves
  - Activation tracking and status reporting

#### 5. MultiplayerFluidSync.java
- **Purpose**: Multiplayer synchronization for fluid updates
- **Features**:
  - Batched sync updates (50 per batch)
  - Priority sync for nearby players (16 blocks)
  - Chunk-based update tracking
  - Critical update immediate sync

### Support Components

#### 6. BiomeOptimization.java (Step 22)
- **Purpose**: Biome-aware fluid optimization
- **Features**:
  - Infinite water biome detection (oceans, rivers, swamps)
  - Hot biome evaporation handling
  - Cold biome flow speed reduction
  - Flowing Fluids biome compatibility

#### 7. FlowingFluidsDebugLogger.java (Step 10)
- **Purpose**: Comprehensive debug logging
- **Features**:
  - File-based logging to logs/flowingfluids/
  - Update type tracking (optimized, skipped, deferred)
  - Performance snapshot logging
  - API interaction logging

#### 8. VanillaFluidFallback.java (Step 9)
- **Purpose**: Fallback when Flowing Fluids API unavailable
- **Features**:
  - Vanilla-compatible optimization
  - Distance-based delay calculation
  - Emergency mode integration
  - Automatic activation/deactivation

#### 9. CreateModCompatibility.java (Step 7)
- **Purpose**: Create mod compatibility
- **Features**:
  - Infinite pipe fluid source handling
  - Water wheel requirement modes
  - Hose pulley integration
  - Biome-aware extraction rates

#### 10. FluidOptimizationRollback.java (Step 25)
- **Purpose**: Automatic rollback on instability
- **Features**:
  - Consecutive low TPS detection
  - Emergency activation counting
  - Automatic recovery after cooldown
  - Manual rollback/recover commands

#### 11. AdaptiveFeedbackLoop.java (Step 36)
- **Purpose**: Automatic parameter adjustment
- **Features**:
  - Performance trend analysis
  - Dynamic parameter tuning
  - TPS-based optimization scaling
  - Deferred queue management

#### 12. FlowingFluidsVersionDetector.java (Step 20)
- **Purpose**: Version detection and compatibility
- **Features**:
  - Automatic version detection
  - API capability detection
  - Minimum version warnings
  - Forward compatibility support

#### 13. FluidMemoryOptimizer.java (Step 24)
- **Purpose**: Memory optimization
- **Features**:
  - Periodic cache cleanup
  - Weak reference pooling
  - Memory pressure detection
  - Automatic GC suggestion

### Configuration Options

#### FlowingFluidsOptimizationConfig.java (Step 6)
```
# Optimization Level (AGGRESSIVE, BALANCED, MINIMAL)
optimizationLevel = BALANCED

# Enable Flowing Fluids Integration
enableFlowingFluidsIntegration = true

# Maximum Updates Per Tick
maxUpdatesPerTick = 500

# Critical Distance (immediate updates)
criticalDistance = 16

# Normal Distance (standard updates)
normalDistance = 64

# Delay Multiplier
delayMultiplier = 1

# Enable Player Proximity Prioritization
enablePlayerProximityPrioritization = true

# Enable Detailed Logging
enableDetailedLogging = false

# Enable Flowing Fluids Debug Mode
enableFlowingFluidsDebug = false
```

### Performance Metrics

The integration tracks the following metrics:

- **TPS Impact**: Percentage impact on server TPS
- **Fluid Updates/Second**: Rate of fluid updates
- **Deferred Queue Size**: Pending deferred updates
- **Adaptive Update Limit**: Current dynamic limit
- **Emergency Activations**: Count of emergency mode activations
- **Rollback Count**: Number of rollback events

### Compatibility

#### Flowing Fluids Versions
- Minimum: 0.5.0
- Recommended: 0.6.0+
- Tested: 0.6.1

#### Supported Features
- Finite fluid mechanics
- Pressure systems
- Biome-specific behavior (oceans, rivers, swamps)
- Create mod integration
- Piston pumps

### Troubleshooting

1. **High TPS Impact**: Lower optimization level or reduce maxUpdatesPerTick
2. **Fluid Updates Delayed**: Increase criticalDistance or use MINIMAL optimization
3. **Emergency Mode Frequent**: Check for excessive fluid activity, consider biome optimization
4. **Rollback Activated**: Server under extreme load, check other mods

### API Integration Points

The mod integrates with Flowing Fluids API through:
- `FlowingFluidsAPI.getInstance(modId)` - Get API instance
- `doesModifyThisFluid(Fluid)` - Check if fluid is modified
- `doesBiomeInfiniteWaterRefill(Level, BlockPos)` - Check biome behavior
- `isModCurrentlyMovingFluids()` - Check pressure system activity

### Event Handling (Step 37)

All fluid-related events are processed through `FluidEventHandler.java` which:
- Resets tick counters
- Adjusts dynamic update rates
- Checks for instability
- Collects performance data
- Processes deferred updates
- Handles multiplayer sync
- Logs performance status

## Version History

- 1.0.0: Initial integration with core optimization features
- 1.1.0: Added adaptive feedback loop and rollback mechanism
- 1.2.0: Enhanced Create mod compatibility and biome optimization
