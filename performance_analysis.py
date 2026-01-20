#!/usr/bin/env python3
"""
Spark Performance Analysis - Focus on bottlenecks and optimization opportunities
"""

import re
from collections import Counter, defaultdict

def analyze_performance_bottlenecks(strings):
    """Analyze performance bottlenecks from Spark profile data"""
    
    # Key performance indicators
    bottlenecks = {
        'fluid_operations': [],
        'block_operations': [],
        'entity_operations': [],
        'chunk_operations': [],
        'neighbor_updates': [],
        'redstone_updates': [],
        'lighting_updates': [],
        'high_frequency_methods': []
    }
    
    # Count method frequencies
    method_counts = Counter()
    
    for s in strings:
        # Count all method-like patterns
        if '.' in s and '(' in s:
            method_counts[s] += 1
        
        # Categorize by operation type
        s_lower = s.lower()
        
        # Fluid operations
        if any(keyword in s_lower for keyword in ['flowingfluid', 'fluid', 'traben', 'fffluid']):
            bottlenecks['fluid_operations'].append(s)
        
        # Block operations
        elif any(keyword in s_lower for keyword in ['block', 'blockstate', 'blockpos']):
            bottlenecks['block_operations'].append(s)
        
        # Entity operations
        elif any(keyword in s_lower for keyword in ['entity', 'livingentity', 'monster']):
            bottlenecks['entity_operations'].append(s)
        
        # Chunk operations
        elif any(keyword in s_lower for keyword in ['chunk', 'chunkmap', 'chunkstatus']):
            bottlenecks['chunk_operations'].append(s)
        
        # Neighbor updates (redstone)
        elif any(keyword in s_lower for keyword in ['neighbor', 'redstone', 'collectingneighbor']):
            bottlenecks['neighbor_updates'].append(s)
        
        # Lighting operations
        elif any(keyword in s_lower for keyword in ['light', 'threadedlevelightengine']):
            bottlenecks['lighting_updates'].append(s)
    
    # Get top methods by frequency
    bottlenecks['high_frequency_methods'] = method_counts.most_common(30)
    
    return bottlenecks

def print_performance_analysis(bottlenecks):
    """Print detailed performance analysis"""
    
    print("\n" + "="*80)
    print("PERFORMANCE BOTTLENECK ANALYSIS")
    print("="*80)
    
    print(f"\n🌊 FLUID OPERATIONS ({len(bottlenecks['fluid_operations'])}):")
    fluid_counts = Counter(bottlenecks['fluid_operations'])
    for method, count in fluid_counts.most_common(10):
        print(f"  {count:3d}: {method}")
    
    print(f"\n🧱 BLOCK OPERATIONS ({len(bottlenecks['block_operations'])}):")
    block_counts = Counter(bottlenecks['block_operations'])
    for method, count in block_counts.most_common(10):
        print(f"  {count:3d}: {method}")
    
    print(f"\n👾 ENTITY OPERATIONS ({len(bottlenecks['entity_operations'])}):")
    entity_counts = Counter(bottlenecks['entity_operations'])
    for method, count in entity_counts.most_common(10):
        print(f"  {count:3d}: {method}")
    
    print(f"\n🗺️ CHUNK OPERATIONS ({len(bottlenecks['chunk_operations'])}):")
    chunk_counts = Counter(bottlenecks['chunk_operations'])
    for method, count in chunk_counts.most_common(10):
        print(f"  {count:3d}: {method}")
    
    print(f"\n🔌 NEIGHBOR/REDSTONE UPDATES ({len(bottlenecks['neighbor_updates'])}):")
    neighbor_counts = Counter(bottlenecks['neighbor_updates'])
    for method, count in neighbor_counts.most_common(10):
        print(f"  {count:3d}: {method}")
    
    print(f"\n💡 LIGHTING OPERATIONS ({len(bottlenecks['lighting_updates'])}):")
    light_counts = Counter(bottlenecks['lighting_updates'])
    for method, count in light_counts.most_common(10):
        print(f"  {count:3d}: {method}")
    
    print(f"\n🔥 TOP 30 HIGH-FREQUENCY METHODS:")
    for i, (method, count) in enumerate(bottlenecks['high_frequency_methods'], 1):
        print(f"  {i:2d}. {count:4d}: {method}")

