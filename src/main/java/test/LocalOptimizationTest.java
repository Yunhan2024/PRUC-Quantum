package test;

import GSLO.LocalOptimization;
import GSLO.GlobalSearch;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.Jsoner;
import util.Area;
import util.Region;
import util.Preprocess;

import java.io.FileReader;
import java.lang.reflect.Field;
import java.util.ArrayList;

/**
 * Test class for running Local Optimization on a pre-existing p-region solution loaded from JSON
 */
public class LocalOptimizationTest {

    public static void main(String[] args) throws Exception {
        // Configuration - modify these values as needed
        String jsonFilePath = "gs_assignment_2k_p25_sol0.json";
        int maxNoImprove = 100;
        double alpha = 0.99;

        runLocalOptimization(jsonFilePath, maxNoImprove, alpha);
    }

    /**
     * Runs local optimization on a solution loaded from JSON
     */
    public static void runLocalOptimization(String jsonFilePath, int maxNoImprove, double alpha) throws Exception {
        System.out.println("=== LOCAL OPTIMIZATION TEST ===");
        System.out.println("JSON file: " + jsonFilePath);
        System.out.println("Max no-improve iterations: " + maxNoImprove);
        System.out.println("Alpha (cooling rate): " + alpha);
        System.out.println();

        // Track timing for different phases
        long totalStartTime = System.currentTimeMillis();
        long dataLoadingTime = 0;
        long regionConstructionTime = 0;
        long verificationTime = 0;
        long localOptimizationTime = 0;

        // Phase 1: Load JSON file
        long phaseStart = System.currentTimeMillis();
        JsonObject jsonObject;
        try (FileReader reader = new FileReader(jsonFilePath)) {
            jsonObject = (JsonObject) Jsoner.deserialize(reader);
        }

        String dataset = (String) jsonObject.get("dataset");
        int p = ((Number) jsonObject.get("p_value")).intValue();
        JsonObject regionAssignments = (JsonObject) jsonObject.get("region_assignments");

        System.out.println("Dataset: " + dataset);
        System.out.println("P value: " + p);
        dataLoadingTime = System.currentTimeMillis() - phaseStart;

        // Phase 2: Load dataset
        phaseStart = System.currentTimeMillis();
        ArrayList<Area> allAreas;
        try {
            allAreas = Preprocess.GeoSetBuilder(dataset);
        } catch (Exception e) {
            System.err.println("Error loading dataset '" + dataset + "'.");
            System.err.println("You may need to add this case to Preprocess.java:");
            System.err.println("case \"2k\":");
            System.err.println("    file = new File(\"DataFile/2056dataset/merge.shp\");");
            System.err.println("    break;");
            throw e;
        }

        System.out.println("Loaded " + allAreas.size() + " areas");
        long datasetLoadTime = System.currentTimeMillis() - phaseStart;

        long threshold = 0; // As specified by user

        // Phase 3: Reset all area assignments and create regions
        phaseStart = System.currentTimeMillis();

        // Reset all area assignments
        for (Area area : allAreas) {
            area.set_region(-1);
        }

        // Create regions from JSON assignments
        Region[] regions = new Region[p];

        // 首先设置所有区域的关联关系
        for (int regionId = 0; regionId < p; regionId++) {
            String regionKey = String.valueOf(regionId);
            JsonArray areaIndices = (JsonArray) regionAssignments.get(regionKey);

            if (areaIndices == null || areaIndices.isEmpty()) {
                throw new RuntimeException("Region " + regionId + " has no areas assigned");
            }

            for (Object areaIndexObj : areaIndices) {
                int areaIndex = ((Number) areaIndexObj).intValue();
                if (areaIndex >= allAreas.size()) {
                    throw new RuntimeException("Area index " + areaIndex + " is out of bounds for dataset with " + allAreas.size() + " areas");
                }
                Area area = allAreas.get(areaIndex);
                area.set_region(regionId); // Set the region assignment
            }
        }

        // 然后创建Region对象
        for (int regionId = 0; regionId < p; regionId++) {
            String regionKey = String.valueOf(regionId);
            JsonArray areaIndices = (JsonArray) regionAssignments.get(regionKey);

            ArrayList<Area> areasInRegion = new ArrayList<>();
            for (Object areaIndexObj : areaIndices) {
                int areaIndex = ((Number) areaIndexObj).intValue();
                Area area = allAreas.get(areaIndex);
                areasInRegion.add(area);
            }

            if (!areasInRegion.isEmpty()) {
                // 使用第一个区域作为种子创建区域，然后添加其余区域
                Area firstArea = areasInRegion.get(0);
                regions[regionId] = new Region(regionId, firstArea, threshold, allAreas);

                // 添加其余区域到该区域（跳过第一个，因为已经在构造函数中添加了）
                for (int i = 1; i < areasInRegion.size(); i++) {
                    regions[regionId].add_area_to_region(areasInRegion.get(i));
                }
            } else {
                throw new RuntimeException("Region " + regionId + " has no areas assigned");
            }
        }
        regionConstructionTime = System.currentTimeMillis() - phaseStart;

        // Phase 4: Verify solution
        phaseStart = System.currentTimeMillis();

        // Verify all areas are assigned
        int assignedAreas = 0;
        for (Region region : regions) {
            assignedAreas += region.get_region_size();
        }

        if (assignedAreas != allAreas.size()) {
            throw new RuntimeException("Not all areas are assigned to regions. Assigned: " + assignedAreas + ", Total: " + allAreas.size());
        }

        // Calculate initial heterogeneity and other statistics
        long initialHeterogeneity = Region.get_all_region_hetero(regions);
        System.out.println("\n=== INITIAL SOLUTION STATISTICS ===");
        System.out.println("Total heterogeneity: " + initialHeterogeneity);

        // Print region statistics
        for (int i = 0; i < regions.length; i++) {
            Region region = regions[i];
            System.out.printf("Region %d: %d areas, heterogeneity: %d, extensive attr: %d%n",
                    i, region.get_region_size(), region.get_region_hetero(), region.get_region_extensive_attr());
        }

        // Verify that all regions are complete (should be true since threshold = 0)
        boolean allComplete = true;
        for (int i = 0; i < regions.length; i++) {
            if (!regions[i].is_region_complete()) {
                System.err.println("Warning: Region " + i + " is not complete!");
                allComplete = false;
            }
        }

        if (!allComplete) {
            System.err.println("Some regions are not complete. Local optimization may not run.");
            return;
        }

        // Verify solution correctness
        try {
            // 首先检查regions是否正确初始化
            for (int i = 0; i < regions.length; i++) {
                Region region = regions[i];
                if (region == null) {
                    System.err.println("Region " + i + " is null!");
                    continue;
                }
                if (region.get_areas_in_region() == null) {
                    System.err.println("Region " + i + " has null areas_in_region!");
                    continue;
                }
                if (region.getAreas_on_margin() == null) {
                    System.err.println("Region " + i + " has null areas_on_margin!");
                    continue;
                }
                System.out.println("Region " + i + " initialized correctly with " +
                        region.get_areas_in_region().size() + " areas and " +
                        (region.getAreas_on_margin() != null ? region.getAreas_on_margin().size() : "null") + " margin areas");
            }

            Region.test_result_correctness(regions, allAreas, threshold, true);
            System.out.println("Solution correctness verified successfully.");
        } catch (Exception e) {
            System.err.println("Solution correctness check failed: " + e.getMessage());
            e.printStackTrace();
            // 不要返回，继续尝试运行优化
        }
        verificationTime = System.currentTimeMillis() - phaseStart;

        // Phase 5: Create mock GlobalSearch object
        phaseStart = System.currentTimeMillis();
        GlobalSearch mockGlobalSearch = createMockGlobalSearch(allAreas, regions, p, threshold);
        long mockCreationTime = System.currentTimeMillis() - phaseStart;

        // Phase 6: Run Local Optimization
        System.out.println("\n=== RUNNING LOCAL OPTIMIZATION ===");
        System.out.println("Starting optimization...");

        phaseStart = System.currentTimeMillis();

        LocalOptimization localOpt = new LocalOptimization(
                mockGlobalSearch,
                maxNoImprove,
                alpha,
                allAreas,
                regions,
                threshold
        );

        localOptimizationTime = System.currentTimeMillis() - phaseStart;
        long totalRuntime = System.currentTimeMillis() - totalStartTime;

        // Print results
        System.out.println("\n=== LOCAL OPTIMIZATION RESULTS ===");

        // Key metrics highlighted
        System.out.println("┌─────────────────────────────────────────────┐");
        System.out.println("│              KEY RESULTS                   │");
        System.out.println("├─────────────────────────────────────────────┤");
        System.out.printf("│ Initial Heterogeneity:     %15d │%n", initialHeterogeneity);
        System.out.printf("│ Final Heterogeneity:       %15d │%n", localOpt.getBest_hetero());
        System.out.printf("│ Local Optimization Runtime: %14d ms │%n", localOpt.getTotal_time());
        System.out.printf("│ Total Runtime:              %14d ms │%n", totalRuntime);
        System.out.println("└─────────────────────────────────────────────┘");

        long improvement = initialHeterogeneity - localOpt.getBest_hetero();
        System.out.println("\nImprovement Analysis:");
        System.out.println("Absolute improvement: " + improvement);

        if (initialHeterogeneity > 0) {
            double improvementPercentage = ((double) improvement / initialHeterogeneity) * 100;
            System.out.println("Improvement percentage: " + String.format("%.2f%%", improvementPercentage));
        }

        // Detailed timing breakdown
        System.out.println("\n=== DETAILED TIMING BREAKDOWN ===");
        System.out.printf("Data Loading & JSON Parsing: %8d ms%n", dataLoadingTime);
        System.out.printf("Dataset Loading:              %8d ms%n", datasetLoadTime);
        System.out.printf("Region Construction:          %8d ms%n", regionConstructionTime);
        System.out.printf("Solution Verification:        %8d ms%n", verificationTime);
        System.out.printf("Mock GlobalSearch Creation:   %8d ms%n", mockCreationTime);
        System.out.printf("Local Optimization (actual):  %8d ms%n", localOpt.getTotal_time());
        System.out.printf("Local Optimization (total):   %8d ms%n", localOptimizationTime);
        System.out.printf("Total Runtime:                %8d ms%n", totalRuntime);

        // Summary line for easy parsing
        System.out.println("\n=== SUMMARY (for easy parsing) ===");
        System.out.println("HETEROGENEITY_INITIAL=" + initialHeterogeneity);
        System.out.println("HETEROGENEITY_FINAL=" + localOpt.getBest_hetero());
        System.out.println("RUNTIME_LOCAL_OPT_INTERNAL=" + localOpt.getTotal_time());
        System.out.println("RUNTIME_LOCAL_OPT_TOTAL=" + localOptimizationTime);
        System.out.println("RUNTIME_TOTAL=" + totalRuntime);
        System.out.println("IMPROVEMENT_ABSOLUTE=" + improvement);
        if (initialHeterogeneity > 0) {
            double improvementPercentage = ((double) improvement / initialHeterogeneity) * 100;
            System.out.println("IMPROVEMENT_PERCENTAGE=" + String.format("%.2f", improvementPercentage));
        }
        System.out.println("MAX_NO_IMPROVE=" + maxNoImprove);
        System.out.println("ALPHA=" + alpha);
        System.out.println("P_VALUE=" + p);
        System.out.println("DATASET=" + dataset);

        // Print final region statistics
        System.out.println("\n=== FINAL REGION STATISTICS ===");
        long finalHeterogeneity = Region.get_all_region_hetero(regions);
        System.out.println("Final total heterogeneity: " + finalHeterogeneity);

        for (int i = 0; i < regions.length; i++) {
            Region region = regions[i];
            System.out.printf("Region %d: %d areas, heterogeneity: %d, extensive attr: %d%n",
                    i, region.get_region_size(), region.get_region_hetero(), region.get_region_extensive_attr());
        }

        // Final verification
        try {
            Region.test_result_correctness(regions, allAreas, threshold, true);
            System.out.println("\nFinal solution correctness verified successfully.");
        } catch (Exception e) {
            System.err.println("Final solution correctness check failed: " + e.getMessage());
        }

        System.out.println("\n=== OPTIMIZATION COMPLETE ===");
    }

