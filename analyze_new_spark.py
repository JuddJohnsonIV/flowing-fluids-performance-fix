#!/usr/bin/env python3
"""
Spark Profile Analyzer for FlowingFluidsFixes Performance
Analyzes the new spark profile to identify current performance issues
"""

import struct
import json
import sys
import os
from collections import defaultdict, Counter
from datetime import datetime

def analyze_spark_profile(profile_path):
    """Analyze spark profile and extract performance metrics"""
    
    print("=" * 80)
    print("SPARK PROFILE ANALYSIS - FLOWING FLUIDS FIXES PERFORMANCE")
    print("=" * 80)
    print(f"Profile: {os.path.basename(profile_path)}")
    print(f"Analysis Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 80)
    
    try:
        with open(profile_path, 'rb') as f:
            data = f.read()
            
        print(f"Profile Size: {len(data):,} bytes")
        print()
        
        # Look for key performance indicators in the binary data
        text_data = data.decode('utf-8', errors='ignore')
        
        # Extract MSPT data patterns
        mspt_patterns = []
        lines = text_data.split('\n')
        
        for line in lines:
            if 'mspt' in line.lower() or 'tick' in line.lower():
                if any(char.isdigit() for char in line):
                    mspt_patterns.append(line.strip())
        
        print("MSPT PATTERNS FOUND:")
        print("-" * 40)
        if mspt_patterns:
            for i, pattern in enumerate(mspt_patterns[:10]):  # Show first 10
                print(f"{i+1:2d}. {pattern}")
            if len(mspt_patterns) > 10:
                print(f"... and {len(mspt_patterns) - 10} more patterns")
        else:
            print("No explicit MSPT patterns found in text data")
        print()
        
        # Look for Flowing Fluids specific data
        flowing_fluids_patterns = []
        for line in lines:
            if any(keyword in line.lower() for keyword in ['flowing', 'fluid', 'water', 'lava']):
                if any(char.isdigit() for char in line):
                    flowing_fluids_patterns.append(line.strip())
        
        print("FLOWING FLUIDS PATTERNS:")
        print("-" * 40)
        if flowing_fluids_patterns:
            for i, pattern in enumerate(flowing_fluids_patterns[:15]):  # Show first 15
                print(f"{i+1:2d}. {pattern}")
            if len(flowing_fluids_patterns) > 15:
                print(f"... and {len(flowing_fluids_patterns) - 15} more patterns")
        else:
            print("No Flowing Fluids patterns found in text data")
        print()
        
        # Look for method/procedure names that might indicate performance issues
        method_patterns = []
        for line in lines:
            if any(keyword in line.lower() for keyword in ['method', 'function', 'process', 'update', 'tick']):
                if any(char.isdigit() for char in line):
                    method_patterns.append(line.strip())
        
        print("METHOD/TICK PATTERNS:")
        print("-" * 40)
        if method_patterns:
            for i, pattern in enumerate(method_patterns[:10]):  # Show first 10
                print(f"{i+1:2d}. {pattern}")
            if len(method_patterns) > 10:
                print(f"... and {len(method_patterns) - 10} more patterns")
        else:
            print("No method patterns found in text data")
        print()
        
        # Look for performance bottleneck indicators
        bottleneck_patterns = []
        bottleneck_keywords = ['slow', 'lag', 'high', 'bottleneck', 'expensive', 'heavy', 'costly']
        
        for line in lines:
            if any(keyword in line.lower() for keyword in bottleneck_keywords):
                if any(char.isdigit() for char in line):
                    bottleneck_patterns.append(line.strip())
        
        print("PERFORMANCE BOTTLENECK INDICATORS:")
        print("-" * 40)
        if bottleneck_patterns:
            for i, pattern in enumerate(bottleneck_patterns[:10]):  # Show first 10
                print(f"{i+1:2d}. {pattern}")
            if len(bottleneck_patterns) > 10:
                print(f"... and {len(bottleneck_patterns) - 10} more patterns")
        else:
            print("No explicit bottleneck patterns found")
        print()
        
        # Extract numeric values that might represent performance metrics
        import re
        numbers = re.findall(r'\d+\.?\d*', text_data)
        
        # Look for large numbers that might represent operation counts
        large_numbers = [float(n) for n in numbers if float(n) > 1000]
        
        print("LARGE NUMERICAL VALUES (Potential Operation Counts):")
        print("-" * 40)
        if large_numbers:
            # Sort and show top 10 largest numbers
            sorted_numbers = sorted(large_numbers, reverse=True)
            for i, num in enumerate(sorted_numbers[:10]):
                print(f"{i+1:2d}. {num:,.0f}")
            if len(sorted_numbers) > 10:
                print(f"... and {len(sorted_numbers) - 10} more large numbers")
        else:
            print("No large numerical values found")
        print()
        
        # Look for our mod's specific patterns
        mod_patterns = []
        mod_keywords = ['flowingfluidsfixes', 'teleport', 'momentum', 'cache', 'throttle']
        
        for line in lines:
            if any(keyword in line.lower() for keyword in mod_keywords):
                mod_patterns.append(line.strip())
        
        print("FLOWINGFLUIDSFIXES MOD PATTERNS:")
        print("-" * 40)
        if mod_patterns:
            for i, pattern in enumerate(mod_patterns[:10]):  # Show first 10
                print(f"{i+1:2d}. {pattern}")
            if len(mod_patterns) > 10:
                print(f"... and {len(mod_patterns) - 10} more patterns")
        else:
            print("No FlowingFluidsFixes mod patterns found")
        print()
        
        # Summary analysis
        print("PERFORMANCE ANALYSIS SUMMARY:")
        print("=" * 50)
        
        # Estimate MSPT from patterns
        mspt_values = []
        for pattern in mspt_patterns:
            # Extract numbers from MSPT patterns
            nums = re.findall(r'\d+\.?\d*', pattern)
            for num in nums:
                try:
                    val = float(num)
                    if 0 < val < 1000:  # Reasonable MSPT range
                        mspt_values.append(val)
                except:
                    continue
        
        if mspt_values:
            avg_mspt = sum(mspt_values) / len(mspt_values)
            max_mspt = max(mspt_values)
            min_mspt = min(mspt_values)
            
            print(f"Estimated MSPT Range: {min_mspt:.1f}ms - {max_mspt:.1f}ms")
            print(f"Average MSPT: {avg_mspt:.1f}ms")
            
            if max_mspt > 100:
                print("⚠️  CRITICAL: MSPT still exceeding 100ms!")
            elif max_mspt > 50:
                print("⚠️  WARNING: MSPT exceeding 50ms")
            elif max_mspt > 20:
                print("✅ MODERATE: MSPT under 50ms but above 20ms")
            else:
                print("✅ GOOD: MSPT under 20ms")
        else:
            print("❓ Unable to determine MSPT from profile data")
        
        # Check for operation counts
        if large_numbers:
            max_ops = max(large_numbers)
            print(f"Highest Operation Count: {max_ops:,.0f}")
            
            if max_ops > 10000000:
                print("⚠️  CRITICAL: Still seeing 10M+ operations!")
            elif max_ops > 5000000:
                print("⚠️  WARNING: Still seeing 5M+ operations")
            elif max_ops > 1000000:
                print("✅ MODERATE: Under 5M operations")
            else:
                print("✅ GOOD: Under 1M operations")
        
        print()
        print("RECOMMENDATIONS:")
        print("-" * 20)
        
        if max_mspt > 50:
            print("🔥 MSPT is still too high - consider more aggressive throttling")
            print("   - Increase MSPT thresholds further")
            print("   - Reduce teleportation frequency")
            print("   - Add more aggressive event blocking")
        
        if max_ops > 5000000:
            print("🔥 Operation count is still too high")
            print("   - Check if teleportation is causing overhead")
            print("   - Consider reducing search radius")
            print("   - Add more cache cleanup")
        
        if not mod_patterns:
            print("❓ Mod patterns not found in profile")
            print("   - Check if mod is actually running")
            print("   - Verify event handlers are being called")
        
        print("=" * 80)
        
    except Exception as e:
        print(f"Error analyzing profile: {e}")
        print("=" * 80)

if __name__ == "__main__":
    profile_path = r"C:\Users\jwema\Downloads\TFtR7MUCMh.sparkprofile"
    analyze_spark_profile(profile_path)
