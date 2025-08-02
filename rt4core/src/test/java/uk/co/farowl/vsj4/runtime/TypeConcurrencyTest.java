// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

import uk.co.farowl.vsj4.runtime.Exposed.PythonMethod;
import uk.co.farowl.vsj4.runtime.kernel.BaseType;
import uk.co.farowl.vsj4.runtime.kernel.KernelTypeFlag;

/**
 * This is a variant of {@link BootstrapTest} focusing on competition to
 * create and use non-bootstrap types in Java. We test that when threads
 * create (and therefore publish) types they are either Python-ready
 * from the start or properly protected by locks that block attribute
 * access.
 * <p>
 * We shall start a lot of threads, as close to simultaneously as we can
 * manage, that each access a type that the type system will not have
 * been seen before.
 * <p>
 * We have each thread make observation and attempt operations on the
 * type that should be consistent with that type's final state. The
 * thread race occurs during {@link #setUpClass()}. Tests (JUnit tests)
 * run after the threads become joined, and take arbitrarily long, so
 * they have to operate on observations made "hot" during the race.
 * <p>
 * Each thread records when it started and when it got its answer. For a
 * satisfactory test, multiple threads should be racing before the first
 * thread completes its first action so we have a genuine race. (This is
 * less likely on hosts with few CPUs.)
 *
 * @implNote A comparison of tabulated times for this test run in
 *     isolation (in an IDE, say) and in bulk regression tests (under
 *     Gradle, say) will show a substantial difference in the time
 *     elapsed between thread start and first action complete. During
 *     this time, a type has to be constructed (e.g.
 *     {@link MyList#type}). In an isolated test, this includes creating
 *     the base type (here {@code list}), whereas in the batch tests it
 *     tends to exist already. Do not including pre-existing types as
 *     actual thread targets since their threads may complete without
 *     racing.
 */
@DisplayName("When multiple threads publish type objects")
@TestMethodOrder(MethodOrderer.MethodName.class)
class TypeConcurrencyTest {

    /** Logger for the test. */
    static final Logger logger =
            LoggerFactory.getLogger(TypeConcurrencyTest.class);

    /** Threads in total. &gt;&gt; {@code setUpClass().NCASES} */
    static final int NTHREADS = 20;

    /** If defined, dump the times recorded by threads. */
    static final String DUMP_PROPERTY =
            "uk.co.farowl.vsj4.runtime.TypeConcurrencyTest.times";

    /**
     * Check this many threads actually concurrent. Ideally
     * &gt;{@code setUpClass().NCASES} but expectation depends on CPUs.
     */
    static int MIN_THREADS =
            Math.min(Runtime.getRuntime().availableProcessors(),
                    NTHREADS) / 2;

    /** Random (or deterministic) order. */
    static final long seed = System.currentTimeMillis();
    // static final long seed = 12345L; // Somewhat repeatable

    /** Threads to run. */
    static final List<AccessThread> threads = new ArrayList<>();
    /** A barrier they all wait behind. */
    static CyclicBarrier barrier;
    /** Source of random behaviour. */
    static Random random = new Random(seed);

    /** Time the {@link #barrierNanoTime} triggered thread starts. */
    static long barrierNanoTime;

    /** Time the first thread completed its first action. */
    static long refNanoTime;

    /**
     * We create multiple threads for each behavioural sequence and run
     * them concurrently. They will make observations of the types that
     * ought only to be possible definitely after each is Python-ready.
     * Each will note the high-resolution time at which it began and was
     * able to complete its first action, and complete its other
     * actions.
     */
    @BeforeAll
    static void setUpClass() {
        // Ensure the type system exists
        assertTrue(TypeSystem.bootstrapNanoTime > 0L,
                "Check type system booted");
        // Match the number of cases. (We keep adding to them.)
        final int NCASES = 2;
        // Create NTHREADS choosing which action comes first.
        for (int i = 0; i < NTHREADS; i++) {
            int k = i % NCASES;
            threads.add(switch (k) {
                case 0 -> new AccessThread(k, MyList.class) {
                    @Override
                    void action() throws Throwable {
                        type = MyList.TYPE;
                        String name = "a" + tid;
                        Abstract.setAttr(type, name, k);
                        attrsAdded.put(name, k);
                    }
                };

                case 1 -> new AccessThread(k, MyList2.class) {
                    @Override
                    void action() throws Throwable {
                        type = MyList2.TYPE;
                        String name = "b" + tid;
                        Abstract.setAttr(type, name, k);
                        attrsAdded.put(name, k);
                    }
                };

                // --------------- not used from here: see NCASES.
                // See implNote concerning pre-existing types.

                case 2 -> new AccessThread(k, PyNameError.class) {
                    @Override
                    void action() { type = PyNameError.TYPE; }
                };

                default -> new AccessThread(k, PyObject.class) {
                    @Override
                    void action() { type = PyObject.TYPE; }
                };
            });
        }

        // Create a barrier of matching capacity.
        barrier = new CyclicBarrier(threads.size(),
                () -> barrierNanoTime = System.nanoTime());

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
        for (Thread t : threads) { allStopped &= hasStopped(t); }
        assertTrue(allStopped, "Threads were still running");

        // Now sort them by the time the first action completed
        Comparator<AccessThread> byFirst = new Comparator<>() {

            @Override
            public int compare(AccessThread t1, AccessThread t2) {
                return Long.compare(t1.firstNanoTime, t2.firstNanoTime);
            }
        };
        Collections.sort(threads, byFirst);

        // Make the winning thread the reference time for the test
        refNanoTime = threads.get(0).firstNanoTime;
        for (AccessThread at : threads) {
            at.subtractTime(refNanoTime);
        }

        // Dump the thread times by start time.
        // TODO Make dump conditional again (once test reliable on CI)
        // if (truthy(DUMP_PROPERTY)) { dumpThreads(); }
        dumpThreads();
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
        double r = (TypeSystem.readyNanoTime - refNanoTime) * 1e-6;
        String fmt = "%42s   =%10.4f  (relative ms)\n";
        System.out.printf(fmt, "Type system ready at", r);
        for (AccessThread t : threads) { System.out.println(t); }
    }

