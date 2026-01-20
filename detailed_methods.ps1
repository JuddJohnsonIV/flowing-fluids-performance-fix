$bytes = [System.IO.File]::ReadAllBytes('c:\Users\jwema\CascadeProjects\FlowingFluidsStandalone\methods_profile.sparkprofile')
$text = [System.Text.Encoding]::ASCII.GetString($bytes)

Write-Host "=== DETAILED METHOD BOTTLENECK ANALYSIS ==="
Write-Host ""

# Extract specific method signatures with context
$methodSignatures = [regex]::Matches($text, '[a-zA-Z_$][a-zA-Z0-9_$]*m_\d+_\d+[a-zA-Z0-9_$]*', [Text.RegularExpressions.RegexOptions]::IgnoreCase)

Write-Host "=== TOP 50 METHOD CALLS ==="
$methodCounts = @{}
foreach ($method in $methodSignatures) {
    $sig = $method.Value
    if ($methodCounts.ContainsKey($sig)) {
        $methodCounts[$sig]++
    } else {
        $methodCounts[$sig] = 1
    }
}

$topMethods = $methodCounts.GetEnumerator() | Sort-Object -Property Value -Descending | Select-Object -First 50

foreach ($method in $topMethods) {
    $calls = $method.Value
    $status = ""
    if ($calls -gt 1000) {
        $status = "🔴 CRITICAL"
    } elseif ($calls -gt 500) {
        $status = "🟠 HIGH"
    } elseif ($calls -gt 200) {
        $status = "🟡 MODERATE"
    } elseif ($calls -gt 100) {
        $status = "🟢 LOW"
    } else {
        $status = "✅ MINIMAL"
    }
    
    # Extract class name
    if ($method.Key -match '^([a-zA-Z_$][a-zA-Z0-9_$]*)m_\d+_\d+') {
        $className = $matches[1]
        Write-Host "($($calls) calls) $className.m_*_* $status"
    } else {
        Write-Host "($($calls) calls) $($method.Key) $status"
    }
}

Write-Host ""
Write-Host "=== SPECIFIC HIGH-CALL METHODS ==="

# Look for specific problematic method patterns
$problematicMethods = @{
    "LevelChunk" = @()
    "ServerChunkCache" = @()
    "ChunkTaskPriorityQueue" = @()
    "ChunkMap" = @()
    "EntityTracker" = @()
    "LevelTicks" = @()
}

foreach ($category in $problematicMethods.Keys) {
    $pattern = "$category.*?m_\d+_\d+"
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    
    Write-Host "$category methods:"
    foreach ($match in $matches | Select-Object -First 10) {
        # Get context around the method
        $start = [Math]::Max(0, $match.Index - 50)
        $end = [Math]::Min($text.Length - 1, $match.Index + 50)
        $context = $text.Substring($start, $end - $start)
        
        # Extract just the method signature
        if ($context -match '([a-zA-Z_$][a-zA-Z0-9_$]*m_\d+_\d+[a-zA-Z0-9_$]*)') {
            $methodSig = $matches[1]
            $count = $methodCounts[$methodSig]
            Write-Host "  $methodSig ($count calls)"
        }
    }
    Write-Host ""
}

Write-Host "=== OUR OPTIMIZATION IMPACT ==="

# Count our specific optimization calls
$ourOptimizationMethods = @(
    'onNeighborNotify',
    'isHighStressLocation', 
    'incrementBlockUpdateCount',
    'incrementChunkGenerationCount',
    'isChunkNearPlayers',
    'shouldProcessFluid'
)