def generate_optimization_recommendations(bottlenecks):
    """Generate specific optimization recommendations"""
    
    print("\n" + "="*80)
    print("OPTIMIZATION RECOMMENDATIONS")
    print("="*80)
    
    recommendations = []
    
    # Analyze fluid operations
    if len(bottlenecks['fluid_operations']) > 0:
        recommendations.append({
            'category': 'Fluid Operations',
            'priority': 'HIGH',
            'issue': f"Found {len(bottlenecks['fluid_operations'])} fluid-related operations",
            'solution': "Implement fluid tick throttling and location-based optimization",
            'methods': [m for m, c in Counter(bottlenecks['fluid_operations']).most_common(5)]
        })
    
    # Analyze block operations
    if len(bottlenecks['block_operations']) > 50:
        recommendations.append({
            'category': 'Block Operations',
            'priority': 'HIGH',
            'issue': f"Found {len(bottlenecks['block_operations'])} block operations",
            'solution': "Implement block update batching and spatial partitioning",
            'methods': [m for m, c in Counter(bottlenecks['block_operations']).most_common(5)]
        })
    
    # Analyze neighbor updates
    if len(bottlenecks['neighbor_updates']) > 20:
        recommendations.append({
            'category': 'Neighbor Updates',
            'priority': 'MEDIUM',
            'issue': f"Found {len(bottlenecks['neighbor_updates'])} neighbor update operations",
            'solution': "Implement neighbor update throttling and redstone optimization",
            'methods': [m for m, c in Counter(bottlenecks['neighbor_updates']).most_common(5)]
        })
    
    # Analyze entity operations
    if len(bottlenecks['entity_operations']) > 30:
        recommendations.append({
            'category': 'Entity Operations',
            'priority': 'MEDIUM',
            'issue': f"Found {len(bottlenecks['entity_operations'])} entity operations",
            'solution': "Implement entity processing throttling and distance-based culling",
            'methods': [m for m, c in Counter(bottlenecks['entity_operations']).most_common(5)]
        })
    
    # Print recommendations
    for i, rec in enumerate(recommendations, 1):
        print(f"\n{i}. {rec['category']} [{rec['priority']}]")
        print(f"   Issue: {rec['issue']}")
        print(f"   Solution: {rec['solution']}")
        print(f"   Target Methods:")
        for method in rec['methods']:
            print(f"     - {method}")
    
    return recommendations

