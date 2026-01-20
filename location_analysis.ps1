$bytes = [System.IO.File]::ReadAllBytes('c:\Users\jwema\CascadeProjects\FlowingFluidsStandalone\location_profile.sparkprofile')
$text = [System.Text.Encoding]::ASCII.GetString($bytes)

Write-Host "=== LOCATION-BASED PERFORMANCE ANALYSIS ==="
Write-Host ""

Write-Host "Analyzing performance issues when standing in specific locations..."
Write-Host ""

# Look for chunk-related operations (location-specific)
$chunkPatterns = @(
    'chunk',
    'Chunk',
    'LevelChunk',
    'chunk.*load',
    'chunk.*unload',
    'getChunk'
)

Write-Host "=== CHUNK OPERATIONS (LOCATION-SPECIFIC) ==="
foreach ($pattern in $chunkPatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "$pattern : $($matches.Count) operations"
    }
}

# Look for entity operations (can be location-specific)
$entityPatterns = @(
    'entity',
    'Entity',
    'mob',
    'Mob',
    'player',
    'Player'
)

Write-Host ""
Write-Host "=== ENTITY OPERATIONS (LOCATION-SPECIFIC) ==="
foreach ($pattern in $entityPatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "$pattern : $($matches.Count) operations"
    }
}

# Look for block operations (location-specific)
$blockPatterns = @(
    'block',
    'Block',
    'getBlockState',
    'setBlockState',
    'BlockPos'
)

Write-Host ""
Write-Host "=== BLOCK OPERATIONS (LOCATION-SPECIFIC) ==="
foreach ($pattern in $blockPatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "$pattern : $($matches.Count) operations"
    }
}

# Look for world generation/loading (location-specific)
$worldPatterns = @(
    'world',
    'World',
    'level',
    'Level',
    'dimension',
    'Dimension'
)

Write-Host ""
Write-Host "=== WORLD OPERATIONS (LOCATION-SPECIFIC) ==="
foreach ($pattern in $worldPatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "$pattern : $($matches.Count) operations"
    }
}

# Look for specific performance bottlenecks
$bottleneckPatterns = @(
    'tick',
    'Tick',
    'update',
    'Update',
    'process',
    'Process'
)

Write-Host ""
Write-Host "=== PROCESSING OPERATIONS (POTENTIAL BOTTLENECKS) ==="
foreach ($pattern in $bottleneckPatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "$pattern : $($matches.Count) operations"
    }
}

Write-Host ""
Write-Host "=== FLOWING FLUIDS LOCATION ANALYSIS ==="

# Check if Flowing Fluids is causing location-specific issues
$flowingFluidsOps = [regex]::Matches($text, 'flowing_fluids', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
$vanillaFluidOps = [regex]::Matches($text, 'FlowingFluid', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count

Write-Host "Flowing Fluids operations: $flowingFluidsOps"
Write-Host "Vanilla fluid operations: $vanillaFluidOps"

if ($flowingFluidsOps -gt 100) {
    Write-Host "⚠️  HIGH: Flowing Fluids operations are elevated"
    Write-Host "   This could indicate fluid-related performance in specific locations"
} elseif ($flowingFluidsOps -gt 50) {
    Write-Host "🟡 MODERATE: Flowing Fluids operations are moderate"
} else {
    Write-Host "✅ LOW: Flowing Fluids operations are under control"
}

Write-Host ""
Write-Host "=== LOCATION-SPECIFIC BOTTLENECKS ==="

# Look for high-frequency operations that could be location-specific
$operationCounts = @{
    "Chunk operations" = [regex]::Matches($text, 'chunk', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
    "Entity operations" = [regex]::Matches($text, 'entity', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
    "Block operations" = [regex]::Matches($text, 'block', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
    "World operations" = [regex]::Matches($text, 'world', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
    "Fluid operations" = [regex]::Matches($text, 'fluid', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
}

# Sort by count to find the biggest bottlenecks
$sortedOps = $operationCounts.GetEnumerator() | Sort-Object -Property Value -Descending

Write-Host "Operations by frequency (highest potential bottlenecks):"
foreach ($op in $sortedOps) {
    $status = ""
    if ($op.Value -gt 1000) {
        $status = "🔴 CRITICAL"
    } elseif ($op.Value -gt 500) {
        $status = "🟠 HIGH"
    } elseif ($op.Value -gt 200) {
        $status = "🟡 MODERATE"
    } else {
        $status = "✅ LOW"
    }
    Write-Host "$($op.Key): $($op.Value) $status"
}

Write-Host ""
Write-Host "=== RECOMMENDATIONS ==="

$topBottleneck = $sortedOps | Select-Object -First 1
Write-Host "Primary bottleneck: $($topBottleneck.Key) ($($topBottleneck.Value) operations)"

switch ($topBottleneck.Key) {
    "Chunk operations" {
        Write-Host "→ Likely chunk loading/unloading issues in specific areas"
        Write-Host "→ Check for chunk boundaries, world borders, or new chunks"
        Write-Host "→ Consider reducing render distance or view distance"
    }
    "Entity operations" {
        Write-Host "→ Likely entity processing issues in specific areas"
        Write-Host "→ Check for entity farms, mob spawners, or high entity counts"
        Write-Host "→ Consider reducing entity simulation distance"
    }
    "Block operations" {
        Write-Host "→ Likely block update issues in specific areas"
        Write-Host "→ Check for redstone machines, pistons, or block update cascades"
        Write-Host "→ Look for water/lava flows or sand/gravel cascades"
    }
    "Fluid operations" {
        Write-Host "→ Likely fluid-related performance issues"
        Write-Host "→ Check for large water/lava bodies or complex fluid systems"
        Write-Host "→ Our FlowingFluidsFixes should help with this"
    }
    "World operations" {
        Write-Host "→ Likely world generation/loading issues"
        Write-Host "→ Check for new chunks being generated"
        Write-Host "→ Consider pre-generating chunks or reducing exploration"
    }
}
