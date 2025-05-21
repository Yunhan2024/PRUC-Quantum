package test;

import GSLO.Tarjan;
import util.Area;
import util.Preprocess;
import util.Region;

import java.io.IOException;
import java.util.*;

/**
 * This class calculates and analyzes movable areas in a region-based solution
 */
public class MovableAreaCalculator {
    private ArrayList<Area> allAreas;
    private Region[] regions;
    private long threshold;

    // Maps to store the analysis results
    private Map<Integer, List<Integer>> movableAreas; // Region index -> Movable area indices
    private Map<Integer, List<Integer>> fixedAreas;   // Region index -> Fixed area indices
    private Map<Integer, List<Integer>> potentialDestinations; // Area index -> Potential destination region indices

    /**
     * Constructor for MovableAreaCalculator
     *
     * @param dataset Name of the dataset to analyze
     * @param threshold Threshold value (0 for this implementation)
     * @throws IOException If dataset cannot be loaded
     */
    public MovableAreaCalculator(String dataset, long threshold) throws IOException {
        this.threshold = threshold;
        this.allAreas = Preprocess.GeoSetBuilder(dataset);
        initializeRegions();

        // Initialize result maps
        this.movableAreas = new HashMap<>();
        this.fixedAreas = new HashMap<>();
        this.potentialDestinations = new HashMap<>();

        for (int i = 0; i < regions.length; i++) {
            movableAreas.put(i, new ArrayList<>());
            fixedAreas.put(i, new ArrayList<>());
        }

        // Run the analysis
        findMovableAreas();
        findFixedAreas();
        findPotentialDestinations();
    }

