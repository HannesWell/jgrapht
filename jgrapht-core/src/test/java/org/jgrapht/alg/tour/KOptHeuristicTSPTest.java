/*
 * (C) Copyright 2020-2020, by Hannes Wellmann and Contributors.
 *
 * JGraphT : a free Java graph-theory library
 *
 * See the CONTRIBUTORS.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the
 * GNU Lesser General Public License v2.1 or later
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR LGPL-2.1-or-later
 */
package org.jgrapht.alg.tour;

import org.apache.commons.math3.geometry.euclidean.twod.*;
import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.graph.*;
import org.junit.*;
import org.junit.experimental.categories.*;

import java.util.*;
import java.util.stream.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.jgrapht.alg.tour.HamiltonianCycleAlgorithmTests.*;

/**
 * @author Hannes Wellmann
 *
 */
@Category(SlowTests.class)
public class KOptHeuristicTSPTest
{
    private static List<Vector2D> locations;
    private static Graph<Vector2D, DefaultWeightedEdge> graph;
    private static Map<Integer, GraphPath<Vector2D, DefaultWeightedEdge>> k2expectedTour;
    private static GraphPath<Vector2D, DefaultWeightedEdge> initialTour;

    @BeforeClass
    public static void setUpBeforeClass()
        throws Exception
    {
        List<Vector2D> loc = new ArrayList<>();

        loc.add(new Vector2D(468, 781));
        loc.add(new Vector2D(241, 284));
        loc.add(new Vector2D(774, 636));
        loc.add(new Vector2D(74, 416));
        loc.add(new Vector2D(227, 816));
        loc.add(new Vector2D(267, 489));
        loc.add(new Vector2D(302, 365));
        loc.add(new Vector2D(919, 686));
        loc.add(new Vector2D(935, 135));
        loc.add(new Vector2D(515, 544));
        loc.add(new Vector2D(733, 495));
        loc.add(new Vector2D(376, 326));
        loc.add(new Vector2D(534, 971));
        loc.add(new Vector2D(562, 403));
        loc.add(new Vector2D(410, 281));
        loc.add(new Vector2D(638, 950));
        loc.add(new Vector2D(470, 344));
        loc.add(new Vector2D(488, 822));
        loc.add(new Vector2D(436, 99));
        loc.add(new Vector2D(946, 648));

        // FIXME: try to construct a generic hard case for each k

        locations = Collections.unmodifiableList(loc);
        graph = buidCompleteDistancesGraph(loc, (v1, v2) -> v1.distance(v2));

        // initial tour pre-conditioned by NearestNeighbor (to reduce runtime)
        int[] vertexNumberTour =
            { 13, 16, 14, 11, 6, 1, 5, 3, 4, 0, 17, 12, 15, 2, 10, 9, 7, 19, 8, 18 };
        initialTour = vertexNumbersToTour(
            Arrays.stream(vertexNumberTour).filter(i -> i < loc.size()).toArray(), loc, graph);

        // build expected tours:
        // For each of the above specified locations the distances of each edge are different.
        // Therefore for a given initial tour the resulting tour computed is unambiguous.
        Map<Integer, GraphPath<Vector2D, DefaultWeightedEdge>> tours = new HashMap<>();

        // FIXME: check these values.
        int[] tour2 = { 13, 16, 18, 14, 11, 6, 1, 3, 5, 4, 0, 17, 12, 15, 9, 10, 2, 7, 19, 8 };
        tours.put(2, vertexNumbersToTour(tour2, loc, graph)); // length: 4048.7
        int[] tour3 = { 13, 9, 0, 17, 15, 12, 4, 5, 3, 1, 6, 11, 16, 14, 18, 8, 19, 7, 2, 10 };
        tours.put(3, vertexNumbersToTour(tour3, loc, graph)); // length: 3937.7
        int[] tour4 = { 13, 9, 10, 2, 19, 7, 15, 12, 17, 0, 4, 5, 3, 1, 6, 11, 16, 14, 18, 8 };
        tours.put(4, vertexNumbersToTour(tour4, loc, graph)); // length: 3934.5
        int[] tour5 = { 13, 16, 18, 14, 11, 6, 1, 3, 5, 4, 0, 17, 12, 15, 2, 7, 19, 8, 10, 9 };
        tours.put(5, vertexNumbersToTour(tour5, loc, graph)); // length: 3921.9
        int[] tour6 = { 13, 9, 4, 0, 17, 12, 15, 7, 19, 2, 10, 8, 18, 1, 3, 5, 6, 11, 14, 16 };
        tours.put(6, vertexNumbersToTour(tour6, loc, graph)); // length: 3913.7

        k2expectedTour = Collections.unmodifiableMap(tours);
    }

    @Test
    public void testK2Opt()
    {
        int k = 2;
        testKOpt(k);
    }

