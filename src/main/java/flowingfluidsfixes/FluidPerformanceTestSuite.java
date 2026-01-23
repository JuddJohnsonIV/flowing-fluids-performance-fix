package flowingfluidsfixes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive Fluid Performance Test Suite
 * Tests ocean drains, tall water columns, and large fluid machines to measure MSPT improvements
 */
public class FluidPerformanceTestSuite {
    
    // Test configuration
    private static final int OCEAN_DRAIN_SIZE = 50; // 50x50 ocean drain
    private static final int TALL_COLUMN_HEIGHT = 200; // 200 block tall water column
    private static final int LARGE_MACHINE_SIZE = 30; // 30x30 large fluid machine
    private static final int TEST_DURATION_MS = 30000; // 30 seconds per test
    private static final int WARMUP_DURATION_MS = 5000; // 5 seconds warmup
    
    // Test tracking
    private static final AtomicInteger testsRun = new AtomicInteger(0);
    private static final AtomicInteger testsPassed = new AtomicInteger(0);
    private static final AtomicInteger testsFailed = new AtomicInteger(0);
    
    // Performance metrics
    private static final AtomicLong totalMSPTBefore = new AtomicLong(0);
    private static final AtomicLong totalMSPTAfter = new AtomicLong(0);
    private static final AtomicLong totalFluidEventsBefore = new AtomicLong(0);
    private static final AtomicLong totalFluidEventsAfter = new AtomicLong(0);
    
    // Test results storage
    private static final List<TestResult> testResults = new ArrayList<>();
    
    /**
     * Test result data structure
     */
    public static class TestResult {
        public final String testName;
        public final double msptBefore;
        public final double msptAfter;
        public final double msptImprovement;
        public final long fluidEventsBefore;
        public final long fluidEventsAfter;
        public final double eventReduction;
        public final boolean passed;
        public final String details;
        
        public TestResult(String testName, double msptBefore, double msptAfter, 
                         long fluidEventsBefore, long fluidEventsAfter, boolean passed, String details) {
            this.testName = testName;
            this.msptBefore = msptBefore;
            this.msptAfter = msptAfter;
            this.msptImprovement = ((msptBefore - msptAfter) / msptBefore) * 100.0;
            this.fluidEventsBefore = fluidEventsBefore;
            this.fluidEventsAfter = fluidEventsAfter;
            this.eventReduction = ((double)(fluidEventsBefore - fluidEventsAfter) / fluidEventsBefore) * 100.0;
            this.passed = passed;
            this.details = details;
        }
        
        @Override
        public String toString() {
            return String.format(
                "%s: MSPT %.1f→%.1f (%.1f%% improvement) | Events %d→%d (%.1f%% reduction) | %s",
                testName, msptBefore, msptAfter, msptImprovement,
                fluidEventsBefore, fluidEventsAfter, eventReduction,
                passed ? "✅ PASS" : "❌ FAIL"
            );
        }
    }
    
