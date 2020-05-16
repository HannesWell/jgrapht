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
package org.jgrapht.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/**
 * A fully concurrent thread-safe and key-based cache of computation results. It is based on a
 * {@link ConcurrentHashMap} and their {@link ConcurrentHashMap#computeIfAbsent(Object, Function)}
 * method. But in contrast to using that method directly, the computation of a value does not block
 * other computations, so multiple computations can be performed concurrently.
 *
 * @param <K> the type of keys used to identify computation results
 * @param <V> the type of computed values
 * @author Hannes Wellmann
 */
public class ConcurrentComputationCache<K, V>
{
    private final ConcurrentHashMap<K, RunnableFuture<V>> map = new ConcurrentHashMap<>();
    private final Function<? super K, ? extends V> valueComputationFunction;

    /**
     * Creates a ConcurrentComputationCache using the specified {@code valueComputationFunction}.
     * <p>
     * The {@code valueComputationFunction} may be called concurrently by multiple threads and must
     * therefore be thread-safe. Nevertheless multiple concurrent calls should not block each other
     * in order the achieve full concurrency. If this is not possible, consider to use
     * {@link ConcurrentHashMap#computeIfAbsent(Object, Function)} directly, which does only one
     * value computation at the same time.
     * </p>
     * <p>
     * In contrast to {@link Map#computeIfAbsent(Object, Function)} null-values returned by
     * {@code valueComputationFunction} are stored as regular values in this cache.
     * </p>
     *
     * @param valueComputationFunction the mapping function to compute a value for a key
     * @throws NullPointerException if the valueComputationFunction is null
     */
    public ConcurrentComputationCache(Function<? super K, ? extends V> valueComputationFunction)
    {
        this.valueComputationFunction = Objects.requireNonNull(valueComputationFunction);
    }

    // FIXME: improve doc
    // TODO: null values, null key?

    /**
     * Returns the value to which the specified key is associated in this cache. If no value is
     * present yet, it is computed with the {@code valueComputationFunction} of this cache and
     * stored.
     * <p>
     * This method is similar to {@link Map#computeIfAbsent(Object, Function)}, except that the
     * value-computation for one key does not block the computations for other keys. For each key
     * the computation is performed exactly once in any of the first threads that request that value
     * before its computation is completed. All other threads requesting the value for the same
     * (equal) key are blocked until the computation is completed and then return the computed
     * value.
     * <p>
     * This method is thread-safe and can be called concurrently by multiple threads.
     * </p>
     * <p>
     * Any {@link RuntimeException} thrown by the {@code valueComputingFunction} of this cache while
     * computing the value of a key is propagated to the caller and re-thrown each time the value of
     * that key is requested.
     * </p>
     *
     * @param key key for which the associated value will be returned
     * @return the value computed (and cached) for the specified key, may be null
     * @throws NullPointerException if the specified key is null
     * @throws InterruptedException if the current thread was interrupted while waiting for the
     *         computation result
     */
    public V getComputedValue(K key)
        throws InterruptedException
    {
        Objects.requireNonNull(key); // ConcurrentHashMap does not support null-keys anyway
        RunnableFuture<V> future = map.computeIfAbsent(key, k -> {
            Callable<V> computer = () -> valueComputationFunction.apply(k);
            return new FutureTask<>(computer);
        });
        // call computer outside of computeIfAbsent() to avoid blocking
        future.run(); // no-op if already computing in other thread or already completed

        return getValue(future);
    }

    private V getValue(Future<V> future)
        throws InterruptedException
    {
        try {
            return future.get(); // if computation is not yet completed, blocks until then
        } catch (ExecutionException e) {
            throw (RuntimeException) e.getCause(); // Callable can only throw RuntimeException
        }
    }
}
