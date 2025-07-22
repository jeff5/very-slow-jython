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
 */
@DisplayName("When multiple threads publish type objects")
@TestMethodOrder(MethodOrderer.MethodName.class)
class TypeConcurrencyTest {

    /** Logger for the test. */
    static final Logger logger =
            LoggerFactory.getLogger(TypeConcurrencyTest.class);

    /** Threads in total. &gt;&gt; {@code setUpClass().NCASES} */
    static final int NTHREADS = 10;

    /** If defined, dump the times recorded by threads. */
    static final String DUMP_PROPERTY =
            "uk.co.farowl.vsj4.runtime.TypeConcurrencyTest.times";

    /**
     * Check this many threads actually concurrent. Ideally
     * &gt;{@code setUpClass().NCASES} but expectation depends on CPU.
     */
    static int MIN_THREADS =
            Math.min(Runtime.getRuntime().availableProcessors(), 10);

    /** Random (or deterministic) order. */
    // static final long seed = System.currentTimeMillis();
    static final long seed = 1234L; // Somewhat repeatable

    /** Threads to run. */
    static final List<AccessThread> threads = new ArrayList<>();
    /** A barrier they all wait behind. */
    static CyclicBarrier barrier;
    /** Source of random behaviour. */
    static Random random = new Random(seed);

    /** Time the first thread completed its first action. */
    static long referenceNanoTime;

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
                case 0 -> new AccessThread(k) {
                    @Override
                    void action() throws Throwable {
                        type = MyList.TYPE;
                        String name = "a" + tid;
                        Abstract.setAttr(type, name, k);
                        attrsAdded.put(name, k);
                    }
                };

                case 1 -> new AccessThread(k) {
                    @Override
                    void action() throws Throwable {
                        type = MyList2.TYPE;
                        String name = "b" + tid;
                        Abstract.setAttr(type, name, k);
                        attrsAdded.put(name, k);
                    }
                };

                // --------------- not used from here: see NCASES

                case 2 -> new AccessThread(k) {
                    @Override
                    void action() { type = PyBaseException.TYPE; }
                };

                default -> new AccessThread(k) {
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
        referenceNanoTime = threads.get(0).firstNanoTime;
        for (AccessThread at : threads) {
            at.subtractTime(referenceNanoTime);
        }

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

    /** Some threads started before the first action completed. */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("A race takes place")
    void aRaceTookPlace() {
        // Enough relative start times should be negative
        long competitors = threads.stream()
                .filter(t -> t.startNanoTime <= 0L).count();
        logger.info("{} threads were racing.", competitors);
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
    @DisplayName("__repr__ seen if present")
    void checkRepr() throws PyAttributeError, Throwable {
        for (AccessThread at : threads) {
            PyType t = at.type;
            /*
             * Failure here is a sign that this thread was allowed a
             * lookup (see otherActions()) before t was Python ready.
             */
            assertSame(t.lookup("__repr__"), at.reprMethod,
                    "check '__repr__' was seen");
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
    @DisplayName("__call__ seen if present")
    void checkCall() throws PyAttributeError, Throwable {
        for (AccessThread at : threads) {
            PyType t = at.type;
            /*
             * Failure here is a sign that this thread was allowed a
             * lookup (see otherActions()) before t was Python ready.
             */
            assertSame(t.lookup("__call__"), at.callMethod,
                    "check '__call__' was seen");
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
                    "check 'foo' was seen");
            assertSame(t.lookup("bar"), at.barMethod,
                    "check 'bar' was seen");
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
        /** Time this thread started. */
        long startNanoTime;
        /** Time this thread completed {@code action()}. */
        long firstNanoTime;
        /** Time this thread completed {@code otherActions()}. */
        long finishNanoTime;
        /** The value seen in {@code TYPE}. */
        PyType type;
        /** Attribute name suffix unique-ish to thread. */
        final String tid;
        /** Attribute names added by this thread to its type. */
        final Map<String, Object> attrsAdded = new HashMap<>();

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
         * specific definition of {@link #action()}.
         *
         * @param choice of type for this thread
         */
        AccessThread(int choice) {
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
        }

        /** The required actions apart from the one already done. */
        void otherActions() {
            BaseType t = (BaseType)type;
            ready = t.hasFeature(KernelTypeFlag.READY);
            features = t.getFeatures();
            fooMethod = t.lookup("foo");
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
