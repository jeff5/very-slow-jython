package uk.co.farowl.vsj2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj2.PyByteCode2.Slot.EmptyException;

/**
 * A test illustrating a naive emulation using {@code MethodHandle} of
 * CPython's approach to type objects.
 * <p>
 * For simplicity, we use a byte code interpreter, implemented after
 * CPython's, but for just the opcodes we need.
 */
class PyByteCode2 {

    /** All Python object implementations implement this interface. */
    interface PyObject {
        /** The Python {@code type} of this object. */
        PyType getType();
    }

    /** Holds each type as it is defined. (Not used in this version.) */
    static class TypeRegistry {
        private static Map<String, PyType> registry = new HashMap<>();
        void put(String name, PyType type) { registry.put(name, type); }
    }

    static final TypeRegistry TYPE_REGISTRY = new TypeRegistry();

    /** The Python {@code type} object. */
    static class PyType implements PyObject {
        static final PyType TYPE = new PyType("type", PyType.class);
        @Override
        public PyType getType() { return TYPE; }
        final String name;
        private final Class<? extends PyObject> implClass;

        // Method suites for standard abstract types.
        final NumberMethods number;
        final SequenceMethods sequence;

        // Methods to implement standard operations.
        MethodHandle hash;
        MethodHandle repr;
        MethodHandle str;

        PyType(String name, Class<? extends PyObject> implClass) {
            this.name = name;
            this.implClass = implClass;

            // Initialise slots to implement standard operations.
            hash = TPSlot.hash.findInClass(implClass);
            repr = TPSlot.repr.findInClass(implClass);
            str = TPSlot.str.findInClass(implClass);

            // If immutable, could use NumberMethods.EMPTY, etc.
            (number = new NumberMethods()).fillFromClass(implClass);
            (sequence = new SequenceMethods()).fillFromClass(implClass);

            TYPE_REGISTRY.put(name, this);
        }

        @Override
        public String toString() { return "<class '" + name + "'>"; }

        public String getName() { return name; }

        void setSlot(SlotEnum slot, MethodHandle mh) {
            slot.setSlot(this, mh);
        }

        /** True iff b is a sub-type (on the MRO of) this type. */
        boolean isSubTypeOf(PyType b) {
            /*
             * Not supported yet. Later, search the MRO (or base-chain) of
             * this for b, and if it is found, then this is a sub-type.
             */
            return false;
        }
    }

    /** Miscellaneous static helpers common to built-in objects. */
    static class PyObjectUtil {

        /** Helper to create an exception for internal type error. */
        static InterpreterError typeMismatch(PyObject v, PyType expected) {
            String fmt = "'%s; argument to slot where '%s' expected";
            return new InterpreterError(fmt, v.getType().name,
                    expected.name);
        }
    }

    /*
     * Some basic built-in types
     */

