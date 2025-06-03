package test;

import GSLO.Tarjan;
import util.Area;
import util.Preprocess;
import util.Region;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.Jsoner;

/**
 * This class calculates and analyzes movable areas in a region-based solution
 */
public class MovableAreaCalculator {
    private ArrayList<Area> allAreas;
    private Region[] regions;
    private long threshold;
    private String jsonAssignmentPath;

    // Maps to store the analysis results
    private Map<Integer, List<Integer>> movableAreas; // Region index -> Movable area indices
    private Map<Integer, List<Integer>> potentialDestinations; // Area index -> Potential destination region indices

    /**
     * Constructor for MovableAreaCalculator
     *
     * @param dataset Name of the dataset to analyze
     * @param threshold Threshold value (0 for this implementation)
     * @param jsonAssignmentPath Path to the JSON file containing region assignments
     * @throws IOException If dataset cannot be loaded
     */
    public MovableAreaCalculator(String dataset, long threshold, String jsonAssignmentPath) throws IOException {
        this.threshold = threshold;
        this.jsonAssignmentPath = jsonAssignmentPath;
        this.allAreas = Preprocess.GeoSetBuilder(dataset);
        initializeRegions();

        // Initialize result maps
        this.movableAreas = new HashMap<>();
        this.potentialDestinations = new HashMap<>();

        for (int i = 0; i < regions.length; i++) {
            movableAreas.put(i, new ArrayList<>());
        }

        // Run the analysis
        findMovableAreas();
        findPotentialDestinations();
    }

    /**
     * Constructor with default paths
     */
    public MovableAreaCalculator(String dataset, long threshold) throws IOException {
        //this(dataset, threshold, "gs_assignment_" + dataset + "_p10_sol0.json");
        this(dataset, threshold, "C:/Users/21065/Desktop/Quantum-Spatial/QuantumDecomposing/Assignment/gs_assignment_5k_p10_sol0.json");
    }

