$bytes = [System.IO.File]::ReadAllBytes('c:\Users\jwema\CascadeProjects\FlowingFluidsStandalone\profile.sparkprofile')
$text = [System.Text.Encoding]::ASCII.GetString($bytes)

# Look for flowing fluids related terms
$patterns = @('flowing_fluids', 'FlowingFluid', 'flowingfluidsfixes', 'fluid', 'liquid', 'water', 'block')

foreach ($pattern in $patterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "Found '$pattern': $($matches.Count) times"
        $count = 0
        foreach ($match in $matches) {
            if ($count -ge 10) { break }
            $start = [Math]::Max(0, $match.Index - 50)
            $end = [Math]::Min($text.Length - 1, $match.Index + 50)
            $context = $text.Substring($start, $end - $start)
            Write-Host "  Context: $context"
            $count++
        }
    }
}

# Also look for general method patterns that might indicate our mixin is working
$methodPatterns = @('onNeighborNotify', 'NeighborNotifyEvent', 'shouldProcessFluid', 'FlowingFluidsFixes')

foreach ($pattern in $methodPatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "Found method '$pattern': $($matches.Count) times"
    }
}