# Extract strings from the previous analysis
previous_output = """
minecraft/network/syncher/EntityDataAccessor;)Ljava/lang/Object;B                                                          1: el.redstone.CollectingNeighborUpdater"  m_230660_0A
:n(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/redstone/                                                       1: necraft.world.level.redstone.CollectingNeighborUpda
ter"       m_214152_0/:e(Lnet/minecraft/core/BlockPos;Lnet/m          1: ecraft.world.level.entity.PersistentEntitySectionMa
nager"       m_157538_0P:3(Lnet/minecraft/world/level/entity/Enti                                                          1: yncher.SynchedEntityData"       m_135379_0f:n(Lnet/
minecraft/network/syncher/EntityDataAccessor;)Lnet/minecraft/network/                                                     1: craft.network.syncher.SynchedEntityData"        m_1
35379_0f:n(Lnet/minecraft/network/syncher/EntityDataAcces       1: velgen.structure.templatesystem.StructureTemplateMa
nager"       m_230425_0l:@(Lnet/minecraft/resources/Reso        1: level.CollisionGetter"  m_285750_0f:X(Lnet/mine
     1: .minecraft.world.level.entity.EntitySectionStorage"
     m_261111_0z:Q(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util                                                      1: ecraft.world.level.LevelAccessor"       m_186469_0=
:K(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/materi                                                          1: rld.level.entity.EntitySectionStorage"  m_188362_0A
:Q(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/Abortab                                                              1: ecraft.world.level.entity.EntitySectionStorage" m_1
88362_0A:Q(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/ut     1: .behavior.GateBehavior$RunningPolicy$1" m_142144_0u
:n(Ljava/util/stream/Stream;Lnet/minecraft/server/leve          1: ft.world.level.redstone.CollectingNeighborUpdater" 
     m_230660_0A:n(Lnet/minecraft/core/BlockPos;Lne             1: ntity.EntitySectionStorage"     m_261111_0z:Q(Lnet/
minecraft/world/phys/AABB;Lnet/minecraft/util/Abortabl          1: .world.level.LevelAccessor"     m_186469_0=:K(Lnet/
minecraft/core/BlockPos;Lnet/minecraft/world/level/material/Fluid;I)V                                                      1: er.level.ThreadedLevelLightEngine"      m_284126_0b
:s(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;                                                   1: y.ai.behavior.declarative.BehaviorBuilder"      m_2
58034_0+:O(Ljava/util/function/Function;)Lnet/minecraft/world/entity/a                                                     1: necraft.world.level.entity.LevelEntityGetterAdapter
"    m_142232_0+:?(Lnet/minecraft/world/phys/AABB;Ljava/u       1: et.minecraft.network.syncher.SynchedEntityData" m_1
35379_0f:n(Lnet/minecraft/network/syncher/EntityDataAccesso     1: block.entity.BlockEntity"       m_187482_0M:!()Lnet
/                                                               1: r.SynchedEntityData"    m_135379_0f:n(Lnet/minecraf
t/network/syncher/EntityDataAccessor;)Lnet/minecraft/           1: vel.ChunkTaskPriorityQueueSorter"       m_143188_0?
:B(Ljava/lang/Runnable;Lnet/minecraft/util/thread/Proces        1: raft.world.level.redstone.CollectingNeighborUpdater
"    m_214152_0/:e(Lnet/minecraft/core/BlockPos;Lne             1: twork.syncher.SynchedEntityData"        m_135370_0x
:F(Lnet/minecraft/network/syncher/EntityDataAccessor;)Lja       1: avior.declarative.BehaviorBuilder"      m_258034_0+
:O(Ljava/util/function/Function;)Lnet/minecraft/world/enti      1: world.level.biome.BiomeManager" m_204214_0O::(
     1: erver.network.ServerGamePacketListenerImpl"     m_2
43119_0      :T(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/ne                                              
aft.world.level.material.FlowingFluid"Kmd36345f$lamb
da$flowing_fluids$getLowestSpreadableLookingFor4BlockDrops$3$4                                                           10: x000002e1211a7ab8
   11: P2
   12: UniApply
   13: network.protocol.game.ClientboundLightUpdatePa
   14: x000002e1212ebd70
   15: longs
   16: mapFirst0
   17: (J)V
   18: .ChunkMap"       m_287285_0
:S(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/serve
r/level/FullChunkStatus;)V                                    19: 1com.google.common.base.Mo
   20: m_21752_06
   21: net.minecraft.world.phys.shapes.VoxelShape
   22: m_7351_0
   23: :&(L
   24: net.minecraft.world.entity.EntitySelector
   25: BlockCollisions" m_186411_0>:+(II)Lnet/minecra
   26: BlockState;Ljava/util/EnumSet;Z)Lnet/minecraftf
   27: findFile
   28: ;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/p
hys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/                                                           29: EntityTypeTest
   30: Lnet/minecraft/util/AbortableIterationCon
   31: BlockPos;Lnet/minecraft/world/level/block/Block
   32: net.minecraft.world.level.material.Fluid
   33: stream/PipelineHelper;Ljava/util/Spliterator;)L
   34: 5452_0!:p(Lnet/minecraft/world/level/levelgen/Height
map$Types;Lnet/minecraft/core/BlockPos;)Lne                   35: the_endJ
   36: X:C(Ljava/util/function/Function;Ljava/lang/Obje
   37: net.minecraft.world.entity.monster.Zombie
   38: irectory=C:\\Users\\jwema\\curseforge\\minecraft\\Install
\\libraries --mod                                              39: m_8119_0O
   40: minBy
   41: m_5705_0
   42: x000002e1212f9c00
   43: work.syncher.SynchedEntityData"  m_135370_0x:F(Lnet/
minecraft/network/syncher/EntityDataAccessor;)Ljava/lang/Object;B                                                        44: m_207333_B
   45: Function_WithExceptions
   46: m_184145_0m
   47: m_44942_0
   48: m_6147_0
   49: net.minecraft.server.level.ServerEntity
   50: Ctraben.flowing_fluids.FFFluidUtils
"""

# Extract strings from the output
strings = []
lines = previous_output.split('\n')
for line in lines:
    # Extract method-like patterns
    methods = re.findall(r'[a-zA-Z_][a-zA-Z0-9_\.]*\.[a-zA-Z_][a-zA-Z0-9_]*', line)
    for method in methods:
        if len(method) > 10:  # Filter out short patterns
            strings.append(method)

# Analyze the data
bottlenecks = analyze_performance_bottlenecks(strings)
print_performance_analysis(bottlenecks)
recommendations = generate_optimization_recommendations(bottlenecks)