    @Test
    public void testKTwoOptConsistency()
    {
        int locationCount = 10;
        int graphsToTest = 10;
        int seedsToTest = 5;

        for (int i = 0; i < graphsToTest; i++) {
            Random rng = new Random(stringBytesAsLong("JGraphT" + i));

            List<Vector2D> locations = generateRandomLocations(rng, locationCount);

            Graph<Vector2D, DefaultWeightedEdge> graph =
                buidCompleteDistancesGraph(locations, (v1, v2) -> v1.distance(v2));

            for (int j = 0; j < seedsToTest; j++) {
                long seed = rng.nextLong();

                HamiltonianCycleAlgorithm<Vector2D, DefaultWeightedEdge> twoOpt =
                    new TwoOptHeuristicTSP<>(1, seed);
                HamiltonianCycleAlgorithm<Vector2D, DefaultWeightedEdge> k2Opt =
                    new KOptHeuristicTSP<>(2, 1, seed);

                assertThat(twoOpt.getTour(graph), is(equalTo(k2Opt.getTour(graph))));
            }
        }
    }

    private static List<Vector2D> generateRandomLocations(Random rng, long locationCount)
    {
        return Stream
            .generate(() -> new Vector2D(rng.nextInt(1000), rng.nextInt(1000))).limit(locationCount)
            .collect(Collectors.toList());
    }

    @Test
    public void testK3Opt()
    {
        int k = 3;
        testKOpt(k);
    }

    @Test
    public void testK4Opt()
    {
        int k = 4;
        testKOpt(k);
    }

    @Test
    public void testK5Opt()
    {
        int k = 5;
        testKOpt(k);
    }

    @Test
    public void testK6Opt()
    {
        int k = 6;
        testKOpt(k);
    }

    private static void testKOpt(int k)
    {
        GraphPath<Vector2D, DefaultWeightedEdge> expectedTour = k2expectedTour.get(k);

        // only improve a fixed initial tour to avoid randomness
        GraphPath<Vector2D, DefaultWeightedEdge> tour =
            new KOptHeuristicTSP<Vector2D, DefaultWeightedEdge>(k).improveTour(initialTour);

        assertThat(tour, is(equalTo(expectedTour)));
    }

    // FIXME: create test cases where the special new moves of each k up to 6 need to be performed.
    // Make a List of these cases and check that all algorithms with smaller k cant solve them and
    // all with k or greater do.

    // test data build //FIXME: remove?!

    public static void main(String[] args)
    {
        final long locationCount = 20;
        final int kMax = 6;

        long start = System.currentTimeMillis();

        // ThreadLocal<Random> random = ThreadLocal.withInitial(Random::new);
        Random rng = new Random();
        List<Vector2D> locations;
        while (true) {
            locations = generateRandomLocations(rng, locationCount);
            if (isDistinct(locations, kMax)) {
                break;
            }
        }
        System.out.println("Locations:");
        for (Vector2D location : locations) {
            int x = (int) location.getX();
            int y = (int) location.getY();
            System.out.println("loc.add(new Vector2D(" + x + ", " + y + "));");
        }
        System.out
            .println("Took: " + Math.round((System.currentTimeMillis() - start) / 1000) + "s");
    }

    private static boolean isDistinct(List<Vector2D> locations, int kMax)
    {
        if (new HashSet<>(locations).size() != locations.size()) {
            return false; // locations are equal
        }
        return isDistinct(
            locations, buidCompleteDistancesGraph(locations, (v1, v2) -> v1.distance(v2)), kMax);
    }

    private static <V, E> boolean isDistinct(List<V> vertexList, Graph<V, E> graph, int kMax)
    {

        Set<Double> edgeWeights = graph
            .edgeSet().stream().map(e -> round(graph.getEdgeWeight(e))).collect(Collectors.toSet());

        if (edgeWeights.size() == graph.edgeSet().size()) {

            long seed = stringBytesAsLong("JGraphT");
            HamiltonianCycleAlgorithm<V, E> preConditioner =
                new NearestNeighborHeuristicTSP<>(seed);
            GraphPath<V, E> initialTour = preConditioner.getTour(graph);

            Set<Double> tourWeights = new HashSet<>();
            List<GraphPath<V, E>> tours = new ArrayList<>();

            for (int k = 2; k <= kMax; k++) {
                GraphPath<V, E> tour = new KOptHeuristicTSP<V, E>(k).improveTour(initialTour);
                tours.add(tour);
                double weight = round(tour.getWeight());
                if (!tourWeights.add(weight)) {
                    return false;
                }
            }

            System.out.println("Initial Tour:");
            System.out.println(asIntTourString(initialTour, vertexList));
            System.out.println("Tour weights:");
            for (int i = 0; i < tours.size(); i++) {
                GraphPath<V, E> tour = tours.get(i);
                int k = i + 2;
                String str = "k=" + k + ", weight=" + round(tour.getWeight());
                str += ", tour=" + asIntTourString(tour, vertexList);
                System.out.println(str);
            }
            return true;
        }
        return false;
    }

    private static <V, E> double round(double d)
    {
        return Math.round(d * 10.0) / 10.0;
    }

    private static <V, E> String asIntTourString(GraphPath<V, E> best, List<V> vertexList)
    {
        return Arrays
            .toString(best.getVertexList().stream().mapToInt(vertexList::indexOf).toArray());
    }
}
