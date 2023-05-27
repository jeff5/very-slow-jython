// Copyright (c)2023 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.EnumSet;

import uk.co.farowl.vsj3.evo1.Exposed.Getter;
import uk.co.farowl.vsj3.evo1.Exposed.Member;

/**
 * The Python {@code code} object. A {@code code} object describes the
 * layout of a {@link PyFrame}, and is a factory for frames of matching
 * type.
 * <p>
 * In this implementation, while there is only one Python type
 * {@code code}, we allow alternative implementations of it. In
 * particular, we provide for a code object that is the result of
 * compiling to JVM byte code, in addition to the expected support for
 * Python byte code.
 * <p>
 * The abstract base {@code PyCode} has a need to store fewer attributes
 * than the concrete CPython {@code code} object, where the only
 * realisation holds a block of byte code with broadly similar needs
 * from one version to the next. We provide get-methods matching all
 * those of CPython, and each concrete class can override them where
 * meaningful.
 */
// Compare CPython PyCodeObject in codeobject.c
public abstract class PyCode implements CraftedPyObject {

    /** The Python type {@code code}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("code", MethodHandles.lookup())
                    .flagNot(PyType.Flag.BASETYPE));
    /*
     * It is not easy to say, while there is only one concrete sub-class
     * to learn from, which attributes may safely be be in the base, and
     * which implemented in the sub-class to suit the local needs of a
     * definition in CPython or Java byte code.
     */

    /**
     * Characteristics of a {@code PyCode} (as CPython co_flags). These
     * are not all relevant to all code types.
     */
    enum Trait {
        OPTIMIZED, NEWLOCALS, VARARGS, VARKEYWORDS, NESTED, GENERATOR,
        COROUTINE, ITERABLE_COROUTINE, ASYNC_GENERATOR
    }

    /** Characteristics of this {@code PyCode} (as CPython co_flags). */
    final EnumSet<Trait> traits;

    /** Source file from which compiled. */
    @Member("co_filename")
    final String filename;
    /** Name of function etc. */
    @Member("co_name")
    final String name;
    /** Fully qualified name of function etc. */
    @Member("co_qualname")
    final String qualname;

    /** {@code int} bitmap of code traits compatible with CPython. */
    final int flags;

    /** Number of positional parameters (not counting {@code *args}). */
    @Member("co_argcount")
    final int argcount;
    /** Number of positional-only parameters. */
    @Member("co_posonlyargcount")
    final int posonlyargcount;
    /** Number of keyword-only parameters. */
    @Member("co_kwonlyargcount")
    final int kwonlyargcount;

    /** First source line number. */
    final int firstlineno;

    // Questionable: would a Java Python frame need this?
    /** Constant objects needed by the code. Not {@code null}. */
    final Object[] consts;

    /** Names referenced in the code. Not {@code null}. */
    final String[] names;

    /*
     * In CPython 3.11 variable names are in one consolidated array,
     * aligned to the one consolidated frame holding all the local
     * variables, whether plain, cell or free. We find it helpful to
     * have three such arrays for the variables, and corresponding
     * arrays for the names.
     */
    /**
     * Non-cell locals, beginning with the arguments. These are the
     * variables eligible for access by the FAST_LOAD family of opcodes.
     * Essentially this is {@code co_varnames}. (Not {@code null}.)
     */
    final String[] varnames;

    /**
     * Names of {@link PyCell} variables defined in this {@code code}
     * and referenced elsewhere. Essentially this is
     * {@code co_cellvars}. (Not {@code null}.)
     */
    final String[] cellvars;

    /**
     * Names referenced but not defined here. These are names of
     * {@link PyCell} variables (in the frame of this {@code code}) have
     * been created in another execution frame and passed as the closure
     * of a function. Essentially this is {@code co_freevars}. (Not
     * {@code null}.)
     */
    final String[] freevars;

    /**
     * Describe the layout of the frame local variables (including
     * arguments), cell and free variables. {@link #varnames},
     * {@link #cellvars} and {@link #freevars} are derived from this
     * during construction, but we need it during execution as well.
     */
    // CPython specific at first glance but not after reflection.
    // Compare CPython 3.11 localsplusnames and localspluskinds
    final Layout layout;

