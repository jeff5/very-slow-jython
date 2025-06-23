// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import static org.junit.jupiter.api.Assertions.*;

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
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.kernel.TypeRegistry;

/**
 * This is a variant of {@link BootstrapTest} focusing particularly on
 * competition for the static TYPE members of bootstrap types. We test
 * that threads that access those members before the type system exists
 * will never see them {@code null}.
 *
 * As before, the intent is that the first thread to ask anything of the
 * type system initialises it fully (as the <i>bootstrap thread</i>),
 * before its request is answered, while all other threads with a
 * request have to wait on {@link TypeSystem}.
 * <p>
 * We shall start a lot of threads, as close to simultaneously as we can
 * manage, that each access the {@code TYPE} static member of a
 * particular bootstrap type. Each thread records when it started and
 * when it got its answer. {@code TypeSystem} itself keeps track of when
 * the bootstrap started and finished. The bootstrap should complete
 * before any thread gets its answer, and (for a satisfactory test)
 * multiple threads should be racing before the bootstrap begins.
 */
@DisplayName("When multiple threads reference TYPE")
@TestMethodOrder(MethodOrderer.MethodName.class)
@Timeout(2)
class StaticTYPETest {

    /** Logger for the test. */
    static final Logger logger =
            LoggerFactory.getLogger(StaticTYPETest.class);

    /** Random (or deterministic) order. */
    static final long seed = System.currentTimeMillis();
    // static final long seed = 1234L; // Somewhat repeatable

    /** If defined, dump the times recorded by threads. */
    static final String DUMP_PROPERTY =
            "uk.co.farowl.vsj4.runtime.BootstrapTest.times";

    /** Threads of each kind. */
    static final int NTHREADS = 20; // >= #clauses in setUpClass
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
            int k = i % 12; // random.nextInt(12);
            threads.add(switch (k) {

                case 0 -> new InitThread(k, PyType.class) {
                    @Override
                    void action() { type = PyType.TYPE(); }
                };

                case 1 -> new InitThread(k, PyLong.class) {
                    @Override
                    void action() { type = PyLong.TYPE; }
                };

                case 2 -> new InitThread(k, PyBool.class) {
                    @Override
                    void action() { type = PyBool.TYPE; }
                };

                case 3 -> new InitThread(k, PyFloat.class) {
                    @Override
                    void action() { type = PyFloat.TYPE; }
                };

                case 4 -> new InitThread(k, PyUnicode.class) {
                    @Override
                    void action() { type = PyUnicode.TYPE; }
                };

                case 5 -> new InitThread(k, PyGetSetDescr.class) {
                    @Override
                    void action() { type = PyGetSetDescr.TYPE; }
                };

                case 6 -> new InitThread(k, PyJavaFunction.class) {
                    @Override
                    void action() { type = PyJavaFunction.TYPE; }
                };

                case 7 -> new InitThread(k, PyMemberDescr.class) {
                    @Override
                    void action() { type = PyMemberDescr.TYPE; }
                };

                case 8 -> new InitThread(k, PyMethodDescr.class) {
                    @Override
                    void action() { type = PyMethodDescr.TYPE; }
                };

                case 9 -> new InitThread(k, PyMethodWrapper.class) {
                    @Override
                    void action() { type = PyMethodWrapper.TYPE; }
                };

                case 10 -> new InitThread(k, PyWrapperDescr.class) {
                    @Override
                    void action() { type = PyWrapperDescr.TYPE; }
                };

                default -> new InitThread(k, PyObject.class) {
                    @Override
                    void action() { type = PyObject.TYPE; }
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
        boolean allStopped = true;
        for (Thread t : threads) { allStopped &= ensureStopped(t); }
        assertTrue(allStopped, "A forced stop was needed");

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
        String fmt = "           PyType.ready=%10d  (relative)\n";
        System.out.printf(fmt, TypeSystem.readyNanoTime
                - TypeSystem.bootstrapNanoTime);
        Collections.sort(threads, byFirst);
        for (InitThread t : threads) { System.out.println(t); }
    }

    @SuppressWarnings("deprecation")
    private static boolean ensureStopped(Thread t) {
        if (t.isAlive()) {
            logger.warn("Still running {}", t.getName());
            // t.stop();
            return false;
        }
        return true;
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
        assertTrue(competitors > 10L, () -> String
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

    /** All the threads see a correct TYPE. */
    @Test
    @DisplayName("All threads see their individual 'TYPE'")
    void individualTYPE() {
        for (InitThread init : threads) {
            PyType t = init.type;
            assertNotNull(t);
            assertSame(t.canonicalClass(), init.klass);
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
        /** Time this thread completed {@code action()}. */
        long finishNanoTime;
        /** The reference {@link TypeSystem#registry} when inspected. */
        TypeRegistry reg;
        /** The canonical class of the type this thread addresses. */
        Class<?> klass;
        /** The value seen in {@code klass} {@code :: TYPE}. */
        PyType type;

        /**
         * Create a {@code Thread} to run. Providing a reference to a
         * class here does <i>not</i> statically initialise it.
         *
         * @param choice of type for this thread
         * @param klass canonical class of TYPE accessed
         */
        InitThread(int choice, Class<?> klass) {
            this.klass = klass;
        }

        /**
         * Each implementation of {@code InitThread} retrieves the same
         * data, but chooses to do one action first by overriding this
         * method. {@link #otherActions()} then completes the work.
         */
        abstract void action();

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
            reg = TypeSystem.registry;
        }

        @Override
        public String toString() {
            return String.format("start=%10d, first=%10d, finish=%10d",
                    startNanoTime, firstNanoTime, finishNanoTime);
        }
    }
}
