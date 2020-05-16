package org.jgrapht.util;

import org.jgrapht.alg.util.*;
import org.junit.*;
import org.mockito.*;

import java.util.*;
import java.util.function.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ConcurrentComputationCacheTest
{

    @Test
    public void testComputeIfAbsent_twoRequestsForEqualKey_cachedValueOnSecondCall()
        throws InterruptedException
    {
        @SuppressWarnings("unchecked") Function<Integer, Object> valueComputer =
            mock(Function.class);
        when(valueComputer.apply(any())).thenAnswer(i -> new Object());

        ConcurrentComputationCache<Integer, Object> cache =
            new ConcurrentComputationCache<>(valueComputer);

        Object result1 = cache.getComputedValue(0); // computes the value
        Object result2 = cache.getComputedValue(0); // returns the cached value
        assertThat(result1, is(sameInstance(result2)));

        Mockito.verify(valueComputer).apply(any());
    }

    @Test
    public void testComputeIfAbsent_computerThrowsException_propagatedExceptionOnEachValueRequest()
        throws InterruptedException
    {
        RuntimeException e = new RuntimeException();
        Function<Integer, Object> valueComputer = i -> {
            throw e;
        };
        ConcurrentComputationCache<Integer, Object> cache =
            new ConcurrentComputationCache<>(valueComputer);

        try {
            cache.getComputedValue(0);
            fail("Expected exception");
        } catch (RuntimeException e1) { // exception is result of computation
            assertThat(e1, is(sameInstance(e)));
        }
        try {
            cache.getComputedValue(0);
            fail("Expected exception");
        } catch (RuntimeException e1) { // exception is cached
            assertThat(e1, is(sameInstance(e)));
        }
    }

    @Test
    public void testComputeIfAbsent_concurrentComputationOfDifferentValues_noBlocking()
        throws InterruptedException
    {

        final int timeout = 5_000; // use timeouts to avoid infinite blocking due to fails

        BooleanCondition isThread1 = new BooleanCondition();
        BooleanCondition thread1ComputationStarted = new BooleanCondition();
        BooleanCondition thread2ComputationCompleted = new BooleanCondition();
        List<Pair<Thread, Throwable>> uncaughtExceptions = new ArrayList<>();

        @SuppressWarnings("unchecked") Function<Integer, String> computer = mock(Function.class);
        when(computer.apply(any())).thenAnswer(i -> {
            if (isThread1.holds()) {
                thread1ComputationStarted.signal(); // called from thread1
                thread2ComputationCompleted.await(timeout); // Simulate long running computation
            }
            return Integer.toString(i.getArgument(0));
        });

        ConcurrentComputationCache<Integer, String> cache =
            new ConcurrentComputationCache<>(computer);

        // create two threads that request/compute values for two different keys, where the
        // computation of thread2 is done within the time during that thread1 computes its value.

        final int key1 = 0;
        Thread thread1 = startNewThread(() -> {
            isThread1.signal();
            getComputedValue(cache, key1);
        }, uncaughtExceptions);

        final int key2 = 1;
        Thread thread2 = startNewThread(() -> {
            thread1ComputationStarted.await(timeout);
            isThread1.unsignal();
            getComputedValue(cache, key2);
            thread2ComputationCompleted.signal();
        }, uncaughtExceptions);

        joinAll(timeout, thread1, thread2);

        Mockito.verify(computer).apply(key1);
        Mockito.verify(computer).apply(key2);

        if (!uncaughtExceptions.isEmpty()) {
            fail("Computer thread terminated due to exception: " + uncaughtExceptions);
        }
    }

    @Test
    public void testComputeIfAbsent_concurrentRequestOfSameKey_blockingUntilValueIsComputed()
        throws InterruptedException
    {
        final int timeout = 5_000;

        BooleanCondition thread2IsRunning = new BooleanCondition();
        BooleanCondition computationStarted = new BooleanCondition();
        List<Pair<Thread, Throwable>> uncaughtExceptions = new ArrayList<>();

        @SuppressWarnings("unchecked") Function<Integer, String> computer = mock(Function.class);
        when(computer.apply(any())).thenAnswer(i -> {
            computationStarted.signal();
            wait(1000); // wait some time, so the other thread is blocked
            return Integer.toString(i.getArgument(0));
        });

        ConcurrentComputationCache<Integer, String> cache =
            new ConcurrentComputationCache<>(computer);

        // create two threads that request the value of the same key

        final int key = 0;
        Thread thread1 = startNewThread(() -> {
            // try to bring the threads as close together as possible
            thread2IsRunning.await(timeout);
            getComputedValue(cache, key); // does the actual computation
        }, uncaughtExceptions);

        Thread thread2 = startNewThread(() -> {
            // try to bring the threads as close together as possible
            thread2IsRunning.signal();
            computationStarted.await(timeout);
            String value = getComputedValue(cache, key);
            assertThat(value, is(equalTo(Integer.toString(key))));
        }, uncaughtExceptions);

        joinAll(timeout, thread1, thread2);

        Mockito.verify(computer).apply(key);

        if (!uncaughtExceptions.isEmpty()) {
            fail("Computer thread terminated due to exception: " + uncaughtExceptions);
        }
    }

    private static Thread startNewThread(
        Runnable target, List<Pair<Thread, Throwable>> uncaughtExceptions)
    {
        Thread thread = new Thread(target);
        thread.setUncaughtExceptionHandler((t, e) -> uncaughtExceptions.add(Pair.of(t, e)));
        thread.start();
        return thread;
    }

    private static void wait(int time)
    {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < time) {
            Thread.onSpinWait();
        }
    }

    private static void joinAll(final int timeout, Thread... threads)
        throws InterruptedException
    {
        for (Thread thread : threads) {
            thread.join(timeout);
            assertFalse(thread.isAlive());
        }
    }

    private static <K, V> V getComputedValue(ConcurrentComputationCache<K, V> cache, K key)
    {
        try {
            return cache.getComputedValue(key);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Unexpected interruption", e);
        }
    }

    private static class BooleanCondition
    {
        private volatile boolean value = false;

        public boolean holds()
        {
            return value;
        }

        public void signal()
        {
            value = true;
        }

        public void unsignal()
        {
            value = false;
        }

        public void await(long timeout)
        {
            long start = System.currentTimeMillis(); // use time-out to avoid infinite waiting
            while (!value && (System.currentTimeMillis() - start) < timeout) {
                Thread.onSpinWait();
            }
            if (timeout - (System.currentTimeMillis() - start) <= 0) {
                fail("Timeout expired");
            }
        }
    }
}
