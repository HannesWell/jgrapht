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

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.*;
import org.jgrapht.util.*;

import java.util.*;
import java.util.function.*;

//FIXME: complete doc. See TwoOpt. Maybe find another paper.
/**
 * The tour is split into k segments. These segments are re-combined in all possible ways which
 * includes reordering the segments and reverse of segments.
 *
 * Runtime behavior is {@code n^k}, so for high values of {@code k} the runtime can be high even for
 * small cardinality instances. The memory consumption is {@code n^2}. In favor of the runtime, it's
 * highly recommended to provide initial tours that is preconditions.
 * {@link NearestNeighborHeuristicTSP} is a good choice. Also consider using
 * {@link IncrementalKOptHeuristicTSP} instead of this class especially for greater values of
 * {@code k}.
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author Hannes Wellmann
 */
public class KOptHeuristicTSP<V, E>
    extends
    TwoOptHeuristicTSP<V, E>
{
    protected final int k;
    private final int k2;
    private final List<int[]> combinations;

    /**
     * Constructor. By default one initial random tour is used.
     *
     * @param k the number of edges to consider in each iteration
     */
    public KOptHeuristicTSP(int k)
    {
        this(k, 1, new Random());
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial random tours to check
     */
    public KOptHeuristicTSP(int k, int passes)
    {
        this(k, passes, new Random());
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial random tours to check
     * @param seed seed for the random number generator
     */
    public KOptHeuristicTSP(int k, int passes, long seed)
    {
        this(k, passes, new Random(seed));
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial random tours to check
     * @param rng random number generator
     */
    public KOptHeuristicTSP(int k, int passes, Random rng)
    {
        this(k, passes, new RandomTourTSP<>(rng));
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial random tours to check
     * @param rng random number generator
     * @param minCostImprovement Minimum cost improvement per iteration
     */
    public KOptHeuristicTSP(int k, int passes, Random rng, double minCostImprovement)
    {
        this(k, passes, new RandomTourTSP<>(rng), minCostImprovement);
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param initializer Algorithm to generate initial tour
     */
    public KOptHeuristicTSP(int k, HamiltonianCycleAlgorithm<V, E> initializer)
    {
        this(k, 1, initializer);
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial tours to check
     * @param initializer Algorithm to generate initial tour
     */
    public KOptHeuristicTSP(int k, int passes, HamiltonianCycleAlgorithm<V, E> initializer)
    {
        this(k, passes, initializer, DEFAULT_MIN_COST_IMPROVEMENT);
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial tours to check
     * @param initializer Algorithm to generate initial tours
     * @param minCostImprovement Minimum cost improvement per iteration
     */
    public KOptHeuristicTSP(
        int k, int passes, HamiltonianCycleAlgorithm<V, E> initializer, double minCostImprovement)
    {
        super(passes, initializer, minCostImprovement);
        if (k < 2) {
            throw new IllegalArgumentException("k must be at least two");
        }
        this.k = k;
        this.k2 = 2 * k;
        combinations = getTourSegmentCombinations(NORMALIZED_COMBINATIONS, k);
    }

    KOptHeuristicTSP(int k, double minCostImprovement) // only used by IncrementalKOptHeuristicTSP
    {
        super(minCostImprovement);
        this.k = k;
        this.k2 = 2 * k;
        combinations = getTourSegmentCombinations(PURE_NORMALIZED_COMBINATIONS, k);
    }

    // algorithm

    @Override
    protected void init(Graph<V, E> graph)
    {
        if (graph.vertexSet().size() < k) {
            throw new IllegalArgumentException("k must not be greater than the number of vertices");
        }
        super.init(graph);
    }

    // FIXME: actually up to three vertices the TSP is a trivial problem and could be solved in
    // the base class (maybe not in palmer, but for true TSP algorithms)

    // picture:
    // break a given tour into k segments by cutting the closed tour at k edges.
    // Then recombine all segments in all possible combinations (reorder and reverse are possible)
    // and pick the one recombination that results in the shortest new tour.

    // FIXME: double check every thing !!!
    // print all indices

    private Consumer<int[]> stepApplicationCallback = null;

    public void setStepApplicationCallback(Consumer<int[]> stepApplicationCallback)
    {
        this.stepApplicationCallback = stepApplicationCallback;
    }

    private void showTour(int[] tour) // TODO: better name
    {
        if (stepApplicationCallback != null) {
            stepApplicationCallback.accept(tour);
        }
    }

    @Override
    protected int[] improve(int[] tour)
    {
        final int[] baseCombination = combinations.get(0);
        final List<int[]> tourSegmentRecombinations = combinations.subList(1, combinations.size());

        // Indices of the edges about to break in the tour.
        final int[] tourEdgeIndices = new int[k];
        // the tourEdgeIndices of the best move.
        final int[] bestIndices = new int[k];
        // utility to store the vertex (index) of each segmentBound (index).
        final int[] segmentBound2vertex = new int[k2];

        int[] newTour = new int[n + 1];

        showTour(tour);

        while (true) {
            double minChange = -minCostImprovement;
            int[] bestCombination = null;

            for (initializeIndices(tourEdgeIndices); incrementIndices(tourEdgeIndices);) {

                computeSegment2vertexIndexMapping(segmentBound2vertex, tourEdgeIndices, tour);

                double baseCost =
                    computeSegmentCombinationCost(baseCombination, segmentBound2vertex);

                for (int[] combination : tourSegmentRecombinations) {

                    double cost = computeSegmentCombinationCost(combination, segmentBound2vertex);
                    double change = cost - baseCost;
                    if (change < minChange) { // improvement found -> save it
                        minChange = change;
                        bestCombination = combination;
                        System.arraycopy(tourEdgeIndices, 0, bestIndices, 0, k);
                    }
                }
            }

            if (bestCombination == null) {
                return tour; // no improvement found -> terminate.
            }

            // improvement found -> apply move

            int[] tourSegmentBoundaries = segmentBound2vertex; // reuse array
            computeTourSegmentsBoundaries(bestCombination, bestIndices, tourSegmentBoundaries);

            applyMove(tourSegmentBoundaries, tour, newTour);
            int[] tmp = newTour; // swap tour and newTour reference
            newTour = tour;
            tour = tmp;

            showTour(tour);
        }
    }

    // index handling

    // FIXME: explain it better
    // I think it is, since a one vertex segment could also be a useful move. Only for 2-opt is
    // unnecessary because it would only reverse the tour direction with the single vertex segment's
    // vertex as anchor.
    // If it's settled explain it, and why its different to 2-opt.

    // only cut edge from last to first if edge from first to second is not cut too.
    // this is forgotten in TwoOptHeuristic?! double check it. It was not forgotten, it
    // was always the case like i0!=0 were true was used. This is not wrong but it is useless
    // since it is like going a circular tour clockwise instead of counterclockwise.

    private static void initializeIndices(int[] indices)
    {
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        indices[indices.length - 1] -= 1; // compensate first forward
    }

    private boolean incrementIndices(int[] indices)
    {
        // fast path for highest index
        if (++indices[k - 1] < n) {
            return true;
        }
        for (int i = k - 2, limit = n - 1; i >= 0; --i, --limit) {
            if (++indices[i] < limit) {
                for (int j = i + 1; j < k; j++) { // restart all higher indices
                    indices[j] = indices[j - 1] + 1;
                }
                return true;
            }
        }
        return false;
    }

    private void computeSegment2vertexIndexMapping(
        int[] segmentBound2vertex, int[] indices, int[] tour)
    {
        for (int i = 0; i < k; i++) {
            segmentBound2vertex[2 * i] = tour[indices[i]]; // end of segment i
            segmentBound2vertex[2 * i + 1] = tour[indices[i] + 1]; // start of segment i+1
        }
    }

    private void computeTourSegmentsBoundaries(
        int[] combination, int[] indices, int[] boundaryIndices)
    {
        for (int i = 0; i < k2; i++) {
            boundaryIndices[i] = segment2tourIndex(combination[i], indices);
        }
    }

    private static int segment2tourIndex(int segmentIndex, int[] tourEdgeIndices)
    {
        // tourEdgeIndices[segmentIndex / 2] + (segmentIndex % 2)
        return tourEdgeIndices[segmentIndex >> 1] + (segmentIndex & 1);
    }

    // cost computation

    private double computeSegmentCombinationCost(int[] combination, int[] segmentBound2vertex)
    {
        double cost = 0;
        for (int i = 0; i < k2; i += 2) {

            int segmentEndIndex = combination[i]; // index of the normalized segment vertices
            int nextSegmentStartIndex = combination[i + 1];

            int edgeSourceVertex = segmentBound2vertex[segmentEndIndex];
            int edgeTargetVertex = segmentBound2vertex[nextSegmentStartIndex];

            cost += dist[edgeSourceVertex][edgeTargetVertex];
        }
        return cost;
    }

    // move application

    private void applyMove(int[] tourSegmentBoundaries, int[] tour, int[] newTour)
    {
        int segmentZeroEnd = tourSegmentBoundaries[0];
        int newTourIndex = copySegment(tour, 0, segmentZeroEnd, newTour, 0);

        for (int i = 1; i < k2 - 1; i += 2) {

            int segmentStartTourIndex = tourSegmentBoundaries[i];
            int segmentEndTourIndex = tourSegmentBoundaries[i + 1];

            newTourIndex = copySegment(
                tour, segmentStartTourIndex, segmentEndTourIndex, newTour, newTourIndex);
        }

        // copy the remaining part to the end (copy start vertex add the end, too!)
        copySegment(tour, newTourIndex, tour.length - 1, newTour, newTourIndex);
    }

    /**
     * @param source the source tour from which the a segment is copied
     * @param start the segment start (inclusive) specified by its index in the given source
     * @param end the segment end (inclusive) specified by its index in the given source
     * @param target the target array into which the segment is copied
     * @param targetIndex the start index of the segment in the target array
     * @return the index of the first element after the copied segment in the target
     */
    private static int copySegment(int[] source, int start, int end, int[] target, int targetIndex)
    {
        int length;
        if (start < end) {
            length = end - start + 1;
            System.arraycopy(source, start, target, targetIndex, length);
            return targetIndex + length;

        } else { // copy segment in reversed order
            for (int i = start; end <= i; --i) {
                target[targetIndex++] = source[i];
            }
            return targetIndex;
        }
    }

    // tour segments re-combinations

    private static final ConcurrentComputationCache<Integer, List<int[]>> NORMALIZED_COMBINATIONS =
        new ConcurrentComputationCache<>(KOptHeuristicTSP::computeTourSegmentCombinations);

    private static final ConcurrentComputationCache<Integer,
        List<int[]>> PURE_NORMALIZED_COMBINATIONS =
            new ConcurrentComputationCache<>(KOptHeuristicTSP::computePureTourSegmentCombinations);

    private static List<int[]> getTourSegmentCombinations(
        ConcurrentComputationCache<Integer, List<int[]>> combinations, int k)
    {
        try {
            return combinations.getComputedValue(k);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interruped", e);
        }
    }

    private static List<int[]> computeTourSegmentCombinations(final int k)
    {
        // there are k tour segments. Their start and end vertices are indexed. The start vertex of
        // each segment has a odd index, the end an even index. For example segment one starts
        // with vertex 1 and ends with 2, the segment two starts with 3 and ends with four.
        // Which index in the tour this corresponds to depends on the current indices. An index
        // always specified the end of a segment and its successor the beginning of the next one.
        // <seg0>--<seg1>--<seg2>
        // <0,,1>--<2,,3>--<4,,5>

        // k = 2:
        // ,0>--<1,,2>--<3,
        // ,1>--<2,,1>--<3,

        // k = 3:
        // ,0>--<1,,2>--<3,,4>--<5,
        // ,0>--<1,,2>--<4,,3>--<5,
        // ,0>--<2,,1>--<3,,4>--<5,
        // ,0>--<2,,1>--<4,,3>--<5,

        // ,0>--<3,,4>--<1,,2>--<5,
        // ,0>--<3,,4>--<2,,1>--<5,
        // ,0>--<4,,3>--<1,,2>--<5,
        // ,0>--<4,,3>--<2,,1>--<5,

        int[] segmentBoundIndices = new int[] { 0 };
        // start with 1 and will end with 0. Zero is implicit since the segment zero is always fixed
        List<int[]> segmentCombinations = Collections.singletonList(segmentBoundIndices);

        for (int tourSegment = 1; tourSegment < k; tourSegment++) { // segment 0 is fixed
            int remainingSegments = 2 * (k - tourSegment); // forward plus reversed
            int combinationCount = segmentCombinations.size() * remainingSegments;
            List<int[]> newSegmentCombinations = new ArrayList<>(combinationCount);

            for (int[] baseCombination : segmentCombinations) {

                for (int segmentVertex = 1; segmentVertex < 2 * k - 1; segmentVertex += 2) {
                    // segmentBoundVertex <0> and <2k-1> are always contained
                    if (!contains(baseCombination, segmentVertex)) {
                        // start vertex of segment is not yet contained and so is the end vertex.
                        // -> add combination with segment in forward and reversed direction

                        int oppositeSegmentVertex = segmentVertex + 1;

                        int[] newCombinationForward =
                            appendToCopy(baseCombination, segmentVertex, oppositeSegmentVertex);
                        newSegmentCombinations.add(newCombinationForward);

                        int[] newCombinationReversed =
                            appendToCopy(baseCombination, oppositeSegmentVertex, segmentVertex);
                        newSegmentCombinations.add(newCombinationReversed);
                    }
                }
            }
            segmentCombinations = newSegmentCombinations;
        }
        // add last segment vertex index <2k-1>
        int lastIndex = 2 * k - 1;
        segmentCombinations.replaceAll(c -> appendToCopy(c, lastIndex));

        // self check
        int k2 = 2 * k;
        if (!segmentCombinations.stream().allMatch(c -> c.length == k2)) {
            throw new IllegalStateException("Combinations do not have expected number of elements");
        }

        if (segmentCombinations.size() != expectedSegmentCombinationCount(k)) {
            throw new IllegalStateException("Unexpected number of combinations");
        }

        return segmentCombinations;
    }
    // FIXME: remain only one of the each type of move if this works with my indexing?

    private static boolean contains(int[] arr, int value)
    {
        for (int element : arr) {
            if (element == value) {
                return true;
            }
        }
        return false;
    }

    private static int[] appendToCopy(int[] arr, int... values)
    {
        int[] copy = Arrays.copyOf(arr, arr.length + values.length);
        System.arraycopy(values, 0, copy, arr.length, values.length);
        return copy;
    }

    private static int expectedSegmentCombinationCount(int k)
    {
        int segmentCombinationsCount = 1;
        for (int i = 1; i < k; i++) {
            segmentCombinationsCount *= 2 * (k - i);
        }
        return segmentCombinationsCount;
    }

    private static List<int[]> computePureTourSegmentCombinations(final int k)
    {
        List<int[]> segmentCombinations = getTourSegmentCombinations(NORMALIZED_COMBINATIONS, k);
        ArrayList<int[]> pureCombinations = new ArrayList<>(segmentCombinations);
        pureCombinations.subList(1, pureCombinations.size()).removeIf(t -> !isPureKOptMove(t));
        pureCombinations.trimToSize();
        return pureCombinations;
    }

    /**
     * Test if the given normalized segment re-combination is a pure k-opt move, that really changes
     * all possible k edges.
     * <p>
     * For {@code k>2} the set of all possible k-opt moves contains moves that do not change all
     * {@code k} edges. For some edges the end vertices are re-connected like they were before. This
     * method tests if the given segment re-combination does not leaf any of the {@code k} edges
     * unchanged.
     * </p>
     *
     * @param segmentCombination the normalized segment re-combination to test
     * @return true if the segment combination changes all k edges, else false
     */
    private static boolean isPureKOptMove(int[] segmentCombination)
    {
        int edges = segmentCombination.length / 2; // equal to k
        for (int edge = 0; edge < edges; edge++) {
            int sourceVertex = segmentCombination[2 * edge];
            int targetVertex = segmentCombination[2 * edge + 1];
            boolean isEdgeChanged = Math.abs(targetVertex - sourceVertex) > 1;
            if (!isEdgeChanged) {
                // edge was not broken and replaced by another -> only k-1 edges are changed
                return false; // the effective k is reduced by (at least) one
            }
        }
        return true; // all edges are broken and re-connected differently
    }
}
