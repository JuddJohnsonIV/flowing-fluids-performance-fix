#!/usr/bin/env python3
import re
import sys

def analyze_spark_profile(file_path):
    """Analyze Spark profiler binary data to extract performance bottlenecks"""
    
    with open(file_path, 'rb') as f:
        data = f.read()
    
    print(f"📊 Spark Profile Analysis - File Size: {len(data):,} bytes")
    print("=" * 60)
    
    # Extract text patterns from binary data
    text_pattern = re.compile(rb'[a-zA-Z][a-zA-Z0-9_\.]{10,}')
    matches = text_pattern.findall(data)
    
    # Convert to strings and filter for relevant patterns
    method_names = []
    for match in matches:
        try:
            method_name = match.decode('utf-8')
            if any(keyword in method_name.lower() for keyword in 
                   ['minecraft', 'level', 'chunk', 'fluid', 'flowing', 'tick', 'block']):
                method_names.append(method_name)
        except:
            continue
    
    # Count operation frequencies
    level_count = data.count(b'level')
    chunk_count = data.count(b'chunk')
    fluid_count = data.count(b'fluid')
    flowing_count = data.count(b'flowing')
    tick_count = data.count(b'tick')
    block_count = data.count(b'block')
    
    print(f"🔍 Operation Frequency Analysis:")
    print(f"   Level operations: {level_count:,}")
    print(f"   Chunk operations: {chunk_count:,}")
    print(f"   Fluid operations: {fluid_count:,}")
    print(f"   Flowing operations: {flowing_count:,}")
    print(f"   Tick operations: {tick_count:,}")
    print(f"   Block operations: {block_count:,}")
    print()
    
    # Extract unique method traces
    unique_methods = list(set(method_names))
    
    print(f"🎯 Critical Method Traces Found:")
    minecraft_methods = [m for m in unique_methods if 'minecraft' in m.lower()]
    flowing_methods = [m for m in unique_methods if 'flowing' in m.lower()]
    level_methods = [m for m in unique_methods if 'level' in m.lower()]
    chunk_methods = [m for m in unique_methods if 'chunk' in m.lower()]
    
    print(f"\n📋 Minecraft Methods ({len(minecraft_methods)}):")
    for method in sorted(minecraft_methods)[:15]:
        print(f"   {method}")
    
    if len(minecraft_methods) > 15:
        print(f"   ... and {len(minecraft_methods) - 15} more")
    
    print(f"\n🌊 Flowing Methods ({len(flowing_methods)}):")
    for method in sorted(flowing_methods)[:10]:
        print(f"   {method}")
    
    if len(flowing_methods) > 10:
        print(f"   ... and {len(flowing_methods) - 10} more")
    
    print(f"\n🏔️ Level Methods ({len(level_methods)}):")
    for method in sorted(level_methods)[:10]:
        print(f"   {method}")
    
    if len(level_methods) > 10:
        print(f"   ... and {len(level_methods) - 10} more")
    
    print(f"\n🧱 Chunk Methods ({len(chunk_methods)}):")
    for method in sorted(chunk_methods)[:10]:
        print(f"   {method}")
    
    if len(chunk_methods) > 10:
        print(f"   ... and {len(chunk_methods) - 10} more")
    
    # Performance analysis
    print(f"\n📈 Performance Analysis:")
    
    if level_count > 1000:
        print(f"🚨 CRITICAL: Excessive Level operations ({level_count:,})")
        print(f"   Likely cause: Worldwide player scanning via hasNearbyAlivePlayer()")
    
    if chunk_count > 500:
        print(f"⚠️  HIGH: Excessive Chunk operations ({chunk_count:,})")
        print(f"   Likely cause: Chunk boundary loading cascade")
    
    if fluid_count > 200:
        print(f"⚠️  HIGH: Excessive Fluid operations ({fluid_count:,})")
        print(f"   Likely cause: Uncontrolled fluid processing")
    
    if flowing_count > 100:
        print(f"🎯 TARGET: Flowing Fluids operations detected ({flowing_count:,})")
        print(f"   This confirms Flowing Fluids mod is active in profile")
    
    # Calculate severity
    total_operations = level_count + chunk_count + fluid_count + tick_count + block_count
    
    print(f"\n🎯 Overall Assessment:")
    print(f"   Total operations detected: {total_operations:,}")
    
    if total_operations > 5000:
        print(f"🚨 SEVERE: Server is critically overloaded")
        print(f"   Expected MSPT: 100ms+ (severe lag)")
    elif total_operations > 2000:
        print(f"⚠️  HIGH: Server is significantly overloaded")
        print(f"   Expected MSPT: 50-100ms (noticeable lag)")
    elif total_operations > 1000:
        print(f"🟡 MODERATE: Server is experiencing load")
        print(f"   Expected MSPT: 20-50ms (minor lag)")
    else:
        print(f"✅ GOOD: Server performance is acceptable")
        print(f"   Expected MSPT: <20ms (smooth gameplay)")
    
    return {
        'level_count': level_count,
        'chunk_count': chunk_count,
        'fluid_count': fluid_count,
        'flowing_count': flowing_count,
        'tick_count': tick_count,
        'block_count': block_count,
        'total_operations': total_operations,
        'unique_methods': len(unique_methods)
    }

if __name__ == "__main__":
    results = analyze_spark_profile("1JVPqTItRq.sparkprofile")
    print(f"\n✅ Analysis complete!")