    /**
     * Run comprehensive test suite
     */
    public static void runComprehensiveTestSuite(ServerLevel level) {
        System.out.println("[FLUID PERFORMANCE TEST] Starting comprehensive test suite...");
        System.out.println("[FLUID PERFORMANCE TEST] Level: " + level.dimension().location());
        
        // Clear previous results
        testResults.clear();
        testsRun.set(0);
        testsPassed.set(0);
        testsFailed.set(0);
        
        // Reset performance counters
        resetPerformanceCounters();
        
        try {
            // Test 1: Ocean Drain Scenario
            TestResult oceanDrainResult = testOceanDrainScenario(level);
            testResults.add(oceanDrainResult);
            
            // Test 2: Tall Water Column
            TestResult tallColumnResult = testTallWaterColumn(level);
            testResults.add(tallColumnResult);
            
            // Test 3: Large Fluid Machine
            TestResult largeMachineResult = testLargeFluidMachine(level);
            testResults.add(largeMachineResult);
            
            // Test 4: Mixed Scenario (Ocean + Machine)
            TestResult mixedResult = testMixedScenario(level);
            testResults.add(mixedResult);
            
            // Test 5: Stress Test (Maximum load)
            TestResult stressResult = testStressScenario(level);
            testResults.add(stressResult);
            
        } catch (Exception e) {
            System.err.println("[FLUID PERFORMANCE TEST] Test suite failed: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Generate comprehensive report
        generateTestReport();
    }
    
    /**
     * Test ocean drain scenario (most critical for ocean lag)
     */
    private static TestResult testOceanDrainScenario(ServerLevel level) {
        System.out.println("[FLUID PERFORMANCE TEST] Testing Ocean Drain Scenario...");
        
        BlockPos center = new BlockPos(0, 63, 0); // Ocean level
        
        // Measure baseline performance
        double baselineMSPT = measureMSPT(level, WARMUP_DURATION_MS);
        long baselineEvents = FlowingFluidsFixesMinimal.totalFluidEvents.get();
        
        // Create ocean drain (remove blocks in large area)
        createOceanDrain(level, center, OCEAN_DRAIN_SIZE);
        
        // Measure performance during drain
        double drainMSPT = measureMSPT(level, TEST_DURATION_MS);
        long drainEvents = FlowingFluidsFixesMinimal.totalFluidEvents.get();
        
        // Clean up test area
        cleanupTestArea(level, center, OCEAN_DRAIN_SIZE);
        
        // Evaluate results
        boolean passed = evaluateResults("Ocean Drain", baselineMSPT, drainMSPT, baselineEvents, drainEvents);
        
        TestResult result = new TestResult(
            "Ocean Drain", baselineMSPT, drainMSPT, baselineEvents, drainEvents, passed,
            String.format("Size: %dx%d, Center: %s", OCEAN_DRAIN_SIZE, OCEAN_DRAIN_SIZE, center)
        );
        
        updateTestCounters(passed);
        return result;
    }
    
    /**
     * Test tall water column scenario
     */
    private static TestResult testTallWaterColumn(ServerLevel level) {
        System.out.println("[FLUID PERFORMANCE TEST] Testing Tall Water Column...");
        
        BlockPos base = new BlockPos(100, 0, 100);
        
        // Measure baseline
        double baselineMSPT = measureMSPT(level, WARMUP_DURATION_MS);
        long baselineEvents = FlowingFluidsFixesMinimal.totalFluidEvents.get();
        
        // Create tall water column
        createTallWaterColumn(level, base, TALL_COLUMN_HEIGHT);
        
        // Measure performance
        double columnMSPT = measureMSPT(level, TEST_DURATION_MS);
        long columnEvents = FlowingFluidsFixesMinimal.totalFluidEvents.get();
        
        // Clean up
        cleanupTestArea(level, base, 10);
        
        boolean passed = evaluateResults("Tall Column", baselineMSPT, columnMSPT, baselineEvents, columnEvents);
        
        TestResult result = new TestResult(
            "Tall Water Column", baselineMSPT, columnMSPT, baselineEvents, columnEvents, passed,
            String.format("Height: %d blocks, Base: %s", TALL_COLUMN_HEIGHT, base)
        );
        
        updateTestCounters(passed);
        return result;
    }
    
    /**
     * Test large fluid machine scenario
     */
    private static TestResult testLargeFluidMachine(ServerLevel level) {
        System.out.println("[FLUID PERFORMANCE TEST] Testing Large Fluid Machine...");
        
        BlockPos center = new BlockPos(-100, 64, -100);
        
        // Measure baseline
        double baselineMSPT = measureMSPT(level, WARMUP_DURATION_MS);
        long baselineEvents = FlowingFluidsFixesMinimal.totalFluidEvents.get();
        
        // Create large fluid machine
        createLargeFluidMachine(level, center, LARGE_MACHINE_SIZE);
        
        // Measure performance
        double machineMSPT = measureMSPT(level, TEST_DURATION_MS);
        long machineEvents = FlowingFluidsFixesMinimal.totalFluidEvents.get();
        
        // Clean up
        cleanupTestArea(level, center, LARGE_MACHINE_SIZE);
        
        boolean passed = evaluateResults("Large Machine", baselineMSPT, machineMSPT, baselineEvents, machineEvents);
        
        TestResult result = new TestResult(
            "Large Fluid Machine", baselineMSPT, machineMSPT, baselineEvents, machineEvents, passed,
            String.format("Size: %dx%d, Center: %s", LARGE_MACHINE_SIZE, LARGE_MACHINE_SIZE, center)
        );
        
        updateTestCounters(passed);
        return result;
    }
    
    /**
     * Test mixed scenario (ocean drain + fluid machine)
     */
    private static TestResult testMixedScenario(ServerLevel level) {
        System.out.println("[FLUID PERFORMANCE TEST] Testing Mixed Scenario...");
        
        BlockPos oceanCenter = new BlockPos(200, 63, 200);
        BlockPos machineCenter = new BlockPos(250, 64, 250);
        
        // Measure baseline
        double baselineMSPT = measureMSPT(level, WARMUP_DURATION_MS);
        long baselineEvents = FlowingFluidsFixesMinimal.totalFluidEvents.get();
        
        // Create both scenarios
        createOceanDrain(level, oceanCenter, 30);
        createLargeFluidMachine(level, machineCenter, 20);
        
        // Measure performance
        double mixedMSPT = measureMSPT(level, TEST_DURATION_MS);
        long mixedEvents = FlowingFluidsFixesMinimal.totalFluidEvents.get();
        
        // Clean up both areas
        cleanupTestArea(level, oceanCenter, 30);
        cleanupTestArea(level, machineCenter, 20);
        
        boolean passed = evaluateResults("Mixed Scenario", baselineMSPT, mixedMSPT, baselineEvents, mixedEvents);
        
        TestResult result = new TestResult(
            "Mixed Scenario", baselineMSPT, mixedMSPT, baselineEvents, mixedEvents, passed,
            "Ocean drain (30x30) + Fluid machine (20x20)"
        );
        
        updateTestCounters(passed);
        return result;
    }
    
    /**
     * Test stress scenario (maximum load)
     */
    private static TestResult testStressScenario(ServerLevel level) {
        System.out.println("[FLUID PERFORMANCE TEST] Testing Stress Scenario (Maximum Load)...");
        
        // Create multiple stress points
        List<BlockPos> stressPoints = new ArrayList<>();
        Random random = new Random(12345); // Fixed seed for reproducible tests
        
        for (int i = 0; i < 5; i++) {
            BlockPos point = new BlockPos(
                random.nextInt(200) - 100,
                64,
                random.nextInt(200) - 100
            );
            stressPoints.add(point);
        }
        
        // Measure baseline
        double baselineMSPT = measureMSPT(level, WARMUP_DURATION_MS);
        long baselineEvents = FlowingFluidsFixesMinimal.totalFluidEvents.get();
        
        // Create stress scenarios at all points
        for (BlockPos point : stressPoints) {
            createOceanDrain(level, point, 15);
            createTallWaterColumn(level, point.offset(20, 0, 20), 50);
        }
        
        // Measure performance under stress
        double stressMSPT = measureMSPT(level, TEST_DURATION_MS);
        long stressEvents = FlowingFluidsFixesMinimal.totalFluidEvents.get();
        
        // Clean up all stress points
        for (BlockPos point : stressPoints) {
            cleanupTestArea(level, point, 25);
        }
        
        boolean passed = evaluateResults("Stress Test", baselineMSPT, stressMSPT, baselineEvents, stressEvents);
        
        TestResult result = new TestResult(
            "Stress Test", baselineMSPT, stressMSPT, baselineEvents, stressEvents, passed,
            String.format("5 stress points with mixed scenarios")
        );
        
        updateTestCounters(passed);
        return result;
    }
    
    /**
     * Measure MSPT over specified duration
     */
    private static double measureMSPT(ServerLevel level, long durationMs) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + durationMs;
        
        double totalMSPT = 0.0;
        int measurements = 0;
        
        while (System.currentTimeMillis() < endTime) {
            // Get current MSPT from the optimization system
            double currentMSPT = FlowingFluidsFixesMinimal.cachedMSPT;
            totalMSPT += currentMSPT;
            measurements++;
            
            try {
                Thread.sleep(100); // Sample every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return measurements > 0 ? totalMSPT / measurements : 0.0;
    }
    
    /**
     * Create ocean drain (remove blocks to create water flow)
     */
    private static void createOceanDrain(ServerLevel level, BlockPos center, int size) {
        for (int x = -size/2; x <= size/2; x++) {
            for (int z = -size/2; z <= size/2; z++) {
                for (int y = 60; y <= 65; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (level.isLoaded(pos)) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }
    
    /**
     * Create tall water column
     */
    private static void createTallWaterColumn(ServerLevel level, BlockPos base, int height) {
        for (int y = 0; y < height; y++) {
            BlockPos pos = base.offset(0, y, 0);
            if (level.isLoaded(pos)) {
                level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
            }
        }
    }
    
    /**
     * Create large fluid machine
     */
    private static void createLargeFluidMachine(ServerLevel level, BlockPos center, int size) {
        Random random = new Random(54321); // Fixed seed
        
        for (int x = -size/2; x <= size/2; x++) {
            for (int z = -size/2; z <= size/2; z++) {
                for (int y = 0; y <= 10; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (level.isLoaded(pos)) {
                        // Create random fluid pattern
                        if (random.nextBoolean()) {
                            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
                        } else {
                            level.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Clean up test area
     */
    private static void cleanupTestArea(ServerLevel level, BlockPos center, int size) {
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                for (int y = 0; y <= 100; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (level.isLoaded(pos)) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }
    
    /**
     * Evaluate test results
     */
    private static boolean evaluateResults(String testName, double baselineMSPT, double testMSPT, 
                                          long baselineEvents, long testEvents) {
        // Performance targets
        double msptImprovementTarget = 20.0; // 20% MSPT improvement target
        double eventReductionTarget = 30.0; // 30% event reduction target
        
        double msptImprovement = ((baselineMSPT - testMSPT) / baselineMSPT) * 100.0;
        double eventReduction = ((double)(baselineEvents - testEvents) / baselineEvents) * 100.0;
        
        // Test passes if either MSPT improves significantly OR events are reduced significantly
        boolean passed = (msptImprovement >= msptImprovementTarget) || (eventReduction >= eventReductionTarget);
        
        System.out.println(String.format(
            "[TEST RESULT] %s: MSPT %.1f→%.1f (%.1f%% improvement) | Events %d→%d (%.1f%% reduction) | %s",
            testName, baselineMSPT, testMSPT, msptImprovement,
            baselineEvents, testEvents, eventReduction,
            passed ? "✅ PASS" : "❌ FAIL"
        ));
        
        return passed;
    }
    
    /**
     * Update test counters
     */
    private static void updateTestCounters(boolean passed) {
        testsRun.incrementAndGet();
        if (passed) {
            testsPassed.incrementAndGet();
        } else {
            testsFailed.incrementAndGet();
        }
    }
    
    /**
     * Reset performance counters
     */
    private static void resetPerformanceCounters() {
        FlowingFluidsFixesMinimal.totalFluidEvents.set(0);
        FlowingFluidsFixesMinimal.skippedFluidEvents.set(0);
        
        // Reset all optimization system counters
        PlayerProximityCache.clearAll();
        EntityProcessingOptimizer.clearAllCaches();
        SpatialPartitioningSystem.clearAll();
        UnifiedCacheManager.clearAll();
    }
    
    /**
     * Generate comprehensive test report
     */
    private static void generateTestReport() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("[FLUID PERFORMANCE TEST] COMPREHENSIVE TEST REPORT");
        System.out.println("=".repeat(80));
        
        // Individual test results
        System.out.println("\n--- INDIVIDUAL TEST RESULTS ---");
        for (TestResult result : testResults) {
            System.out.println(result);
        }
        
        // Overall statistics
        System.out.println("\n--- OVERALL STATISTICS ---");
        System.out.println(String.format("Tests Run: %d", testsRun.get()));
        System.out.println(String.format("Tests Passed: %d", testsPassed.get()));
        System.out.println(String.format("Tests Failed: %d", testsFailed.get()));
        System.out.println(String.format("Success Rate: %.1f%%", 
            (testsPassed.get() * 100.0 / testsRun.get())));
        
        // Performance improvements
        if (!testResults.isEmpty()) {
            double avgMSPTImprovement = testResults.stream()
                .mapToDouble(r -> r.msptImprovement)
                .average()
                .orElse(0.0);
            
            double avgEventReduction = testResults.stream()
                .mapToDouble(r -> r.eventReduction)
                .average()
                .orElse(0.0);
            
            System.out.println(String.format("Average MSPT Improvement: %.1f%%", avgMSPTImprovement));
            System.out.println(String.format("Average Event Reduction: %.1f%%", avgEventReduction));
        }
        
        // System status
        System.out.println("\n--- SYSTEM STATUS ---");
        System.out.println("[CACHE] " + FlowingFluidsFixesMinimal.getCacheStatistics());
        System.out.println("[FLUID TICK] " + FlowingFluidsFixesMinimal.getFluidTickThrottlingStats());
        System.out.println("[NEIGHBOR] " + FlowingFluidsFixesMinimal.getNeighborUpdateBlockingStats());
        System.out.println("[PLAYER PROXIMITY] " + PlayerProximityCache.getCacheStatistics());
        System.out.println("[ENTITY PROCESSING] " + EntityProcessingOptimizer.getEntityProcessingStatistics());
        System.out.println("[SPATIAL PARTITIONING] " + SpatialPartitioningSystem.getSpatialStatistics());
        
        // Recommendations
        System.out.println("\n--- RECOMMENDATIONS ---");
        generateRecommendations();
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("[FLUID PERFORMANCE TEST] TEST SUITE COMPLETE");
        System.out.println("=".repeat(80));
    }
    
    /**
     * Generate performance recommendations
     */
    private static void generateRecommendations() {
        double avgMSPTImprovement = testResults.stream()
            .mapToDouble(r -> r.msptImprovement)
            .average()
            .orElse(0.0);
        
        if (avgMSPTImprovement >= 30.0) {
            System.out.println("✅ EXCELLENT: System shows >30% MSPT improvement - optimizations highly effective");
        } else if (avgMSPTImprovement >= 20.0) {
            System.out.println("✅ GOOD: System shows >20% MSPT improvement - optimizations working well");
        } else if (avgMSPTImprovement >= 10.0) {
            System.out.println("⚠️ MODERATE: System shows >10% MSPT improvement - consider fine-tuning");
        } else {
            System.out.println("❌ POOR: System shows <10% MSPT improvement - optimizations need adjustment");
        }
        
        // Check cache efficiency
        double cacheEfficiency = PlayerProximityCache.getCacheEfficiency();
        if (cacheEfficiency < 50.0) {
            System.out.println("⚠️ WARNING: Player proximity cache efficiency below 50% - consider increasing cache duration");
        }
        
        // Check spatial partitioning
        double spatialEfficiency = SpatialPartitioningSystem.getProcessingEfficiency();
        if (spatialEfficiency < 70.0) {
            System.out.println("⚠️ WARNING: Spatial partitioning efficiency below 70% - consider adjusting chunk radius");
        }
        
        // Check entity processing
        double entityEfficiency = EntityProcessingOptimizer.getEntityProcessingEfficiency();
        if (entityEfficiency < 60.0) {
            System.out.println("⚠️ WARNING: Entity processing efficiency below 60% - consider adjusting entity limits");
        }
    }
    
    /**
     * Get test summary
     */
    public static String getTestSummary() {
        if (testResults.isEmpty()) {
            return "No tests run yet";
        }
        
        return String.format(
            "Fluid Performance Tests: %d run, %d passed (%.1f%% success), %.1f%% avg MSPT improvement",
            testsRun.get(), testsPassed.get(), 
            (testsPassed.get() * 100.0 / testsRun.get()),
            testResults.stream().mapToDouble(r -> r.msptImprovement).average().orElse(0.0)
        );
    }
}
