$bytes = [System.IO.File]::ReadAllBytes('c:\Users\jwema\CascadeProjects\FlowingFluidsStandalone\profile.sparkprofile')
$text = [System.Text.Encoding]::ASCII.GetString($bytes)

Write-Host "=== FLOWING FLUIDS METHOD ANALYSIS ==="
Write-Host ""

# Look for specific Flowing Fluids method calls
$flowingFluidsMethods = @(
    'FFFluidUtils',
    'canFitIntoFluid',
    'setFluidStateAtPosToN',
    'getStateForFluidByAmo',
    'placeConnected',
    'setOrRemoveWaterAmount',
    'flowing_fluids\$',
    'traben.flowing_fluids'
)

foreach ($method in $flowingFluidsMethods) {
    $methodMatches = [regex]::Matches($text, $method, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($methodMatches.Count -gt 0) {
        Write-Host "$method : $($methodMatches.Count) calls"
        
        # Show context for first few matches
        $count = 0
        foreach ($match in $methodMatches) {
            if ($count -ge 3) { break }
            $start = [Math]::Max(0, $match.Index - 30)
            $end = [Math]::Min($text.Length - 1, $match.Index + 30)
            $context = $text.Substring($start, $end - $start)
            Write-Host "  Context: $context"
            $count++
        }
        Write-Host ""
    }
}

# Look for our optimization methods
$ourMethods = @(
    'shouldProcessFluid',
    'isOnSlope',
    'adjustFlowingFluidsConfig',
    'FlowingFluidsFixes\$'
)

Write-Host "=== OUR OPTIMIZATION METHODS ==="
foreach ($method in $ourMethods) {
    $methodMatches = [regex]::Matches($text, $method, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    Write-Host "$method : $($methodMatches.Count) calls"
}

Write-Host ""
Write-Host "=== METHOD CALL ANALYSIS ==="

# Count different types of method calls
$flowingFluidsUtilCalls = [regex]::Matches($text, 'FFFluidUtils', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
$vanillaFluidCalls = [regex]::Matches($text, 'FlowingFluid', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
$ourMethodCalls = [regex]::Matches($text, 'FlowingFluidsFixes', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count

Write-Host "Flowing Fluids Utils calls: $flowingFluidsUtilCalls"
Write-Host "Vanilla FlowingFluid calls: $vanillaFluidCalls"  
Write-Host "Our optimization calls: $ourMethodCalls"

Write-Host ""
Write-Host "=== INTERCEPTION EFFECTIVENESS ==="

# Are we intercepting before or after Flowing Fluids?
$flowingFluidsActivity = [regex]::Matches($text, 'traben.flowing_fluids', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
$neighborNotifyActivity = [regex]::Matches($text, 'NeighborNotify', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count

Write-Host "Flowing Fluids internal activity: $flowingFluidsActivity"
Write-Host "NeighborNotify events: $neighborNotifyActivity"

if ($neighborNotifyActivity -gt 0 -and $flowingFluidsActivity -gt 0) {
    Write-Host "✅ We're intercepting events that trigger Flowing Fluids"
} elseif ($neighborNotifyActivity -gt 0) {
    Write-Host "⚠️  We're getting events but Flowing Fluids may not be processing them"
} else {
    Write-Host "❌ No event interception detected"
}

# Check if we're calling Flowing Fluids methods directly vs letting vanilla trigger them
$directFFCalls = [regex]::Matches($text, 'FFFluidUtils', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
$indirectFFCalls = [regex]::Matches($text, 'flowing_fluids\$setOrRemoveWaterAmount', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count

Write-Host ""
Write-Host "Direct Flowing Fluids util calls: $directFFCalls"
Write-Host "Indirect Flowing Fluids calls: $indirectFFCalls"

if ($directFFCalls -gt 0) {
    Write-Host "⚠️  We may be calling Flowing Fluids methods directly"
} else {
    Write-Host "✅ We're likely intercepting events, not calling methods directly"
}
