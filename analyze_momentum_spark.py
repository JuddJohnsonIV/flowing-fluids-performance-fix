#!/usr/bin/env python3
"""
Spark Profile Analyzer for Momentum-Based Jumping System
Analyzes the new spark profile to see how momentum-based jumping affects performance
"""

import struct
import json
import sys
import os
import re
from collections import defaultdict, Counter
from datetime import datetime

def analyze_momentum_spark_profile(profile_path):
    """Analyze spark profile with focus on momentum-based jumping performance"""
    
    print("=" * 100)
    print("SPARK PROFILE ANALYSIS - MOMENTUM-BASED JUMPING SYSTEM")
    print("=" * 100)
    print(f"Profile: {os.path.basename(profile_path)}")
    print(f"Analysis Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 100)
    
    try:
        with open(profile_path, 'rb') as f:
            data = f.read()
            
        print(f"Profile Size: {len(data):,} bytes ({len(data)/1024/1024:.2f} MB)")
        print()
        
        # Extract all text data
        text_data = data.decode('utf-8', errors='ignore')
        lines = text_data.split('\n')
        
        # 1. METHOD CALL ANALYSIS - Focus on jumping methods
        print("1. MOMENTUM-BASED JUMPING METHOD ANALYSIS")
        print("=" * 60)
        
        # Look for our new jumping methods
        jumping_methods = []
        jumping_patterns = [
            'applyMomentumBasedJumping',
            'calculateJumpDistanceFromMomentum',
            'applyForwardJump',
            'calculateJumpTargets',
            'determineFlowDirection'
        ]
        
        for line in lines:
            for pattern in jumping_patterns:
                if pattern in line:
                    jumping_methods.append(line.strip())
        
        jumping_counter = Counter(jumping_methods)
        
        print("JUMPING METHOD CALLS:")
        for method, count in jumping_counter.most_common():
            print(f"  {count:6d}: {method}")
        print()
        
        # 2. FLOWINGFLUIDSFIXES MOD ANALYSIS
        print("2. FLOWINGFLUIDSFIXES MOD ANALYSIS")
        print("=" * 60)
        
        mod_methods = []
        for line in lines:
            if 'flowingfluidsfixes' in line.lower():
                # Extract method names
                method_matches = re.findall(r'flowingfluidsfixes\.[A-Za-z_][A-Za-z0-9_]*', line)
                mod_methods.extend(method_matches)
        
        mod_counter = Counter(mod_methods)
        
        print("FLOWINGFLUIDSFIXES METHOD CALLS:")
        for method, count in mod_counter.most_common(20):
            print(f"  {count:6d}: {method}")
        print()
        
        # 3. JUMPING PERFORMANCE ANALYSIS
        print("3. JUMPING PERFORMANCE ANALYSIS")
        print("=" * 60)
        
        # Look for jumping-related patterns
        jumping_patterns = [
            'jump', 'momentum', 'distance', 'forward', 'chain',
            'applyMomentumBasedJumping', 'applyForwardJump'
        ]
        
        jumping_calls = defaultdict(int)
        for line in lines:
            for pattern in jumping_patterns:
                if pattern.lower() in line.lower():
                    jumping_calls[pattern] += 1
        
        print("JUMPING-RELATED OPERATIONS:")
        for operation, count in sorted(jumping_calls.items(), key=lambda x: x[1], reverse=True):
            print(f"  {count:6d}: {operation}")
        print()
        
        # 4. NUMERICAL ANALYSIS - Focus on momentum values
        print("4. MOMENTUM VALUE ANALYSIS")
        print("=" * 60)
        
        # Extract numbers that might represent momentum values
        all_numbers = re.findall(r'\d+\.?\d*', text_data)
        numbers = [float(n) for n in all_numbers]
        
        # Look for momentum-related numbers
        momentum_numbers = []
        for line in lines:
            if any(keyword in line.lower() for keyword in ['momentum', 'jump', 'distance']):
                nums = re.findall(r'\d+', line)
                for num in nums:
                    try:
                        val = int(num)
                        if 1 <= val <= 10000:  # Reasonable momentum range
                            momentum_numbers.append(val)
                    except:
                        continue
        
        if momentum_numbers:
            print("MOMENTUM VALUES FOUND:")
            print(f"  Count: {len(momentum_numbers)}")
            print(f"  Min: {min(momentum_numbers)}")
            print(f"  Max: {max(momentum_numbers)}")
            print(f"  Average: {sum(momentum_numbers)/len(momentum_numbers):.1f}")
            
            # Momentum distribution
            momentum_ranges = {
                '1-10': 0, '11-25': 0, '26-50': 0, '51-100': 0,
                '101-200': 0, '201-500': 0, '501-1000': 0, '1000+': 0
            }
            
            for num in momentum_numbers:
                if num <= 10:
                    momentum_ranges['1-10'] += 1
                elif num <= 25:
                    momentum_ranges['11-25'] += 1
                elif num <= 50:
                    momentum_ranges['26-50'] += 1
                elif num <= 100:
                    momentum_ranges['51-100'] += 1
                elif num <= 200:
                    momentum_ranges['101-200'] += 1
                elif num <= 500:
                    momentum_ranges['201-500'] += 1
                elif num <= 1000:
                    momentum_ranges['501-1000'] += 1
                else:
                    momentum_ranges['1000+'] += 1
            
            print("MOMENTUM DISTRIBUTION:")
            for range_name, count in momentum_ranges.items():
                print(f"  {range_name:8}: {count:6d} ({count/len(momentum_numbers)*100:.1f}%)")
        print()
        
        # 5. PERFORMANCE COMPARISON
        print("5. PERFORMANCE COMPARISON")
        print("=" * 60)
        
        # Extract all numerical values for comparison
        large_numbers = [float(n) for n in all_numbers if float(n) > 1000]
        
        if large_numbers:
            print("LARGEST NUMERICAL VALUES:")
            sorted_large = sorted(large_numbers, reverse=True)
            for i, num in enumerate(sorted_large[:10]):
                print(f"  {i+1:2d}: {num:,.0f}")
        print()
        
        # 6. JUMPING EFFECTIVENESS ANALYSIS
        print("6. JUMPING EFFECTIVENESS ANALYSIS")
        print("=" * 60)
        
        # Look for evidence of jumping effectiveness
        effectiveness_indicators = [
            'jump', 'distance', 'forward', 'target', 'momentum',
            'chain', 'reaction', 'flow', 'direction'
        ]
        
        effectiveness = defaultdict(int)
        for line in lines:
            for indicator in effectiveness_indicators:
                if indicator.lower() in line.lower():
                    effectiveness[indicator] += 1
        
        print("JUMPING EFFECTIVENESS INDICATORS:")
        for indicator, count in sorted(effectiveness.items(), key=lambda x: x[1], reverse=True):
            print(f"  {count:6d}: {indicator}")
        print()
        
        # 7. ORIGINAL MOD COMPARISON
        print("7. ORIGINAL FLOWING FLUIDS MOD COMPARISON")
        print("=" * 60)
        
        original_mod_calls = 0
        our_mod_calls = 0
        
        for line in lines:
            if 'traben.flowing_fluids' in line.lower():
                original_mod_calls += 1
            elif 'flowingfluidsfixes' in line.lower():
                our_mod_calls += 1
        
        print("MOD COMPARISON:")
        print(f"  Original Flowing Fluids: {original_mod_calls:6d} calls")
        print(f"  FlowingFluidsFixes:    {our_mod_calls:6d} calls")
        if original_mod_calls > 0:
            ratio = our_mod_calls / original_mod_calls
            print(f"  Ratio: {ratio:.2f} (our mod / original mod)")
        print()
        
        # 8. PERFORMANCE SUMMARY
        print("8. PERFORMANCE SUMMARY")
        print("=" * 60)
        
        # Calculate performance metrics
        total_method_calls = sum(mod_counter.values())
        jumping_method_calls = sum(jumping_counter.values())
        
        print(f"Total Method Calls: {total_method_calls:,}")
        print(f"Jumping Method Calls: {jumping_method_calls:,}")
        print(f"Jumping Method Ratio: {(jumping_method_calls/total_method_calls)*100:.1f}%")
        
        if large_numbers:
            max_ops = max(large_numbers)
            print(f"Highest Operation Count: {max_ops:,.0f}")
            
            if max_ops > 1000000000:
                print("⚠️  CRITICAL: Still seeing 1B+ operations!")
            elif max_ops > 500000000:
                print("⚠️  SEVERE: Still seeing 500M+ operations!")
            elif max_ops > 100000000:
                print("⚠️  HIGH: Still seeing 100M+ operations!")
            else:
                print("✅ GOOD: Under 100M operations")
        
        # Jumping effectiveness assessment
        if jumping_method_calls > 1000:
            print("✅ EXCELLENT: High jumping activity detected")
        elif jumping_method_calls > 500:
            print("✅ GOOD: Moderate jumping activity detected")
        elif jumping_method_calls > 100:
            print("✅ OKAY: Some jumping activity detected")
        else:
            print("❓ LOW: Minimal jumping activity detected")
        
        print()
        print("RECOMMENDATIONS:")
        print("-" * 40)
        
        if max_ops > 100000000:
            print("🔥 Operation count still high - consider reducing jump frequency")
        
        if jumping_method_calls < 100:
            print("⚡ Jumping activity low - check if momentum thresholds are too high")
        
        if our_mod_calls > original_mod_calls * 2:
            print("🔥 Our mod is very active - ensure performance is acceptable")
        
        print("=" * 100)
        print("ANALYSIS COMPLETE")
        print("=" * 100)
        
    except Exception as e:
        print(f"Error analyzing profile: {e}")
        import traceback
        traceback.print_exc()
        print("=" * 100)

if __name__ == "__main__":
    profile_path = r"C:\Users\jwema\Downloads\IUjTomnna8.sparkprofile"
    analyze_momentum_spark_profile(profile_path)