Write-Host "Our optimization method calls:"
foreach ($method in $ourOptimizationMethods) {
    $matches = [regex]::Matches($text, $method, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    Write-Host "  $method : $($matches.Count) calls"
}

Write-Host ""
Write-Host "=== OPTIMIZATION RECOMMENDATIONS ==="

# Analyze top methods and suggest optimizations
$totalMethodCalls = $methodCounts.Values | Measure-Object -Sum | Select-Object -ExpandProperty Sum
Write-Host "Total method calls in profile: $totalMethodCalls"

# Get top 10 methods for analysis
$top10 = $topMethods | Select-Object -First 10
Write-Host ""
Write-Host "Top 10 methods needing attention:"

foreach ($method in $top10) {
    $methodName = $method.Key
    $callCount = $method.Value
    
    # Analyze method name for optimization suggestions
    $suggestion = ""
    if ($methodName -match 'LevelChunk') {
        $suggestion = "→ Chunk access optimization: Implement chunk caching and proximity culling"
    } elseif ($methodName -match 'ServerChunkCache') {
        $suggestion = "→ Chunk cache optimization: Implement cache invalidation and batching"
    } elseif ($methodName -match 'ChunkTaskPriorityQueue') {
        $suggestion = "→ Chunk task optimization: Implement task batching and priority queuing"
    } elseif ($methodName -match 'ChunkMap') {
        $suggestion = "→ Chunk mapping optimization: Implement spatial partitioning and indexing"
    } elseif ($methodName -match 'EntityTracker') {
        $suggestion = "→ Entity tracking optimization: Implement distance-based culling and batching"
    } elseif ($methodName -match 'LevelTicks') {
        $suggestion = "→ Tick scheduling optimization: Implement tick throttling and batching"
    } elseif ($methodName -match 'BlockState') {
        $suggestion = "→ Block state optimization: Implement state caching and update batching"
    } elseif ($methodName -match 'FluidState') {
        $suggestion = "→ Fluid state optimization: Implement state caching and flow throttling"
    } else {
        $suggestion = "→ General optimization: Consider caching, batching, or throttling"
    }
    
    Write-Host "  $methodName ($callCount calls)"
    Write-Host "    $suggestion"
}

Write-Host ""
Write-Host "=== IMMEDIATE ACTIONS NEEDED ==="

# Check if our optimizations are actually working
$ourTotalCalls = 0
foreach ($method in $ourOptimizationMethods) {
    $matches = [regex]::Matches($text, $method, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    $ourTotalCalls += $matches.Count
}

if ($ourTotalCalls -lt 10) {
    Write-Host "⚠️  WARNING: Our optimization methods have very few calls"
    Write-Host "   This suggests our optimizations may not be intercepting the right methods"
    Write-Host "   Consider:"
    Write-Host "   1. Adding more comprehensive method interception"
    Write-Host "   2. Implementing mixin-based method replacement"
    Write-Host "   3. Adding event-based optimization hooks"
} else {
    Write-Host "✅ Our optimizations are being called ($ourTotalCalls total)"
}

Write-Host ""
Write-Host "=== SPECIFIC METHOD OPTIMIZATION STRATEGIES ==="

# Provide specific optimization strategies for the top methods
if ($top10.Count -gt 0) {
    Write-Host "For the top bottleneck methods:"
    
    foreach ($method in $top10 | Select-Object -First 5) {
        $methodName = $method.Key
        $callCount = $method.Value
        
        Write-Host ""
        Write-Host "Method: $methodName ($callCount calls)"
        
        if ($methodName -match 'LevelChunk') {
            Write-Host "Strategy: Chunk Access Optimization"
            Write-Host "- Implement chunk proximity culling (already done)"
            Write-Host "- Add chunk access caching with expiry"
            Write-Host "- Implement chunk preloading for player areas"
            Write-Host "- Limit concurrent chunk access per tick"
        }
        elseif ($methodName -match 'ServerChunkCache') {
            Write-Host "Strategy: Chunk Cache Optimization"
            Write-Host "- Implement cache warming for player areas"
            Write-Host "- Add cache invalidation batching"
            Write-Host "- Implement cache size limits and cleanup"
            Write-Host "- Use LRU cache for frequently accessed chunks"
        }
        elseif ($methodName -match 'ChunkTaskPriorityQueue') {
            Write-Host "Strategy: Chunk Task Optimization"
            Write-Host "- Implement task batching and coalescing"
            Write-Host "- Add priority-based task scheduling"
            Write-Host "- Limit tasks per tick based on MSPT"
            Write-Host "- Implement task deferral for high-load periods"
        }
        elseif ($methodName -match 'ChunkMap') {
            Write-Host "Strategy: Chunk Mapping Optimization"
            Write-Host "- Implement spatial indexing for fast lookups"
            Write-Host "- Add chunk proximity-based culling"
            Write-Host "- Implement chunk access pattern analysis"
            Write-Host "- Add chunk access rate limiting"
        }
    }
}