    // Bit masks appearing in flags
    /** The code uses fast local local variables, not a map. */
    public static final int CO_OPTIMIZED = 0x0001;
    /** A new {@code dict} should be created for local variables. */
    // NEWLOCALS is never acted on in CPython (but set for functions)
    public static final int CO_NEWLOCALS = 0x0002;
    /** The function has a collector for excess positional arguments */
    public static final int CO_VARARGS = 0x0004;
    /** The function has a collector for excess keyword arguments */
    public static final int CO_VARKEYWORDS = 0x0008;
    /** The code is for a nested function. */
    public static final int CO_NESTED = 0x0010;
    /**
     * The code is for a generator function, i.e. a generator object is
     * returned when the code object is executed..
     */
    public static final int CO_GENERATOR = 0x0020;

    /**
     * The code is for a coroutine function (defined with
     * {@code async def}). When the code object is executed it returns a
     * coroutine object.
     */
    public static final int CO_COROUTINE = 0x0080;
    /**
     * The flag is used to transform generators into generator-based
     * coroutines. Generator objects with this flag can be used in
     * {@code await} expression, and can {@code yield from} coroutine
     * objects. See PEP 492 for more details.
     */
    public static final int CO_ITERABLE_COROUTINE = 0x0100;
    /**
     * The code object is an asynchronous generator function. When the
     * code object is executed it returns an asynchronous generator
     * object. See PEP 525 for more details.
     */
    public static final int CO_ASYNC_GENERATOR = 0x0200;

    // Construct with arrays not tuples.
    /**
     * Full constructor. The {@link #traits} of the code are supplied
     * here as CPython reports them: as a bit array in an integer, but
     * the constructor makes a conversion, and it is the {@link #traits}
     * which should be used at the Java level.
     * <p>
     * Where the parameters map directly to an attribute of the code
     * object, that is the best way to explain them. Note that this
     * factory method is tuned to the needs of {@code marshal.read}
     * where the serialised form makes no secret of the version-specific
     * implementation details.
     *
     * @param filename {@code co_filename}
     * @param name {@code co_name}
     * @param qualname {@code co_qualname}
     * @param flags {@code co_flags} a bitmap of traits
     *
     * @param firstlineno {@code co_firstlineno}
     *
     * @param consts {@code co_consts}
     * @param names {@code co_names}
     *
     * @param layout variable names and properties, in the order
     *     {@code co_varnames + co_cellvars + co_freevars} but without
     *     repetition.
     *
     * @param argcount {@code co_argcount} the number of positional
     *     parameters (including positional-only arguments and arguments
     *     with default values)
     * @param posonlyargcount {@code co_posonlyargcount} the number of
     *     positional-only arguments (including arguments with default
     *     values)
     * @param kwonlyargcount {@code co_kwonlyargcount} the number of
     *     keyword-only arguments (including arguments with default
     *     values)
     */
    public PyCode( //
            // Grouped as _PyCodeConstructor in pycore_code.h
            // Metadata
            String filename, String name, String qualname, //
            int flags,
            // The code (not seeing actual byte code in abstract base)
            int firstlineno, // ??? sensible given filename
            // Used by the code
            Object[] consts, String[] names, //
            // Mapping frame offsets to information
            Layout layout,
            // Parameter navigation with varnames
            int argcount, int posonlyargcount, int kwonlyargcount) {
        this.argcount = argcount;
        this.posonlyargcount = posonlyargcount;
        this.kwonlyargcount = kwonlyargcount;

        this.flags = flags;
        this.consts = consts;

        this.names = names;

        this.filename = filename;
        this.name = name;
        this.qualname = qualname;
        this.firstlineno = firstlineno;

        this.traits = traitsFrom(flags);

        // Get the names of plain, cell and free variables
        this.layout = layout;
        this.varnames = layout.varnames();
        this.cellvars = layout.cellvars();
        this.freevars = layout.freevars();
    }

