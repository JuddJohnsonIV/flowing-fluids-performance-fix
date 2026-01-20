import struct
import sys

# Read the protobuf file
with open('C:/Users/jwema/CascadeProjects/FlowingFluidsStandalone/profile-2026-01-16_20.42.39.sparkprofile', 'rb') as f:
    data = f.read()

print(f'File size: {len(data)} bytes')
print(f'File header (first 20 bytes): {data[:20]}')

# Extract readable strings from binary data
def extract_strings(data, min_length=4):
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
print(f'\nFound {len(strings)} readable strings:')
for i, s in enumerate(strings[:50]):  # Show first 50 strings
    print(f'{i+1:2d}. {s}')

# Look for common spark profile indicators
spark_indicators = ['mspt', 'MSPT', 'tick', 'Tick', 'method', 'Method', 'time', 'Time']
print(f'\nSpark-related strings:')
for s in strings:
    if any(indicator in s for indicator in spark_indicators):
        print(f'- {s}')
