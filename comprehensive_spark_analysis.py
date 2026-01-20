#!/usr/bin/env python3
"""
Comprehensive Spark Profile Analysis
Examines the entire spark profile data structure and methods
"""

import struct
import json
import sys
import os
import re
from collections import defaultdict, Counter
from datetime import datetime

def comprehensive_spark_analysis(profile_path):
    """Complete analysis of spark profile data"""
    
    print("=" * 100)
    print("COMPREHENSIVE SPARK PROFILE ANALYSIS - FLOWING FLUIDS FIXES")
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
        
        # 1. METHOD CALL ANALYSIS
        print("1. METHOD CALL ANALYSIS")
        print("=" * 50)
        
        # Extract all method calls
        method_calls = []
        method_pattern = r'([a-zA-Z_$][a-zA-Z0-9_$]*\.[a-zA-Z_$][a-zA-Z0-9_$]*(?:\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)'
        
        for line in lines:
            matches = re.findall(method_pattern, line)
            for match in matches:
                if any(keyword in match.lower() for keyword in ['flowingfluids', 'fluid', 'water', 'minecraft']):
                    method_calls.append(match)
        
        # Count method calls
        method_counter = Counter(method_calls)
        
        print("TOP METHOD CALLS:")
        for method, count in method_counter.most_common(20):
            print(f"  {count:6d}: {method}")
        print()
        
        # 2. FLOWINGFLUIDSFIXES SPECIFIC ANALYSIS
        print("2. FLOWINGFLUIDSFIXES MOD ANALYSIS")
        print("=" * 50)
        
        mod_methods = []
        for line in lines:
            if 'flowingfluidsfixes' in line.lower():
                # Extract method names
                method_matches = re.findall(r'flowingfluidsfixes\.[A-Za-z_][A-Za-z0-9_]*', line)
                mod_methods.extend(method_matches)
        
        mod_counter = Counter(mod_methods)
        
        print("FLOWINGFLUIDSFIXES METHOD CALLS:")
        for method, count in mod_counter.most_common():
            print(f"  {count:6d}: {method}")
        print()
        
        # 3. PERFORMANCE BOTTLENECK ANALYSIS
        print("3. PERFORMANCE BOTTLENECK ANALYSIS")
        print("=" * 50)
        
        # Look for expensive operations
        expensive_patterns = [
            'ConcurrentHashMap', 'put', 'get', 'compute', 'removeIf',
            'BlockPos', 'Level', 'Chunk', 'neighbor', 'update',
            'tick', 'process', 'handle', 'event'
        ]
        
        expensive_calls = defaultdict(int)
        for line in lines:
            for pattern in expensive_patterns:
                if pattern in line:
                    expensive_calls[pattern] += 1
        
        print("EXPENSIVE OPERATIONS:")
        for operation, count in sorted(expensive_calls.items(), key=lambda x: x[1], reverse=True):
            print(f"  {count:6d}: {operation}")
        print()
        
        # 4. NUMERICAL ANALYSIS
        print("4. NUMERICAL ANALYSIS")
        print("=" * 50)
        
        # Extract all numbers
        all_numbers = re.findall(r'\d+\.?\d*', text_data)
        numbers = [float(n) for n in all_numbers]
        
        # Categorize numbers
        tiny_numbers = [n for n in numbers if n < 1]
        small_numbers = [n for n in numbers if 1 <= n < 100]
        medium_numbers = [n for n in numbers if 100 <= n < 10000]
        large_numbers = [n for n in numbers if 10000 <= n < 1000000]
        huge_numbers = [n for n in numbers if n >= 1000000]
        
        print(f"NUMBERS BY CATEGORY:")
        print(f"  < 1:      {len(tiny_numbers):6d}")
        print(f"  1-99:     {len(small_numbers):6d}")
        print(f"  100-9999: {len(medium_numbers):6d}")
        print(f"  10k-999k: {len(large_numbers):6d}")
        print(f"  >= 1M:    {len(huge_numbers):6d}")
        print()
        
        if huge_numbers:
            print("LARGEST NUMBERS (Potential Operation Counts):")
            sorted_huge = sorted(huge_numbers, reverse=True)
            for i, num in enumerate(sorted_huge[:10]):
                print(f"  {i+1:2d}: {num:,.0f}")
        print()
        
        # 5. MEMORY ANALYSIS
        print("5. MEMORY ANALYSIS")
        print("=" * 50)
        
        # Look for memory-related patterns
        memory_patterns = ['cache', 'memory', 'heap', 'gc', 'allocate', 'free']
        memory_calls = defaultdict(int)
        
        for line in lines:
            for pattern in memory_patterns:
                if pattern in line.lower():
                    memory_calls[pattern] += 1
        
        print("MEMORY-RELATED OPERATIONS:")
        for operation, count in sorted(memory_calls.items(), key=lambda x: x[1], reverse=True):
            print(f"  {count:6d}: {operation}")
        print()
        
        # 6. THREAD ANALYSIS
        print("6. THREAD ANALYSIS")
        print("=" * 50)
        
        # Look for thread-related patterns
        thread_patterns = ['thread', 'server', 'main', 'worker', 'pool']
        thread_calls = defaultdict(int)
        
        for line in lines:
            for pattern in thread_patterns:
                if pattern in line.lower():
                    thread_calls[pattern] += 1
        
        print("THREAD-RELATED OPERATIONS:")
        for operation, count in sorted(thread_calls.items(), key=lambda x: x[1], reverse=True):
            print(f"  {count:6d}: {operation}")
        print()
        
        # 7. EVENT ANALYSIS
        print("7. EVENT ANALYSIS")
        print("=" * 50)
        
        # Look for event-related patterns
        event_patterns = ['event', 'handler', 'subscribe', 'fire', 'dispatch']
        event_calls = defaultdict(int)
        
        for line in lines:
            for pattern in event_patterns:
                if pattern in line.lower():
                    event_calls[pattern] += 1
        
        print("EVENT-RELATED OPERATIONS:")
        for operation, count in sorted(event_calls.items(), key=lambda x: x[1], reverse=True):
            print(f"  {count:6d}: {operation}")
        print()
        
        # 8. DETAILED FLOWINGFLUIDSFIXES ANALYSIS
        print("8. DETAILED FLOWINGFLUIDSFIXES ANALYSIS")
        print("=" * 50)
        
        # Extract all lines containing our mod
        mod_lines = []
        for line in lines:
            if 'flowingfluidsfixes' in line.lower():
                mod_lines.append(line.strip())
        
        print(f"TOTAL FLOWINGFLUIDSFIXES REFERENCES: {len(mod_lines)}")
        print()
        
        # Analyze specific methods
        method_details = defaultdict(list)
        for line in mod_lines:
            for method in ['calculateFluidPressure', 'isHighPriorityWater', 'applyBlockwiseJumping', 
                          'onFluidAccelerationEvent', 'onFluidTeleportation', 'processFluidTeleportation',
                          'findOptimalDestination', 'scoreDestination']:
                if method in line:
                    method_details[method].append(line)
        
        for method, method_lines in method_details.items():
            print(f"{method}:")
            print(f"  Call count: {len(method_lines)}")
            if method_lines:
                # Extract numbers from method calls
                for line in method_lines[:3]:  # Show first 3 lines
                    numbers = re.findall(r'\d+\.?\d*', line)
                    if numbers:
                        print(f"  Sample numbers: {', '.join(numbers[:5])}")
            print()
        
        # 9. PERFORMANCE IMPACT ANALYSIS
        print("9. PERFORMANCE IMPACT ANALYSIS")
        print("=" * 50)
        
        # Calculate potential impact
        total_method_calls = sum(method_counter.values())
        mod_method_calls = sum(mod_counter.values())
        
        print(f"TOTAL METHOD CALLS: {total_method_calls:,}")
        print(f"FLOWINGFLUIDSFIXES CALLS: {mod_method_calls:,}")
        print(f"MOD IMPACT RATIO: {(mod_method_calls/total_method_calls*100):.2f}%")
        print()
        
        # Identify most expensive operations
        if huge_numbers:
            max_ops = max(huge_numbers)
            print(f"MAXIMUM OPERATIONS: {max_ops:,.0f}")
            
            if max_ops > 1000000000:  # 1 billion
                print("⚠️  CRITICAL: Billion+ operations detected!")
            elif max_ops > 100000000:  # 100 million
                print("⚠️  SEVERE: 100M+ operations detected!")
            elif max_ops > 10000000:  # 10 million
                print("⚠️  HIGH: 10M+ operations detected!")
            else:
                print("✅ MODERATE: Under 10M operations")
        print()
        
        # 10. ROOT CAUSE ANALYSIS
        print("10. ROOT CAUSE ANALYSIS")
        print("=" * 50)
        
        # Identify the main culprits
        main_culprits = []
        
        if mod_method_calls > 100000:
            main_culprits.append("Excessive FlowingFluidsFixes method calls")
        
        if huge_numbers and max(huge_numbers) > 10000000:
            main_culprits.append("Massive operation counts (10M+)")
        
        if expensive_calls.get('ConcurrentHashMap', 0) > 10000:
            main_culprits.append("Excessive ConcurrentHashMap operations")
        
        if expensive_calls.get('BlockPos', 0) > 50000:
            main_culprits.append("Excessive BlockPos operations")
        
        print("PRIMARY ROOT CAUSES:")
        for i, culprit in enumerate(main_culprits, 1):
            print(f"  {i}. {culprit}")
        print()
        
        # 11. SPECIFIC RECOMMENDATIONS
        print("11. SPECIFIC RECOMMENDATIONS")
        print("=" * 50)
        
        recommendations = []
        
        if 'calculateFluidPressure' in method_details:
            call_count = len(method_details['calculateFluidPressure'])
            if call_count > 1000:
                recommendations.append(f"Reduce calculateFluidPressure calls (currently {call_count})")
        
        if 'findOptimalDestination' in method_details:
            call_count = len(method_details['findOptimalDestination'])
            if call_count > 100:
                recommendations.append(f"Reduce findOptimalDestination calls (currently {call_count})")
        
        if expensive_calls.get('ConcurrentHashMap', 0) > 10000:
            recommendations.append("Optimize ConcurrentHashMap operations - consider reducing cache size")
        
        if huge_numbers and max(huge_numbers) > 10000000:
            recommendations.append("Implement more aggressive throttling - operation count too high")
        
        if not recommendations:
            recommendations.append("Performance appears acceptable - monitor for issues")
        
        print("RECOMMENDATIONS:")
        for i, rec in enumerate(recommendations, 1):
            print(f"  {i}. {rec}")
        print()
        
        print("=" * 100)
        print("ANALYSIS COMPLETE")
        print("=" * 100)
        
    except Exception as e:
        print(f"Error analyzing profile: {e}")
        import traceback
        traceback.print_exc()
        print("=" * 100)

if __name__ == "__main__":
    import sys
    profile_path = sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\jwema\Downloads\rm0pSjFuhh.sparkprofile"
    comprehensive_spark_analysis(profile_path)
