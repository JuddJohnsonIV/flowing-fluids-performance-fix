#!/usr/bin/env python3
"""
Spark Profile Decoder - Analyzes protobuf Spark profiles
"""

import json
import struct
import sys
from collections import defaultdict
from typing import Dict, List, Tuple

def decode_varint(data: bytes, offset: int) -> Tuple[int, int]:
    """Decode a varint from protobuf data"""
    result = 0
    shift = 0
    while True:
        if offset >= len(data):
            raise ValueError("Ran out of data while decoding varint")
        b = data[offset]
        offset += 1
        result |= (b & 0x7F) << shift
        if not (b & 0x80):
            break
        shift += 7
        if shift >= 64:
            raise ValueError("Varint too long")
    return result, offset

def decode_string(data: bytes, offset: int) -> Tuple[str, int]:
    """Decode a string from protobuf data"""
    length, offset = decode_varint(data, offset)
    if offset + length > len(data):
        raise ValueError("String length exceeds data size")
    string_bytes = data[offset:offset + length]
    try:
        return string_bytes.decode('utf-8'), offset + length
    except UnicodeDecodeError:
        return string_bytes.decode('utf-8', errors='ignore'), offset + length

def extract_strings(data: bytes) -> List[str]:
    """Extract all readable strings from protobuf data"""
    strings = []
    offset = 0
    while offset < len(data):
        try:
            # Try to decode a varint (field number and wire type)
            field_num, offset = decode_varint(data, offset)
            
            # Check wire type
            wire_type = field_num & 0x7
            field_num = field_num >> 3
            
            if wire_type == 2:  # Length-delimited (string)
                string_val, offset = decode_string(data, offset)
                if len(string_val) > 3 and all(c.isprintable() or c in '\n\r\t' for c in string_val):
                    strings.append(string_val)
            elif wire_type == 0:  # Varint
                _, offset = decode_varint(data, offset)
            elif wire_type == 1:  # 64-bit
                offset += 8
            elif wire_type == 5:  # 32-bit
                offset += 4
            else:
                # Skip unknown wire types
                break
                
        except:
            # If we can't decode, skip ahead and try again
            offset += 1
            
    return strings

def analyze_spark_profile(filename: str) -> Dict:
    """Analyze a Spark profile file"""
    try:
        with open(filename, 'rb') as f:
            data = f.read()
        
        print(f"Reading {filename} ({len(data)} bytes)")
        
        # Extract all strings
        strings = extract_strings(data)
        
        # Filter for relevant content
        minecraft_strings = [s for s in strings if 'minecraft' in s.lower() or 'net.minecraft' in s]
        flowing_strings = [s for s in strings if 'flowing' in s.lower() or 'fluid' in s.lower()]
        
        # Count method occurrences
        method_counts = defaultdict(int)
        for s in strings:
            if '.' in s and len(s) > 10:
                method_counts[s] += 1
        
        # Sort by frequency
        top_methods = sorted(method_counts.items(), key=lambda x: x[1], reverse=True)[:50]
        
        return {
            'total_strings': len(strings),
            'minecraft_strings': minecraft_strings,
            'flowing_strings': flowing_strings,
            'top_methods': top_methods,
            'all_strings': strings[:100]  # First 100 strings for context
        }
        
    except Exception as e:
        print(f"Error analyzing {filename}: {e}")
        return {}

def print_analysis(analysis: Dict):
    """Print the analysis results"""
    if not analysis:
        print("No analysis data available")
        return
    
    print("\n" + "="*60)
    print("SPARK PROFILE ANALYSIS")
    print("="*60)
    
    print(f"\nTotal strings extracted: {analysis['total_strings']}")
    
    print(f"\nMINECRAFT-RELATED STRINGS ({len(analysis['minecraft_strings'])}):")
    for s in analysis['minecraft_strings'][:20]:
        print(f"  {s}")
    
    print(f"\nFLOWING/FLUID-RELATED STRINGS ({len(analysis['flowing_strings'])}):")
    for s in analysis['flowing_strings'][:20]:
        print(f"  {s}")
    
    print(f"\nTOP METHODS BY FREQUENCY:")
    for method, count in analysis['top_methods'][:30]:
        print(f"  {count:4d}: {method}")
    
    print(f"\nFIRST 100 STRINGS (for context):")
    for i, s in enumerate(analysis['all_strings']):
        print(f"  {i+1:3d}: {s}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python spark_decoder.py <spark_profile_file>")
        sys.exit(1)
    
    filename = sys.argv[1]
    analysis = analyze_spark_profile(filename)
    print_analysis(analysis)
