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
import org.jgrapht.generate.*;
import org.jgrapht.graph.*;

import java.util.*;
import java.util.function.*;

import static java.util.stream.Collectors.*;

/**
 * Utility class providing static methods used to test {@link HamiltonianCycleAlgorithm}
 * implementations.
 *
 * @author Hannes Wellmann
 *
 */
public class HamiltonianCycleAlgorithmTests
{
    // utility methods to build test graphs

    /**
     * Builds a complete weighted graph of the given locations where the weight of each edge is the
     * distance of the edge's vertices.
     *
     * @param <V> the graph vertex type
     * @param locations the vertices representing locations
     * @param distanceFunction the function used to compute the distance between two vertices
     * @return
     */
    public static <V> Graph<V, DefaultWeightedEdge> buidCompleteDistancesGraph(
        List<V> locations, ToDoubleBiFunction<V, V> distanceFunction)
    {
        // build complete graph
        Graph<V, DefaultWeightedEdge> g =
            new SimpleWeightedGraph<>(locations.iterator()::next, DefaultWeightedEdge::new);

        new CompleteGraphGenerator<V, DefaultWeightedEdge>(locations.size()).generateGraph(g);

        // compute edge weights
        for (DefaultWeightedEdge edge : g.edgeSet()) {
            V source = g.getEdgeSource(edge);
            V target = g.getEdgeTarget(edge);
            double weight = distanceFunction.applyAsDouble(source, target);
            g.setEdgeWeight(edge, weight);
        }

        return new AsUnmodifiableGraph<>(g);
    }

    /**
     * Returns a {@link GraphPath}-tour for a tour specified by an int[], where each int represents
     * the number of a vertex.
     *
     * @param <V> the graph vertex type
     * @param <E> the graph edge type
     * @param vertexNumberTour the array of integers specifying the vertex order
     * @param vertexList the list of vertices ordered according to the vertices' number
     * @param graph the graph containing all vertices
     * @return the {@code GraphPath} containing the tour specified by {@code vertexNumberTour}
     */
    public static <V, E> GraphPath<V, E> vertexNumbersToTour(
        int[] vertexNumberTour, List<V> vertexList, Graph<V, E> graph)
    {
        List<V> tour = Arrays.stream(vertexNumberTour).mapToObj(vertexList::get).collect(toList());
        tour.add(tour.get(0)); // close tour

        double weight = 0;
        for (int i = 1; i < vertexNumberTour.length; i++) {
            E edge = graph.getEdge(tour.get(i - 1), tour.get(i));
            weight += graph.getEdgeWeight(edge);
        }
        return new GraphWalk<>(graph, Collections.unmodifiableList(tour), weight);
    }

    public static long stringBytesAsLong(String str)
    {
        if (str.length() > 8) { // if longer than 8, bytes are lost
            throw new IllegalArgumentException("String must not be longer than 8 chars");
        }
        int length = str.length();
        long l = 0;
        for (int i = 0; i < length; i++) {
            l += ((long) str.charAt(length - 1 - i)) << (8 * i);
        }
        return l;
    }
}
