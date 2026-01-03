# Observational Testing Guide

## How to Test the Chunk-Based Fluid System

Since you're the observer, here's what to look for during gameplay:

### **Before vs After Comparison**

#### **Old System (Deferral-Based)**
- ❌ Fluids freezing mid-air
- ❌ Server becoming unresponsive
- ❌ Backlog buildup causing delays
- ❌ Mob interaction issues

#### **New System (Chunk-Based)**
- ✅ Fluids flow naturally
- ✅ Server stays responsive
- ✅ Consistent performance
- ✅ No freezing issues

### **Testing Scenarios**

#### **1. Basic Fluid Flow**
- Place water source blocks
- Observe if water flows normally
- Check for freezing mid-air
- Verify drainage works

#### **2. Large Scale Fluid Systems**
- Create large water bodies (oceans, lakes)
- Build multi-level waterfalls
- Test extensive irrigation systems
- Monitor server TPS during heavy fluid activity

#### **3. Chunk Boundary Testing**
- Place fluids across chunk boundaries
- Move between chunks with active fluids
- Test fluid flow when chunks load/unload
- Verify wake/sleep transitions work

#### **4. Performance Under Load**
- Spawn many fluid sources simultaneously
- Test during server stress (many players, entities)
- Monitor memory usage patterns
- Check for performance degradation

### **What to Observe**

#### **Visual Indicators**
- Fluids flow smoothly without stopping
- No "stuck" water blocks
- Natural spreading and falling behavior
- Responsive to player interactions

#### **Performance Metrics**
- Server TPS remains stable (15-20)
- No sudden TPS drops during fluid activity
- Memory usage stays reasonable
- Chunk loading/unloading works normally

#### **Behavioral Checks**
- Water flows to lower areas correctly
- Source blocks propagate properly
- Drainage systems work as expected
- No fluid "teleporting" or strange behavior

### **Debug Information**

The system provides debug logs. Check logs for:
- "Scheduled fluid update at {} using chunk-based optimization"
- "Woke chunk {}" / "Sleep chunk {}"
- "Processed chunk optimization for {} chunks"

### **Commands for Testing**

Use these commands to monitor the system:
```
/fluidoptimizer status  - Shows system status
/fluidoptimizer stats    - Displays chunk statistics
```

### **Expected Results**

If the system works correctly:
1. **No fluid freezing** - All fluids continue moving
2. **Stable performance** - TPS stays above 15
3. **Natural behavior** - Fluids act like vanilla Minecraft
4. **No lag spikes** - Smooth performance even with many fluids

### **Red Flags to Watch For**

- ❌ Fluids stopping mid-air
- ❌ Server TPS dropping below 10
- ❌ Memory usage climbing continuously
- ❌ Chunks failing to load/unload
- ❌ Mobs becoming unresponsive

### **Success Criteria**

The implementation is successful when:
- Fluids behave exactly like vanilla Minecraft
- Performance is better or equal to vanilla
- No freezing or unresponsiveness occurs
- System handles large fluid volumes gracefully

### **Testing Duration**

Test for at least:
- **30 minutes** of normal gameplay
- **10 minutes** of heavy fluid activity
- **Multiple server restarts** to check stability

### **Comparison Points**

Compare against:
- Vanilla Minecraft (no mods)
- Previous version with deferral system
- Expected behavior from documentation

This observational approach will give you real-world performance data that unit tests can't provide.
