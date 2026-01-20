import struct
import sys

# Read the protobuf file
with open('C:/Users/jwema/CascadeProjects/FlowingFluidsStandalone/profile-2026-01-16_20.42.39.sparkprofile', 'rb') as f:
    data = f.read()

print(f'File size: {len(data)} bytes')

# Look for specific patterns that indicate performance metrics
patterns_to_find = [
    'mspt', 'MSPT', 'tick', 'Tick', 'method', 'Method', 
    'time', 'Time', 'duration', 'Duration', 'average', 'Average',
    'worst', 'Worst', 'tps', 'TPS', 'self', 'Self',
    'flowing_fluids', 'FlowingFluids', 'fluid', 'Fluid',
    'block', 'Block', 'entity', 'Entity', 'chunk', 'Chunk'
]

# Extract readable strings and filter for performance metrics
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
print(f'\nFound {len(strings)} readable strings')

# Filter for performance-related strings
performance_strings = []
for s in strings:
    if any(pattern.lower() in s.lower() for pattern in patterns_to_find):
        performance_strings.append(s)

print(f'\nPerformance-related strings ({len(performance_strings)}):')
for i, s in enumerate(performance_strings[:100]):  # Show first 100
    print(f'{i+1:3d}. {s}')

# Look for specific method patterns that indicate Flowing Fluids
flowing_fluids_strings = []
for s in strings:
    if 'flowing_fluids' in s.lower() or 'FlowingFluid' in s:
        flowing_fluids_strings.append(s)

print(f'\nFlowing Fluids related strings ({len(flowing_fluids_strings)}):')
for i, s in enumerate(flowing_fluids_strings):
    print(f'{i+1:2d}. {s}')

# Look for numeric patterns that might indicate timing data
numeric_strings = []
for s in strings:
    if any(char.isdigit() for char in s) and ('ms' in s.lower() or 'tick' in s.lower()):
        numeric_strings.append(s)

print(f'\nTiming-related strings ({len(numeric_strings)}):')
for i, s in enumerate(numeric_strings[:50]):
    print(f'{i+1:2d}. {s}')