    /**
     * In a {@code code} object, there is an instance of
     * {@code Variable} for each variable that must be accommodated in
     * the frame when the code runs. This is mostly relevant to function
     * (or method) bodies: there is no such storage for code blocks that
     * expect their locals to be in a dictionary.
     * <p>
     * Every {@code PyCode} holds an array of these {@code Variable}s to
     * enumerate the local variables and, according to their kind
     * (plain, cell or free), to specify where the value is held. This
     * array is supplied at construction as the {@code layout} argument
     * to the constructor
     * {@link PyCode#PyCode(String, String, String, int, int, Object[], String[], Layout, int, int, int)
     * PyCode(...)} and certain sub-class constructors.
     * <p>
     * The concrete class {@code Variable} describes a plain local
     * variable or argument whose value is held as an {@code Object}.
     * Three other cases, where the value is held in a {@link PyCell},
     * are dealt with by sub-classes of {@code Variable}:
     * {@link CellVariable}, {@link CellArgument} and
     * {@link FreeVariable}.
     */
    static class Variable {
        /** The variable name. */
        final String name;

        /** Index in the array where the value is stored. */
        final int index;

        /**
         * Base constructor and constructor for an argument or a private
         * local variable.
         *
         * @param name sets {@link #name}
         * @param index sets {@link #index}
         */
        Variable(String name, int index) {
            this.name = name;
            this.index = index;
        }

        /**
         * True if and only if this is a plain (direct) local variable
         * or argument, not stored in a {@link PyCell}, exactly of class
         * {@code Variable}.
         *
         * @return {@code true} iff plain local variable or argument.
         */
        boolean isLocal() { return true; }

        /**
         * True if and only if this is stored in a {@link PyCell}
         * allocated for the current frame, exactly of class
         * {@link CellVariable} or {@link CellArgument}.
         *
         * @return {@code true} iff exactly .
         */
        boolean isCell() { return false; }

        /**
         * True if and only if this is stored in a {@link PyCell}
         * allocated by a different frame, exactly of class
         * {@link FreeVariable}.
         *
         * @return {@code true} iff exactly .
         */
        boolean isFree() { return false; }

        @Override
        public String toString() {
            return String.format("%s%s%s[%d] %s",
                    isLocal() ? "LOCAL " : "", isCell() ? "CELL " : "",
                    isFree() ? "FREE " : "", index, name);
        }
    }

    /**
     * A {@code CellVariable} is a {@link Variable} that describes a
     * variable allocated here and shared with other frames. It is held
     * in a {@link PyCell} allocated when the frame is created.
     */
    static class CellVariable extends Variable {
        /**
         * Constructor for variable created here and shared with other
         * frames (for nested scopes).
         *
         * @param name sets {@link #name}
         * @param index sets {@link #index}
         */
        CellVariable(String name, int index) { super(name, index); }

        @Override
        boolean isLocal() { return false; }

        @Override
        boolean isCell() { return true; }
    }

    /**
     * A {@code CellArgument} is a {@link CellVariable} that describes
     * an argument allocated here and shared with other frames. It is
     * held in a {@link PyCell} allocated when the frame is created but
     * initialised from an entry in the plain storage to which arguments
     * are delivered when processing a call.
     */
    /*
     * This design appears not to appreciate the virtues of a change in
     * the CPython approach (in 3.11) that has the locals in a single
     * linear array, whose elements are cast to PyCell if necessary. We
     * do not favour this because casts in Java are checked at run-time:
     * our design is to maintains type-safe separation.
     */
    static class CellArgument extends CellVariable {
        /**
         * The index in the plain arguments from which to take the
         * initial value.
         */
        final int argIndex;

        /**
         * Constructor for a variable that is an argument here and
         * shared with other frames (for nested scopes).
         *
         * @param name sets {@link #name}
         * @param index sets {@link #index}
         * @param argIndex sets {@link #argIndex}
         */
        CellArgument(String name, int index, int argIndex) {
            super(name, index);
            this.argIndex = argIndex;
        }

        @Override
        boolean isLocal() { return true; }
    }

