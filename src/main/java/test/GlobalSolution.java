package test;

import GSLO.GlobalSearch;
import GSLO.Tarjan;
import util.Area;
import util.Preprocess;
import util.Region;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * This class tests the Global Search (GS) algorithm by generating
 * initial solutions with p=3 and threshold=0 for the 5k dataset,
 * and saves the complete region assignments for later analysis.
 */
public class GlobalSolution {

    // Configuration parameters
    private static final int MAX_ITERATIONS = 10; // Number of GS runs
    private static final String OUTPUT_DIR = "gs_test_results";
    private static final String DATASET = "500";
    private static final double THRESHOLD_SCALE = 0; // Fixed threshold of 0
    private static final int P_VALUE = 10; // Fixed p value of 3

    public static void main(String[] args) throws IOException, InterruptedException, CloneNotSupportedException {
        System.out.println("Starting Global Search (GS) algorithm test with fixed p=" + P_VALUE +
                " and threshold=" + THRESHOLD_SCALE);

        // Create output directory if it doesn't exist
        Files.createDirectories(Paths.get(OUTPUT_DIR));

        // Load areas from dataset
        ArrayList<Area> all_areas = Preprocess.GeoSetBuilder(DATASET);
        long total_ext = 0;

        for (Area area : all_areas) {
            total_ext += area.get_extensive_attr();
        }

        System.out.println("Total areas: " + all_areas.size());
        System.out.println("Total extensive attribute: " + total_ext);

        // Fixed threshold scale - for threshold=0
        double scale = THRESHOLD_SCALE;
        long threshold = (long)(total_ext * scale);
        System.out.println("\n--- Testing with threshold scale: " + scale + " (value: " + threshold + ") ---");

        // Fixed p value
        int p = P_VALUE;
        System.out.println("Testing with p = " + p);

        // Statistics tracking
        int successCount = 0;
        int iterations = MAX_ITERATIONS;
        long totalRuntime = 0;
        long totalHeterogeneity = 0;

        // Create results file to track success patterns
        String resultsFile = String.format("%s/gs_results_%s_p%d_scale%.4f.csv",
                OUTPUT_DIR, DATASET, p, scale);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(resultsFile))) {
            writer.write("iteration,success,runtime_ms,heterogeneity,seed_quality");
            writer.newLine();

            // Run GS multiple times with same configuration
            for (int i = 0; i < iterations; i++) {
                // Create deep copy of areas to ensure independence between runs
                ArrayList<Area> areas_copy = Area.area_list_copy(all_areas);

                // Run Global Search
                long startTime = System.currentTimeMillis();
                GlobalSearch sol = new GlobalSearch(areas_copy, p, areas_copy.size(), threshold, false);
                long runtime = System.currentTimeMillis() - startTime;

                totalRuntime += runtime;

                boolean success = sol.solved();
                double seedQuality = sol.get_seed_quality();
                long heterogeneity = -1;

                // Check if solution is found
                if (success) {
                    successCount++;
                    Region[] regions = sol.get_regions();
                    heterogeneity = Region.get_all_region_hetero(regions);
                    totalHeterogeneity += heterogeneity;

                    // Save the complete GlobalSearch solution with area assignments
                    saveGlobalSearchSolution(DATASET, p, scale, successCount, sol, heterogeneity, runtime);
                }

                // Write result to CSV
                writer.write(String.format("%d,%b,%d,%d,%.6f",
                        i, success, runtime, heterogeneity, seedQuality));
                writer.newLine();

                // Print progress
                if ((i + 1) % 50 == 0) {
                    System.out.println("  Progress: " + (i + 1) + "/" + iterations +
                            " runs, success rate so far: " + successCount + "/" + (i + 1) +
                            String.format(" (%.2f%%)", (double)successCount/(i+1)*100));
                }
            }

            System.out.println("\nTest completed for " + DATASET);
            System.out.println("  Results: " + successCount + "/" + iterations +
                    " successful runs (" + (double) successCount / iterations * 100 + "%)");
            System.out.println("  Avg runtime: " + (totalRuntime / iterations) + " ms");
            if (successCount > 0) {
                System.out.println("  Avg heterogeneity: " + (totalHeterogeneity / successCount));
            }
        }

        System.out.println("\nGlobal Search test completed. Results saved to " + OUTPUT_DIR + " directory.");
    }

    /**
     * Save the complete GlobalSearch solution including all area assignments
     */
    private static void saveGlobalSearchSolution(String dataset, int p, double scale,
                                                 int solutionNum, GlobalSearch sol,
                                                 long heterogeneity, long runtime) throws IOException {
        // Save solution to a unique file based on solution number
        String filename = String.format("%s/gs_solution_%s_p%d_scale%.4f_sol%d.txt",
                OUTPUT_DIR, dataset, p, scale, solutionNum);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
            writer.write("Dataset: " + dataset);
            writer.newLine();
            writer.write("Number of regions (p): " + p);
            writer.newLine();
            writer.write("Threshold scale: " + scale);
            writer.newLine();
            writer.write("Solution number: " + solutionNum);
            writer.newLine();
            writer.write("Seed quality (min distance): " + sol.get_seed_quality());
            writer.newLine();
            writer.write("Total heterogeneity: " + heterogeneity);
            writer.newLine();
            writer.write("Runtime (ms): " + runtime);
            writer.newLine();
            writer.newLine();

            // Save ALAND (extensive attribute) values for first 50 areas
            writer.write("aland (Extensive Attribute) Values for First 50 Areas:");
            writer.newLine();
            writer.write("AreaID,aland");
            writer.newLine();

            ArrayList<Area> all_areas = sol.get_all_areas();
            int maxAreas = Math.min(50, all_areas.size());
            for (int i = 0; i < maxAreas; i++) {
                Area area = all_areas.get(i);
                writer.write(area.get_geo_index() + "," + area.get_extensive_attr());
                writer.newLine();
            }
            writer.newLine();

            Region[] regions = sol.get_regions();

            writer.write("Region details:");
            writer.newLine();

            for (int i = 0; i < regions.length; i++) {
                Region r = regions[i];
                writer.write("Region " + i + ":");
                writer.newLine();
                writer.write("  Size: " + r.get_region_size() + " areas");
                writer.newLine();
                writer.write("  Extensive attribute: " + r.get_region_extensive_attr());
                writer.newLine();
                writer.write("  Heterogeneity: " + r.get_region_hetero());
                writer.newLine();
                writer.write("  Connected: " + r.is_connected());
                writer.newLine();

                writer.write("  Areas: ");
                ArrayList<Area> areas = r.get_areas_in_region();
                for (Area area : areas) {
                    writer.write(area.get_geo_index() + " ");
                }
                writer.newLine();
                writer.newLine();
            }

            // Save area-to-region assignments
            writer.write("Area-to-Region Assignments:");
            writer.newLine();
            writer.write("AreaID,RegionID");
            writer.newLine();

            for (Area area : all_areas) {
                writer.write(area.get_geo_index() + "," + area.get_associated_region_index());
                writer.newLine();
            }
            writer.newLine();

            // Find and save movable areas information
            writer.write("Movable Areas Analysis:");
            writer.newLine();

            // This holds all movable areas across all regions
            ArrayList<Area> allMovableAreas = new ArrayList<>();

            for (int i = 0; i < regions.length; i++) {
                Region r = regions[i];

                // Use Tarjan algorithm to find articulation points
                ArrayList<Area> articulationPoints = new Tarjan(r, all_areas).findAPs_Tarjan();

                // Identify movable areas (areas on margin that are not articulation points)
                ArrayList<Area> movableAreas = new ArrayList<>(r.getAreas_on_margin());
                movableAreas.removeAll(articulationPoints);

                writer.write("Region " + i + " Movable Areas: ");
                if (movableAreas.isEmpty()) {
                    writer.write("None");
                } else {
                    for (Area area : movableAreas) {
                        writer.write(area.get_geo_index() + " ");
                        allMovableAreas.add(area);
                    }
                }
                writer.newLine();
            }
            writer.newLine();

            // Analyze potential destinations for each movable area
            writer.write("Potential Destinations for Movable Areas:");
            writer.newLine();
            writer.write("AreaID,CurrentRegion,PotentialDestinations");
            writer.newLine();

            for (Area area : allMovableAreas) {
                int currentRegion = area.get_associated_region_index();

                // Find neighboring regions
                HashSet<Integer> neighborRegions = new HashSet<>();
                for (Area neighbor : area.get_neigh_area(all_areas)) {
                    int neighborRegion = neighbor.get_associated_region_index();
                    if (neighborRegion != currentRegion) {
                        neighborRegions.add(neighborRegion);
                    }
                }

                writer.write(area.get_geo_index() + "," + currentRegion + ",");
                if (neighborRegions.isEmpty()) {
                    writer.write("None");
                } else {
                    for (Integer neighborRegion : neighborRegions) {
                        writer.write(neighborRegion + " ");
                    }
                }
                writer.newLine();
            }
        }

        System.out.println("  Saved solution " + solutionNum + " to " + filename);
    }
}