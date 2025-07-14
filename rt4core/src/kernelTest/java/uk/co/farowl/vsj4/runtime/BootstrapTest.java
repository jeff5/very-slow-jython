// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.kernel.AdoptiveType;
import uk.co.farowl.vsj4.runtime.kernel.TypeRegistry;

/**
 * Test that the type system is initialised in a single bootstrap
 * thread. The design intent is that the first thread to ask anything of
 * the type system initialises it fully (as the <i>bootstrap
 * thread</i>), before its request is answered, while all other threads
 * with a request have to wait on {@link TypeSystem}. We are are
 * concerned about these possible failure modes:
 * <ol>
 * <li>Deadlock: a thread waiting for the bootstrap thread holds a lock
 * on objects that the bootstrap thread needs.</li>
 * <li>Incomplete publication: type objects become visible outside the
 * bootstrap thread in an incomplete state.</li>
 * <li>Incomplete bootstrap: the bootstrap thread completes type system
 * creation (and its initiating request) while type objects are still
 * not complete.</li>
 * </ol>
 * The last two look much the same to the test, since it is not aware
 * which thread actually performed the bootstrap.
 * <p>
 * We test this by starting a lot of threads, as close to simultaneously
 * as we can manage, that access {@code TypeSystem} static members in a
 * variety of orders. Each thread records when it started and when it
 * got its first answer. {@code TypeSystem} itself keeps track of when
 * the bootstrap started and finished. The bootstrap should complete
 * before any thread gets its first answer, and (for a satisfactory
 * test) multiple threads should be racing before the bootstrap begins.
 * They should all get the same answers.
 */
@DisplayName("When multiple threads use the type system")
@TestMethodOrder(MethodOrderer.MethodName.class)
class BootstrapTest {

    /** Logger for the test. */
    static final Logger logger =
            LoggerFactory.getLogger(BootstrapTest.class);

    /** Random (or deterministic) order. */
    static final long seed = System.currentTimeMillis();
    // static final long seed = 1234L; // Somewhat repeatable

    /** If defined, dump the times recorded by threads. */
    static final String DUMP_PROPERTY =
            "uk.co.farowl.vsj4.runtime.BootstrapTest.times";

    /** Threads in total. &gt;&gt; {@code setUpClass()} cases. */
    static final int NTHREADS = 100;
    /**
     * Check this many threads actually concurrent. Ideally
     * &gt;{@code setUpClass()} cases but expectation depends on CPU.
     */
    static int MIN_THREADS =
            Math.min(Runtime.getRuntime().availableProcessors(), 20);

    /** Threads to run. */
    static final List<InitThread> threads = new ArrayList<>();
    /** A barrier they all wait behind. */
    static CyclicBarrier barrier;
    /** Source of random behaviour. */
    static Random random = new Random(seed);

    /**
     * We create multiple threads for each of several ways the type
     * system might be initiated and run them concurrently. They will
     * make observations of the type system that ought only to be
     * possible definitely after the type system initialises. Each will
     * note the high-resolution time at which it began and was able to
     */
    @BeforeAll
    static void setUpClass() {
        // Create NTHREADS randomly choosing which action comes first.
        for (int i = 0; i < NTHREADS; i++) {
            threads.add(switch (random.nextInt(4)) {

                case 0 -> new InitThread() {
                    @Override
                    void action() { reg = TypeSystem.registry; }
                };

                case 1 -> new InitThread() {
                    @Override
                    void action() { floatType = PyFloat.TYPE; }
                };

                case 2 -> new InitThread() {
                    @Override
                    void action() { objectType = PyObject.TYPE; }
                };

                default -> new InitThread() {
                    @Override
                    void action() throws Throwable {
                        result = PyNumber.multiply(6, 7);
                    }
                };
            });
        }

        // Create a barrier of matching capacity.
        barrier = new CyclicBarrier(threads.size());

        // Start the threads in a shuffled order.
        Collections.shuffle(threads, random);
        logger.info("{} threads prepared.", threads.size());
        for (Thread t : threads) { t.start(); }

        // Wait for the threads to finish.
        for (Thread t : threads) {
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                // Check completion later
            }
        }

        // Make sure they all stop (so the test does).
        for (Thread t : threads) { ensureStopped(t); }

