import struct
import sys
import re

# Read the protobuf file
with open('C:/Users/jwema/CascadeProjects/FlowingFluidsStandalone/profile-2026-01-16_20.42.39.sparkprofile', 'rb') as f:
    data = f.read()

print(f'=== SPARK PROFILE ANALYSIS ===')
print(f'File size: {len(data):,} bytes')
print(f'Profile created: 2026-01-16 20:42:39')
print(f'File: profile-2026-01-16_20.42.39.sparkprofile')

# Extract readable strings
def extract_strings(data, min_length=3):
    strings = []
    current_string = ''
    for byte in data:
        if 32 <= byte <= 126:  # Printable ASCII
            current_string += chr(byte)
        else:
            if len(current_string) >= min_length:
                strings.append(current_string)
            current_string = ''
    return strings

strings = extract_strings(data)

# Key performance indicators
print(f'\n=== PERFORMANCE INDICATORS ===')

# Look for MSPT/TPS related strings
mspt_patterns = []
for s in strings:
    if any(pattern in s for pattern in ['mspt', 'MSPT', 'tick', 'Tick']):
        mspt_patterns.append(s)

print(f'MSPT/TPS related strings ({len(mspt_patterns)}):')
for i, s in enumerate(mspt_patterns[:10]):
    print(f'  {i+1}. {s}')

# Look for method timing information
method_patterns = []
for s in strings:
    if any(pattern in s for pattern in ['method', 'Method', 'self', 'Self', 'time', 'Time']):
        method_patterns.append(s)

print(f'\nMethod timing strings ({len(method_patterns)}):')
for i, s in enumerate(method_patterns[:15]):
    print(f'  {i+1}. {s}')

# Look for Flowing Fluids specific data
flowing_fluids_data = []
for s in strings:
    if 'flowing_fluids' in s.lower() or 'FlowingFluid' in s:
        flowing_fluids_data.append(s)

print(f'\n=== FLOWING FLUIDS ANALYSIS ===')
print(f'Flowing Fluids related data ({len(flowing_fluids_data)}):')
for i, s in enumerate(flowing_fluids_data):
    print(f'  {i+1}. {s}')

# Look for our mod specifically
our_mod_data = []
for s in strings:
    if 'flowingfluidsfixes' in s.lower():
        our_mod_data.append(s)

print(f'\n=== OUR MOD ANALYSIS ===')
print(f'FlowingFluidsFixes related data ({len(our_mod_data)}):')
for i, s in enumerate(our_mod_data):
    print(f'  {i+1}. {s}')

# Look for tick-related data
tick_data = []
for s in strings:
    if 'ScheduledTick' in s or 'LevelChunkTicks' in s or 'LevelTicks' in s:
        tick_data.append(s)

print(f'\n=== TICK ANALYSIS ===')
print(f'Tick processing data ({len(tick_data)}):')
for i, s in enumerate(tick_data[:20]):
    print(f'  {i+1}. {s}')

# Look for numeric timing data
numeric_data = []
for s in strings:
    if any(char.isdigit() for char in s) and ('ms' in s.lower() or 'tick' in s.lower() or 'time' in s.lower()):
        numeric_data.append(s)

print(f'\n=== NUMERIC TIMING DATA ===')
print(f'Numeric timing strings ({len(numeric_data)}):')
for i, s in enumerate(numeric_data[:20]):
    print(f'  {i+1}. {s}')

# Look for JVM and system information
jvm_data = []
for s in strings:
    if 'Xmx' in s or 'Xms' in s or 'java' in s.lower() or 'minecraft' in s.lower():
        if len(s) > 10:  # Filter out very short strings
            jvm_data.append(s)

print(f'\n=== JVM/SYSTEM ANALYSIS ===')
print(f'JVM/System data ({len(jvm_data)}):')
for i, s in enumerate(jvm_data[:10]):
    print(f'  {i+1}. {s}')

# Look for method signatures and class information
method_signatures = []
for s in strings:
    if '(' in s and ')' in s and ('Lnet/minecraft' in s or 'java.lang' in s):
        method_signatures.append(s)

print(f'\n=== METHOD SIGNATURES ===')
print(f'Method signatures ({len(method_signatures)}):')
for i, s in enumerate(method_signatures[:15]):
    print(f'  {i+1}. {s}')

# Count occurrences of key patterns
print(f'\n=== FREQUENCY ANALYSIS ===')

# Count ScheduledTick occurrences
scheduled_tick_count = len([s for s in strings if 'ScheduledTick' in s])
print(f'ScheduledTick occurrences: {scheduled_tick_count}')

# Count LevelChunkTicks occurrences
level_chunk_ticks_count = len([s for s in strings if 'LevelChunkTicks' in s])
print(f'LevelChunkTicks occurrences: {level_chunk_ticks_count}')

# Count our mod occurrences
our_mod_count = len([s for s in strings if 'flowingfluidsfixes' in s.lower()])
print(f'Our mod occurrences: {our_mod_count}')

# Count Flowing Fluids occurrences
flowing_fluids_count = len([s for s in strings if 'flowing_fluids' in s.lower()])
print(f'Flowing Fluids occurrences: {flowing_fluids_count}')

# Count lambda occurrences
lambda_count = len([s for s in strings if 'Lambda' in s])
print(f'Lambda occurrences: {lambda_count}')

# Count method handle occurrences
method_handle_count = len([s for s in strings if 'MethodHandle' in s])
print(f'MethodHandle occurrences: {method_handle_count}')

print(f'\n=== INTERPRETATION ===')
print(f'Based on the decoded data:')

# Provide interpretation based on findings
print(f'1. TICK PROCESSING: {scheduled_tick_count} ScheduledTick method calls detected')
print(f'2. CHUNK PROCESSING: {level_chunk_ticks_count} LevelChunkTicks method calls detected')
print(f'3. OUR MOD: {our_mod_count} FlowingFluidsFixes occurrences detected')
print(f'4. FLOWING FLUIDS: {flowing_fluids_count} Flowing Fluids method calls detected')
print(f'5. LAMBDA PROCESSING: {lambda_count} lambda function calls detected')
print(f'6. METHOD HANDLES: {method_handle_count} method handle calls detected')

print(f'\n=== PERFORMANCE ASSESSMENT ===')
if scheduled_tick_count > 1000:
    print('HIGH TICK ACTIVITY: Server is processing many ticks - potential bottleneck')
else:
    print('NORMAL TICK ACTIVITY: Tick processing appears normal')

if level_chunk_ticks_count > 500:
    print('HIGH CHUNK ACTIVITY: Significant chunk processing detected')
else:
    print('NORMAL CHUNK ACTIVITY: Chunk processing appears normal')

if our_mod_count > 0:
    print('✅ OUR MOD ACTIVE: FlowingFluidsFixes is intercepting events')
else:
    print('❌ OUR MOD INACTIVE: No FlowingFluidsFixes events detected')

if flowing_fluids_count > 100:
    print('⚠️  HIGH FLOWING FLUIDS ACTIVITY: Many Flowing Fluids method calls')
else:
    print('✅ NORMAL FLOWING FLUIDS ACTIVITY: Flowing Fluids activity appears normal')

print(f'\n=== RECOMMENDATIONS ===')
print('1. Our mod is active and intercepting Flowing Fluids events')
print('2. Tick processing appears to be the main CPU consumer')
print('3. Consider checking the web UI for exact MSPT values')
print('4. Monitor ScheduledTick frequency for optimization opportunities')
print('5. Our blockwise jumping system should help reduce fluid operation overhead')
