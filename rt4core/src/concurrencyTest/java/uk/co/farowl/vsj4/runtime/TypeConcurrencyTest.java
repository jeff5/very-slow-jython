// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This test modifies the attributes of some (mutable) type objects and
 * reads those attributes, or performs operations dependent on them,
 * concurrently from distinct threads. It is a collection of nested
 * tests. Each will look for (and fail on) outcomes we deem impossible.
 * <p>
 * Unusually for JUnit tests, the intense processing takes place during
 * the static initialisation method (annotated &#64;{@link BeforeAll})
 * of each test, during which we collect a list of violations. The
 * individual JUnit tests are predicates on that list. If the list is
 * empty, that's a pass.
 * <p>
 * Java guarantees "sequentially consistent" execution of correctly
 * synchronised programs, but the tests here are (deliberately)
 * <b>not</b> correctly synchronised. The hypothesis we attempt to
 * disprove by testing is that unsynchronised use of Python objects we
 * provide leads to behaviour not expected of Python.
 * <p>
 * It is challenging to define the allowed or forbidden outcomes. Python
 * has only recently started to allow true concurrency without the GIL
 * and the required behaviours are not yet clearly stated (2025).
 * <p>
 * This test is influenced by the literature on the "litmus tests", used
 * to test or complement assertions made for a processor architecture
 * and to validate language memory models. Listing 16.1 in <i>Java
 * Concurrency in Practice</i>, Peierls, Goetz, et al. is an example of
 * the latter.
 */
@DisplayName("During concurrent use of type objects ...")
@TestMethodOrder(MethodOrderer.MethodName.class)
class TypeConcurrencyTest {

    /** Logger for the tests. */
    static final Logger logger =
            LoggerFactory.getLogger(TypeConcurrencyTest.class);

    /** Threads in each batch. */
    static final int BATCH_SIZE = 5; // ~ 10s

    /** Number of batches. */
    static final int BATCH_COUNT = 3; // ~ 1000s

    /** Curtail a test once we have found this many failures. */
    static final int FAILURE_LIMIT = BATCH_SIZE + 5;

    /**
     * A test fixture that runs two threads defined by the methods
     * {@link #t1()} and {@link #t2()} in a race. The test object itself
     * implements {@link Runnable} so that we can run many copies in
     * parallel (for efficiency and to stress the processor), but it is
     * the pair of threads in each case that constitute the effective
     * "litmus test program".
     * <p>
     * In order to use this class, a subclass defines {@link #t1()} and
     * {@link #t2()} to define the activity, and {@link #allowed()} or
     * {@link #forbidden()} (not usually both) to and determine whether
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
         * behaviour (a pass) or not (a fail). Returns {@code true} by
         * default.
         *
         * @return {@code false} if a fail
         */
        @SuppressWarnings("static-method")
        boolean allowed() { return true; }

        /**
         * Test whether the result of the pair of threads is forbidden
         * behaviour (a fail) or not (a pass). Returns {@code false} by
         * default.
         *
         * @return {@code true} if a fail
         */
        @SuppressWarnings("static-method")
        boolean forbidden() { return false; }

        /**
         * Create and run two threads, one to call {@link #t1()} and the
         * other {@link #t2()}. Both threads wait at a barrier of
         * capacity two, meaning the first waits until the second
         * arrives there, then both proceed to call their respective
         * methods in a race. Afterwards, {@link #allowed()} (must be
         * {@code true}) {@link #forbidden()} (must be {@code false})
         * determine whether the state reached is permitted.
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
         * Return {@code true} if there was an exception during
         * {@link #t1()} or {@link #t2()}, if {@link #allowed()} is
         * {@code false} or if {@link #forbidden()} is {@code true}.
         *
         * @return {@code true} on failure
         */
        boolean failed() {
            return exc1 != null || exc2 != null || !allowed()
                    || forbidden();
        }

        /**
         * Return the test to initial conditions to save creating a
         * fresh one. Only re-define this if that can be validly done,
         * in which case it should return {@code true}. The default
         * return value is {@code false}, which indicates to the caller
         * it should create a new test object.
         */
        @SuppressWarnings("static-method")
        boolean reset() { return false; }
    }

    /**
     * A base class for tests that run multiple litmus tests in parallel
     * and examine the outcomes.
     */
    abstract static class Examination {

        /** Failing litmus tests */
        List<? extends LitmusTest> failures = new LinkedList<>();

        /**
         * This method must be called from the static set-up of each
         * (annotated &#64;{@link BeforeAll}) individual test class, and
         * the returned list be in the (instance) field {@code failures}
         * by a &#64;{@link BeforeEach} method. It is given a factory
         * method ({@code new}) for a litmus test object of the specific
         * kind needed by the test.
         * <p>
         * This is the method that performs most of the work. It returns
         * a list of failing cases (as determined by
         * {@link LitmusTest#failed()}).
         *
         * @param <T> Specific type of litmus test
         * @param newInstance new method of that type (or factory)
         * @return instances where LitmusTest#failed
         * @throws Throwable on uncaught errors (not failed tests)
         */
        static <T extends LitmusTest> List<T>
                race(Supplier<T> newInstance) throws Throwable {

            // Failing litmus tests
            List<T> failures = new LinkedList<>();

            // Ensure the type system exists
            assertTrue(TypeSystem.bootstrapNanoTime > 0L,
                    "Check type system booted");

            // Batch of tests to run concurrently.
            ArrayList<T> tests = new ArrayList<>(BATCH_SIZE);
            Thread[] threads = new Thread[BATCH_SIZE];

            for (int i = 0; i < BATCH_SIZE; i++) {
                tests.add(i, newInstance.get());
            }
            String name = tests.get(0).getClass().getCanonicalName();

            logger.atInfo().setMessage("Race begun ({})")
                    .addArgument(name).log();

            // We use that storage repeatedly with new tests.
            for (int batch = 0; batch < BATCH_COUNT; batch++) {
                // Create threads: each will run a pair of racing
                // threads.
                for (int i = 0; i < BATCH_SIZE; i++) {
                    threads[i] = new Thread(tests.get(i));
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
                    T test = tests.get(i);
                    if (test.failed()) {
                        // Collect failed example and replace in tests.
                        failures.add(test);
                        tests.add(i, newInstance.get());
                    } else if (!test.reset()) {
                        // We could not reset the test object.
                        tests.add(i, newInstance.get());
                    }
                }

                // Make sure they all stop (so the test does).
                boolean allStopped = true;
                for (Thread t : threads) {
                    allStopped &= hasStopped(t);
                }
                assertTrue(allStopped, "Threads were still running");

                // Early exit if ample evidence.
                if (failures.size() >= FAILURE_LIMIT) { break; }
            }
            logger.atInfo().setMessage("Race ended").addArgument(name)
                    .log();

            return failures;
        }

        /**
         * Publish failing litmus tests to this test instance.
         *
         * @param failures resulting from the racing threads
         */
        void setFailures(List<? extends LitmusTest> failures) {
            this.failures = failures;
        }

        private static boolean hasStopped(Thread t) {
            if (t.isAlive()) {
                logger.warn("Still running {}", t.getName());
                return false;
            }
            return true;
        }

        /** Check for tests that ended with an exceptions. */
        @Test
        @DisplayName("No exceptions were thrown")
        void checkException() {
            for (LitmusTest t : failures) {
                assertNull(t.exc1, () -> String
                        .format("thread t1 threw %s", t.exc1));
                assertNull(t.exc2, () -> String
                        .format("thread t2 threw %s", t.exc2));
            }
        }

        /** Check the outcome was allowed. */
        @Test
        @DisplayName("Only allowed states were reached")
        void checkAllowed() throws PyAttributeError, Throwable {
            for (LitmusTest t : failures) {
                if (!t.allowed()) { fail(t.toString()); }
            }
        }
    }

    /**
     * Extend {@link LitmusTest} for a single type object name space as
     * the "store". The subclass should add attributes "A" and "B" in a
     * race and expect a sequentially consistent result.
     */
    static abstract class SingleTypeLitmusTest extends LitmusTest {
        /** Type object to use as a namespace. */
        PyType type;
        /** Variables set by the racing threads (registers). */
        Object r1, r2;

        SingleTypeLitmusTest() {
            try {
                this.type = (PyType)PyType.TYPE().call("LitmusTestType",
                        Py.tuple(PyObject.TYPE), Py.dict());
            } catch (Throwable e) {
                fail("Constructing an instance");
            }
        }

        @Override
        boolean reset() {
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
            return true;
        }

        @Override
        public String toString() {
            return String.format("[type=%s, r1=%s, r2=%s]", type, r1,
                    r2);
        }
    }

    /**
     * The Load Buffering litmus test interpreted for a single type
     * object name space as the "store". We add attributes in a race and
     * expect a sequentially consistent result.
     */
    @Nested
    @DisplayName("Load Buffering (LB) single type")
    class SingleTypeLB extends Examination {

        /** Failing litmus tests */
        static List<? extends LitmusTest> failures;

        /** Run the set-up and collect failing cases. */
        @BeforeAll
        static void setUpClass() throws Throwable {
            failures = Examination.race(Litmus::new);
        }

        @BeforeEach
        void getFailures() { setFailures(failures); }

        /**
         * Define LB for type object name space as the "store". We add
         * attributes in a race and expect a sequentially consistent
         * result.
         */
        static class Litmus extends SingleTypeLitmusTest {

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
        }
    }

    /**
     * The Storage Buffering litmus test interpreted for a single type
     * object name space as the "store". We add attributes in a race and
     * expect a sequentially consistent result.
     */
    @Nested
    @DisplayName("Store Buffering (SB) single type")
    class SingleTypeSB extends Examination {

        /** Failing litmus tests */
        static List<? extends LitmusTest> failures;

        /** Run the set-up and collect failing cases. */
        @BeforeAll
        static void setUpClass() throws Throwable {
            failures = Examination.race(Litmus::new);
        }

        @BeforeEach
        void getFailures() { setFailures(failures); }

        /**
         * Define SB for a type object name space as the "store". We add
         * attributes in a race and expect a sequentially consistent
         * result.
         */
        static class Litmus extends SingleTypeLitmusTest {
            @Override
            void t1() throws Throwable {
                Abstract.setAttr(type, "B", 2);
                r1 = Abstract.lookupAttr(type, "A");
            }

            @Override
            void t2() throws Throwable {
                Abstract.setAttr(type, "A", 1);
                r2 = Abstract.lookupAttr(type, "B");
            }

            @Override
            boolean forbidden() {
                // Sequentially consistent answer.
                return r1 == null && r2 == null;
            }
        }
    }
}
