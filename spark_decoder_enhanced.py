#!/usr/bin/env python3
"""
Enhanced Spark Profile Decoder - Better protobuf analysis
"""

import json
import struct
import sys
import re
from collections import defaultdict, Counter
from typing import Dict, List, Tuple

def extract_all_strings(data: bytes) -> List[str]:
    """Extract all possible strings from binary data"""
    strings = []
    
    # Method 1: UTF-8 string extraction
    try:
        decoded = data.decode('utf-8', errors='ignore')
        # Find strings between common delimiters
        strings.extend(re.findall(r'[a-zA-Z_][a-zA-Z0-9_\.]*[a-zA-Z0-9_]', decoded))
    except:
        pass
    
    # Method 2: Length-prefixed strings (common in protobuf)
    offset = 0
    while offset < len(data) - 4:
        try:
            # Look for potential length prefixes
            for i in range(offset, min(offset + 10, len(data) - 4)):
                length = struct.unpack('>I', data[i:i+4])[0]
                if 1 <= length <= 1000 and i + 4 + length <= len(data):
                    try:
                        string_data = data[i+4:i+4+length]
                        decoded_str = string_data.decode('utf-8', errors='ignore')
                        if len(decoded_str) > 3 and all(c.isprintable() or c in '\n\r\t' for c in decoded_str):
                            strings.append(decoded_str)
                        offset = i + 4 + length
                        break
                    except:
                        continue
            else:
                offset += 1
        except:
            offset += 1
    
    # Method 3: Varint-length strings
    offset = 0
    while offset < len(data):
        try:
            # Decode varint length
            length = 0
            shift = 0
            varint_offset = offset
            while varint_offset < len(data):
                b = data[varint_offset]
                varint_offset += 1
                length |= (b & 0x7F) << shift
                if not (b & 0x80):
                    break
                shift += 7
                if shift >= 64:
                    break
            
            if 1 <= length <= 1000 and varint_offset + length <= len(data):
                try:
                    string_data = data[varint_offset:varint_offset+length]
                    decoded_str = string_data.decode('utf-8', errors='ignore')
                    if len(decoded_str) > 3 and all(c.isprintable() or c in '\n\r\t' for c in decoded_str):
                        strings.append(decoded_str)
                    offset = varint_offset + length
                except:
                    offset = varint_offset + 1
            else:
                offset += 1
        except:
            offset += 1
    
    return strings

def analyze_methods(strings: List[str]) -> Dict[str, int]:
    """Analyze method calls from strings"""
    method_pattern = re.compile(r'[a-zA-Z_][a-zA-Z0-9_]*\.[a-zA-Z_][a-zA-Z0-9_]*\([^)]*\)')
    method_counts = Counter()
    
    for s in strings:
        # Look for method patterns
        methods = method_pattern.findall(s)
        for method in methods:
            method_counts[method] += 1
        
        # Also look for class.method patterns
        if '.' in s and '(' in s:
            method_counts[s] += 1
    
    return method_counts

def analyze_spark_profile(filename: str) -> Dict:
    """Analyze a Spark profile file"""
    try:
        with open(filename, 'rb') as f:
            data = f.read()
        
        print(f"Reading {filename} ({len(data)} bytes)")
        
        # Extract all strings using multiple methods
        strings = extract_all_strings(data)
        
        # Remove duplicates and filter
        unique_strings = list(set(strings))
        
        # Filter for relevant content
        minecraft_strings = [s for s in unique_strings if any(keyword in s.lower() for keyword in ['minecraft', 'net.minecraft', 'mojang'])]
        flowing_strings = [s for s in unique_strings if any(keyword in s.lower() for keyword in ['flowing', 'fluid', 'traben'])]
        forge_strings = [s for s in unique_strings if any(keyword in s.lower() for keyword in ['forge', 'fml', 'net.minecraftforge'])]
        
        # Analyze methods
        method_counts = analyze_methods(unique_strings)
        
        # Look for performance-related strings
        performance_strings = [s for s in unique_strings if any(keyword in s.lower() for keyword in ['tick', 'update', 'render', 'process', 'execute', 'mspt', 'tps'])]
        
        return {
            'total_strings': len(unique_strings),
            'minecraft_strings': minecraft_strings,
            'flowing_strings': flowing_strings,
            'forge_strings': forge_strings,
            'performance_strings': performance_strings,
            'top_methods': method_counts.most_common(50),
            'sample_strings': unique_strings[:100]
        }
        
    except Exception as e:
        print(f"Error analyzing {filename}: {e}")
        import traceback
        traceback.print_exc()
        return {}

def print_analysis(analysis: Dict):
    """Print the analysis results"""
    if not analysis:
        print("No analysis data available")
        return
    
    print("\n" + "="*80)
    print("SPARK PROFILE ANALYSIS")
    print("="*80)
    
    print(f"\nTotal unique strings extracted: {analysis['total_strings']}")
    
    print(f"\nMINECRAFT-RELATED STRINGS ({len(analysis['minecraft_strings'])}):")
    for s in analysis['minecraft_strings'][:15]:
        print(f"  {s}")
    
    print(f"\nFLOWING/FLUID-RELATED STRINGS ({len(analysis['flowing_strings'])}):")
    for s in analysis['flowing_strings'][:15]:
        print(f"  {s}")
    
    print(f"\nFORGE-RELATED STRINGS ({len(analysis['forge_strings'])}):")
    for s in analysis['forge_strings'][:15]:
        print(f"  {s}")
    
    print(f"\nPERFORMANCE-RELATED STRINGS ({len(analysis['performance_strings'])}):")
    for s in analysis['performance_strings'][:15]:
        print(f"  {s}")
    
    print(f"\nTOP METHODS BY FREQUENCY:")
    for method, count in analysis['top_methods'][:30]:
        print(f"  {count:4d}: {method}")
    
    print(f"\nSAMPLE STRINGS (first 100):")
    for i, s in enumerate(analysis['sample_strings']):
        print(f"  {i+1:3d}: {s}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python spark_decoder_enhanced.py <spark_profile_file>")
        sys.exit(1)
    
    filename = sys.argv[1]
    analysis = analyze_spark_profile(filename)
    print_analysis(analysis)