        // Dump the thread times by start time.
        if (truthy(DUMP_PROPERTY)) { dumpThreads(); }
    }

    /** Property is defined and nothing like "false". */
    private static boolean truthy(String property) {
        property = System.getProperty(property, "false").toLowerCase();
        return !"false".equals(property);
    }

    /**
     * Dump (relative) bootstrap time and thread times to standard
     * output.
     */
    private static void dumpThreads() {
        Comparator<InitThread> byFirst = new Comparator<>() {

            @Override
            public int compare(InitThread t1, InitThread t2) {
                return Long.compare(t1.firstNanoTime, t2.firstNanoTime);
            }
        };
        String fmt = "Type system ready at   =%10d  (relative)\n";
        System.out.printf(fmt, TypeSystem.readyNanoTime
                - TypeSystem.bootstrapNanoTime);
        Collections.sort(threads, byFirst);
        for (InitThread t : threads) { System.out.println(t); }
    }

    @SuppressWarnings("deprecation")
    private static void ensureStopped(Thread t) {
        if (t.isAlive()) {
            logger.warn("Forcing stop {}", t.getName());
            t.stop();
        }
    }

    /** All threads completed. */
    @Test
    @DisplayName("All threads complete")
    void allComplete() {
        long completed = threads.stream()
                .filter(t -> t.finishNanoTime > 0L).count();
        assertTrue(completed == threads.size(),
                () -> String.format("%d threads did not complete.",
                        threads.size() - completed));
    }

    /** Some threads started before the bootstrap started. */
    @Test
    @DisplayName("A race takes place")
    void aRaceTookPlace() {
        // Enough relative start times should be negative
        long competitors = threads.stream()
                .filter(t -> t.startNanoTime <= 0L).count();
        logger.info("{} threads were racing.", competitors);
        logger.info("Required at least {} racing.", MIN_THREADS);
        assertTrue(competitors > MIN_THREADS, () -> String
                .format("Only %d competitors.", competitors));
    }

    /** Bootstrap completed before the first action completed. */
    @Test
    @DisplayName("The bootstrap completes before any action.")
    void bootstrapBeforeAction() {
        final long ready =
                TypeSystem.readyNanoTime - TypeSystem.bootstrapNanoTime;
        // All first actions should be after type system ready.
        long hasty = threads.stream()
                .filter(t -> t.firstNanoTime < ready).count();
        logger.info("{} threads failed to wait for the type system.",
                hasty);
        assertEquals(0L, hasty, () -> String
                .format("%d threads failed to wait.", hasty));
    }

    /** All the threads see the same type registry. */
    @Test
    @DisplayName("All threads see the same type registry")
    void sameTypeRegistry() {
        TypeRegistry registry = TypeSystem.registry;
        for (InitThread init : threads) {
            assertSame(registry, init.reg);
        }
    }

    /** All the threads see a correct PyFloat.TYPE. */
    @Test
    @DisplayName("All threads see 'float'")
    void sameFloat() {
        PyType f = PyFloat.TYPE;
        assertInstanceOf(AdoptiveType.class, f);
        for (InitThread init : threads) {
            assertSame(f, init.floatType);
        }
    }

    /**
     * A thread that performs actions on the type system, so that we may
     * test the outcome is the same for all threads. We keep track of
     * times in nanoseconds so we can be sure of the order of events.
     * All times are relative to the bootstrap time so that they are
     * reasonably printable.
     */
    static abstract class InitThread extends Thread {
        /** Time this thread started. */
        long startNanoTime;
        /** Time this thread completed line one of {@code action()}. */
        long firstNanoTime;
        /** Time this thread completed {@code otherActions()}. */
        long finishNanoTime;
        /** The reference {@link TypeSystem#registry} when inspected. */
        TypeRegistry reg;
        /** The type {@code object} when inspected. */
        PyType objectType;
        /** The type {@code float} when inspected. */
        PyType floatType;
        /** The result of an operation. */
        Object result;

        /**
         * Each implementation of {@code InitThread} retrieves the same
         * data, but chooses to do one action first by overriding this
         * method. {@link #otherActions()} then completes the work.
         */
        abstract void action() throws Throwable;

        @Override
        public void run() {
            // Wait at the barrier until every thread arrives.
            try {
                barrier.await();
                // sleep(random.nextInt(3) * 1000);
            } catch (InterruptedException | BrokenBarrierException e) {
                // This shouldn't happen.
                fail("A thread was interrupted at the barrier.");
                return;
            }
            // Perform the action: *raw* nanos before and after.
            startNanoTime = System.nanoTime();
            try {
                action();
            } catch (Throwable e) {
                logger.atWarn().setMessage("action() threw {}")
                        .addArgument(e).log();
            }
            firstNanoTime = System.nanoTime();
            otherActions();
            finishNanoTime = System.nanoTime();
            // *Only afterwards* make relative to bootstrap time.
            startNanoTime -= TypeSystem.bootstrapNanoTime;
            firstNanoTime -= TypeSystem.bootstrapNanoTime;
            finishNanoTime -= TypeSystem.bootstrapNanoTime;
        }

        /** The required actions apart from the one already done. */
        void otherActions() {
            if (objectType == null) { objectType = PyObject.TYPE; }
            if (floatType == null) { floatType = PyFloat.TYPE; }
            if (reg == null) { reg = TypeSystem.registry; }
        }

        @Override
        public String toString() {
            return String.format("start=%10d, first=%10d, finish=%10d",
                    startNanoTime, firstNanoTime, finishNanoTime);
        }
    }
}
