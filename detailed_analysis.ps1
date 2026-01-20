$bytes = [System.IO.File]::ReadAllBytes('c:\Users\jwema\CascadeProjects\FlowingFluidsStandalone\profile.sparkprofile')
$text = [System.Text.Encoding]::ASCII.GetString($bytes)

Write-Host "=== FLOWING FLUIDS INTERCEPTION ANALYSIS ==="
Write-Host ""

# Check for our mod's presence
$flowingfluidsfixesMatches = [regex]::Matches($text, 'flowingfluidsfixes', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
Write-Host "FlowingFluidsFixes mod references: $($flowingfluidsfixesMatches.Count)"

# Check for our event handler methods
$eventMethods = @('onNeighborNotify', 'NeighborNotifyEvent', 'shouldProcessFluid')
foreach ($method in $eventMethods) {
    $matches = [regex]::Matches($text, $method, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    Write-Host "$method method calls: $($matches.Count)"
}

# Check for Flowing Fluids mod activity
$flowingFluidsMatches = [regex]::Matches($text, 'flowing_fluids', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
Write-Host "Flowing Fluids mod activity: $($flowingFluidsMatches.Count)"

# Check for vanilla fluid operations
$vanillaFluidOps = @('FlowingFluid', 'FluidState', 'LiquidBlock')
foreach ($op in $vanillaFluidOps) {
    $matches = [regex]::Matches($text, $op, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    Write-Host "Vanilla $op operations: $($matches.Count)"
}

# Look for our specific class references
$ourClasses = @('FlowingFluidsFixes', 'FlowingFluidsFixesMinimal')
foreach ($class in $ourClasses) {
    $matches = [regex]::Matches($text, $class, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    Write-Host "$class references: $($matches.Count)"
}

Write-Host ""
Write-Host "=== INTERCEPTION STATUS ==="

# Key indicators that our mixin is working
$indicators = @{
    "onNeighborNotify calls" = [regex]::Matches($text, 'onNeighborNotify', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
    "NeighborNotifyEvent processing" = [regex]::Matches($text, 'NeighborNotifyEvent', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
    "FlowingFluidsFixes activity" = [regex]::Matches($text, 'FlowingFluidsFixes', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
    "Flowing Fluids mod activity" = [regex]::Matches($text, 'flowing_fluids', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
}

$interceptionWorking = $false
foreach ($indicator in $indicators.GetEnumerator()) {
    Write-Host "$($indicator.Key): $($indicator.Value)"
    if ($indicator.Value -gt 0) {
        $interceptionWorking = $true
    }
}

Write-Host ""
if ($interceptionWorking) {
    Write-Host "✅ INTERCEPTION ACTIVE: Our mod is detected in the profile"
} else {
    Write-Host "❌ INTERCEPTION NOT DETECTED: Our mod may not be intercepting"
}

Write-Host ""
Write-Host "=== PERFORMANCE ANALYSIS ==="
$totalFluidOps = [regex]::Matches($text, 'fluid', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
$vanillaFluidOps = [regex]::Matches($text, 'FlowingFluid', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
$flowingFluidsOps = [regex]::Matches($text, 'flowing_fluids', [Text.RegularExpressions.RegexOptions]::IgnoreCase).Count

Write-Host "Total fluid-related operations: $totalFluidOps"
Write-Host "Vanilla FlowingFluid operations: $vanillaFluidOps"
Write-Host "Flowing Fluids mod operations: $flowingFluidsOps"

if ($vanillaFluidOps -gt 0 -and $flowingFluidsOps -gt 0) {
    $ratio = [math]::Round($flowingFluidsOps / $vanillaFluidOps * 100, 2)
    Write-Host "Flowing Fluids to Vanilla ratio: $ratio%"
    
    if ($ratio -lt 100) {
        Write-Host "✅ GOOD: Flowing Fluids operations are below vanilla levels"
    } else {
        Write-Host "⚠️  WARNING: Flowing Fluids operations are high"
    }
}
