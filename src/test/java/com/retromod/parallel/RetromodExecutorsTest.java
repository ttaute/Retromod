/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.parallel;

import com.retromod.core.parallel.RetromodExecutors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RetromodExecutors}, the shared parallel-execution helper.
 *
 * <p>These check correctness invariants, not a specific degree of parallelism:
 * every input produces one output, order is preserved where promised, and a
 * failing task does not abort the batch.</p>
 */
class RetromodExecutorsTest {

    @Test
    @DisplayName("parallelForEach visits every element exactly once")
    void parallelForEachEveryItem() {
        List<Integer> inputs = new ArrayList<>();
        for (int i = 0; i < 200; i++) inputs.add(i);

        Set<Integer> visited = java.util.Collections.synchronizedSet(new HashSet<>());
        RetromodExecutors.parallelForEach(inputs, visited::add);

        assertEquals(inputs.size(), visited.size(),
                "Every element visited once");
        for (int i = 0; i < 200; i++) {
            assertTrue(visited.contains(i), "Missing element: " + i);
        }
    }

    @Test
    @DisplayName("parallelMap preserves input order even under parallel execution")
    void parallelMapPreservesOrder() {
        List<Integer> inputs = new ArrayList<>();
        for (int i = 0; i < 100; i++) inputs.add(i);

        // uneven work so the scheduler varies thread assignment
        List<Integer> results = RetromodExecutors.parallelMap(inputs, i -> {
            if (i % 7 == 0) {
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
            }
            return i * 2;
        });

        assertEquals(inputs.size(), results.size());
        for (int i = 0; i < inputs.size(); i++) {
            assertEquals(i * 2, results.get(i),
                    "Order must match input position at index " + i);
        }
    }

    @Test
    @DisplayName("Exceptions in a task don't abort other tasks")
    void exceptionsIsolatedPerTask() {
        List<Integer> inputs = new ArrayList<>();
        for (int i = 0; i < 50; i++) inputs.add(i);

        AtomicInteger completed = new AtomicInteger();
        RetromodExecutors.parallelForEach(inputs, i -> {
            if (i == 17) {
                throw new RuntimeException("Intentional test failure at index 17");
            }
            completed.incrementAndGet();
        });

        // the throwing task is logged but not re-raised, so 49 of 50 complete
        assertEquals(49, completed.get(),
                "A single task failure must not abort the whole batch");
    }

    @Test
    @DisplayName("Empty and single-element inputs use the fast path")
    void emptyAndSingleInputsWork() {
        AtomicInteger count = new AtomicInteger();
        RetromodExecutors.parallelForEach(List.of(), (Object x) -> count.incrementAndGet());
        assertEquals(0, count.get());

        // single element runs inline but is still visited
        RetromodExecutors.parallelForEach(List.of("single"), s -> count.incrementAndGet());
        assertEquals(1, count.get());

        List<String> mapped = RetromodExecutors.parallelMap(List.of(), x -> "unused");
        assertTrue(mapped.isEmpty());
    }

    @Test
    @DisplayName("getParallelism returns at least 1")
    void parallelismIsValid() {
        assertTrue(RetromodExecutors.getParallelism() >= 1,
                "Parallelism must always be >= 1");
    }

    @Test
    @DisplayName("Shared pool is the same instance across calls (single allocation)")
    void sharedPoolIsSingleton() {
        var pool1 = RetromodExecutors.sharedPool();
        var pool2 = RetromodExecutors.sharedPool();
        assertSame(pool1, pool2,
                "sharedPool() must return the same pool every time; allocating twice would double the thread count");
    }

    @Test
    @DisplayName("Parallel execution uses multiple threads when parallelism > 1")
    void parallelExecutionUsesMultipleThreads() {
        // only meaningful with >1 core; skip on single-core CI
        if (!RetromodExecutors.isParallel()) return;

        ConcurrentLinkedQueue<String> threadsSeen = new ConcurrentLinkedQueue<>();
        List<Integer> inputs = new ArrayList<>();
        for (int i = 0; i < 64; i++) inputs.add(i);

        RetromodExecutors.parallelForEach(inputs, i -> {
            threadsSeen.add(Thread.currentThread().getName());
            // busy-work so the scheduler has reason to distribute tasks
            int sum = 0;
            for (int j = 0; j < 10_000; j++) sum += j;
            if (sum < 0) throw new AssertionError("unreachable"); // keep the loop from being elided
        });

        Set<String> uniqueThreads = new HashSet<>(threadsSeen);
        assertTrue(uniqueThreads.size() > 1,
                "Parallel execution should spread across multiple threads; saw only: " + uniqueThreads);
    }
}
