package test;

import GSLO.GlobalSearch;
import GSLO.LocalOptimization;
import util.Area;
import util.Preprocess;
import util.Region;

import java.io.IOException;
import java.util.ArrayList;

/**
 * This class tests the performance of the GSLO algorithm
 * on the classic p-regions problem (threshold = 0).
 */
public class ClassicSolution {

    public static void main(String[] args) throws IOException, CloneNotSupportedException, InterruptedException {
        // Config parameters
        int iterGSLO = 100;      // Number of GSLO runs

        // Datasets to test
        String[] datasets = {"50"};

        ArrayList<Area> areas = Preprocess.GeoSetBuilder("50");
        long heterogeneity = 0;

// 计算异质性（所有区域对之间internal_attr的绝对差值之和）
        for (int i = 0; i < areas.size(); i++) {
            for (int j = i + 1; j < areas.size(); j++) {
                heterogeneity += Math.abs(areas.get(i).get_internal_attr() - areas.get(j).get_internal_attr());
            }
        }

        System.out.println("Heterogeneity of 50 areas: " + heterogeneity);

        System.out.println("------------- P-Regions Problem Testing (threshold = 0) -------------");

        for (String dataset : datasets) {
            System.out.println("\nTesting dataset: " + dataset);
            runPRegionsProblem(dataset, iterGSLO);
        }
    }

    /**
     * Runs p-regions problem tests for a given dataset
     *
     * @param dataset The dataset name
     * @param iterGSLO Number of GSLO iterations
     */
    public static void runPRegionsProblem(String dataset, int iterGSLO)
            throws IOException, CloneNotSupportedException, InterruptedException {

        ArrayList<Area> allAreas = Preprocess.GeoSetBuilder(dataset);
        System.out.println("Dataset size: " + allAreas.size() + " areas");

        // Set threshold to 0 for classic p-regions problem
        long threshold = 0;

        // Test with different p values
        for (int p = 3; p <= 5; p += 5) {
            System.out.println("\n----- Testing with p = " + p + " -----");

            // Results storage
            ArrayList<Long> gsloHetero = new ArrayList<>();
            ArrayList<Long> gsloRuntime = new ArrayList<>();
            ArrayList<Long> gsSeeds = new ArrayList<>();
            ArrayList<Long> gsGrowth = new ArrayList<>();
            ArrayList<Long> gsEnclaves = new ArrayList<>();
            ArrayList<Long> gsInterregion = new ArrayList<>();
            ArrayList<Long> gsFlow = new ArrayList<>();
            ArrayList<Long> localOpt = new ArrayList<>();
            int gsloSuccessCount = 0;

            // Run GSLO
            System.out.println("Running GSLO (" + iterGSLO + " iterations)...");
            for (int i = 0; i < iterGSLO; i++) {
                System.out.print("Iteration " + (i+1) + "... ");

                long startTime = System.currentTimeMillis();
                GlobalSearch gs = new GlobalSearch(Area.area_list_copy(allAreas), p, allAreas.size(), threshold, false);

                if (gs.solved()) {
                    System.out.println("SUCCESS");
                    gsloSuccessCount++;

                    // Record phase times
                    gsSeeds.add(gs.getSeed_time());
                    gsGrowth.add(gs.getRegion_growth_time());
                    gsEnclaves.add(gs.getEnclaves_assign_time());

                    if (gs.isInterregion_flag()) {
                        gsInterregion.add(gs.getInterregion_update_time());
                    }

                    if (gs.isFlow_flag()) {
                        gsFlow.add(gs.getIndirect_flow_time());
                    }

                    // Run local optimization
                    long loStartTime = System.currentTimeMillis();
                    LocalOptimization lo = new LocalOptimization(gs, allAreas.size(), 0.99, gs.get_all_areas(), gs.get_regions(), threshold);
                    long loEndTime = System.currentTimeMillis();

                    // Validate solution
                    Region.test_result_correctness(gs.get_regions(), allAreas, threshold, true);

                    // Record results
                    gsloHetero.add(lo.getBest_hetero());
                    gsloRuntime.add(gs.getTotal_running_time() + lo.getTotal_time());
                    localOpt.add(lo.getTotal_time());
                } else {
                    System.out.println("FAILED");
                    gsloRuntime.add(gs.getTotal_running_time());
                }
            }

            // Output results
            System.out.println("\nResults for p = " + p + ":");
            System.out.println("  Success rate: " + gsloSuccessCount + "/" + iterGSLO);

            if (!gsloHetero.isEmpty()) {
                System.out.println("  Avg. heterogeneity: " + computeLongAvg(gsloHetero));
            } else {
                System.out.println("  Avg. heterogeneity: N/A (no successful runs)");
            }

            System.out.println("  Avg. runtime (ms): " + computeLongAvg(gsloRuntime));

            // Output phase timing details (if successful runs exist)
            if (gsloSuccessCount > 0) {
                System.out.println("\n  Phase timing breakdown:");
                System.out.println("    Seed Identification: " + computeLongAvg(gsSeeds) + " ns");
                System.out.println("    Region Growth: " + computeLongAvg(gsGrowth) + " ns");
                System.out.println("    Enclaves Assignment: " + computeLongAvg(gsEnclaves) + " ns");

                if (!gsInterregion.isEmpty()) {
                    System.out.println("    Inter-region Update: " + computeLongAvg(gsInterregion) + " ms");
                }

                if (!gsFlow.isEmpty()) {
                    System.out.println("    Indirect Flow Push: " + computeLongAvg(gsFlow) + " ms");
                }

                System.out.println("    Local Optimization: " + computeLongAvg(localOpt) + " ms");
            }
        }
    }

    /**
     * Computes the average of a list of long values
     *
     * @param values The list of long values
     * @return The average, or -1 if the list is empty
     */
    private static long computeLongAvg(ArrayList<Long> values) {
        if (values.isEmpty()) {
            return -1;
        }

        long sum = 0;
        for (long value : values) {
            sum += value;
        }

        return sum / values.size();
    }
}