    /**
     * Initialize regions based on the predefined assignment
     */
    private void initializeRegions() {
        // Define the region assignments as provided
        int[][] regionAssignments = {
                // Region 0 (20 areas)
                {352, 258, 263, 266, 354, 360, 361, 362, 364, 365, 474, 475, 480, 481, 482, 483, 484, 485, 486, 487},

                // Region 1 (5 areas)
                {499, 377, 387, 388, 498},

                // Region 2 (26 areas)
                {82, 45, 46, 48, 49, 50, 51, 72, 83, 109, 110, 121, 122, 141, 145, 168, 178, 179, 180, 181, 182, 216, 217, 243, 244, 245},

                // Region 3 (292 areas)
                {21, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 47, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 73, 74, 75, 76, 77, 78, 79, 80, 81, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 142, 143, 144, 146, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 169, 170, 172, 173, 174, 175, 176, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 218, 219, 220, 221, 222, 226, 227, 228, 229, 230, 231, 232, 233, 234, 235, 236, 237, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255, 256, 257, 259, 261, 268, 270, 271, 272, 273, 274, 275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 285, 286, 287, 289, 290, 291, 292, 294, 296, 297, 298, 299, 304, 305, 306, 307, 308, 310, 311, 312, 313, 314, 315, 318, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 369, 370, 376, 379, 381, 382, 383, 385, 386, 390, 391, 392, 393, 399, 403, 404, 406, 0, 288, 309},

                // Region 4 (23 areas)
                {437, 345, 346, 347, 348, 407, 438, 439, 440, 441, 442, 443, 444, 447, 449, 450, 451, 452, 453, 454, 455, 457, 405},

                // Region 5 (37 areas)
                {262, 198, 199, 260, 264, 265, 267, 269, 356, 357, 358, 359, 363, 366, 367, 368, 371, 372, 373, 374, 375, 378, 380, 384, 477, 478, 479, 488, 489, 490, 491, 492, 493, 494, 495, 496, 497},

                // Region 6 (15 areas)
                {462, 349, 445, 446, 456, 458, 459, 460, 461, 463, 464, 465, 466, 467, 468},

                // Region 7 (6 areas)
                {395, 293, 332, 333, 394, 436},

                // Region 8 (11 areas)
                {470, 350, 351, 353, 355, 448, 469, 471, 472, 473, 476},

                // Region 9 (65 areas)
                {224, 171, 177, 223, 225, 238, 239, 240, 241, 242, 295, 300, 301, 302, 303, 316, 317, 319, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 396, 397, 398, 400, 401, 402, 408, 409, 410, 411, 412, 413, 414, 415, 416, 417, 419, 420, 421, 422, 423, 424, 425, 426, 427, 429, 430, 431, 432, 433, 434, 435, 320, 389, 418, 428}
        };

        // Create map from area index to region index
        Map<Integer, Integer> areaToRegionMap = new HashMap<>();
        for (int regionIndex = 0; regionIndex < regionAssignments.length; regionIndex++) {
            for (int areaIndex : regionAssignments[regionIndex]) {
                areaToRegionMap.put(areaIndex, regionIndex);
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
        regions = new Region[regionAssignments.length];

        // Initialize each region with its first area
        for (int i = 0; i < regions.length; i++) {
            for (Area area : allAreas) {
                if (area.get_associated_region_index() == i) {
                    regions[i] = new Region(i, area, threshold, allAreas);
                    break;
                }
            }
        }

        // Add remaining areas to their regions
        for (Area area : allAreas) {
            int regionIndex = area.get_associated_region_index();
            if (regionIndex >= 0 && regionIndex < regions.length) {
                if (!regions[regionIndex].get_areas_in_region().contains(area)) {
                    regions[regionIndex].add_area_to_region(area);
                }
            }
        }
    }

    /**
     * Find all movable areas in each region
     */
    private void findMovableAreas() {
        for (int i = 0; i < regions.length; i++) {
            Region region = regions[i];

            // Get areas on the margin (potential movable candidates)
            ArrayList<Area> areasOnMargin = region.getAreas_on_margin();

            // Find articulation points (areas that would disconnect the region if removed)
            ArrayList<Area> articulationPoints = new Tarjan(region, allAreas).findAPs_Tarjan();

            // Areas on margin that aren't articulation points and meet threshold requirements are movable
            for (Area area : areasOnMargin) {
                if (!articulationPoints.contains(area) &&
                        (region.get_region_extensive_attr() - area.get_extensive_attr() >= threshold || threshold == 0) &&
                        region.get_region_size() > 1) {
                    movableAreas.get(i).add(area.get_geo_index());
                }
            }
        }
    }

    /**
     * Find fixed areas (non-movable) in each region
     */
    private void findFixedAreas() {
        for (int i = 0; i < regions.length; i++) {
            Region region = regions[i];
            List<Integer> movable = movableAreas.get(i);

            // All areas in region that are not movable are fixed
            for (Area area : region.get_areas_in_region()) {
                if (!movable.contains(area.get_geo_index())) {
                    fixedAreas.get(i).add(area.get_geo_index());
                }
            }
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

        // For each area in the dataset
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
     * Calculate mean value of fixed areas in each region
     */
    private Map<Integer, Double> calculateFixedAreasMeans() {
        Map<Integer, Double> means = new HashMap<>();

        for (int regionIndex : fixedAreas.keySet()) {
            List<Integer> areas = fixedAreas.get(regionIndex);
            if (areas.isEmpty()) {
                means.put(regionIndex, 0.0);
                continue;
            }

            double sum = 0;
            for (int areaIndex : areas) {
                // Using internal attribute for the mean calculation
                sum += allAreas.get(areaIndex).get_internal_attr();
            }
            means.put(regionIndex, sum / areas.size());
        }

        return means;
    }

    /**
     * Print the analysis results in the required format
     */
    public void printResults() {
        // Print number of fixed areas in each region
        System.out.println("number_of_areas= {");
        for (int i = 0; i < regions.length; i++) {
            System.out.println("  " + i + ": [" + regions[i].get_region_size() + "],");
        }
        System.out.println("}");

        // Print mean values of fixed areas in each region
        Map<Integer, Double> means = calculateFixedAreasMeans();
        System.out.println("region_areas_means = {");
        for (int i = 0; i < regions.length; i++) {
            System.out.println("  " + i + ":[" + means.get(i) + "]");
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
     * Main method to test the calculator with a dataset
     */
    public static void main(String[] args) {
        try {
            // Create and run the calculator
            MovableAreaCalculator calculator = new MovableAreaCalculator("2k", 0);
            calculator.printResults();
        } catch (IOException e) {
            System.err.println("Error loading dataset: " + e.getMessage());
            e.printStackTrace();
        }
    }
}