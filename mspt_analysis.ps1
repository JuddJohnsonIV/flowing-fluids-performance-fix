$bytes = [System.IO.File]::ReadAllBytes('c:\Users\jwema\CascadeProjects\FlowingFluidsStandalone\profile.sparkprofile')
$text = [System.Text.Encoding]::ASCII.GetString($bytes)

Write-Host "=== MSPT ANALYSIS FROM SPARK PROFILE ==="
Write-Host ""

# Look for MSPT-related patterns
$msptPatterns = @(
    'mspt',
    'MSPT', 
    'tick.*time',
    'millisecond',
    'ms.*tick',
    'tick.*ms'
)

foreach ($pattern in $msptPatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "Found '$pattern': $($matches.Count) occurrences"
        
        # Show context for matches
        $count = 0
        foreach ($match in $matches) {
            if ($count -ge 5) { break }
            $start = [Math]::Max(0, $match.Index - 40)
            $end = [Math]::Min($text.Length - 1, $match.Index + 40)
            $context = $text.Substring($start, $end - $start)
            Write-Host "  Context: $context"
            $count++
        }
        Write-Host ""
    }
}

# Look for numeric values that could be MSPT (typically 1-1000 range)
$numberPattern = '\b([1-9][0-9]{0,3}|10000)\b'
$numbers = [regex]::Matches($text, $numberPattern)

Write-Host "=== POTENTIAL MSPT VALUES ==="
$foundMSPT = $false
foreach ($match in $numbers) {
    $value = [int]$match.Value
    if ($value -ge 5 -and $value -le 10000) {
        # Check if this number appears near timing-related words
        $start = [Math]::Max(0, $match.Index - 50)
        $end = [Math]::Min($text.Length - 1, $match.Index + 50)
        $context = $text.Substring($start, $end - $start)
        
        if ($context -match 'tick|time|ms|second|delay|lag') {
            Write-Host "Potential MSPT: $value"
            Write-Host "  Context: $context"
            $foundMSPT = $true
        }
    }
}

if (-not $foundMSPT) {
    Write-Host "No clear MSPT values found in profile"
}

Write-Host ""
Write-Host "=== SEARCHING FOR PERFORMANCE METRICS ==="

# Look for other performance indicators
$performancePatterns = @(
    'tps',
    'TPS',
    'performance',
    'lag',
    'slow',
    'delay',
    'time.*per'
)

foreach ($pattern in $performancePatterns) {
    $matches = [regex]::Matches($text, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($matches.Count -gt 0) {
        Write-Host "Found '$pattern': $($matches.Count) occurrences"
        
        $count = 0
        foreach ($match in $matches) {
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

Write-Host "=== ANALYSIS COMPLETE ==="
Write-Host "Note: Spark profiles are binary protobuf files."
Write-Host "MSPT data may be stored in binary format not visible as text."
Write-Host "The visible text may not contain the actual timing measurements."