    /**
     * Initialize regions based on assignments from JSON file
     */
    private void initializeRegions() throws IOException {
        // Read region assignments from the JSON file
        File jsonFile = new File(jsonAssignmentPath);
        if (!jsonFile.exists()) {
            throw new IOException("Assignment JSON file not found: " + jsonAssignmentPath);
        }

        try (FileReader reader = new FileReader(jsonFile)) {
            JsonObject root = (JsonObject) Jsoner.deserialize(reader);

            // Get p value (number of regions)
            int pValue = ((Number) root.get("p_value")).intValue();

            // Get region assignments
            JsonObject assignments = (JsonObject) root.get("region_assignments");

            // Create map from area index to region index
            Map<Integer, Integer> areaToRegionMap = new HashMap<>();
            for (int regionIndex = 0; regionIndex < pValue; regionIndex++) {
                JsonArray areas = (JsonArray) assignments.get(Integer.toString(regionIndex));
                if (areas != null) {
                    for (Object areaObj : areas) {
                        int areaIndex = ((Number) areaObj).intValue();
                        areaToRegionMap.put(areaIndex, regionIndex);
                    }
                }
            }

            // Assign each area to its region
            for (Area area : allAreas) {
                int areaIndex = area.get_geo_index();
                if (areaToRegionMap.containsKey(areaIndex)) {
                    area.set_region(areaToRegionMap.get(areaIndex));
                } else {
                    area.set_region(-1); // Unassigned
                }
            }

            // Create regions array
            regions = new Region[pValue];

            // Initialize regions and add areas to them
            for (int i = 0; i < regions.length; i++) {
                for (Area area : allAreas) {
                    if (area.get_associated_region_index() == i) {
                        regions[i] = new Region(i, area, threshold, allAreas);
                        break;
                    }
                }

                // Add remaining areas to their regions
                for (Area area : allAreas) {
                    if (area.get_associated_region_index() == i &&
                            !regions[i].get_areas_in_region().contains(area)) {
                        regions[i].add_area_to_region(area);
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Error parsing assignment JSON file: " + e.getMessage(), e);
        }
    }


    /**
     * Find all movable areas in each region (FIXED VERSION)
     */
    private void findMovableAreas() {
        for (int i = 0; i < regions.length; i++) {
            Region region = regions[i];

            // Get areas on the margin (potential movable candidates)
            ArrayList<Area> areasOnMargin = region.getAreas_on_margin();

            // Find articulation points (areas that would disconnect the region if removed)
            ArrayList<Area> articulationPoints = new Tarjan(region, allAreas).findAPs_Tarjan();

            // Use a Set to avoid duplicates
            Set<Integer> movableAreaSet = new HashSet<>();

            // Areas on margin that aren't articulation points and meet threshold requirements are movable
            for (Area area : areasOnMargin) {
                if (!articulationPoints.contains(area) &&
                        (region.get_region_extensive_attr() - area.get_extensive_attr() >= threshold || threshold == 0) &&
                        region.get_region_size() > 1) {

                    // Add to Set to prevent duplicates
                    movableAreaSet.add(area.get_geo_index());
                }
            }

            // Convert Set to List and add to movableAreas map
            movableAreas.put(i, new ArrayList<>(movableAreaSet));
        }
    }

    /**
     * Find potential destination regions for all areas
     */
    private void findPotentialDestinations() {
        // Collect all movable areas
        Set<Integer> allMovableAreaIndices = new HashSet<>();
        for (List<Integer> areaIndices : movableAreas.values()) {
            allMovableAreaIndices.addAll(areaIndices);
        }

        // For each movable area in the dataset
        for (Area area : allAreas) {
            int areaIndex = area.get_geo_index();
            // Skip if not a movable area
            if (!allMovableAreaIndices.contains(areaIndex)) {
                continue;
            }

            int currentRegion = area.get_associated_region_index();
            List<Integer> destinations = new ArrayList<>();

            // Check neighbors to find potential destination regions
            for (Area neighbor : area.get_neigh_area(allAreas)) {
                int neighborRegion = neighbor.get_associated_region_index();
                if (neighborRegion != currentRegion && !destinations.contains(neighborRegion)) {
                    destinations.add(neighborRegion);
                }
            }

            // Add -1 to indicate "no specific region" or "can't be moved"
            destinations.add(-1);
            potentialDestinations.put(areaIndex, destinations);
        }
    }

    /**
     * Calculate mean value of all areas in each region
     */
    private Map<Integer, Double> calculateRegionMeans() {
        Map<Integer, Double> means = new HashMap<>();

        for (int i = 0; i < regions.length; i++) {
            Region region = regions[i];
            ArrayList<Area> areas = region.get_areas_in_region();

            if (areas.isEmpty()) {
                means.put(i, 0.0);
                continue;
            }

            double sum = 0;
            for (Area area : areas) {
                sum += area.get_internal_attr();
            }
            means.put(i, sum / areas.size());
        }

        return means;
    }

    /**
     * Calculate variance of internal attributes for all areas in each region
     */
    private Map<Integer, Double> calculateRegionVariances() {
        Map<Integer, Double> variances = new HashMap<>();
        Map<Integer, Double> means = calculateRegionMeans();

        for (int i = 0; i < regions.length; i++) {
            Region region = regions[i];
            ArrayList<Area> areas = region.get_areas_in_region();

            if (areas.isEmpty() || areas.size() == 1) {
                variances.put(i, 0.0);
                continue;
            }

            double mean = means.get(i);
            double sumSquaredDiff = 0;

            for (Area area : areas) {
                double diff = area.get_internal_attr() - mean;
                sumSquaredDiff += diff * diff;
            }

            // Using sample variance (divide by n-1)
            variances.put(i, sumSquaredDiff / (areas.size() - 1));
        }

        return variances;
    }

    /**
     * Print the analysis results in the required format
     */
    public void printResults() {
        // Print number of areas in each region
        System.out.println("number_of_areas = {");
        for (int i = 0; i < regions.length; i++) {
            System.out.println("  " + i + ": [" + regions[i].get_region_size() + "],");
        }
        System.out.println("}");

        // Print mean values of all areas in each region
        Map<Integer, Double> means = calculateRegionMeans();
        System.out.println("region_areas_means = {");
        for (int i = 0; i < regions.length; i++) {
            System.out.println("  " + i + ": [" + means.get(i) + "],");
        }
        System.out.println("}");

        // Print variance values of all areas in each region
        Map<Integer, Double> variances = calculateRegionVariances();
        System.out.println("region_variances = {");
        for (int i = 0; i < regions.length; i++) {
            System.out.println("  " + i + ": [" + variances.get(i) + "],");
        }
        System.out.println("}");

        // Print movable areas for each region
        System.out.println("movable_areas = {");
        for (int i = 0; i < regions.length; i++) {
            System.out.print("  " + i + ": [");
            List<Integer> areas = movableAreas.get(i);
            for (int j = 0; j < areas.size(); j++) {
                System.out.print(areas.get(j));
                if (j < areas.size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println("],");
        }
        System.out.println("}");

        // Print internal attributes of movable areas for each region
        System.out.println("movable_areas_attribute = {");
        for (int i = 0; i < regions.length; i++) {
            System.out.print("  " + i + ": [");
            List<Integer> areaIndices = movableAreas.get(i);
            for (int j = 0; j < areaIndices.size(); j++) {
                // Find the area and get its internal attribute
                for (Area area : allAreas) {
                    if (area.get_geo_index() == areaIndices.get(j)) {
                        System.out.print(area.get_internal_attr());
                        break;
                    }
                }
                if (j < areaIndices.size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println("],");
        }
        System.out.println("}");

        // Print potential destinations for movable areas
        System.out.println("potential_destinations = {");
        List<Integer> sortedAreaIndices = new ArrayList<>(potentialDestinations.keySet());
        Collections.sort(sortedAreaIndices);
        for (int areaIndex : sortedAreaIndices) {
            System.out.print("  " + areaIndex + ": [");
            List<Integer> destinations = potentialDestinations.get(areaIndex);
            for (int j = 0; j < destinations.size(); j++) {
                System.out.print(destinations.get(j));
                if (j < destinations.size() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println("],");
        }
        System.out.println("}");
    }

    /**
     * Creates a JSON object containing all the analysis results
     */
    @SuppressWarnings("unchecked")
    public JsonObject createJsonResults() {
        JsonObject results = new JsonObject();

        // Number of areas in each region
        JsonObject numAreas = new JsonObject();
        for (int i = 0; i < regions.length; i++) {
            JsonArray regionSize = new JsonArray();
            regionSize.add(Integer.valueOf(regions[i].get_region_size()));
            numAreas.put(String.valueOf(i), regionSize);
        }
        results.put("number_of_areas", numAreas);

        // Mean values of areas in each region
        JsonObject regionMeans = new JsonObject();
        Map<Integer, Double> means = calculateRegionMeans();
        for (int i = 0; i < regions.length; i++) {
            JsonArray meanArray = new JsonArray();
            meanArray.add(means.get(i));
            regionMeans.put(String.valueOf(i), meanArray);
        }
        results.put("region_areas_means", regionMeans);

        // Variance values of areas in each region
        JsonObject regionVariances = new JsonObject();
        Map<Integer, Double> variances = calculateRegionVariances();
        for (int i = 0; i < regions.length; i++) {
            JsonArray varianceArray = new JsonArray();
            varianceArray.add(variances.get(i));
            regionVariances.put(String.valueOf(i), varianceArray);
        }
        results.put("region_variances", regionVariances);

        // Movable areas for each region
        JsonObject movableAreasJson = new JsonObject();
        for (int i = 0; i < regions.length; i++) {
            JsonArray areasList = new JsonArray();
            List<Integer> areas = movableAreas.get(i);
            for (Integer area : areas) {
                areasList.add(area);
            }
            movableAreasJson.put(String.valueOf(i), areasList);
        }
        results.put("movable_areas", movableAreasJson);

        // Internal attributes of movable areas for each region
        JsonObject movableAreasAttribute = new JsonObject();
        for (int i = 0; i < regions.length; i++) {
            JsonArray attributeList = new JsonArray();
            List<Integer> areaIndices = movableAreas.get(i);
            for (Integer areaIndex : areaIndices) {
                // Find the area and get its internal attribute
                for (Area area : allAreas) {
                    if (area.get_geo_index() == areaIndex) {
                        attributeList.add(area.get_internal_attr());
                        break;
                    }
                }
            }
            movableAreasAttribute.put(String.valueOf(i), attributeList);
        }
        results.put("movable_areas_attribute", movableAreasAttribute);

        // Potential destinations for movable areas
        JsonObject potentialDestsJson = new JsonObject();
        List<Integer> sortedAreaIndices = new ArrayList<>(potentialDestinations.keySet());
        Collections.sort(sortedAreaIndices);
        for (int areaIndex : sortedAreaIndices) {
            JsonArray destsList = new JsonArray();
            List<Integer> destinations = potentialDestinations.get(areaIndex);
            for (Integer dest : destinations) {
                destsList.add(dest);
            }
            potentialDestsJson.put(String.valueOf(areaIndex), destsList);
        }
        results.put("potential_destinations", potentialDestsJson);

        return results;
    }

    /**
     * Save the analysis results to a JSON file
     *
     * @param filename The name of the file to save to
     * @throws IOException If there's an error writing to the file
     */
    public void saveToJson(String filename) throws IOException {
        JsonObject results = createJsonResults();

        // Create the output directory if it doesn't exist
        File file = new File(filename);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // Write the JSON to file
        try (Writer writer = new FileWriter(filename)) {
            writer.write(results.toJson());
        }

        System.out.println("Results saved to " + filename);
    }

    /**
     * Main method to test the calculator with a dataset
     */
    public static void main(String[] args) {
        String dataset = "5k";
        long threshold = 0;
        String outputFile = "C:/Users/21065/Desktop/Quantum-Spatial/QuantumDecomposing/Assignment/movable_areas_data_5k.json";
        String jsonAssignmentPath = null;
        int p =10;

        // Parse command line arguments if provided
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if ("-d".equals(args[i]) || "--dataset".equals(args[i])) {
                    if (i+1 < args.length) {
                        dataset = args[++i];
                    }
                } else if ("-t".equals(args[i]) || "--threshold".equals(args[i])) {
                    if (i+1 < args.length) {
                        threshold = Long.parseLong(args[++i]);
                    }
                } else if ("-o".equals(args[i]) || "--output".equals(args[i])) {
                    if (i+1 < args.length) {
                        outputFile = args[++i];
                    }
                } else if ("-j".equals(args[i]) || "--json".equals(args[i])) {
                    if (i+1 < args.length) {
                        jsonAssignmentPath = args[++i];
                    }
                } else if ("-h".equals(args[i]) || "--help".equals(args[i])) {
                    System.out.println("Usage: java MovableAreaCalculator [options]");
                    System.out.println("Options:");
                    System.out.println("  -d, --dataset DATASET    Specify the dataset (default: 500)");
                    System.out.println("  -t, --threshold VALUE    Specify the threshold value (default: 0)");
                    System.out.println("  -o, --output FILE        Specify the output JSON file (default: output/movable_areas_data.json)");
                    System.out.println("  -j, --json FILE          Specify the input JSON assignment file (default: gs_assignment_DATASET_p10_sol0.json)");
                    System.out.println("  -h, --help               Show this help message");
                    return;
                }
            }
        }

        System.out.println("Dataset: " + dataset);
        System.out.println("Threshold: " + threshold);
        System.out.println("Output file: " + outputFile);

        if (jsonAssignmentPath != null) {
            System.out.println("Assignment JSON: " + jsonAssignmentPath);
        } else {
            //jsonAssignmentPath = "gs_assignment_" + dataset + "_p"+p+"_sol0.json";
            jsonAssignmentPath = "C:/Users/21065/Desktop/Quantum-Spatial/QuantumDecomposing/Assignment/gs_assignment_5k_p10_sol0.json";
            System.out.println("Assignment JSON: " + jsonAssignmentPath + " (default)");
        }

        try {
            // Create and run the calculator
            MovableAreaCalculator calculator = new MovableAreaCalculator(dataset, threshold, jsonAssignmentPath);

            // Print results to console
            calculator.printResults();

            // Save results to JSON file
            calculator.saveToJson(outputFile);

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.err.println("Error parsing numeric argument: " + e.getMessage());
        }
    }
}