    /**
     * A {@code FreeVariable} is a {@link Variable} that describes a
     * variable free in the frame. It is held in a {@link PyCell} in an
     * array supplied as a closure in a function object.
     */
    static class FreeVariable extends Variable {
        /**
         * Constructor for a variable that is created in another frame
         * (and possibly nested scopes).
         *
         * @param name sets {@link #name}
         * @param index sets {@link #index}
         */
        FreeVariable(String name, int index) { super(name, index); }

        @Override
        boolean isLocal() { return false; }

        @Override
        boolean isFree() { return true; }
    }

    /**
     * Interface on a store of information about the variables required
     * by a code object and where they will be stored in the frame it
     * creates. It is used to initialise
     */
    interface Layout {
        /** @return names of plain variables (including arguments). */
        String[] varnames();

        /** @return names of cell variables (including arguments). */
        String[] cellvars();

        /** @return names of free variables. */
        String[] freevars();

        /** @return an array of all the variables. */
        Variable[] vars();

        /**
         * Return details (name and kind) of one variable.
         *
         * @param index of variable
         * @return details (name and kind) of one variable.
         */
        default Variable get(int index) { return vars()[index]; }
    }

    // Attributes -----------------------------------------------------

    @SuppressWarnings("static-method")
    @Getter
    int co_stacksize() { return 0; }

    @SuppressWarnings("static-method")
    @Getter
    PyBytes co_code() { return PyBytes.EMPTY; }

    @SuppressWarnings("static-method")
    @Getter
    PyBytes co_lnotab() { return PyBytes.EMPTY; }

    /**
     * Get {@link #consts} as a {@code tuple}.
     *
     * @return {@link #consts} as a {@code tuple}
     */
    @Getter
    PyTuple co_consts() { return PyTuple.from(consts); }

    /**
     * Get {@link #names} as a {@code tuple}.
     *
     * @return {@link #names} as a {@code tuple}
     */
    @Getter
    PyTuple co_names() { return PyTuple.from(names); }

    /**
     * Get {@code co_varnames} as a {@code tuple}.
     *
     * @return {@code co_varnames} as a {@code tuple}
     */
    @Getter
    PyTuple co_varnames() { return PyTuple.from(varnames); }

    /**
     * Get {@code co_cellvars} as a {@code tuple}.
     *
     * @return {@code co_cellvars} as a {@code tuple}
     */
    @Getter
    PyTuple co_cellvars() { return PyTuple.from(cellvars); }

    /**
     * Get {@code co_freevars} as a {@code tuple}.
     *
     * @return {@code co_freevars} as a {@code tuple}
     */
    @Getter
    PyTuple co_freevars() { return PyTuple.from(freevars); }

    // slot methods --------------------------------------------------

    @SuppressWarnings("unused")
    private Object __repr__() { return toString(); }

    @SuppressWarnings("unused")
    private Object __str__() { return toString(); }

    // Java API -------------------------------------------------------

    @Override
    public PyType getType() { return TYPE; }

    @Override
    // Compare CPython code_repr in codeobject.c
    public String toString() {
        int lineno = firstlineno != 0 ? firstlineno : -1;
        String file = filename, q = "\"";
        if (file == null) { file = "???"; q = ""; }
        return String.format(
                "<code object %s at %#x, file %s%s%s, line %d>", name,
                Py.id(this), q, file, q, lineno);
    }

    /**
     * Create a {@code PyFunction} that will execute this
     * {@code PyCode}. The strongly-typed {@code defaults},
     * {@code kwdefaults} , {@code closure} and {@code annotations} may
     * be {@code null} if they would otherwise be empty.
     * {@code annotations} is always exposed as a {@code dict}, but may
     * be presented to the constructor as a {@code dict} or
     * {@code tuple} of keys and values (or {@code null}).
     *
     * @param interpreter providing the module context
     * @param globals name space to treat as global variables
     * @param defaults default positional argument values or
     *     {@code null}
     * @param kwdefaults default keyword argument values or {@code null}
     * @param annotations type annotations ({@code dict}, {@code null}
     *     or maybe {@code tuple})
     * @param closure variables referenced but not defined here, must be
     *     size expected by code or {@code null} if empty.
     * @return the function from this code
     */
    abstract PyFunction<? extends PyCode> createFunction(
            Interpreter interpreter, PyDict globals, Object[] defaults,
            PyDict kwdefaults, Object annotations, PyCell[] closure);

