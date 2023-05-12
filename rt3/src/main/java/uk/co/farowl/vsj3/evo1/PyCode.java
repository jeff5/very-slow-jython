// Copyright (c)2023 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
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
     * which implemented in the sub-class to suit its local needs.
     */

    /**
     * Characteristics of a {@code PyCode} (as CPython co_flags). These
     * are not all relevant to all code types.
     */
    enum Trait {
        OPTIMIZED, NEWLOCALS, VARARGS, VARKEYWORDS, NESTED, GENERATOR,
        NOFREE, COROUTINE, ITERABLE_COROUTINE, ASYNC_GENERATOR
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

    /** int expression of {@link #traits} compatible with CPython. */
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

    // ??? Here or in 3.11 sub-class?
    final String[] localsplusnames;
    final byte[] localspluskinds;

    /* Masks for co_flags above */
    public static final int CO_OPTIMIZED = 0x0001;
    public static final int CO_NEWLOCALS = 0x0002;
    public static final int CO_VARARGS = 0x0004;
    public static final int CO_VARKEYWORDS = 0x0008;
    public static final int CO_NESTED = 0x0010;
    public static final int CO_GENERATOR = 0x0020;

    /*
     * The CO_NOFREE flag is set if there are no free or cell variables.
     * This information is redundant, but it allows a single flag test
     * to determine whether there is any extra work to be done when the
     * call frame it setup.
     */
    public static final int CO_NOFREE = 0x0040;

    /*
     * The CO_COROUTINE flag is set for coroutine functions (defined
     * with ``async def`` keywords)
     */
    public static final int CO_COROUTINE = 0x0080;
    public static final int CO_ITERABLE_COROUTINE = 0x0100;
    public static final int CO_ASYNC_GENERATOR = 0x0200;

    // Construct with arrays not tuples.
    /**
     * Full constructor. The {@link #traits} of the code are supplied
     * here as CPython reports them: as a bit array in an integer, but
     * the constructor makes a conversion, and it is the {@link #traits}
     * which should be used at the Java level.
     *
     * @param filename value of {@link #filename} must be {@code str}
     * @param name value of {@link #name}
     * @param qualname value of {@link #qualname}
     * @param flags value of {@link #flags} and {@link #traits}
     *
     * @param firstlineno value of {@link #firstlineno}
     *
     * @param argcount value of {@link #argcount}
     * @param posonlyargcount value of {@link #posonlyargcount}
     * @param kwonlyargcount value of {@link #kwonlyargcount}
     *
     * @param consts value of {@link #consts}
     * @param names value of {@link #names}
     */
    public PyCode( //
            // Grouped as _PyCodeConstructor in pycore_code.h
            // Metadata
            String filename, String name, String qualname, //
            int flags,
            // The code
            int firstlineno, // ??? sensible given filename
            // Used by the code
            Object[] consts, String[] names, //
            // Mapping frame offsets to information
            String[] localsplusnames, byte[] localspluskinds,
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
// if (varnames.size() != nlocals)
// throw new ValueError("code: varnames is too small");

        // Here or in sub-class?
        this.localsplusnames = localsplusnames;
        this.localspluskinds = localspluskinds;
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
     * Get {@link #varnames} as a {@code tuple}.
     *
     * @return {@link #varnames} as a {@code tuple}
     */
    @Getter
    PyTuple co_varnames() {
        return null; // PyTuple.from(varnames);
    }

    /**
     * Get {@link #freevars} as a {@code tuple}.
     *
     * @return {@link #freevars} as a {@code tuple}
     */
    @Getter
    PyTuple co_freevars() {
        return null; // PyTuple.from(freevars);
    }

    /**
     * Get {@link #cellvars} as a {@code tuple}.
     *
     * @return {@link #cellvars} as a {@code tuple}
     */
    @Getter
    PyTuple co_cellvars() {
        return null; // PyTuple.from(cellvars);
    }

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

    // Plumbing -------------------------------------------------------

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

    private static final String NAME_TUPLES_STRING =
            "name tuple must contain only strings, not '%s' (in %s)";

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
        return traits.isEmpty() ? EnumSet.noneOf(Trait.class)
                : EnumSet.copyOf(traits);
    }
}
