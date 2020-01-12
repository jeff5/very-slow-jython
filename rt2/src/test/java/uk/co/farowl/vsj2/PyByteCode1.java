package uk.co.farowl.vsj2;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * A test of the basic types supporting a byte code interpreter,
 * implemented after CPython's, and extending so far only to some simple
 * constant loading and data movement..
 */
class PyByteCode1 {

    /** All Python object implementations implement this interface. */
    interface PyObject {}

    /*
     * Some basic built-in types
     */

    /** The Python {@code int} object. */
    static class PyLong implements PyObject {
        final BigInteger value;
        PyLong(BigInteger value) { this.value = value; }
        PyLong(long value) { this.value = BigInteger.valueOf(value); }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PyLong) {
                PyLong other = (PyLong) obj;
                return other.value.equals(this.value);
            } else
                return false;
        }
    }

    /** The Python {@code float} object. */
    static class PyFloat implements PyObject {
        final double value;
        PyFloat(double value) { this.value = value; }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PyFloat) {
                PyFloat other = (PyFloat) obj;
                return other.value == this.value;
            } else
                return false;
        }
    }

    /** The Python {@code str} object. */
    static class PyUnicode implements PyObject {
        final String value; // only supporting BMP for now
        PyUnicode(String value) { this.value = value; }
        @Override
        public int hashCode() { return value.hashCode(); }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PyUnicode) {
                PyUnicode other = (PyUnicode) obj;
                return other.value.equals(this.value);
            } else
                return false;
        }
    }

    /** The Python {@code bytes} object. */
    static class PyBytes implements PyObject {
        final byte[] value;
        PyBytes(byte... value) {
            this.value = new byte[value.length];
            System.arraycopy(value, 0, this.value, 0, value.length);
        }
    }

    /** The Python {@code tuple} object. */
    static class PyTuple implements PyObject {
        final PyObject[] value;
        PyTuple(PyObject... value) {
            this.value = new PyObject[value.length];
            System.arraycopy(value, 0, this.value, 0, value.length);
        }
        public PyObject getItem(int i) { return value[i]; }
    }

    /**
     * The Python {@code dict} object. The Java API is provided directly by
     * the base class implementing {@code Map}, while the Python API has
     * been implemented on top of the Java one.
     */
    static class PyDictionary extends HashMap<PyObject, PyObject>
            implements PyObject {}

    /** The Python {@code exception} object. */
    static class PyException extends RuntimeException implements PyObject {
        public PyException(String msg, Object... args) {
            super(String.format(msg, args));
        }
    }

    /** Runtime */
    static class Py {

        private static class Singleton implements PyObject {
            String name;
            Singleton(String name) { this.name = name; }
            @Override
            public String toString() { return name; }
        }

        /** Python {@code None} object. */
        static final PyObject None = new Singleton("None");
    }

    /**
     * Our equivalent to the Python code object ({@code PyCodeObject} in
     * CPython's C API).
     */
    static class PyCode implements PyObject {

        /** Characteristics of a PyCode (as CPython co_flags). */
        enum Trait {
            OPTIMIZED, NEWLOCALS, VARARGS, VARKEYWORDS, NESTED, GENERATOR,
            NOFREE, COROUTINE, ITERABLE_COROUTINE, ASYNC_GENERATOR
        }

        final EnumSet<Trait> traits;

        /** Number of positional arguments (not counting *args). */
        final int argcount;
        /** Number of positional-only arguments. */
        final int posonlyargcount;
        /** Number of keyword-only arguments. */
        final int kwonlyargcount;
        /** Number of local variables. */
        final int nlocals;
        /** Number of entries needed for evaluation stack. */
        final int stacksize;
        /** int expression of {@link #traits} compatible with CPython. */
        final int flags;
        /** First source line number. */
        final int firstlineno;
        /** Instruction opcodes */
        final PyBytes code;
        /** Constant objects needed by the code */
        final PyTuple consts;
        /** Names referenced in the code */
        final PyTuple names;
        /** Args and non-cell locals */
        final PyTuple varnames;
        /** Names referenced but not defined here */
        final PyTuple freevars;
        /** Names defined here and referenced elsewhere */
        final PyTuple cellvars;
        /* ---------------------- See CPython code.h ------------------ */
        /** Maps cell indexes to corresponding arguments. */
        final int[] cell2arg;
        /** Where it was loaded from */
        final PyUnicode filename;
        /** Name of function etc. */
        final PyUnicode name;

        /** Encodes the address to/from line number mapping */
        final PyBytes lnotab;

        /* Masks for co_flags above */
        public static final int CO_OPTIMIZED = 0x0001;
        public static final int CO_NEWLOCALS = 0x0002;
        public static final int CO_VARARGS = 0x0004;
        public static final int CO_VARKEYWORDS = 0x0008;
        public static final int CO_NESTED = 0x0010;
        public static final int CO_GENERATOR = 0x0020;
        /*
         * The CO_NOFREE flag is set if there are no free or cell
         * variables. This information is redundant, but it allows a single
         * flag test to determine whether there is any extra work to be
         * done when the call frame it setup.
         */
        public static final int CO_NOFREE = 0x0040;

        /*
         * The CO_COROUTINE flag is set for coroutine functions (defined
         * with ``async def`` keywords)
         */
        public static final int CO_COROUTINE = 0x0080;
        public static final int CO_ITERABLE_COROUTINE = 0x0100;
        public static final int CO_ASYNC_GENERATOR = 0x0200;

        /**
         * Full constructor based on CPython's
         * {@code PyCode_NewWithPosOnlyArgs}.
         */
        public PyCode( //
                int argcount,           // co_argcount
                int posonlyargcount,    // co_posonlyargcount
                int kwonlyargcount,     // co_kwonlyargcount
                int nlocals,            // co_nlocals
                int stacksize,          // co_stacksize
                int flags,              // co_flags

                PyBytes code,           // co_code

                PyTuple consts,         // co_consts

                PyTuple names,          // names ref'd in code
                PyTuple varnames,       // args and non-cell locals
                PyTuple freevars,       // ref'd here, def'd outer
                PyTuple cellvars,       // def'd here, ref'd nested

                PyUnicode filename,     // loaded from
                PyUnicode name,         // of function etc.
                int firstlineno,        // of source
                PyBytes lnotab          // map opcode address to source
        ) {
            this.argcount = argcount;
            this.posonlyargcount = posonlyargcount;
            this.kwonlyargcount = kwonlyargcount;
            this.nlocals = nlocals;

            this.stacksize = stacksize;
            this.flags = flags;
            this.code = code;
            this.consts = consts;

            this.names = names(names);
            this.varnames = names(varnames);
            this.freevars = names(freevars);
            this.cellvars = names(cellvars);

            this.cell2arg = null;

            this.filename = filename;
            this.name = name;
            this.firstlineno = firstlineno;
            this.lnotab = lnotab;

            this.traits = traitsFrom(flags);
        }

        /**
         * Create a {@code PyFrame} suitable to execute this {@code PyCode}
         * (adequate for module-level code).
         *
         * @param tstate thread context (supplied the call stack)
         * @param globals name space top treat as global variables
         * @param locals name space top treat as local variables
         * @return the frame
         */
        PyFrame createFrame(ThreadState tstate, PyDictionary globals,
                PyObject locals) {
            return new CPythonFrame(tstate, this, globals, locals);
        }

        /** Check that all the objects in the tuple are {@code str}. */
        private static PyTuple names(PyTuple tuple) {
            for (PyObject name : tuple.value) {
                if (!(name instanceof PyUnicode))
                    throw new IllegalArgumentException(
                            String.format("Non-unicode name: {}", name));
            }
            return tuple;
        }

        /**
         * Convert a CPython-style {@link #flags} specifier to
         * {@link #traits}.
         */
        private static EnumSet<Trait> traitsFrom(int flags) {
            ArrayList<Trait> traits = new ArrayList<>();
            for (int m = 1; flags != 0; m <<= 1) {
                switch (m & flags) {
                    case 0:
                        break; // When bit not set in flag.
                    case CO_OPTIMIZED:
                        traits.add(Trait.OPTIMIZED);
                        break;
                    case CO_NEWLOCALS:
                        traits.add(Trait.NEWLOCALS);
                        break;
                    case CO_VARARGS:
                        traits.add(Trait.VARARGS);
                        break;
                    case CO_VARKEYWORDS:
                        traits.add(Trait.VARKEYWORDS);
                        break;
                    case CO_NESTED:
                        traits.add(Trait.NESTED);
                        break;
                    case CO_GENERATOR:
                        traits.add(Trait.GENERATOR);
                        break;
                    case CO_NOFREE:
                        traits.add(Trait.NOFREE);
                        break;
                    case CO_COROUTINE:
                        traits.add(Trait.COROUTINE);
                        break;
                    case CO_ITERABLE_COROUTINE:
                        traits.add(Trait.ITERABLE_COROUTINE);
                        break;
                    case CO_ASYNC_GENERATOR:
                        traits.add(Trait.ASYNC_GENERATOR);
                        break;
                    default:
                        throw new IllegalArgumentException(
                                "Undefined bit set in 'flags' argument");
                }
                // Ensure the bit we just tested is clear
                flags &= ~m;
            }
            return EnumSet.copyOf(traits);
        }

        @Override
        public String toString() {
            return String.format("<code %s %s>", name, traits);
        }
    }

    /** Holder for objects appearing in the closure of a function. */
    static class Cell {
        PyObject obj;
        Cell(PyObject obj) { this.obj = obj; }
        @Override
        public String toString() {
            return String.format("<cell [%.80s]>", obj);
        }
        static final Cell[] EMPTY_ARRAY = new Cell[0];
    }

    /**
     * Represents a platform thread (that is, a Java {@code Thread})
     * internally to the interpreter.
     */
    // Used here only to represent the stack of frames.
    static class ThreadState {
        /** The top frame of the call stack. */
        PyFrame frame = null;
        /**
         * The Java {@code Thread} where this {@code ThreadState} was
         * created
         */
        final Thread thread;
        // Missing: exception support (main. generators and co-routines).
        // Missing: hooks for _threadmodule (join, resources, etc.).
        PyFrame swap(PyFrame frame) {
            PyFrame prevFrame = this.frame;
            this.frame = frame;
            return prevFrame;
        }
        ThreadState() { this.thread = Thread.currentThread(); }
    }

    /** A {@code PyFrame} is the context for the execution of code. */
    private static abstract class PyFrame implements PyObject {

        /** ThreadState owning this frame. */
        protected final ThreadState tstate;
        /** Frames form a stack by chaining through the back pointer. */
        PyFrame back;
        /** Code this frame is to execute. */
        final PyCode code;
        /** Built-in objects */
        final PyDictionary builtins;
        /** Global context (name space) of execution. */
        final PyDictionary globals;
        /** Local context (name space) of execution. (Assign if needed.) */
        Map<PyObject, PyObject> locals = null;

        /**
         * Partial constructor, leaves {@link #locals} {@code null}.
         * Establishes the back-link to the current stack top but does not
         * make this frame the stack top. ({@link #eval()} should do that.)
         *
         * @param tstate thread state (supplies link to previous frame)
         * @param code that this frame executes
         * @param globals global name space
         */
        PyFrame(ThreadState tstate, PyCode code, PyDictionary globals) {
            this.tstate = tstate;
            this.code = code;
            this.back = tstate.frame; // NB not pushed until eval()
            this.globals = globals;
            // globals.get("__builtins__") ought to be a module with dict:
            this.builtins = new PyDictionary();
        }

        /**
         * Foundation constructor on which subclass constructors rely.
         *
         * <ul>
         * <li>If the code has the trait {@link PyCode.Trait#NEWLOCALS} the
         * {@code locals} argument is ignored.</li>
         * <li>If the code has the trait {@link PyCode.Trait#NEWLOCALS} but
         * not {@link PyCode.Trait#OPTIMIZED}, a new empty ``dict`` will be
         * provided as locals.</li>
         * <li>If the code has the traits {@link PyCode.Trait#NEWLOCALS}
         * and {@link PyCode.Trait#OPTIMIZED}, {@code this.locals} will be
         * {@code null} until set by the sub-class.</li>
         * <li>Otherwise, if the argument {@link #locals} is not
         * {@code null} it specifies {@code this.locals}, and</li>
         * <li>if the argument {@link #locals} is {@code null}
         * {@code this.locals} will be the same as {@code globals}.</li>
         * </ul>
         *
         * @param tstate thread state (supplies back)
         * @param code that this frame executes
         * @param globals global name space
         * @param locals local name space (or it may be {@code globals})
         */
        protected PyFrame(ThreadState tstate, PyCode code,
                PyDictionary globals, PyObject locals) {

            // Initialise the basics.
            this(tstate, code, globals);

            // The need for a dictionary of locals depends on the code
            EnumSet<PyCode.Trait> traits = code.traits;
            if (traits.contains(PyCode.Trait.NEWLOCALS)) {
                // Ignore locals argument
                if (traits.contains(PyCode.Trait.OPTIMIZED)) {
                    // We can create it later but probably won't need to
                    this.locals = null;
                } else {
                    this.locals = new PyDictionary();
                }
            } else if (locals == null) {
                // Default to same as globals.
                this.locals = globals;
            } else {
                /*
                 * Use supplied locals. As it may not implement j.u.Map, we
                 * should arrange to wrap any Python object supporting the
                 * right methods as a Map<>, but later.
                 */
                this.locals = (Map<PyObject, PyObject>) locals;
            }
        }

        /** Execute the code in this frame. */
        abstract PyObject eval();
    }

    private static class Opcode {
        static final byte RETURN_VALUE = 83;
        static final byte STORE_NAME = 90;
        static final byte LOAD_CONST = 100;
        static final byte LOAD_NAME = 101;
    }

    /** A {@link PyFrame} for executing CPython 3.8 byte code. */
    private static class CPythonFrame extends PyFrame {

        /** Cells for free variables (used not created in this code). */
        final Cell[] freevars;
        /** Cells for local cell variables (created in this code). */
        final Cell[] cellvars;
        /** Local simple variables (corresponds to "varnames"). */
        final PyObject[] fastlocals;
        /** Value stack. */
        final PyObject[] valuestack;
        /** Index of first empty space on the value stack. */
        int stacktop = 0;

        /** Assigned eventually by return statement (or stays None). */
        PyObject returnValue = Py.None;

        private static String NAME_ERROR_MSG =
                "name '%.200s' is not defined";

        /**
         * Create a {@code CPythonFrame}, which is a {@code PyFrame} with
         * the storage and mechanism to execute code. The constructor
         * specifies the back-reference to the current frame (which is
         * {@code null} when this frame is first in the stack) via the
         * {@code ThreadState}. No other argument may be {@code null}.
         *
         * The caller specifies the local variables dictionary explicitly:
         * it may be the same as the {@code globals}.
         *
         * @param tstate thread state (supplies back)
         * @param code that this frame executes
         * @param globals global name space
         * @param locals local name space
         */
        CPythonFrame(ThreadState tstate, PyCode code, PyDictionary globals,
                PyObject locals) {
            super(tstate, code, globals, locals);
            this.valuestack = new PyObject[code.stacksize];
            this.fastlocals = null;
            this.freevars = null;
            this.cellvars = null;
        }

        @Override
        PyObject eval() {
            // Push this frame to stack
            back = tstate.swap(this);
            // Evaluation stack index
            int sp = this.stacktop;
            // Cached references from code
            PyTuple names = code.names;
            PyTuple consts = code.consts;
            byte[] inst = code.code.value;
            // Get first instruction
            byte opcode = inst[0];
            int oparg = inst[1] & 0xff;
            int ip = 2;
            // Local variables used repeatedly in the loop
            PyObject name, v;

            loop : for (;;) {

                // Interpret opcode
                switch (opcode) {

                    case Opcode.RETURN_VALUE:
                        returnValue = valuestack[--sp]; // POP
                        break loop;

                    case Opcode.STORE_NAME:
                        name = names.getItem(oparg);
                        v = valuestack[--sp]; // POP
                        if (locals == null)
                            throw new PyException(
                                    "no locals found when storing '%s'",
                                    name);
                        locals.put(name, v);
                        break;

                    case Opcode.LOAD_CONST:
                        v = consts.getItem(oparg);
                        valuestack[sp++] = v; // PUSH
                        break;

                    case Opcode.LOAD_NAME:
                        name = names.getItem(oparg);

                        if (locals == null)
                            throw new PyException(
                                    "no locals found when loading '%s'",
                                    name);
                        v = locals.get(name);
                        if (v == null) {
                            v = globals.get(name);
                            if (v == null) {
                                v = builtins.get(name);
                                if (v == null)
                                    throw new PyException(NAME_ERROR_MSG,
                                            name);
                            }
                        }
                        valuestack[sp++] = v; // PUSH
                        break;

                    default:
                        throw new PyException("ip: %d, opcode: %d", ip - 2,
                                opcode);
                }

                // Pick up the next instruction
                opcode = inst[ip];
                oparg = inst[ip + 1] & 0xff;
                ip += 2;
            }

            tstate.swap(back);
            return returnValue;
        }
    }

    // --------------------- Generated Tests -----------------------
    // Code generated by py_byte_code1.py

    /**
     * Example 'load_store_name': <pre>
     * a = b
     * b = 4
     * c = 6
     * </pre>
     */
    //@formatter:off
    static final PyCode LOAD_STORE_NAME =
    /*
     *   1           0 LOAD_NAME                0 (b)
     *               2 STORE_NAME               1 (a)
     *
     *   2           4 LOAD_CONST               0 (4)
     *               6 STORE_NAME               0 (b)
     *
     *   3           8 LOAD_CONST               1 (6)
     *              10 STORE_NAME               2 (c)
     *              12 LOAD_CONST               2 (None)
     *              14 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 1, 64,
        new PyBytes(new byte[] { 101, 0, 90, 1, 100, 0, 90, 0, 100,
                1, 90, 2, 100, 2, 83, 0 }),
        new PyTuple(new PyObject[] { new PyLong(4), new PyLong(6),
                Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("b"),
                new PyUnicode("a"), new PyUnicode("c") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyUnicode("load_store_name"), new PyUnicode("<module>"),
        1,
        new PyBytes(new byte[] { 4, 1, 4, 1 }));
    //@formatter:on

    @Test
    void test_load_store_name1() {
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(1));
        globals.put(new PyUnicode("b"), new PyLong(2));
        PyCode code = LOAD_STORE_NAME;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        // a == 2
        assertEquals("a", new PyLong(2), globals.get(new PyUnicode("a")));
        // b == 4
        assertEquals("b", new PyLong(4), globals.get(new PyUnicode("b")));
        // c == 6
        assertEquals("c", new PyLong(6), globals.get(new PyUnicode("c")));
    }

    /**
     * Example 'load_store_name_ex': <pre>
     * a = b
     * b = 2.0
     * c = 'begins!'
     * </pre>
     */
    //@formatter:off
    static final PyCode LOAD_STORE_NAME_EX =
    /*
     *   1           0 LOAD_NAME                0 (b)
     *               2 STORE_NAME               1 (a)
     *
     *   2           4 LOAD_CONST               0 (2.0)
     *               6 STORE_NAME               0 (b)
     *
     *   3           8 LOAD_CONST               1 ('begins!')
     *              10 STORE_NAME               2 (c)
     *              12 LOAD_CONST               2 (None)
     *              14 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 1, 64,
        new PyBytes(new byte[] { 101, 0, 90, 1, 100, 0, 90, 0, 100,
                1, 90, 2, 100, 2, 83, 0 }),
        new PyTuple(new PyObject[] { new PyFloat(2.0),
                new PyUnicode("begins!"), Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("b"),
                new PyUnicode("a"), new PyUnicode("c") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyUnicode("load_store_name_ex"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 4, 1, 4, 1 }));
    //@formatter:on

    @Test
    void test_load_store_name_ex1() {
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyUnicode("Hello"));
        globals.put(new PyUnicode("b"), new PyUnicode("World"));
        PyCode code = LOAD_STORE_NAME_EX;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        // a == 'World'
        assertEquals("a", new PyUnicode("World"),
                globals.get(new PyUnicode("a")));
        // b == 2.0
        assertEquals("b", new PyFloat(2.0),
                globals.get(new PyUnicode("b")));
        // c == 'begins!'
        assertEquals("c", new PyUnicode("begins!"),
                globals.get(new PyUnicode("c")));
    }

}