    /**
     * Create a {@code PyFunction} that will execute this {@code PyCode}
     * (adequate for module-level code).
     *
     * @param interpreter providing the module context
     * @param globals name space to treat as global variables
     * @return the function
     */
    // Compare CPython PyFunction_New in funcobject.c
    // ... with the interpreter required by architecture
    PyFunction<? extends PyCode> createFunction(Interpreter interpreter,
            PyDict globals) {
        return createFunction(interpreter, globals, Py.EMPTY_ARRAY,
                Py.dict(), Py.dict(), PyCell.EMPTY_ARRAY);
    }

    /**
     * Return the total space in a frame of a code object, that must be
     * reserved for arguments. This is also the size of the layout array
     * appearing as an argument to constructors.
     *
     * @return total space in frame for arguments
     */
    int totalargs() { return totalargs(argcount, flags); }

    private static final int CO_VARARGS_SHIFT = // 2
            Integer.numberOfTrailingZeros(CO_VARARGS);
    private static final int CO_VARKEYWORDS_SHIFT =// 3
            Integer.numberOfTrailingZeros(CO_VARKEYWORDS);

    /**
     * From the values of {@code co_argcount} and {@code co_flags} (in
     * practice, as they are de-marshalled), compute the total space in
     * a frame of a code object, that must be reserved for arguments.
     * This is also the size of the layout array appearing as an
     * argument to certain constructors.
     *
     * @param argcount argument count excluding collector parameters.
     * @param flags bit map of code traits
     * @return total space in frame for arguments
     */
    static int totalargs(int argcount, int flags) {
        return argcount + (flags >>> CO_VARARGS_SHIFT & 1)
                + (flags >>> CO_VARKEYWORDS_SHIFT & 1);
    }

    // Plumbing -------------------------------------------------------

    /** Empty (zero-length) array of {@code String}. */
    protected static final String[] EMPTY_STRING_ARRAY =
            Py.EMPTY_STRING_ARRAY;

    private static final String NAME_TUPLES_STRING =
            "name tuple must contain only strings, not '%s' (in %s)";

    /**
     * Check that all the argument is a tuple and that all objects in it
     * are {@code str}, and return them as an array of {@code String}.
     *
     * @param v of names
     * @param tupleName the name of the argument (for error production)
     * @return the names as {@code String[]}
     */
    protected static String[] names(Object v, String tupleName) {
        PyTuple tuple = castTuple(v, tupleName);
        String[] s = new String[tuple.size()];
        int i = 0;
        for (Object name : tuple) {
            s[i++] = PyUnicode.asString(name, o -> Abstract
                    .typeError(NAME_TUPLES_STRING, o, tupleName));
        }
        return s;
    }

    /**
     * @param v to check is a Python {@code bytes}
     * @param arg name of argument (for message only)
     * @return {@code v}
     * @throws TypeError if {@code v} cannot be cast to {@code bytes}
     */
    protected static PyBytes castBytes(Object v, String arg)
            throws TypeError {
        if (v instanceof PyBytes b)
            return b;
        else
            throw Abstract.argumentTypeError("code", arg, "bytes", v);
    }

    /**
     * @param v to check is a Python {@code tuple}
     * @param arg name of argument (for message only)
     * @return {@code v}
     * @throws TypeError if {@code v} cannot be cast to {@code tuple}
     */
    protected static PyTuple castTuple(Object v, String arg) {
        if (v instanceof PyTuple t)
            return t;
        else
            throw Abstract.argumentTypeError("code", arg, "tuple", v);
    }

    /**
     * Cast a Python {@code str} to a Java String or raise a
     * {@code TypeError} mentioning an argument name.
     *
     * @param v to check and cast/convert
     * @param argName the name of the argument (for error production)
     * @return {@code v}
     */
    protected static String castString(Object v, String argName) {
        return PyUnicode.asString(v, o -> Abstract
                .argumentTypeError("code", argName, "str", o));
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
        return traits.isEmpty() ? EnumSet.noneOf(Trait.class)
                : EnumSet.copyOf(traits);
    }
}
