# Flowing Fluids Fixes - User Guide

## Step 16: User Guide for Configuring Integration Settings

### Quick Start

1. **Install the mod** alongside Flowing Fluids (optional but recommended)
2. **Launch the game** - default settings work for most servers
3. **Adjust optimization level** if needed via config file

### Configuration File

Location: `config/flowingfluidsfixes-common.toml`

### Optimization Levels

Choose based on your server's needs:

| Level | Best For | Updates/Tick | Distance | Visual Impact |
|-------|----------|--------------|----------|---------------|
| **AGGRESSIVE** | Low-end servers, high player count | 25 | 24 blocks | Noticeable delays |
| **BALANCED** | Most servers (default) | 50 | 32 blocks | Minimal |
| **MINIMAL** | High-end servers, visual priority | 100 | 48 blocks | None |

### Key Settings

```toml
[performance]
# Optimization level: AGGRESSIVE, BALANCED, or MINIMAL
optimizationLevel = "BALANCED"

# Maximum fluid updates per game tick
maxUpdatesPerTick = 500

# Distance within which fluids update at full speed
criticalDistance = 16

# Distance within which fluids update at normal speed
normalDistance = 64

# Enable player proximity prioritization
enablePlayerProximityPrioritization = true

[debug]
# Enable detailed logging (for troubleshooting)
enableDetailedLogging = false

# Enable Flowing Fluids debug mode
enableFlowingFluidsDebug = false
```

### Visual Fidelity Settings

Trade visual quality for performance:

| Setting | Description |
|---------|-------------|
| **MAXIMUM** | Full visual quality, minimal optimization |
| **HIGH** | High quality with some optimization |
| **BALANCED** | Balance between quality and performance |
| **LOW** | Prioritize performance over visuals |
| **MINIMAL** | Maximum performance |

### Server Performance Profiles

For server admins, auto-profiles adjust based on conditions:

- **IDLE**: Few players, maximum headroom
- **LIGHT**: Light load, standard optimizations
- **NORMAL**: Normal operation
- **BUSY**: High player count, increased optimization
- **EVENT**: Server events, aggressive optimization
- **EMERGENCY**: Critical TPS, maximum optimization

### Troubleshooting

#### Low TPS with Flowing Fluids

1. Set `optimizationLevel = "AGGRESSIVE"`
2. Reduce `maxUpdatesPerTick` to 200
3. Enable emergency mode earlier by setting `emergencyModeThreshold = 15.0`

#### Fluids Not Updating

1. Check that fluids are within `normalDistance` of players
2. Verify emergency mode is not active
3. Try `optimizationLevel = "MINIMAL"`

#### Visual Glitches

1. Increase `criticalDistance`
2. Set visual fidelity to HIGH or MAXIMUM
3. Reduce delay multiplier

### Commands (if available)

```
/flowingfluidsfix status - Show current optimization status
/flowingfluidsfix profile <name> - Switch server profile
/flowingfluidsfix rollback - Trigger manual rollback
/flowingfluidsfix test - Run integration tests
```

### Compatibility

**Fully Compatible:**
- Sodium, Lithium, Phosphor, Starlight
- Create mod (with special integration)
- FerriteCore, Entity Culling
- Thermal Series, AE2

**Partial Compatibility:**
- Mekanism (test fluid transport)
- IndustrialCraft 2

**Incompatible:**
- Other fluid physics mods (use only one)

---

## Step 17: Plan for Future Updates

### Roadmap

#### Version 1.1 (Next)
- [ ] In-game configuration GUI
- [ ] Real-time TPS graph overlay
- [ ] Per-dimension optimization settings

#### Version 1.2
- [ ] Machine learning for predictive optimization
- [ ] Plugin API for other mods
- [ ] Advanced profiling tools

#### Version 1.3
- [ ] Support for new Minecraft versions
- [ ] Enhanced Create mod integration
- [ ] Cloud-based optimization presets

### Version Compatibility Plan

| Minecraft | Forge | Status |
|-----------|-------|--------|
| 1.20.1 | 47.x | ✅ Supported |
| 1.20.4 | 49.x | 🔄 Planned |
| 1.21.x | 51.x | 🔄 Planned |

---

## Step 18: Community Feedback Integration

### Feedback Channels

- **GitHub Issues**: Bug reports and feature requests
- **Discord**: Community discussion
- **CurseForge/Modrinth**: Reviews and comments

### Known Community Requests

1. ✅ Configurable optimization levels
2. ✅ Create mod compatibility
3. ✅ Emergency performance mode
4. ✅ Multiplayer synchronization
5. 🔄 In-game config GUI
6. 🔄 Per-world settings

### Reporting Issues

When reporting issues, include:
1. Minecraft and Forge version
2. Flowing Fluids version (if installed)
3. Optimization level setting
4. Current TPS (F3 debug screen)
5. Log file (latest.log)

### Contributing

See CONTRIBUTING.md for guidelines on:
- Code contributions
- Testing new features
- Documentation improvements
- Translation help

---

## Step 26: Mod Developer Collaboration

### For Mod Developers

If your mod interacts with fluids, you can integrate with our API:

```java
// Check if optimization is active
boolean optimizing = FlowingFluidsOptimizationConfig.enableFlowingFluidsIntegration.get();

// Get current optimization level
var level = FlowingFluidsOptimizationConfig.optimizationLevel.get();

// Check if emergency mode is active
boolean emergency = EmergencyPerformanceMode.isEmergencyMode();

// Register custom fluid handler (future API)
// FlowingFluidsFixes.registerFluidHandler(myHandler);
```

### Integration Points

1. **Pre-update hooks**: Called before fluid updates
2. **Post-update hooks**: Called after fluid updates
3. **Custom fluid registration**: Register modded fluids for optimization
4. **Priority overrides**: Mark certain fluids as critical

### Contact

For collaboration inquiries:
- GitHub Discussions
- Discord DM to maintainers

---

## Appendix: Performance Metrics

### Understanding the Status Log

```
=== Flowing Fluids Performance Status ===
Optimization Level: BALANCED          # Current level
Fluid Optimization: 1000/50 skipped   # Updates processed/skipped
Tick Scheduler: 500 scheduled         # Ticks in queue
Deferred Queue: 25 pending            # Deferred updates
Adaptive Limit: 450 updates/tick      # Current dynamic limit
Current TPS: 19.50                    # Server TPS
Emergency Status: Level=NONE          # Emergency mode
Multiplayer Sync: 100 pending         # Sync queue
==========================================
```

### Ideal Values

| Metric | Healthy | Warning | Critical |
|--------|---------|---------|----------|
| TPS | 18-20 | 15-18 | <15 |
| Deferred Queue | <100 | 100-500 | >500 |
| Emergency Level | NONE | CAUTION | WARNING+ |
