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

import java.util.*;

//FIXME: add doc?!
/**
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author Hannes Wellmann
 */
public class IncrementalKOptHeuristicTSP<V, E>
    extends
    KOptHeuristicTSP<V, E>
{
    private final List<TwoOptHeuristicTSP<V, E>> stageAlgorithms = new ArrayList<>();

    /**
     * Constructor. By default one initial random tour is used.
     *
     * @param k the number of edges to consider in each iteration
     */
    public IncrementalKOptHeuristicTSP(int k)
    {
        super(k);
        createStageAlgorithms(k);
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial random tours to check
     */
    public IncrementalKOptHeuristicTSP(int k, int passes)
    {
        super(k, passes);
        createStageAlgorithms(k);
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial random tours to check
     * @param seed seed for the random number generator
     */
    public IncrementalKOptHeuristicTSP(int k, int passes, long seed)
    {
        super(k, passes, seed);
        createStageAlgorithms(k);
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial random tours to check
     * @param rng random number generator
     */
    public IncrementalKOptHeuristicTSP(int k, int passes, Random rng)
    {
        super(k, passes, rng);
        createStageAlgorithms(k);
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial random tours to check
     * @param rng random number generator
     * @param minCostImprovement Minimum cost improvement per iteration
     */
    public IncrementalKOptHeuristicTSP(int k, int passes, Random rng, double minCostImprovement)
    {
        super(k, passes, rng, minCostImprovement);
        createStageAlgorithms(k);
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param initializer Algorithm to generate initial tour
     */
    public IncrementalKOptHeuristicTSP(int k, HamiltonianCycleAlgorithm<V, E> initializer)
    {
        super(k, initializer);
        createStageAlgorithms(k);

    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial tours to check
     * @param initializer Algorithm to generate initial tour
     */
    public IncrementalKOptHeuristicTSP(
        int k, int passes, HamiltonianCycleAlgorithm<V, E> initializer)
    {
        super(k, passes, initializer);
        createStageAlgorithms(k);
    }

    /**
     * Constructor
     *
     * @param k the number of edges to consider in each iteration
     * @param passes how many initial tours to check
     * @param initializer Algorithm to generate initial tours
     * @param minCostImprovement Minimum cost improvement per iteration
     */
    public IncrementalKOptHeuristicTSP(
        int k, int passes, HamiltonianCycleAlgorithm<V, E> initializer, double minCostImprovement)
    {
        super(k, passes, initializer, minCostImprovement);
        createStageAlgorithms(k);
    }

    private void createStageAlgorithms(int kMax)
    {
        stageAlgorithms.add(new TwoOptHeuristicTSP<>(this.minCostImprovement));
        for (int k = 3; k < kMax; k++) {
            stageAlgorithms.add(new KOptHeuristicTSP<>(k, this.minCostImprovement));
        }
    }

    @Override
    protected void init(Graph<V, E> graph)
    {
        super.init(graph);
        for (TwoOptHeuristicTSP<V, E> algorithm : stageAlgorithms) {
            // copy relevant data from container to nested algorithms
            algorithm.n = this.n;
            algorithm.dist = this.dist;
        }
    }

    @Override
    protected int[] improve(int[] tour)
    {
        for (TwoOptHeuristicTSP<V, E> algorithm : stageAlgorithms) {
            tour = algorithm.improve(tour);
        }
        return tour;
    }
}