    private static boolean hasStopped(Thread t) {
        if (t.isAlive()) {
            logger.warn("Still running {}", t.getName());
            // t.stop();
            return false;
        }
        return true;
    }

    /** All threads completed. */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("All threads complete")
    void allComplete() {
        long completed = threads.stream()
                .filter(t -> t.finishNanoTime > 0L).count();
        assertTrue(completed == threads.size(),
                () -> String.format("%d threads did not complete.",
                        threads.size() - completed));
    }

    /** Enough threads started before the first action completed. */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("A race takes place")
    void aRaceTookPlace() {
        // Enough relative start times should be negative
        long competitors = threads.stream()
                .filter(t -> t.startNanoTime <= 0L).count();
        logger.info(
                "{} threads were racing. (Min {} on this platform.)",
                competitors, MIN_THREADS);
        assertFalse(competitors < MIN_THREADS, () -> String
                .format("Detect < %d competitors.", MIN_THREADS));
    }

    /** All the threads saw a ready state on their type. */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("All threads see their type ready")
    void readyState() {
        for (AccessThread at : threads) {
            assertTrue(at.ready, "Check READY");
        }
    }

    /** All the threads see the final feature flags. */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("All threads see the final feature flags")
    void finalFeatures() {
        for (AccessThread at : threads) {
            PyType t = at.type;
            assertEquals(t.getFeatures(), at.features);
        }
    }

    /**
     * All the threads saw method "__repr__" when it was looked up
     * during the race.
     *
     * @throws PyAttributeError if attribute lookup fails
     * @throws Throwable if something else goes wrong
     */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("__repr__ is seen")
    void checkRepr() throws PyAttributeError, Throwable {
        for (AccessThread at : threads) {
            PyType t = at.type;
            /*
             * Failure here is a sign that this thread was allowed a
             * lookup (see otherActions()) before t was Python ready.
             */
            assertSame(t.lookup("__repr__"), at.reprMethod,
                    () -> "check '__repr__' in " + t.getName());
        }
    }

    /**
     * All the threads saw method "__call__" when it was looked up
     * during the race.
     *
     * @throws PyAttributeError if attribute lookup fails
     * @throws Throwable if something else goes wrong
     */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("__call__ is seen")
    void checkCall() throws PyAttributeError, Throwable {
        for (AccessThread at : threads) {
            PyType t = at.type;
            /*
             * Failure here is a sign that this thread was allowed a
             * lookup (see otherActions()) before t was Python ready.
             */
            assertSame(t.lookup("__call__"), at.callMethod,
                    () -> "check '__call__' in " + t.getName());
        }
    }

    /**
     * All the threads saw methods "foo" and "bar", where they exist for
     * the type, when they were looked up during the race.
     *
     * @throws PyAttributeError if attribute lookup fails
     * @throws Throwable if something else goes wrong
     */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("foo and bar are seen if present")
    void checkFooBar() throws PyAttributeError, Throwable {
        for (AccessThread at : threads) {
            PyType t = at.type;
            /*
             * Failure here is a sign that this thread was allowed a
             * lookup (see otherActions()) before t was Python ready.
             */
            assertSame(t.lookup("foo"), at.fooMethod,
                    () -> "check 'foo' in " + t.getName());
            assertSame(t.lookup("bar"), at.barMethod,
                    () -> "check 'bar' in " + t.getName());
        }
    }

