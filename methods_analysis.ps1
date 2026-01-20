$bytes = [System.IO.File]::ReadAllBytes('c:\Users\jwema\CascadeProjects\FlowingFluidsStandalone\methods_profile.sparkprofile')
$text = [System.Text.Encoding]::ASCII.GetString($bytes)

Write-Host "=== METHODS-FOCUSED PERFORMANCE ANALYSIS ==="
Write-Host ""

Write-Host "Analyzing high MSPT methods from Spark profile..."
Write-Host ""

# Look for method signatures and class patterns
$methodPatterns = @(
    'm_\d+_\d+',  # Obfuscated method signatures
    'm_\d+',       # Short obfuscated methods
    '\.m_\d+',     # Method calls
    '\$\d+',        # Lambda methods
    '_\d+$'         # Method suffixes
)

Write-Host "=== METHOD SIGNATURE ANALYSIS ==="
foreach ($pattern in $methodPatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "$pattern : $($matches.Count) occurrences"
        
        # Show unique method signatures
        $uniqueMethods = @{}
        foreach ($match in $matches) {
            $contextStart = [Math]::Max(0, $match.Index - 30)
            $contextEnd = [Math]::Min($text.Length - 1, $match.Index + 30)
            $context = $text.Substring($contextStart, $contextEnd - $contextStart)
            
            # Extract full method signature
            if ($context -match '([a-zA-Z_$][a-zA-Z0-9_$]*m_\d+_\d+[a-zA-Z0-9_$]*)') {
                $methodSig = $matches[1]
                if ($uniqueMethods.ContainsKey($methodSig)) {
                    $uniqueMethods[$methodSig]++
                } else {
                    $uniqueMethods[$methodSig] = 1
                }
            }
        }
        
        # Show top methods
        $topMethods = $uniqueMethods.GetEnumerator() | Sort-Object -Property Value -Descending | Select-Object -First 10
        Write-Host "  Top methods:"
        foreach ($method in $topMethods) {
            Write-Host "    $($method.Key): $($method.Value) calls"
        }
        Write-Host ""
    }
}

# Look for specific high-frequency method patterns
$highFreqPatterns = @(
    'LevelChunk',
    'ServerChunkCache', 
    'ChunkTaskPriorityQueue',
    'EntityTracker',
    'LevelTicks',
    'ChunkMap',
    'getChunk',
    'getEntity',
    'getBlockState',
    'setBlockState'
)

Write-Host "=== HIGH-FREQUENCY METHOD ANALYSIS ==="
foreach ($pattern in $highFreqPatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "$pattern : $($matches.Count) operations"
        
        # Extract context for first few matches
        $count = 0
        foreach ($match in $matches) {
            if ($count -ge 3) { break }
            $start = [Math]::Max(0, $match.Index - 40)
            $end = [Math]::Min($text.Length - 1, $match.Index + 40)
            $context = $text.Substring($start, $end - $start)
            Write-Host "  Context: $context"
            $count++
        }
        Write-Host ""
    }
}

# Look for our optimization methods
$ourMethods = @(
    'FlowingFluidsFixes',
    'isHighStressLocation',
    'incrementBlockUpdateCount',
    'incrementChunkGenerationCount',
    'isChunkNearPlayers',
    'shouldProcessFluid',
    'onNeighborNotify'
)

Write-Host "=== OUR OPTIMIZATION METHODS ==="
foreach ($method in $ourMethods) {
    $matches = [regex]::Matches($text, $method, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    Write-Host "$method : $($matches.Count) calls"
}

# Look for problematic patterns
$problemPatterns = @(
    'tick.*time',
    'update.*time',
    'process.*time',
    'load.*chunk',
    'save.*chunk',
    'entity.*tick',
    'block.*update',
    'chunk.*generate'
)

Write-Host "=== POTENTIAL PROBLEMATIC PATTERNS ==="
foreach ($pattern in $problemPatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "$pattern : $($matches.Count) occurrences"
    }
}

Write-Host ""
Write-Host "=== TOP BOTTLENECK METHODS ==="

# Count all method-like patterns
$allMethodCounts = @{}

# Extract all obfuscated methods
$allMethods = [regex]::Matches($text, '[a-zA-Z_$][a-zA-Z0-9_$]*m_\d+_\d+[a-zA-Z0-9_$]*', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
foreach ($method in $allMethods) {
    if ($allMethodCounts.ContainsKey($method.Value)) {
        $allMethodCounts[$method.Value]++
    } else {
        $allMethodCounts[$method.Value] = 1
    }
}

# Get top 20 methods
$topMethods = $allMethodCounts.GetEnumerator() | Sort-Object -Property Value -Descending | Select-Object -First 20

Write-Host "Top 20 method calls:"
foreach ($method in $topMethods) {
    $status = ""
    if ($method.Value -gt 1000) {
        $status = "🔴 CRITICAL"
    } elseif ($method.Value -gt 500) {
        $status = "🟠 HIGH"
    } elseif ($method.Value -gt 200) {
        $status = "🟡 MODERATE"
    } else {
        $status = "✅ LOW"
    }
    Write-Host "$($($method.Value)) $($method.Key) $status"
}

Write-Host ""
Write-Host "=== RECOMMENDATIONS ==="

# Analyze top methods for patterns
$topCritical = $topMethods | Where-Object { $_.Value -gt 500 }
if ($topCritical.Count -gt 0) {
    Write-Host "🚨 CRITICAL METHODS NEEDING OPTIMIZATION:"
    foreach ($method in $topCritical) {
        Write-Host "  - $($method.Key) ($($($method.Value)) calls)"
        
        # Suggest optimization based on method name
        if ($method.Key -match 'LevelChunk') {
            Write-Host "    → Chunk-related: Consider chunk culling or batching"
        } elseif ($method.Key -match 'Entity') {
            Write-Host "    → Entity-related: Consider entity distance limits"
        } elseif ($method.Key -match 'BlockState') {
            Write-Host "    → Block-related: Consider block update throttling"
        } elseif ($method.Key -match 'Tick') {
            Write-Host "    → Tick-related: Consider tick-based throttling"
        } elseif ($method.Key -match 'ChunkMap') {
            Write-Host "    → Chunk mapping: Consider spatial partitioning"
        }
    }
} else {
    Write-Host "✅ No critical bottlenecks detected"
}

Write-Host ""
Write-Host "=== OUR OPTIMIZATION EFFECTIVENESS ==="
$ourTotalCalls = 0
foreach ($method in $ourMethods) {
    $matches = [regex]::Matches($text, $method, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    $ourTotalCalls += $matches.Count
}

Write-Host "Our optimization methods called: $ourTotalCalls times"
Write-Host "Total method calls in profile: $($allMethods.Count)"

if ($ourTotalCalls -gt 0) {
    Write-Host "✅ Our optimizations are active and being called"
} else {
    Write-Host "❌ Our optimizations may not be intercepting properly"
}
