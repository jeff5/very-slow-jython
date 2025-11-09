// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test modifies the attributes of some (mutable) type objects and
 * reads those attributes, or performs operations dependent on them,
 * concurrently from distinct threads. It will look for (and fail on)
 * outcomes we deem impossible.
 * <p>
 * Unusually for JUnit tests, the intense processing takes place during
 * {@link #setUpClass()}, and the individual JUnit tests are predicates
 * on a list of lists of violations collected on during that time. If
 * the list is empty, that's a pass.
 * <p>
 * A significant conceptual challenge is to define the expected or
 * disallowed behaviours. Python has only recently started to allow true
 * concurrency without the GIL and the required behaviours are not yet
 * clearly stated (2025). Java guarantees "sequentially consistent"
 * execution of correctly synchronised programs. The tests here are
 * (deliberately) <b>not</b> correctly synchronised in the test region.
 * The hypothesis we attempt to disprove by testing is that
 * unsynchronised use from Python of the objects we provide leads to
 * behaviour not expected of Python.
 * <p>
 * This test is influenced by the literature on the "litmus tests", used
 * to test or complement assertions made for a processor architecture
 * and to validate language memory models. Listing 16.1 in <i>Java
 * Concurrency in Practice</i>, Peierls, Goetz, et al. is an example of
 * the latter.
 */
@DisplayName("When multiple threads access type objects")
@TestMethodOrder(MethodOrderer.MethodName.class)
class TypeConcurrencyTest {

    /** Logger for the test. */
    static final Logger logger =
            LoggerFactory.getLogger(TypeConcurrencyTest.class);

    /** Threads in each batch. */
    static final int BATCH_SIZE = 20; // ~100

    /** Number of batches. */
    static final int BATCH_COUNT = 100; // ~ 10000

    /** Curtail the test when we have found this many failures. */
    static final int FAILURE_LIMIT = BATCH_SIZE + 5;

    /** Failing litmus tests */
    static List<LitmusTest> failures = new LinkedList<>();

    /**
     * We create multiple threads for each behavioural sequence and run
     * them concurrently. They will make observations of the types that
     * ought only to be possible definitely after each is Python-ready.
     * Each will note the high-resolution time at which it began and was
     * able to complete its first action, and complete its other
     * actions.
     */
    @BeforeAll
    static void setUpClass() throws Throwable {

        // Ensure the type system exists
        assertTrue(TypeSystem.bootstrapNanoTime > 0L,
                "Check type system booted");

        // Batch of tests to run concurrently.
        LitmusTest[] tests = new LitmusTest[BATCH_SIZE];
        Thread[] threads = new Thread[BATCH_SIZE];

        for (int i = 0; i < BATCH_SIZE; i++) {
            tests[i] = new SBLitmusTest();
        }

        logger.debug("TypeConcurrencyTest begun");

        // We use that storage repeatedly with new tests.
        for (int batch = 0; batch < BATCH_COUNT; batch++) {
            // Create threads: each will run a pair of racing threads.
            for (int i = 0; i < BATCH_SIZE; i++) {
                threads[i] = new Thread(tests[i]);
            }
            // Start them all close together in time
            for (Thread t : threads) { t.start(); }

            // Wait for the threads to finish.
            for (int i = 0; i < BATCH_SIZE; i++) {
                try {
                    threads[i].join(1000);
                } catch (InterruptedException e) {
                    // Check completion later
                }
                // Collect failures in a list.
                LitmusTest test = tests[i];
                if (test.failed()) {
                    failures.add(test);
                    tests[i] = new SBLitmusTest();
                } else {
                    test.reset();
                }
            }

            // Make sure they all stop (so the test does).
            boolean allStopped = true;
            for (Thread t : threads) { allStopped &= hasStopped(t); }
            assertTrue(allStopped, "Threads were still running");

            // Early exit if ample evidence.
            if (failures.size() >= FAILURE_LIMIT) { break; }
        }
    }

    @AfterAll
    static void tearDownClass() {
        logger.debug("TypeConcurrencyTest complete");
    }

    private static boolean hasStopped(Thread t) {
        if (t.isAlive()) {
            logger.warn("Still running {}", t.getName());
            return false;
        }
        return true;
    }

    /** Check for tests that ended with an exceptions. */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("No exceptions were thrown")
    void checkException() {
        for (LitmusTest t : failures) {
            assertNull(t.exc1,
                    () -> String.format("thread t1 threw %s", t.exc1));
            assertNull(t.exc2,
                    () -> String.format("thread t2 threw %s", t.exc2));
        }
    }

    /** Check the outcome was allowed. */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("Only allowed states were reached")
    void checkAllowed() throws PyAttributeError, Throwable {
        for (LitmusTest t : failures) {
            if (!t.allowed()) { fail(t.toString()); }
        }
    }

    /**
     * A test object that runs two threads defined by the methods
     * {@link #t1()} and {@link #t2()} in a race. The test object itself
     * implements {@link Runnable} so that we can run many copies in
     * parallel (for efficiency and to stress the processor), but it is
     * the pair of threads in each case that constitute the test, or the
     * effective litmus test {@code program}.
     * <p>
     * A subclass defines {@link #t1()}, {@link #t2()} and
     * {@link #allowed()} to define the activity and determine whether
     * the outcome is allowed (a pass) for not (a fail).
     */
    abstract static class LitmusTest implements Runnable {

        private CyclicBarrier barrier = new CyclicBarrier(2);

        /** Any exception thrown in the corresponding method. */
        Throwable exc1, exc2;

        /** Define the actions of the first thread. */
        abstract void t1() throws Throwable;

        /** Define the actions of the second thread. */
        abstract void t2() throws Throwable;

        /**
         * Test whether the result of the pair of threads is allowed
         * behaviour (a pass) for not (a fail).
         */
        abstract boolean allowed();

        /**
         * Create a test consisting of two threads ready to be started,
         * one to call {@link #t1()} and the other {@link #t2()}. When
         * started by <code>this.{@link #run()}</code>, both threads
         * wait at a barrier of capacity two, meaning the first waits
         * and until the second arrives there, then both proceed to call
         * their respective methods in a race. Afterwards,
         * {@link #allowed()} determines whether the state reached is an
         * allowed one.
         */
        LitmusTest() {}

        /**
         * Run the two threads set up by the constructor and wait for
         * both to complete.
         *
         */
        @Override
        public void run() {

            // This thread calls t1()
            Thread call_t1 = new Thread(() -> {
                try {
                    barrier.await();
                    t1();
                } catch (Throwable t) {
                    exc1 = t;
                }
            });

            // At the same time, this thread calls t2()
            Thread call_t2 = new Thread(() -> {
                try {
                    barrier.await();
                    t2();
                } catch (Throwable t) {
                    exc2 = t;
                }
            });

            Thread[] threads = {call_t1, call_t2};

            // Start both threads. (They sync at the barrier.)
            for (Thread t : threads) { t.start(); }
            // Wait for the threads to finish.
            for (Thread t : threads) {
                try {
                    t.join(1000);
                } catch (InterruptedException e) {
                    // Check completion later
                    exc1 = exc2 = e;
                }
            }
        }

        /**
         * Return true if there was an exception of allowed is false.
         */
        boolean failed() {
            return exc1 != null || exc2 != null || !allowed();
        }

        /**
         * Return the test to initial conditions to save creating a
         * fresh one. Only define this if that can be validly done.
         */
        @SuppressWarnings("static-method")
        void reset() { fail("reset() not supported"); }
    }

    /**
     * The Storage Buffering litmus test interpreted for a type object
     * name space as the "store". We add attributes in a race and test
     * for a sequentially consistent result.
     * <p>
     * The test shows that the updates to a type object are published
     * between threads as if execution were sequentially consistent.
     */
    static class SBLitmusTest extends LitmusTest {
        PyType type;
        Object r1, r2;

        SBLitmusTest() throws Throwable {
            this.type = (PyType)PyType.TYPE().call("SBType",
                    Py.tuple(PyObject.TYPE), Py.dict());
        }

        @Override
        void t1() throws Throwable {
            r1 = Abstract.lookupAttr(type, "A");
            Abstract.setAttr(type, "B", 2);
        }

        @Override
        void t2() throws Throwable {
            r2 = Abstract.lookupAttr(type, "B");
            Abstract.setAttr(type, "A", 1);
        }

        @Override
        boolean allowed() {
            // Sequentially consistent answer.
            return r1 == null && r2 == null  //
                    || r1 == null && r2.equals(2)  //
                    || r1.equals(1) && r2 == null;
        }

        @Override
        void reset() {
            r1 = r2 = null;
            exc1 = exc2 = null;
            try {
                if (Abstract.lookupAttr(type, "A") != null) {
                    Abstract.delAttr(type, "A");
                }
                if (Abstract.lookupAttr(type, "B") != null) {
                    Abstract.delAttr(type, "B");
                }
            } catch (Throwable e) {
                fail("Unable to reset() type attributes");
            }
        }

        @Override
        public String toString() {
            return String.format("SBLitmusTest [type=%s, r1=%s, r2=%s]",
                    type, r1, r2);
        }
    }
}
