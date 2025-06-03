package test;

import GSLO.GlobalSearch;
import util.Area;
import util.Preprocess;
import util.Region;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Driver program that executes GSLO.GlobalSearch several times on a chosen dataset, records the
 * performance metrics, dumps human‑readable CSV/TXT summaries **and** outputs the region–area
 * assignment in a machine‑readable JSON file that MovableAreaCalculator can later consume.
 *
 * <p>Usage example:</p>
 * <pre>
 *   java test.GlobalSolution -d 2k -p 10 -iter 5 -o out/
 * </pre>
 */
public class GlobalSolution {

    /* ---------------- CLI defaults ---------------- */
    private static String DATASET      = "5k";   // folder or id understood by Preprocess
    private static int    P_VALUE      = 10;       // number of regions to create
    private static int    ITERATIONS   = 10;       // how many independent runs
    private static long   THRESHOLD    = 0;        // capacity threshold (0 == ignore)
    private static String OUTPUT_DIR   = "gs_test_results";

    public static void main(String[] args) throws Exception {
        parseArgs(args);
        ensureOutputDir();

        // ---------------- load dataset once ----------------
        ArrayList<Area> allAreas = Preprocess.GeoSetBuilder(DATASET);
        System.out.printf("Loaded dataset %s (%d areas)\n", DATASET, allAreas.size());

        int success = 0;
        double sumRuntimeMs = 0;
        double sumHetero   = 0;

        for (int run = 0; run < ITERATIONS; run++) {
            System.out.printf("==== Run %d/%d ====%n", run + 1, ITERATIONS);

            // deep copy so each GlobalSearch has its own objects
            ArrayList<Area> copy = deepCopyAreaList(allAreas);

            Instant t0 = Instant.now();
            GlobalSearch solver = new GlobalSearch(copy, P_VALUE, copy.size(), THRESHOLD, /*verbose=*/false);
            boolean solved = solver.solved();
            Instant t1 = Instant.now();
            double ms   = Duration.between(t0, t1).toMillis();

            System.out.printf("   solved? %s   runtime = %.1f ms%n", solved, ms);

            if (solved) {
                success++;
                sumRuntimeMs += ms;

                double hetero = Region.get_all_region_hetero(solver.get_regions());
                sumHetero += hetero;

                // ---------- persist results ----------
                saveGlobalSearchSolution(run, solver, hetero, ms);
                saveAssignmentAsJson(run, solver);
            }
        }

        if (success > 0) {
            System.out.printf("\nSUCCESS %d/%d  |  avgRuntime = %.1f ms  |  avgHetero = %.3f\n",
                    success, ITERATIONS, sumRuntimeMs / success, sumHetero / success);
        } else {
            System.out.println("No successful GlobalSearch solutions in the given runs!");
        }
    }

    /* ===================================================== */
    /* ==================  I/O utilities  ================== */
    /* ===================================================== */

    private static void saveGlobalSearchSolution(int id, GlobalSearch sol, double hetero, double runtimeMs) throws IOException {
        String name = String.format("%s/gs_solution_%s_p%d_%d.txt", OUTPUT_DIR, DATASET, P_VALUE, id);
        try (PrintWriter pw = new PrintWriter(new FileWriter(name))) {
            for (Region r : sol.get_regions()) {
                pw.printf("Region %d (%d areas): ", r.get_region_index(), r.get_areas_in_region().size());
                for (Area a : r.get_areas_in_region()) pw.print(a.get_geo_index() + " ");
                pw.println();
            }
            pw.printf("Total heterogeneity: %.3f%nRuntime (ms): %.1f%n", hetero, runtimeMs);
        }
        System.out.println("   > saved human log to " + name);
    }

    /** Dumps only the mapping needed by MovableAreaCalculator. */
    private static void saveAssignmentAsJson(int id, GlobalSearch sol) throws IOException {
        String path = String.format("%s/gs_assignment_%s_p%d_sol%d.json", OUTPUT_DIR, DATASET, P_VALUE, id);

        JsonObject root = new JsonObject();
        root.put("dataset", DATASET);
        root.put("p_value", P_VALUE);

        JsonObject assign = new JsonObject();
        for (Region r : sol.get_regions()) {
            JsonArray list = new JsonArray();
            for (Area a : r.get_areas_in_region()) list.add(a.get_geo_index());
            assign.put(Integer.toString(r.get_region_index()), list);
        }
        root.put("region_assignments", assign);

        try (Writer w = new FileWriter(path)) {
            w.write(root.toJson());
        }
        System.out.println("   > saved JSON assignment to " + path);
    }

    /* ===================================================== */
    /* =================  helper functions  ================= */
    /* ===================================================== */

    private static ArrayList<Area> deepCopyAreaList(List<Area> src) {
        // A shallow copy is sufficient because GlobalSearch only *reads* Area attributes and
        // attaches them to Region lists; it never mutates the Area objects themselves.
        // If you later introduce in‑place mutation, replace this with a proper copy constructor
        // or clone() implementation inside util.Area.
        return new ArrayList<>(src);
    }

    private static void ensureOutputDir() {
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Unable to create output directory " + OUTPUT_DIR);
        }
    }

    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-d": case "--dataset":
                    DATASET = args[++i];
                    break;
                case "-p": case "--pvalue":
                    P_VALUE = Integer.parseInt(args[++i]);
                    break;
                case "-iter": case "--iterations":
                    ITERATIONS = Integer.parseInt(args[++i]);
                    break;
                case "-o": case "--output":
                    OUTPUT_DIR = args[++i];
                    break;
                case "-t": case "--threshold":
                    THRESHOLD = Long.parseLong(args[++i]);
                    break;
                default:
                    System.err.println("Unknown arg: " + args[i]);
            }
        }
    }
}