    /** The Python {@code int} object. */
    static class PyLong implements PyObject {
        static PyType TYPE = new PyType("int", PyLong.class);
        @Override
        public PyType getType() { return TYPE; }
        final BigInteger value;
        PyLong(BigInteger value) { this.value = value; }
        PyLong(long value) { this.value = BigInteger.valueOf(value); }
        @Override
        public String toString() { return value.toString(); }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PyLong) {
                PyLong other = (PyLong) obj;
                return other.value.equals(this.value);
            } else
                return false;
        }

        static PyObject neg(PyObject v) {
            try {
                BigInteger a = valueOf(v);
                return new PyLong(a.negate());
            } catch (ClassCastException cce) {
                // Impossible: throw InterpreterError or EmptyException?
                return Py.NotImplemented;
            }
        }

        static PyObject add(PyObject v, PyObject w) {
            try {
                BigInteger a = valueOf(v);
                BigInteger b = valueOf(w);
                return new PyLong(a.add(b));
            } catch (ClassCastException cce) {
                return Py.NotImplemented;
            }
        }

        static PyObject sub(PyObject v, PyObject w) {
            try {
                BigInteger a = valueOf(v);
                BigInteger b = valueOf(w);
                return new PyLong(a.subtract(b));
            } catch (ClassCastException cce) {
                return Py.NotImplemented;
            }
        }

        static PyObject mul(PyObject v, PyObject w) {
            try {
                BigInteger a = valueOf(v);
                BigInteger b = valueOf(w);
                return new PyLong(a.multiply(b));
            } catch (ClassCastException cce) {
                return Py.NotImplemented;
            }
        }

        /**
         * Check the argument is a {@code PyLong} and return its value.
         *
         * @param v ought to be a {@code PyLong} (or sub-class)
         * @return the {@link #value} field of {@code v}
         * @throws ClassCastException if {@code v} is not compatible
         */
        private static BigInteger valueOf(PyObject v)
                throws ClassCastException {
            return ((PyLong) v).value;
        }

        /**
         * Check the argument is a {@code PyLong} and return its value, or
         * raise internal error. Differs from {@link #valueOf(PyObject)}
         * only in type of exception thrown.
         *
         * @param v ought to be a {@code PyLong} (or sub-class)
         * @return the {@link #value} field of {@code v}
         * @throws InterpreterError if {@code v} is not compatible
         */
        private static BigInteger valueOrError(PyObject v)
                throws InterpreterError {
            try {
                return ((PyLong) v).value;
            } catch (ClassCastException cce) {
                throw PyObjectUtil.typeMismatch(v, TYPE);
            }
        }
    }

    /** The Python {@code float} object. */
    static class PyFloat implements PyObject {
        static final PyType TYPE = new PyType("float", PyFloat.class);
        @Override
        public PyType getType() { return TYPE; }
        final double value;
        PyFloat(double value) { this.value = value; }
        @Override
        public String toString() { return Double.toString(value); }
        @Override

        public boolean equals(Object obj) {
            if (obj instanceof PyFloat) {
                PyFloat other = (PyFloat) obj;
                return other.value == this.value;
            } else
                return false;
        }

        static PyObject neg(PyObject v) {
            try {
                double a = ((PyFloat) v).value;
                return new PyFloat(-a);
            } catch (ClassCastException cce) {
                // Impossible: throw InterpreterError or EmptyException?
                return Py.NotImplemented;
            }
        }

        static PyObject add(PyObject v, PyObject w) {
            try {
                double a = valueOf(v);
                double b = valueOf(w);
                return new PyFloat(a + b);
            } catch (ClassCastException cce) {
                return Py.NotImplemented;
            }
        }

        static PyObject sub(PyObject v, PyObject w) {
            try {
                double a = valueOf(v);
                double b = valueOf(w);
                return new PyFloat(a - b);
            } catch (ClassCastException cce) {
                return Py.NotImplemented;
            }
        }

        static PyObject mul(PyObject v, PyObject w) {
            try {
                double a = valueOf(v);
                double b = valueOf(w);
                return new PyFloat(a * b);
            } catch (ClassCastException cce) {
                return Py.NotImplemented;
            }
        }

        /** Convert to {@code double} */
        static double valueOf(PyObject v) {
            if (v instanceof PyFloat)
                return ((PyFloat) v).value;
            else
                return ((PyLong) v).value.doubleValue();
        }
    }

    /** The Python {@code str} object. */
    static class PyUnicode implements PyObject {
        static final PyType TYPE = new PyType("unicode", PyUnicode.class);
        @Override
        public PyType getType() { return TYPE; }
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
        static final PyType TYPE = new PyType("bytes", PyType.class);
        @Override
        public PyType getType() { return TYPE; }
        final byte[] value;
        PyBytes(byte[] value) {
            this.value = new byte[value.length];
            System.arraycopy(value, 0, this.value, 0, value.length);
        }
    }

    /** The Python {@code tuple} object. */
    static class PyTuple implements PyObject {
        static final PyType TYPE = new PyType("tuple", PyType.class);
        @Override
        public PyType getType() { return TYPE; }
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
            implements PyObject {
        static final PyType TYPE = new PyType("dict", PyDictionary.class);
        @Override
        public PyType getType() { return TYPE; }
    }

    /**
     * Internal error thrown when the Python implementation cannot be
     * relied on to work. A Python exception (a {@code PyObject} that might
     * be caught in Python code) is not then appropriate. Typically thrown
     * during initialisation or for irrecoverable internal errors.
     */
    static class InterpreterError extends RuntimeException {
        protected InterpreterError(String msg, Object... args) {
            super(String.format(msg, args));
        }
        protected InterpreterError(Throwable cause, String msg,
                Object... args) {
            super(String.format(msg, args), cause);
        }
    }

    /** The Python {@code BaseException} exception. */
    static class BaseException extends RuntimeException
            implements PyObject {
        static final PyType TYPE =
                new PyType("BaseException", BaseException.class);
        private final PyType type;
        @Override
        public PyType getType() { return type; }
        /** Constructor for sub-class use specifying {@link #type}. */
        protected BaseException(PyType type, String msg, Object... args) {
            super(String.format(msg, args));
            this.type = type;
        }
        public BaseException(String msg, Object... args) {
            this(TYPE, msg, args);
        }
    }

    /** The Python {@code Exception} exception. */
    static class PyException extends BaseException {
        static final PyType TYPE =
                new PyType("Exception", PyException.class);
        protected PyException(PyType type, String msg, Object... args) {
            super(type, msg, args);
        }
        public PyException(String msg, Object... args) {
            this(TYPE, msg, args);
        }
    }

    /** The Python {@code TypeError} exception. */
    static class TypeError extends PyException {
        static final PyType TYPE =
                new PyType("SystemError", SystemError.class);
        protected TypeError(PyType type, String msg, Object... args) {
            super(type, msg, args);
        }
        public TypeError(String msg, Object... args) {
            this(TYPE, msg, args);
        }
    }

    /** The Python {@code SystemError} exception. */
    static class SystemError extends PyException {
        static final PyType TYPE =
                new PyType("SystemError", SystemError.class);
        protected SystemError(PyType type, String msg, Object... args) {
            super(type, msg, args);
        }
        public SystemError(String msg, Object... args) {
            this(TYPE, msg, args);
        }
    }

    /** Runtime */
    static class Py {

        private static class Singleton implements PyObject {
            final PyType type;
            @Override
            public PyType getType() { return type; }
            String name;
            Singleton(String name) {
                this.name = name;
                type = new PyType(name, getClass());
            }
            @Override
            public String toString() { return name; }
        }

        /** Python {@code None} object. */
        static final PyObject None = new Singleton("None") {};
        /** Python {@code NotImplemented} object. */
        static final PyObject NotImplemented =
                new Singleton("NotImplemented") {};
    }

    /**
     * Our equivalent to the Python code object ({@code PyCodeObject} in
     * CPython's C API).
     */
    static class PyCode implements PyObject {

        static final PyType TYPE = new PyType("code", PyCode.class);
        @Override
        public PyType getType() { return TYPE; }

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

    /** Constants for opcodes taken from CPython {@code opcode.h} */
    @SuppressWarnings("unused")
    private static class Opcode {
        static final int POP_TOP = 1;
        static final int ROT_TWO = 2;
        static final int ROT_THREE = 3;
        static final int DUP_TOP = 4;
        static final int DUP_TOP_TWO = 5;
        static final int ROT_FOUR = 6;
        static final int NOP = 9;
        static final int UNARY_POSITIVE = 10;
        static final int UNARY_NEGATIVE = 11;
        static final int UNARY_NOT = 12;
        static final int UNARY_INVERT = 15;
        static final int BINARY_MATRIX_MULTIPLY = 16;
        static final int INPLACE_MATRIX_MULTIPLY = 17;
        static final int BINARY_POWER = 19;
        static final int BINARY_MULTIPLY = 20;
        static final int BINARY_MODULO = 22;
        static final int BINARY_ADD = 23;
        static final int BINARY_SUBTRACT = 24;
        static final int BINARY_SUBSCR = 25;
        static final int BINARY_FLOOR_DIVIDE = 26;
        static final int BINARY_TRUE_DIVIDE = 27;
        static final int INPLACE_FLOOR_DIVIDE = 28;
        static final int INPLACE_TRUE_DIVIDE = 29;
        static final int GET_AITER = 50;
        static final int GET_ANEXT = 51;
        static final int BEFORE_ASYNC_WITH = 52;
        static final int BEGIN_FINALLY = 53;
        static final int END_ASYNC_FOR = 54;
        static final int INPLACE_ADD = 55;
        static final int INPLACE_SUBTRACT = 56;
        static final int INPLACE_MULTIPLY = 57;
        static final int INPLACE_MODULO = 59;
        static final int STORE_SUBSCR = 60;
        static final int DELETE_SUBSCR = 61;
        static final int BINARY_LSHIFT = 62;
        static final int BINARY_RSHIFT = 63;
        static final int BINARY_AND = 64;
        static final int BINARY_XOR = 65;
        static final int BINARY_OR = 66;
        static final int INPLACE_POWER = 67;
        static final int GET_ITER = 68;
        static final int GET_YIELD_FROM_ITER = 69;
        static final int PRINT_EXPR = 70;
        static final int LOAD_BUILD_CLASS = 71;
        static final int YIELD_FROM = 72;
        static final int GET_AWAITABLE = 73;
        static final int INPLACE_LSHIFT = 75;
        static final int INPLACE_RSHIFT = 76;
        static final int INPLACE_AND = 77;
        static final int INPLACE_XOR = 78;
        static final int INPLACE_OR = 79;
        static final int WITH_CLEANUP_START = 81;
        static final int WITH_CLEANUP_FINISH = 82;
        static final int RETURN_VALUE = 83;
        static final int IMPORT_STAR = 84;
        static final int SETUP_ANNOTATIONS = 85;
        static final int YIELD_VALUE = 86;
        static final int POP_BLOCK = 87;
        static final int END_FINALLY = 88;
        static final int POP_EXCEPT = 89;
        static final int HAVE_ARGUMENT = 90;
        static final int STORE_NAME = 90;
        static final int DELETE_NAME = 91;
        static final int UNPACK_SEQUENCE = 92;
        static final int FOR_ITER = 93;
        static final int UNPACK_EX = 94;
        static final int STORE_ATTR = 95;
        static final int DELETE_ATTR = 96;
        static final int STORE_GLOBAL = 97;
        static final int DELETE_GLOBAL = 98;
        static final int LOAD_CONST = 100;
        static final int LOAD_NAME = 101;
        static final int BUILD_TUPLE = 102;
        static final int BUILD_LIST = 103;
        static final int BUILD_SET = 104;
        static final int BUILD_MAP = 105;
        static final int LOAD_ATTR = 106;
        static final int COMPARE_OP = 107;
        static final int IMPORT_NAME = 108;
        static final int IMPORT_FROM = 109;
        static final int JUMP_FORWARD = 110;
        static final int JUMP_IF_FALSE_OR_POP = 111;
        static final int JUMP_IF_TRUE_OR_POP = 112;
        static final int JUMP_ABSOLUTE = 113;
        static final int POP_JUMP_IF_FALSE = 114;
        static final int POP_JUMP_IF_TRUE = 115;
        static final int LOAD_GLOBAL = 116;
        static final int SETUP_FINALLY = 122;
        static final int LOAD_FAST = 124;
        static final int STORE_FAST = 125;
        static final int DELETE_FAST = 126;
        static final int RAISE_VARARGS = 130;
        static final int CALL_FUNCTION = 131;
        static final int MAKE_FUNCTION = 132;
        static final int BUILD_SLICE = 133;
        static final int LOAD_CLOSURE = 135;
        static final int LOAD_DEREF = 136;
        static final int STORE_DEREF = 137;
        static final int DELETE_DEREF = 138;
        static final int CALL_FUNCTION_KW = 141;
        static final int CALL_FUNCTION_EX = 142;
        static final int SETUP_WITH = 143;
        static final int EXTENDED_ARG = 144;
        static final int LIST_APPEND = 145;
        static final int SET_ADD = 146;
        static final int MAP_ADD = 147;
        static final int LOAD_CLASSDEREF = 148;
        static final int BUILD_LIST_UNPACK = 149;
        static final int BUILD_MAP_UNPACK = 150;
        static final int BUILD_MAP_UNPACK_WITH_CALL = 151;
        static final int BUILD_TUPLE_UNPACK = 152;
        static final int BUILD_SET_UNPACK = 153;
        static final int SETUP_ASYNC_WITH = 154;
        static final int FORMAT_VALUE = 155;
        static final int BUILD_CONST_KEY_MAP = 156;
        static final int BUILD_STRING = 157;
        static final int BUILD_TUPLE_UNPACK_WITH_CALL = 158;
        static final int LOAD_METHOD = 160;
        static final int CALL_METHOD = 161;
        static final int CALL_FINALLY = 162;
        static final int POP_FINALLY = 163;

        /*
         * EXCEPT_HANDLER is a special, implicit block type that is created
         * when entering an except handler. It is not an opcode.
         */
        static final int EXCEPT_HANDLER = 257;

        enum PyCmp {
            LT, LE, EQ, NE, GT, GE, IN, NOT_IN, IS, IS_NOT, EXC_MATCH, BAD
        }

        /** Whether the opcode has an argument. */
        static final boolean hasArg(int op) {
            return op >= HAVE_ARGUMENT;
        }
    }

    /** Compare CPython {@code abstract.h}: {@code Py_Number_*}. */
    static class Number {

        /** Python {@code -v} */
        static PyObject negative(PyObject v) throws Throwable {
            try {
                MethodHandle mh = v.getType().number.negative;
                return (PyObject) mh.invokeExact(v);
            } catch (Slot.EmptyException e) {
                throw typeError("-", v);
            }
        }

        /** Create a {@code TypeError} for the named unary op. */
        static PyException typeError(String op, PyObject v) {
            return new TypeError("bad operand type for unary %s: '%.200s'",
                    op, v.getType().getName());
        }

        /** Python {@code v+w} */
        static PyObject add(PyObject v, PyObject w) throws Throwable {
            try {
                PyObject r = binary_op1(v, w, NBSlot.add);
                if (r != Py.NotImplemented)
                    return r;
            } catch (Slot.EmptyException e) {}
            throw typeError("+", v, w);
        }

        /** Python {@code v-w} */
        static PyObject subtract(PyObject v, PyObject w) throws Throwable {
            try {
                PyObject r = binary_op1(v, w, NBSlot.subtract);
                if (r != Py.NotImplemented)
                    return r;
            } catch (Slot.EmptyException e) {}
            throw typeError("-", v, w);
        }

        /** Python {@code v*w} */
        static PyObject multiply(PyObject v, PyObject w) throws Throwable {
            try {
                PyObject r = binary_op1(v, w, NBSlot.multiply);
                if (r != Py.NotImplemented)
                    return r;
            } catch (Slot.EmptyException e) {}
            throw typeError("*", v, w);
        }

        /**
         * Helper for implementing binary operation. If neither the left
         * type nor the right type implements the operation, it will either
         * return {@link Py#NotImplemented} or throw
         * {@link EmptyException}. Both mean the same thing.
         *
         * @param v left operand
         * @param w right oprand
         * @param binop operation to apply
         * @return result or {@code Py.NotImplemented}
         * @throws Slot.EmptyException when an empty slot is invoked
         * @throws Throwable from the implementation of the operation
         */
        private static PyObject binary_op1(PyObject v, PyObject w,
                NBSlot binop) throws Slot.EmptyException, Throwable {
            PyType vtype = v.getType();
            PyType wtype = w.getType();

            MethodHandle slotv = binop.getSlot(vtype);
            MethodHandle slotw;

            if (wtype == vtype || (slotw = binop.getSlot(wtype)) == slotv)
                // Both types give the same result
                return (PyObject) slotv.invokeExact(v, w);

            else if (!wtype.isSubTypeOf(vtype)) {
                // Ask left (if not empty) then right.
                if (slotv != Slot.BINARY_EMPTY) {
                    PyObject r = (PyObject) slotv.invokeExact(v, w);
                    if (r != Py.NotImplemented)
                        return r;
                }
                return (PyObject) slotw.invokeExact(v, w);

            } else {
                // Right is sub-class: ask first (if not empty).
                if (slotw != Slot.BINARY_EMPTY) {
                    PyObject r = (PyObject) slotw.invokeExact(v, w);
                    if (r != Py.NotImplemented)
                        return r;
                }
                return (PyObject) slotv.invokeExact(v, w);
            }
        }

        /** Create a {@code TypeError} for the named binary op. */
        static PyException typeError(String op, PyObject v, PyObject w) {
            return new TypeError(
                    "unsupported operand type(s) for %.100s: "
                            + "'%.100s' and '%.100s'",
                    op, v.getType().getName(), w.getType().getName());
        }
    }

    /** Compare CPython {@code abstract.h}: {@code Py_Sequence_*}.. */
    static class Sequence {

        /** Python size of {@code s} */
        static PyObject size(PyObject u) throws Throwable {
            // Note that the slot is called sq_length but the method size.
            try {
                // NPE if u (or type?) is null
                MethodHandle mh = u.getType().sequence.length;
                // Could throw anything
                return (PyObject) mh.invokeExact(u);
            } catch (Slot.EmptyException e) {
                throw typeError("-", u);
            }
        }

        static PyException typeError(String op, PyObject o) {
            return new PyException(
                    "bad operand type for unary %s: '%.200s'", op,
                    o.getType().getName());
        }
    }

    /** Utilities to help construct slot functions. */
    static class Slot {

        static class EmptyException extends Exception {}

        private static final MethodHandles.Lookup LOOKUP =
                MethodHandles.lookup();

        static final Class<PyObject> O = PyObject.class;
        static final Class<Opcode.PyCmp> CMP = Opcode.PyCmp.class;
        static final Class<EmptyException> EX = EmptyException.class;

        static final MethodType VOID = MethodType.methodType(void.class);
        static final MethodType UNARY = MethodType.methodType(O, O);
        static final MethodType BINARY = MethodType.methodType(O, O, O);
        static final MethodType TERNARY =
                MethodType.methodType(O, O, O, O);
        static final MethodType RICHCMP =
                MethodType.methodType(O, O, O, CMP);

        private static MethodHandle ese;
        private static MethodHandle th;

        static final MethodHandle EMPTY;
        static final MethodHandle UNARY_EMPTY;
        static final MethodHandle BINARY_EMPTY;
        static final MethodHandle TERNARY_EMPTY;
        static final MethodHandle RICHCMP_EMPTY;

        static {
            try {
                // ese = λ : new EmptyException
                ese = LOOKUP.findConstructor(EX, VOID);
                // th = λ e : throw e (where e is an EmptyException)
                th = MethodHandles.throwException(O, EX);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                e.printStackTrace();
            }
            // EMPTY = λ : throw new EmptyException
            EMPTY = MethodHandles.foldArguments(th, ese);
            // UNARY_EMPTY = λ v : throw new EmptyException
            UNARY_EMPTY = MethodHandles.dropArguments(EMPTY, 0, O);
            // BINARY_EMPTY = λ v w : throw new EmptyException
            BINARY_EMPTY = MethodHandles.dropArguments(EMPTY, 0, O, O);
            // TERNARY_EMPTY = λ u v w : throw new EmptyException
            TERNARY_EMPTY = MethodHandles.dropArguments(EMPTY, 0, O, O, O);
            // RICHCMP_EMPTY = λ v w op : throw new EmptyException
            RICHCMP_EMPTY =
                    MethodHandles.dropArguments(EMPTY, 0, O, O, CMP);
        }
    }

    /** Predefined types of slot. */
    enum SlotSignature {
        UNARY(Slot.UNARY, Slot.UNARY_EMPTY),
        BINARY(Slot.BINARY, Slot.BINARY_EMPTY),
        TERNARY(Slot.TERNARY, Slot.TERNARY_EMPTY);

        /** The method handle in this slot must have this type. */
        final MethodType type;
        /** When the slot is empty, it should hold this handle. */
        final MethodHandle empty;

        SlotSignature(MethodType type, MethodHandle empty) {
            this.type = type;
            this.empty = empty;
        }

        private static final Map<MethodType, SlotSignature> sigMap;
        static {
            sigMap = new HashMap<>();
            for (SlotSignature sig : SlotSignature.values()) {
                sigMap.put(sig.empty.type(), sig);
            }
        }
        static SlotSignature matching(MethodType t) {
            return sigMap.get(t);
        }
    }

    /**
     * Constants for the {@code *Methods} compartments of a {@link PyType}
     * object. There should be a {@code *Slot enum} for each {@code Group}.
     */
    enum Group {
        /**
         * Representing the "basic" group: slots where the name begins
         * {@code tp_}, that are found in the base {@code PyTypeObject}, in
         * CPython.
         */
        TP(PyType.class),
        /**
         * Representing the "number" group: slots where the name begins
         * {@code nb_}, that are found in an optional
         * {@code PyNumberMethods} in CPython.
         */
        NB(NumberMethods.class),
        /**
         * Representing the "sequence" group: slots where the name begins
         * {@code sq_}, that are found in an optional
         * {@code PySequenceMethods} in CPython.
         */
        SQ(SequenceMethods.class),
        // For later use: replace Void with a *Methods class
        MP(Void.class), BF(Void.class), AM(Void.class);

        /** The {@code *Method} class corresponding. */
        final Class<?> methodsClass;

        Group(Class<?> methodsClass) { this.methodsClass = methodsClass; }
    }

    static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * This interface is implemented by the {@code enum} for each group of
     * "slots" that a {@code PyType} contains. These groups are the
     * {@code NumberMethods}, the {@code SequenceMethods}, the
     * {@code MappingMethods}, the {@code BufferMethods}, and the
     * {@code PyType} that (in CPython) are members of the {@code PyType}
     * object itself.
     * <p>
     * These {@code enum}s provide constants that can be are used to refer
     * to these slots. Each constant in each {@code enum} creates a
     * correspondence between its name, the (slot) name in the
     * {@code *Methods} object (because it is the same), the type of the
     * {@code MethodHandle} that slot must contain, and the conventional
     * name by which the implementing class of a type will refer to that
     * method, if it offers one.
     */
    interface SlotEnum {
        /**
         * The group to which this slot belongs (implying a {@code *Method}
         * class that has a members with the same name as this {@code enum}
         * constant.
         */
        Group group();
        /** Name of the slot (supplied by Java for the {@code enum}). */
        String name();
        /**
         * Get the name of the method that, by convention, identifies the
         * corresponding operation in the implementing class. This is not
         * the same as the slot name.
         */
        String getMethodName();
        /**
         * Get the signature required for slots of this name and
         * corresponding default {@code MethodHandle} that fills the slot
         * when it is "empty".
         */
        SlotSignature getSignature();
        /**
         * Create the method handle for this operation, if possible, by
         * reflection on the given class. If the class has a {@code static}
         * method matching the proper name {@link #getMethodName()} and
         * method type {@link #getSignature()}{@code .type}, return that.
         *
         *
         * Return for a slot, a handle to the method in a given class that
         * implements it, of the default handle (of the correct signature)
         * that throws {@link EmptyException}.
         *
         * @param s slot
         * @param c target class
         * @return handle to method in {@code c} implementing thios slot,
         *         or appropriate "empty" if no such method is accessible.
         */
        MethodHandle findInClass(Class<?> c);
        /**
         * Get the contents of this slot in the given type. Each member of
         * this {@code enum} corresponds to the name of a static method
         * which must also have the correct signature.
         *
         * @param t target type
         * @return current contents of this slot in {@code t}
         */
        MethodHandle getSlot(PyType t);
        /**
         * Set the contents of this slot in the given type to the
         * {@code MethodHandle} provided.
         *
         * @param t target type object
         * @param mh handle value to assign
         */
        void setSlot(PyType t, MethodHandle mh);
    }

    /** Common code supporting the {@code *Slot enum}s. */
    static class SlotEnumCommon {
        /**
         * Helper for implementations of
         * {@link SlotEnum#setSlot(PyType, MethodHandle)}, when a bad
         * handle is presented.
         *
         * @param s slot client attempted to set
         * @param mh offered value found unsuitable
         * @return exception with message filled in
         */
        static InterpreterError slotTypeError(SlotEnum s,
                MethodHandle mh) {
            String fmt = "%s not of required type %s for slot %s.%s";
            SlotSignature sig = s.getSignature();
            return new InterpreterError(fmt, mh, sig.type, s.group(),
                    s.toString());
        }

        /**
         * Helper for constructors of {@code *Slot enum}s at the point they
         * need a handle for their named field within a {@code *Methods}s
         * class.
         */
        static VarHandle slotHandle(SlotEnum slot) {
            Class<?> methodsClass = slot.group().methodsClass;
            try {
                return LOOKUP.findVarHandle(methodsClass, slot.name(),
                        MethodHandle.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new InterpreterError(e, "creating enum for %s",
                        methodsClass.getSimpleName());
            }
        }

        /**
         * Return for a slot, a handle to the method in a given class that
         * implements it, of the default handle (of the correct signature)
         * that throws {@link EmptyException}.
         *
         * @param s slot
         * @param c class
         * @return handle to method in {@code c} implementing {@code s}, or
         *         appropriate "empty" if no such method is accessible.
         */
        static MethodHandle findInClass(SlotEnum s, Class<?> c) {
            SlotSignature sig = s.getSignature();
            try {
                return LOOKUP.findStatic(c, s.getMethodName(), sig.type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return sig.empty;
            }
        }
    }

    /**
     * An enumeration of the "basic" group: slots where the name begins
     * {@code tp_}, that are found in the base {@code PyTypeObject}, in
     * CPython.
     */
    enum TPSlot implements SlotEnum {

        hash("hash", SlotSignature.UNARY), //
        repr("repr", SlotSignature.UNARY), //
        str("str", SlotSignature.UNARY);

        final String methodName;
        final SlotSignature signature;
        final VarHandle slotHandle;

        TPSlot(String methodName, SlotSignature signature) {
            this.methodName = methodName;
            this.signature = signature;
            this.slotHandle = SlotEnumCommon.slotHandle(this);
        }

        @Override
        public MethodHandle getSlot(PyType t) {
            return (MethodHandle) slotHandle.get(t);
        }

        @Override
        public void setSlot(PyType t, MethodHandle mh) {
            if (mh == null || !mh.type().equals(signature.type))
                throw SlotEnumCommon.slotTypeError(this, mh);
            slotHandle.set(t, mh);
        }

        @Override
        public Group group() { return Group.TP; }

        @Override
        public String getMethodName() { return this.methodName; }

        @Override
        public SlotSignature getSignature() { return this.signature; }

        @Override
        public MethodHandle findInClass(Class<?> c) {
            return SlotEnumCommon.findInClass(this, c);
        }

    }

    /**
     * An enumeration of the "number" group: slots where the name begins
     * {@code nb_}, that are found in an optional {@code PyNumberMethods}
     * in CPython.
     */
    enum NBSlot implements SlotEnum {

        negative("neg", SlotSignature.UNARY), //
        add("add", SlotSignature.BINARY), //
        subtract("sub", SlotSignature.BINARY), //
        multiply("mul", SlotSignature.BINARY);

        final String methodName;
        final SlotSignature signature;
        final VarHandle slotHandle;

        NBSlot(String methodName, SlotSignature signature) {
            this.methodName = methodName;
            this.signature = signature;
            this.slotHandle = SlotEnumCommon.slotHandle(this);
        }

        /**
         * Lookup this slot in the given object.
         *
         * @param m the {@code *Methods} object to consult
         * @return the {@code MethodHandle} from it
         */
        MethodHandle getSlot(NumberMethods m) {
            return (MethodHandle) slotHandle.get(m);
        }

        /**
         * Set this slot in the given object (if type-compatible).
         *
         * @param m the {@code *Methods} object to consult
         * @param mh the {@code MethodHandle} to set there
         */
        void setSlot(NumberMethods m, MethodHandle mh) {
            if (mh == null || !mh.type().equals(signature.type))
                throw SlotEnumCommon.slotTypeError(this, mh);
            slotHandle.set(m, mh);
        }

        @Override
        public MethodHandle getSlot(PyType t) { return getSlot(t.number); }

        @Override
        public void setSlot(PyType t, MethodHandle mh) {
            assert t.number != NumberMethods.EMPTY;
            setSlot(t.number, mh);
        }

        @Override
        public Group group() { return Group.NB; }

        @Override
        public String getMethodName() { return this.methodName; }

        @Override
        public SlotSignature getSignature() { return this.signature; }

        @Override
        public MethodHandle findInClass(Class<?> c) {
            return SlotEnumCommon.findInClass(this, c);
        }
    }

    /**
     * An enumeration of the "sequence" group: slots where the name begins
     * {@code sq_}, that are found in an optional {@code PySequenceMethods}
     * in CPython.
     */
    enum SQSlot implements SlotEnum {

        length("length", SlotSignature.UNARY);

        final String methodName;
        final SlotSignature signature;
        final VarHandle slotHandle;

        SQSlot(String methodName, SlotSignature signature) {
            this.methodName = methodName;
            this.signature = signature;
            this.slotHandle = SlotEnumCommon.slotHandle(this);
        }

        /**
         * Lookup this slot in the given object.
         *
         * @param m the {@code *Methods} object to consult
         * @return the {@code MethodHandle} from it
         */
        MethodHandle getSlot(SequenceMethods m) {
            return (MethodHandle) slotHandle.get(m);
        }

        /**
         * Set this slot in the given object (if type-compatible).
         *
         * @param m the {@code *Methods} object to consult
         * @param mh the {@code MethodHandle} to set there
         */
        void setSlot(SequenceMethods m, MethodHandle mh) {
            if (mh == null || !mh.type().equals(signature.type))
                throw SlotEnumCommon.slotTypeError(this, mh);
            slotHandle.set(m, mh);
        }

        @Override
        public MethodHandle getSlot(PyType t) {
            return getSlot(t.sequence);
        }

        @Override
        public void setSlot(PyType t, MethodHandle mh) {
            assert t.sequence != SequenceMethods.EMPTY;
            setSlot(t.sequence, mh);
        }

        @Override
        public Group group() { return Group.SQ; }

        @Override
        public String getMethodName() { return this.methodName; }

        @Override
        public SlotSignature getSignature() { return this.signature; }

        @Override
        public MethodHandle findInClass(Class<?> c) {
            return SlotEnumCommon.findInClass(this, c);
        }
    }

    /** Help with the use of {@code *Methods} objects. */
    static class SlotMethods {
        /**
         * A list of all the slots a {@link PyType} object might contain.
         * This is a convenience function allowing client code to iterate
         * over al lthe slots in one loop, such as:<pre>
         * for (SlotEnum s : SlotMethods.ALL) {
         *     // Do something with s ...
         *  }
         * </pre>
         */
        static List<SlotEnum> ALL = allSlots();

        private static List<SlotEnum> allSlots() {
            // Make a stream of the separately-defined enum values
            SlotEnum[] tp, nb, sq;
            tp = TPSlot.values();
            nb = NBSlot.values();
            sq = SQSlot.values();
            // XXX and the rest in due course
            List<SlotEnum> all = new LinkedList<>();
            Collections.addAll(all, tp);
            Collections.addAll(all, nb);
            Collections.addAll(all, sq);
            return List.copyOf(all);
        }
    }

    /** Tabulate the number methods (slots) of a particular type. */
    static class NumberMethods {

        MethodHandle negative = Slot.UNARY_EMPTY;
        MethodHandle add = Slot.BINARY_EMPTY;
        MethodHandle subtract = Slot.BINARY_EMPTY;
        MethodHandle multiply = Slot.BINARY_EMPTY;

        /** An instance in which every slot has its default value. */
        static final NumberMethods EMPTY = new NumberMethods();

        /**
         * Fill the slots in this {@code NumberMethods} with entries that
         * are method handles to the correspondingly named static methods
         * in a given target class, or if no such method is defined by the
         * class, leave the slot as it is (normally the default).
         *
         * @param c class to reflect
         */
        void fillFromClass(Class<? extends PyObject> c) {
            assert this != EMPTY;
            for (NBSlot s : NBSlot.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh != s.signature.empty) { s.setSlot(this, mh); }
            }
        }
    }

    /** Tabulate the sequence methods (slots) of a particular type. */
    static class SequenceMethods {

        MethodHandle length = Slot.UNARY_EMPTY;

        /** An instance in which every slot has its default value. */
        static final SequenceMethods EMPTY = new SequenceMethods();

        /**
         * Fill the slots in this {@code NumberMethods} with entries that
         * are method handles to the correspondingly named static methods
         * in a given target class, or if no such method is defined by the
         * class, leave the slot as it is (normally the default).
         *
         * @param c class to reflect
         */
        void fillFromClass(Class<? extends PyObject> c) {
            assert this != EMPTY;
            for (SQSlot s : SQSlot.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh != s.signature.empty) { s.setSlot(this, mh); }
            }
        }
    }

    /** A {@link PyFrame} for executing CPython 3.8 byte code. */
    private static class CPythonFrame extends PyFrame {
        static final PyType TYPE = new PyType("frame", PyCode.class);
        @Override
        public PyType getType() { return TYPE; }

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
            int opcode = inst[0] & 0xff;
            int oparg = inst[1] & 0xff;
            int ip = 2;
            // Local variables used repeatedly in the loop
            PyObject name, res, v, w;

            loop : for (;;) {
                try {

                    // Interpret opcode
                    switch (opcode) {

                        case Opcode.ROT_TWO:
                            v = valuestack[sp - 1]; // TOP
                            valuestack[sp - 1] = valuestack[sp - 2];
                            valuestack[sp - 2] = v; // SET_SECOND
                            break;

                        case Opcode.UNARY_NEGATIVE:
                            v = valuestack[sp - 1]; // TOP
                            res = Number.negative(v);
                            valuestack[sp - 1] = res; // SET_TOP
                            break;

                        case Opcode.BINARY_MULTIPLY:
                            w = valuestack[--sp]; // POP
                            v = valuestack[sp - 1]; // TOP
                            res = Number.multiply(v, w);
                            valuestack[sp - 1] = res; // SET_TOP
                            break;

                        case Opcode.BINARY_ADD:
                            w = valuestack[--sp]; // POP
                            v = valuestack[sp - 1]; // TOP
                            res = Number.add(v, w);
                            valuestack[sp - 1] = res; // SET_TOP
                            break;

                        case Opcode.BINARY_SUBTRACT:
                            w = valuestack[--sp]; // POP
                            v = valuestack[sp - 1]; // TOP
                            res = Number.subtract(v, w);
                            valuestack[sp - 1] = res; // SET_TOP
                            break;

                        case Opcode.RETURN_VALUE:
                            returnValue = valuestack[--sp]; // POP
                            break loop;

                        case Opcode.STORE_NAME:
                            name = names.getItem(oparg);
                            v = valuestack[--sp]; // POP
                            if (locals == null)
                                throw new SystemError(
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
                                throw new SystemError(
                                        "no locals found when loading '%s'",
                                        name);
                            v = locals.get(name);
                            if (v == null) {
                                v = globals.get(name);
                                if (v == null) {
                                    v = builtins.get(name);
                                    if (v == null)
                                        throw new PyException(
                                                NAME_ERROR_MSG, name);
                                }
                            }
                            valuestack[sp++] = v; // PUSH
                            break;

                        default:
                            throw new SystemError("ip: %d, opcode: %d",
                                    ip - 2, opcode);
                    } // switch

                    // Pick up the next instruction
                    opcode = inst[ip] & 0xff;
                    oparg = inst[ip + 1] & 0xff;
                    ip += 2;
                } catch (PyException pye) {
                    /*
                     * We ought here to check for exception handlers
                     * (defined in Python and reflected in the byte code)
                     * potentially resuming the loop with ip at the handler
                     * code, or in a Python finally clause.
                     */
                    // Should handle within Python, but for now, stop.
                    throw pye;
                } catch (InterpreterError ie) {
                    /*
                     * An InterpreterError signals an internal error,
                     * recognised by our implementation: stop.
                     */
                    throw ie;
                } catch (Throwable t) {
                    /*
                     * A non-Python exception signals an internal error, in
                     * our implementation, in user-supplied Java, or from a
                     * Java library misused from Python.
                     */
                    // Should handle within Python, but for now, stop.
                    throw new InterpreterError("PyException", t);
                }
            } // loop

            tstate.swap(back);
            return returnValue;
        }
    }

    /**
     * A test that the method handles we place in nominally empty slots, do
     * in fact raise the exception used internally to detect them.
     */
    @Test
    void testSlotsEmptyException() {
        // Zero argument call to "empty" slot.
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.EMPTY.invokeExact();
        });
        // Single PyObject argument call to "empty" slot.
        PyObject v = new PyLong(100);
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.UNARY_EMPTY.invokeExact(v);
        });
        // Two PyObject argument call to "empty" slot.
        PyObject w = new PyLong(200);
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.BINARY_EMPTY.invokeExact(v, w);
        });
        // Three PyObject argument call to "empty" slot.
        PyObject u = new PyLong(1);
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r =
                    (PyObject) Slot.TERNARY_EMPTY.invokeExact(u, v, w);
        });
        // Two PyObject argument call to "empty" slot.
        Opcode.PyCmp op = Opcode.PyCmp.LT;
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r =
                    (PyObject) Slot.RICHCMP_EMPTY.invokeExact(v, w, op);
        });
    }

    /** Test that TP slots accept only the right type of method handles. */
    @Test
    void testTPSlot() {
        // Create a type defining none of the reserved names
        final PyType basic = new PyType("0Test", PyObject.class);
        assertEquals(Slot.UNARY_EMPTY, basic.repr, "not EMPTY");

        // Make method handles to try
        MethodHandle unary = MethodHandles.empty(Slot.UNARY);
        MethodHandle binary = MethodHandles.empty(Slot.BINARY);
        MethodHandle ternary = MethodHandles.empty(Slot.TERNARY);

        // These go quietly
        TPSlot.hash.setSlot(basic, unary);
        TPSlot.str.setSlot(basic, unary);
        // Re-type the good method handles as unacceptable types
        final MethodHandle unary2 = unary
                .asType(MethodType.methodType(Float.class, Integer.class));
        final MethodHandle binary2 = binary.asType(MethodType
                .methodType(Float.class, String.class, Byte.class));
        final MethodHandle ternary2 =
                ternary.asType(MethodType.methodType(PyObject.class,
                        Float.class, String.class, Byte.class));

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            TPSlot.hash.setSlot(basic, unary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            TPSlot.hash.setSlot(basic, binary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            TPSlot.hash.setSlot(basic, ternary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            TPSlot.hash.setSlot(basic, null);
        });

        // And the slot should be unaffected
        assertEquals(unary, basic.hash, "slot modified");
    }

    /** Test that NB slots accept only the right type of method handles. */
    @Test
    void testNBSlot() {
        // Create an empty methods holder
        NumberMethods number = new NumberMethods();
        assertEquals(Slot.UNARY_EMPTY, number.negative,
                NBSlot.negative.name());
        assertEquals(Slot.BINARY_EMPTY, number.add, NBSlot.add.name());

        // Make method handles to try
        final MethodHandle unary = MethodHandles.empty(Slot.UNARY);
        final MethodHandle binary = MethodHandles.empty(Slot.BINARY);
        final MethodHandle ternary = MethodHandles.empty(Slot.TERNARY);
        // These go quietly
        NBSlot.negative.setSlot(number, unary);
        NBSlot.add.setSlot(number, binary);

        // Re-type the good method handles as unacceptable types
        final MethodHandle unary2 = unary
                .asType(MethodType.methodType(Float.class, Integer.class));
        final MethodHandle binary2 = binary.asType(MethodType
                .methodType(Float.class, String.class, Byte.class));
        final MethodHandle ternary2 =
                ternary.asType(MethodType.methodType(PyObject.class,
                        Float.class, String.class, Byte.class));

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            NBSlot.negative.setSlot(number, unary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            NBSlot.negative.setSlot(number, binary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            NBSlot.negative.setSlot(number, ternary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            NBSlot.negative.setSlot(number, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            NBSlot.negative.setSlot(number, null);
        });

        assertThrows(InterpreterError.class, () -> { //
            NBSlot.add.setSlot(number, unary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            NBSlot.add.setSlot(number, binary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            NBSlot.add.setSlot(number, ternary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            NBSlot.add.setSlot(number, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            NBSlot.add.setSlot(number, null);
        });

        // And the slots should have the value set earlier
        assertEquals(unary, number.negative, "slot modified");
        assertEquals(binary, number.add, "slot modified");
    }

    /** Test that TP slots accept only the right type of method handles. */
    @Test
    void testSQSlot() {
        // Create an empty methods holder
        final SequenceMethods sequence = new SequenceMethods();
        assertEquals(Slot.UNARY_EMPTY, sequence.length, "not EMPTY");

        // Make method handles to try
        MethodHandle unary = MethodHandles.empty(Slot.UNARY);
        MethodHandle binary = MethodHandles.empty(Slot.BINARY);
        MethodHandle ternary = MethodHandles.empty(Slot.TERNARY);

        // These go quietly
        SQSlot.length.setSlot(sequence, unary);
        // Re-type the good method handles as unacceptable types
        final MethodHandle unary2 = unary
                .asType(MethodType.methodType(Float.class, Integer.class));
        final MethodHandle binary2 = binary.asType(MethodType
                .methodType(Float.class, String.class, Byte.class));
        final MethodHandle ternary2 =
                ternary.asType(MethodType.methodType(PyObject.class,
                        Float.class, String.class, Byte.class));

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            SQSlot.length.setSlot(sequence, unary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            SQSlot.length.setSlot(sequence, binary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            SQSlot.length.setSlot(sequence, ternary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            SQSlot.length.setSlot(sequence, null);
        });

        // And the slot should be unaffected
        assertEquals(unary, sequence.length, "slot modified");
    }

    // --------------------- Generated Tests -----------------------
    // Code generated by py_byte_code2.py

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
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(1));
        globals.put(new PyUnicode("b"), new PyLong(2));
        PyCode code = LOAD_STORE_NAME;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(2), globals.get(new PyUnicode("a")),
            "a == 2");
        assertEquals(new PyLong(4), globals.get(new PyUnicode("b")),
            "b == 4");
        assertEquals(new PyLong(6), globals.get(new PyUnicode("c")),
            "c == 6");
        //@formatter:on
    }

    /**
     * Example 'negate': <pre>
     * a, b = -a, -b
     * </pre>
     */
    //@formatter:off
    static final PyCode NEGATE =
    /*
     *   1           0 LOAD_NAME                0 (a)
     *               2 UNARY_NEGATIVE
     *               4 LOAD_NAME                1 (b)
     *               6 UNARY_NEGATIVE
     *               8 ROT_TWO
     *              10 STORE_NAME               0 (a)
     *              12 STORE_NAME               1 (b)
     *              14 LOAD_CONST               0 (None)
     *              16 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 2, 64,
        new PyBytes(new byte[] { 101, 0, 11, 0, 101, 1, 11, 0, 2, 0,
                90, 0, 90, 1, 100, 0, 83, 0 }),
        new PyTuple(new PyObject[] { Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("a"),
                new PyUnicode("b") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}), new PyUnicode("negate"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] {}));
    //@formatter:on

    @Test
    void test_negate1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(6));
        globals.put(new PyUnicode("b"), new PyLong(-7));
        PyCode code = NEGATE;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(-6), globals.get(new PyUnicode("a")),
            "a == -6");
        assertEquals(new PyLong(7), globals.get(new PyUnicode("b")),
            "b == 7");
        //@formatter:on
    }

    @Test
    void test_negate2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyFloat(6.0));
        globals.put(new PyUnicode("b"), new PyFloat(-7.0));
        PyCode code = NEGATE;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyFloat(-6.0), globals.get(
            new PyUnicode("a")), "a == -6.0");
        assertEquals(new PyFloat(7.0), globals.get(
            new PyUnicode("b")), "b == 7.0");
        //@formatter:on
    }

    /**
     * Example 'binary': <pre>
     * sum = a + b
     * diff = a - b
     * prod = a * b
     * </pre>
     */
    //@formatter:off
    static final PyCode BINARY =
    /*
     *   1           0 LOAD_NAME                0 (a)
     *               2 LOAD_NAME                1 (b)
     *               4 BINARY_ADD
     *               6 STORE_NAME               2 (sum)
     *
     *   2           8 LOAD_NAME                0 (a)
     *              10 LOAD_NAME                1 (b)
     *              12 BINARY_SUBTRACT
     *              14 STORE_NAME               3 (diff)
     *
     *   3          16 LOAD_NAME                0 (a)
     *              18 LOAD_NAME                1 (b)
     *              20 BINARY_MULTIPLY
     *              22 STORE_NAME               4 (prod)
     *              24 LOAD_CONST               0 (None)
     *              26 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 2, 64,
        new PyBytes(new byte[] { 101, 0, 101, 1, 23, 0, 90, 2, 101,
                0, 101, 1, 24, 0, 90, 3, 101, 0, 101, 1, 20, 0, 90,
                4, 100, 0, 83, 0 }),
        new PyTuple(new PyObject[] { Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("a"),
                new PyUnicode("b"), new PyUnicode("sum"),
                new PyUnicode("diff"), new PyUnicode("prod") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}), new PyUnicode("binary"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 8, 1, 8, 1 }));
    //@formatter:on

    @Test
    void test_binary1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(7));
        globals.put(new PyUnicode("b"), new PyLong(6));
        PyCode code = BINARY;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(13), globals.get(
            new PyUnicode("sum")), "sum == 13");
        assertEquals(new PyLong(1), globals.get(
            new PyUnicode("diff")), "diff == 1");
        assertEquals(new PyLong(42), globals.get(
            new PyUnicode("prod")), "prod == 42");
        //@formatter:on
    }

    @Test
    void test_binary2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyFloat(7.0));
        globals.put(new PyUnicode("b"), new PyFloat(6.0));
        PyCode code = BINARY;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyFloat(13.0), globals.get(
            new PyUnicode("sum")), "sum == 13.0");
        assertEquals(new PyFloat(1.0), globals.get(
            new PyUnicode("diff")), "diff == 1.0");
        assertEquals(new PyFloat(42.0), globals.get(
            new PyUnicode("prod")), "prod == 42.0");
        //@formatter:on
    }

    @Test
    void test_binary3() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyFloat(7.0));
        globals.put(new PyUnicode("b"), new PyLong(6));
        PyCode code = BINARY;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyFloat(13.0), globals.get(
            new PyUnicode("sum")), "sum == 13.0");
        assertEquals(new PyFloat(1.0), globals.get(
            new PyUnicode("diff")), "diff == 1.0");
        assertEquals(new PyFloat(42.0), globals.get(
            new PyUnicode("prod")), "prod == 42.0");
        //@formatter:on
    }

    @Test
    void test_binary4() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(7));
        globals.put(new PyUnicode("b"), new PyFloat(6.0));
        PyCode code = BINARY;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyFloat(13.0), globals.get(
            new PyUnicode("sum")), "sum == 13.0");
        assertEquals(new PyFloat(1.0), globals.get(
            new PyUnicode("diff")), "diff == 1.0");
        assertEquals(new PyFloat(42.0), globals.get(
            new PyUnicode("prod")), "prod == 42.0");
        //@formatter:on
    }

}
