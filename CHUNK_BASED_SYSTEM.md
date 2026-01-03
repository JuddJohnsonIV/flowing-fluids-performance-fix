# Chunk-Based Fluid Management System

## Overview

The FluidOptimizer mod has been completely redesigned to replace the problematic deferral system with a **Chunk-Based Sleep/Wake System** inspired by cellular automata optimization techniques used in professional voxel engines.

## Problem with Previous System

The old deferral system had several critical issues:
- **Freezing**: Fluid updates could be deferred indefinitely, causing fluids to freeze mid-air
- **Backlog**: Deferred ticks created massive backlogs that couldn't be processed efficiently
- **Complexity**: Complex deferral logic was hard to debug and maintain
- **Unresponsiveness**: Server became unresponsive when deferral queues overflowed

## New Solution: Chunk-Based Sleep/Wake

### Core Concept

Instead of deferring individual fluid updates, entire chunks go to "sleep" when inactive and "wake" when activity occurs nearby. This provides natural throttling without preventing updates.

### How It Works

1. **Chunk States**: Each chunk can be ACTIVE or SLEEPING
2. **Activity Tracking**: System tracks fluid activity per chunk
3. **Sleep Threshold**: Chunks sleep after 100 ticks of inactivity
4. **Wake Propagation**: Activity in one chunk wakes nearby chunks (3-chunk radius)
5. **Smart Delays**: All updates are scheduled, but sleeping chunks get longer delays

### Key Components

#### ChunkBasedFluidManager
- Manages chunk sleep/wake states
- Tracks active fluid positions per chunk
- Handles cleanup of inactive chunks
- Provides statistics and monitoring

#### FluidTickScheduler (Simplified)
- Routes all fluid updates through chunk system
- No more deferral queues or complex logic
- Clean, simple interface

#### FluidEventHandler (Updated)
- Integrates with chunk-based system
- Provides comprehensive status reporting
- Handles batch processing efficiently

## Performance Benefits

| Feature | Old Deferral System | New Chunk System |
|---------|-------------------|------------------|
| **Freezing Risk** | High (backlog overflow) | None (all updates processed) |
| **Memory Usage** | High (deferred queues) | Low (automatic cleanup) |
| **Response Time** | Poor (backlog delays) | Good (immediate scheduling) |
| **CPU Load** | Spiky (batch processing) | Consistent (distributed load) |
| **Complexity** | High (complex logic) | Low (simple state machine) |

## Configuration

### Sleep/Wake Parameters
- **Sleep Threshold**: 100 ticks without activity
- **Wake Radius**: 3 chunks (96 blocks)
- **Max Fluids/Chunk**: 50 per tick, 500 tracked total
- **Delay Adjustment**: Sleeping chunks get 3x delay (max 20 ticks)

### Performance Tuning
- Active chunks get normal priority
- Sleeping chunks get reduced priority but still process
- System automatically adapts to server load
- Memory usage stays bounded

## Implementation Details

### Chunk State Management
```java
// Each chunk tracks its state
private static class ChunkFluidState {
    private boolean isSleeping = false;
    
    public void wake() { isSleeping = false; }
    public void sleep() { isSleeping = true; }
    public boolean isSleeping() { return isSleeping; }
}
```

### Activity Propagation
When activity occurs in a chunk:
1. Wake the target chunk
2. Wake all chunks within 3-chunk radius
3. Update activity timestamp
4. Track fluid position

### Memory Management
- Automatic cleanup of chunks inactive for 1000+ ticks
- Position tracking limited to 500 per chunk
- FIFO removal when limits exceeded
- No memory leaks or unbounded growth

## Testing

Comprehensive test suite includes:
- Basic scheduling functionality
- Event handler integration
- Chunk optimization processing
- Statistics collection
- System stability under load
- Multiple fluid update scenarios

## Usage

The system is completely transparent to users:
1. Install the updated mod
2. Fluids work normally without freezing
3. Performance is automatically optimized
4. No configuration required

## Monitoring

Use `/fluidoptimizer status` command to view:
- Active vs sleeping chunks
- Total fluid positions tracked
- System performance metrics
- Event handler statistics

## Migration from Old System

The new system is a drop-in replacement:
- All existing functionality preserved
- Better performance and reliability
- No configuration changes needed
- Automatic migration on mod load

## Future Enhancements

Potential improvements:
- Dynamic sleep threshold based on TPS
- Adaptive wake radius based on player density
- Chunk priority levels (high/medium/low)
- Integration with other optimization mods

## Conclusion

The Chunk-Based Sleep/Wake System provides:
- ✅ No fluid freezing
- ✅ Better performance
- ✅ Lower memory usage
- ✅ Simpler implementation
- ✅ Automatic scaling
- ✅ Better user experience

This approach is used in professional voxel engines and cellular automata simulations for optimal performance without sacrificing functionality.
