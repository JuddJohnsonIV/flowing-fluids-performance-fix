$bytes = [System.IO.File]::ReadAllBytes('c:\Users\jwema\CascadeProjects\FlowingFluidsStandalone\profile.sparkprofile')

Write-Host "=== BINARY TIMING DATA ANALYSIS ==="
Write-Host ""

# Look for patterns that might indicate timing data in binary
# Spark profiler often stores timing data as varints or fixed-size integers

# Method 1: Look for sequences that might be timing data (high values that could be microseconds/nanoseconds)
Write-Host "=== SEARCHING FOR HIGH TIMING VALUES ==="

$timingCandidates = @()
for ($i = 0; $i -lt $bytes.Length - 4; $i++) {
    # Try to read 4-byte integer (little-endian)
    $value = [BitConverter]::ToUInt32($bytes, $i)
    
    # Look for values that could be timing data (microseconds: 1000-10000000, nanoseconds: 1000000-1000000000)
    if ($value -gt 1000 -and $value -lt 1000000000) {
        $timingCandidates += $value
    }
}

# Get the highest timing values
$timingCandidates = $timingCandidates | Sort-Object -Descending | Select-Object -First 20

Write-Host "Top 20 potential timing values found:"
foreach ($value in $timingCandidates) {
    # Convert to different time units for interpretation
    $ms = $value / 1000.0
    $seconds = $value / 1000000.0
    
    Write-Host "Raw: $value -> MS: $ms -> Seconds: $seconds"
}

Write-Host ""
Write-Host "=== METHOD 2: LOOK FOR VARINT PATTERNS ==="

# Spark uses protobuf varints for timing data
$varintValues = @()
for ($i = 0; $i -lt $bytes.Length - 1; $i++) {
    $byte = $bytes[$i]
    
    # Varint: MSB indicates continuation
    if (($byte -band 0x80) -eq 0) {
        # Single byte varint
        $varintValues += $byte
    } else {
        # Multi-byte varint (simplified - just take the lower 7 bits)
        $value = $byte -band 0x7F
        $varintValues += $value
    }
}

# Filter for timing-relevant values
$timingVarints = $varintValues | Where-Object { $_ -gt 10 -and $_ -lt 1000 } | Sort-Object -Descending | Select-Object -First 10

Write-Host "Potential timing varints:"
foreach ($value in $timingVarints) {
    Write-Host "Varint: $value"
}

Write-Host ""
Write-Host "=== METHOD 3: LOOK FOR SPECIFIC BYTES PATTERNS ==="

# Look for patterns that might represent high MSPT values
# MSPT typically ranges from 1-50000, so look for byte sequences that could represent these

$highMSPT = @()
for ($i = 0; $i -lt $bytes.Length - 2; $i++) {
    # Look for 16-bit values
    $value16 = [BitConverter]::ToUInt16($bytes, $i)
    if ($value16 -gt 50 -and $value16 -lt 50000) {
        $highMSPT += $value16
    }
}

$highMSPT = $highMSPT | Sort-Object -Descending | Select-Object -First 10

Write-Host "Potential MSPT values (16-bit):"
foreach ($value in $highMSPT) {
    Write-Host "MSPT candidate: $value"
}

Write-Host ""
Write-Host "=== ESTIMATION BASED ON PROFILE SIZE ==="

$profileSize = $bytes.Length
Write-Host "Profile size: $profileSize bytes"

# Based on typical Spark profile characteristics
if ($profileSize -gt 200000) {
    Write-Host "Large profile suggests significant profiling activity"
    Write-Host "This could indicate high MSPT during profiling period"
}

Write-Host ""
Write-Host "=== CONCLUSION ==="
Write-Host "Spark profiles store timing data in binary protobuf format"
Write-Host "The exact MSPT values require proper protobuf decoding"
Write-Host "From the binary analysis, the highest potential timing values are shown above"
Write-Host ""
Write-Host "For accurate MSPT data, the profile should be viewed in:"
Write-Host "- Spark profiler web interface"
Write-Host "- Spark GUI application" 
Write-Host "- Custom protobuf decoder"