    /**
     * All the threads see attributes added to a given type by every
     * thread that referenced it.
     *
     * @throws PyAttributeError if attribute lookup fails
     * @throws Throwable if something else goes wrong
     */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("Attributes added by any thread are present")
    void checkAttrs() throws PyAttributeError, Throwable {
        for (AccessThread at : threads) {
            for (Entry<String, Object> e : at.attrsAdded.entrySet()) {
                assertSame(e.getValue(),
                        Abstract.getAttr(at.type, e.getKey()));
            }
        }
    }

    /**
     * A thread that performs actions on the types, so that we may test
     * the outcome is the same for all threads. We keep track of times
     * in nanoseconds so we can be sure of the order of events. All
     * times will be adjusted relative to the earliest
     * {@link #firstNanoTime} so that they are reasonably printable.
     */
    static abstract class AccessThread extends Thread {
        /** The primary class of the type this thread addresses. */
        final Class<?> klass;

        /** Time this thread started. */
        long startNanoTime;
        /** Time this thread completed {@code action()}. */
        long firstNanoTime;
        /** Time this thread completed {@code otherActions()}. */
        long finishNanoTime;
        /** Attribute name suffix unique-ish to thread. */
        final String tid;
        /** Attribute names added by this thread to its type. */
        final Map<String, Object> attrsAdded = new HashMap<>();

        /** The value seen in {@code TYPE}. */
        PyType type;
        /** Type was ready when inspected. */
        boolean ready;
        /** Type features (flags) when inspected. */
        EnumSet<TypeFlag> features;
        /** Attribute "_repr__" when inspected. */
        Object reprMethod;
        /** Attribute "__call__" when inspected. */
        Object callMethod;
        /** Attribute "foo" when inspected. */
        Object fooMethod;
        /** Attribute "bar" when inspected. */
        Object barMethod;

        /**
         * Create a {@code Thread} to run. We specialise this with a
         * specific definition of {@link #action()}. Providing a
         * reference to a class here does <i>not</i> statically
         * initialise it.
         *
         * @param choice of type for this thread
         */
        AccessThread(int choice, Class<?> klass) {
            this.klass = klass;
            this.tid = String.format("_%03d",
                    Thread.currentThread().getId() % 1000L);
        }

        /**
         * Each implementation of {@code InitThread} retrieves the same
         * data, but chooses to do one action first by overriding this
         * method. {@link #otherActions()} then completes the work.
         *
         * @throws Throwable from failing API calls
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
            /*
             * Perform the action: recording *raw* nanos before and
             * after. On small platforms, as well as MIN_THREADS being
             * small, we may have to use the barrier time as the
             * effective start time to weaken the race test.
             */
            startNanoTime = MIN_THREADS < 4 ? barrierNanoTime
                    : System.nanoTime();
            try {
                action();
            } catch (Throwable e) {
                logger.atWarn().setMessage("action() threw {}")
                        .addArgument(e).log();
            }
            firstNanoTime = System.nanoTime();
            otherActions();
            finishNanoTime = System.nanoTime();
        }

        /** The required actions apart from the one already done. */
        void otherActions() {
            BaseType t = (BaseType)type;
            ready = t.hasFeature(KernelTypeFlag.READY);
            features = t.getFeatures();
            reprMethod = t.lookup("__repr__");
            callMethod = t.lookup("__call__");
            fooMethod = t.lookup("foo");
            barMethod = t.lookup("bar");
        }

        /**
         * Adjust times backwards by the given amount.
         *
         * @param ref to subtract from each recorded nanosecond time
         */
        void subtractTime(long ref) {
            startNanoTime -= ref;
            firstNanoTime -= ref;
            finishNanoTime -= ref;
        }

        @Override
        public String toString() {
            String fmt =
                    "%20s  start=%10.4f, first=%10.4f, finish=%10.4f (%5dns)";
            return String.format(fmt, type.getName(),
                    startNanoTime * 1e-6, firstNanoTime * 1e-6,
                    finishNanoTime * 1e-6,
                    finishNanoTime - firstNanoTime);
        }
    }

    /** A user-defined type */
    private static class MyList extends PyList {
        final static PyType TYPE = PyType
                .fromSpec(new TypeSpec("MyList", MethodHandles.lookup())
                        .base(PyList.TYPE));

        @PythonMethod
        int foo() { return 1; }
    }

    /** A user-defined type */
    private static class MyList2 extends MyList {
        final static PyType TYPE = PyType.fromSpec(
                new TypeSpec("MyList2", MethodHandles.lookup())
                        .base(MyList.TYPE));

        @Override
        @PythonMethod
        int foo() { return 42; }

        @PythonMethod
        double bar(int i) { return 10.0 * foo(); }
    }
}