    /**
     * Creates a mock GlobalSearch object that reports the solution as solved
     * and returns the provided regions and areas
     */
    private static GlobalSearch createMockGlobalSearch(ArrayList<Area> allAreas, Region[] regions, int p, long threshold) throws Exception {
        System.out.println("Creating mock GlobalSearch object...");

        // 创建一个最小的种子区域列表，只取前几个区域避免邻居索引问题
        ArrayList<Area> seedAreas = new ArrayList<>();

        // 取前面几个区域作为种子，确保索引连续
        int seedCount = Math.min(Math.min(p, 5), allAreas.size()); // 最多取5个种子避免复杂性
        for (int i = 0; i < seedCount; i++) {
            seedAreas.add(allAreas.get(i));
        }

        System.out.println("Created " + seedAreas.size() + " seed areas for mock GlobalSearch");

        // 临时清理种子区域的邻居关系，避免索引越界
        ArrayList<ArrayList<Integer>> originalNeighbors = new ArrayList<>();
        for (Area seed : seedAreas) {
            // 保存原始邻居
            originalNeighbors.add(new ArrayList<>(seed.get_neigh_area_index()));
            // 清空邻居列表
            seed.get_neigh_area_index().clear();
        }

        GlobalSearch gs;
        try {
            // 使用种子区域创建 GlobalSearch，传入 maxiter=0 避免复杂的种子选择
            gs = new GlobalSearch(seedAreas, seedCount, 0, threshold, false);
        } catch (Exception e) {
            System.err.println("Failed to create GlobalSearch: " + e.getMessage());
            throw new RuntimeException("Failed to create mock GlobalSearch", e);
        } finally {
            // 恢复原始邻居关系
            for (int i = 0; i < seedAreas.size() && i < originalNeighbors.size(); i++) {
                seedAreas.get(i).get_neigh_area_index().clear();
                seedAreas.get(i).get_neigh_area_index().addAll(originalNeighbors.get(i));
            }
        }

        // 使用反射替换内部字段
        try {
            // 设置 regions 字段
            Field regionsField = GlobalSearch.class.getDeclaredField("regions");
            regionsField.setAccessible(true);
            regionsField.set(gs, regions);

            // 设置 all_areas 字段
            Field areasField = GlobalSearch.class.getDeclaredField("all_areas");
            areasField.setAccessible(true);
            areasField.set(gs, allAreas);

            System.out.println("Successfully created mock GlobalSearch object with reflection");

        } catch (NoSuchFieldException | IllegalAccessException e) {
            System.err.println("Warning: Could not modify GlobalSearch fields via reflection: " + e.getMessage());
            System.err.println("The LocalOptimization might not work correctly.");
            throw new RuntimeException("Failed to create mock GlobalSearch", e);
        }

        return gs;
    }

    /**
     * Alternative method to run multiple tests with different iteration counts
     */
    public static void runIterationAnalysis(String jsonFilePath, int[] iterationCounts) throws Exception {
        System.out.println("=== ITERATION ANALYSIS ===");
        System.out.println("Testing different iteration counts: ");
        for (int count : iterationCounts) {
            System.out.print(count + " ");
        }
        System.out.println("\n");

        for (int iterations : iterationCounts) {
            System.out.println("--- Testing with " + iterations + " iterations ---");
            runLocalOptimization(jsonFilePath, iterations, 0.99);
            System.out.println();
        }
    }

    /**
     * Convenience method for testing multiple iteration counts
     */
    public static void main2(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java LocalOptimizationTest <json_file_path>");
            return;
        }

        String jsonFilePath = args[0];
        int[] iterationCounts = {10, 50, 100, 500, 1000, 2000};

        runIterationAnalysis(jsonFilePath, iterationCounts);
    }
}