// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;

import uk.co.farowl.vsj4.core.PyCode.Layout;
import uk.co.farowl.vsj4.internal._PyUtil;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.types.Exposed.Getter;
import uk.co.farowl.vsj4.types.Exposed.Member;
import uk.co.farowl.vsj4.types.Exposed.Setter;
import uk.co.farowl.vsj4.types.FastCall;
import uk.co.farowl.vsj4.types.TypeSpec;
import uk.co.farowl.vsj4.types.WithDict;

/**
 * Python {@code function} object as created by executing a Python
 * function definition and will subsequently be callable from Python. A
 * {@code PyFunction} is not sensitive to the particular implementation
 * of {@link PyCode} passed to it. It must deal impartially with CPython
 * byte code and JVM byte code since the {@code __code__} attribute is
 * assignable with any {@code code} object.
 * <p>
 * The pattern behind {@code PyFunction}, that permits this flexibility
 * is that a {@code PyFunction} is created from a Python-oriented
 * description of the arguments and an initial {@code code} object. The
 * particular sub-class of {@link PyCode} representing the {@code code}
 * object, chosen by the compiler that processes the Python function or
 * module body, supplies the specialisations subsequently needed.
 */
public class PyFunction implements WithDict, FastCall {

    /** The type of Python object this class implements. */
    public static final PyType TYPE = PyType
            .fromSpec(new TypeSpec("function", MethodHandles.lookup()));

    /**
     * The interpreter that defines the import context. Not
     * {@code null}.
     */
    final Interpreter interpreter;

    /**
     * The {@code __code__} attribute: a code object, which is writable,
     * but only with the right implementation type for the concrete
     * class of the function. Not {@code null}.
     */
    protected PyCode code;

    /**
     * The read-only {@code __globals__} attribute is a {@code dict}:
     * other mappings won't do. Not {@code null}.
     */
    @Member(value = "__globals__", readonly = true)
    final PyDict globals;

    /**
     * The read-only {@code __builtins__} attribute is often a
     * {@code dict} but may be any object. It will be accessed using the
     * Python mapping protocol by the interpreter, at which point an
     * error may be raised. Not {@code null}.
     */
    @Member(value = "__builtins__", readonly = true)
    final Object builtins;

    /** The (positional) {@code __defaults__} or {@code null}. */
    protected Object[] defaults;

    /** The {@code __kwdefaults__} or {@code null}. */
    protected PyDict kwdefaults;

    /**
     * The read-only {@code __closure__} attribute, or {@code null}
     * meaning {@code None}. See {@link #setClosure(Collection)
     * __closure__} access method.
     */
    protected PyCell[] closure;

    /**
     * The {@code __doc__} attribute, can be set to anything or
     * {@code null}.
     */
    // (but only a str prints in help)
    @Member("__doc__")
    Object doc;

    /** The function name ({@code __name__} attribute). */
    String name;

    /**
     * The {@code __dict__} attribute, a {@code dict} or {@code null}.
     */
    private PyDict dict;

    /**
     * The {@code __module__} attribute, can be anything or {@code null}
     * meaning {@code None}.
     */
    @Member(value = "__module__")
    Object module;

    /**
     * The {@code __annotations__} attribute, a {@code dict} or
     * {@code null} which becomes an empty dictionary on use.
     */
    PyDict annotations;

    /** The function qualified name ({@code __qualname__} attribute). */
    private String qualname;

    /** Argument parser matched to {@link #code}. */
    private ArgParser argParser;

    /**
     * Create a {@code PyFunction} supplying most of the attributes at
     * construction time.
     * <p>
     * The strongly-typed {@code defaults}, {@code kwdefaults},
     * {@code annotations} and {@code closure} may be {@code null} if
     * they would otherwise be empty. {@code annotations} is always
     * exposed as a {@code dict}, but may be presented to the
     * constructor as a {@code dict} or {@code tuple} of keys and values
     * (or {@code null}).
     * <p>
     * A {@code PyFunction} is not sensitive to the particular
     * implementation of {@link PyCode} passed to it. The constructor
     * accepts a Python-oriented description of the parameters and their
     * defaults and a alongside the (initial) code object. Behaviour
     * that is sensitive to the implementation has to be supplied by the
     * {@code PyCode} itself and the {@link PyFrame} it returns from
     * {@link PyCode#createFrame(PyFunction, Object)
     * PyCode.createFrame}.
     * <p>
     * The Python-oriented parameter description is converted to a
     * parser by that {@code code} object. The code object, and any
     * subsequent replacement, has to be consistent with the parameter
     * descriptions given to the constructor.
     * <p>
     * The parser is able to place actual arguments (and defaults) into
     * <i>logical addresses</i> in instances of the {@link PyFrame}
     * sub-type specific to that code object implementation. (The
     * logical address of a parameter is its index in that PyCode's
     * {@link Layout#localnames() layout().localnames()}.)
     * <p>
     * When we come to call the function, it obtains a {@link PyFrame}
     * of the specific implementation from the {@link PyCode}, and from
     * that frame, a wrapper that translates the index of a parameter
     * into an operation to set that parameter in the frame.
     *
     * @implNote We differ from CPython in requiring a reference to the
     *     interpreter as an argument. Also, we favour a constructor in
     *     which the attributes are supplied {@code defaults},
     *     {@code kwdefaults}, {@code annotations} and {@code closure}
     *     rather than added after construction.
     *
     * @param interpreter providing the module context not {@code null}
     * @param code to execute (not {@code null})
     * @param globals name space to treat as global variables (not
     *     {@code null})
     * @param defaults default positional argument values or
     *     {@code null}
     * @param kwdefaults default keyword argument values or {@code null}
     * @param annotations type annotations ({@code dict}, {@code null}
     *     or maybe {@code tuple})
     * @param closure variables referenced but not defined here, must be
     *     size expected by code or {@code null} if empty.
     */
    // Compare CPython PyFunction_NewWithQualName in funcobject.c
    public PyFunction(Interpreter interpreter, PyCode code,
            PyDict globals, Object[] defaults, PyDict kwdefaults,
            Object annotations, PyCell[] closure) {
        // We differ from CPython in requiring this reference
        this.interpreter = interpreter;
        assert interpreter != null;

        this.globals = globals;
        this.name = code.name;
        this.qualname = code.qualname;

        // Get __doc__ from first constant in code (if str)
        Object doc;
        Object[] consts = code.consts;
        if (consts.length >= 1 && PyUnicode.TYPE.check(doc = consts[0]))
            this.doc = doc;
        else
            this.doc = Py.None;

        // __module__ = globals['__name__'] or null.
        this.module = globals.get("__name__");
        this.builtins = getBuiltinsFromGlobals();

        // We differ from CPython in having these in construction
        this.defaults = defaults;
        this.kwdefaults = kwdefaults;
        this.closure = closure;
        if (annotations != null) { setAnnotations(annotations); }

        // Now we can check the code object against the closure etc.
        this.code = checkFreevars(code);

        // Construct a parser to move arguments to the frame
        this.argParser = code.buildParser().defaults(defaults)
                .kwdefaults(kwdefaults);
    }

    /**
     * Create a simple {@code PyFunction} supplying minimal attributes
     * at construction time.
     *
     * @implNote We differ from CPython in requiring a reference to the
     *     interpreter as an argument.
     *
     * @param interpreter providing the module context not {@code null}
     * @param code to execute not {@code null}
     * @param globals name space to treat as global variables not
     *     {@code null}
     */
    // Compare CPython PyFunction_NewWithQualName in funcobject.c
    public PyFunction(Interpreter interpreter, PyCode code,
            PyDict globals) {
        this(interpreter, code, globals, null, null, null, null);
    }

    /**
     * Look in {@code __globals__} then the {@code interpreter} to find
     * the container of built-in objects.
     *
     * @return the {@code __builtins__} of the function
     */
    // Compare CPython _PyEval_BuiltinsFromGlobals in frameobject.c
    private Object getBuiltinsFromGlobals() {
        Object builtins = globals.get("__builtins__");
        if (builtins != null) {
            if (PyModule.TYPE.check(builtins)) {
                return ((PyModule)builtins).getDict();
            }
            return builtins;
        }
        /*
         * Difference from CPython: this is always known and will be
         * used by the frame created by a call, not the builtins of a
         * previous frame.
         */
        return interpreter.builtinsModule.dict;
    }

    /**
     * Create a {@code PyFrame} that will execute this
     * {@code PyFunction} on calling {@link PyFrame#eval()}. This frame
     * will be created "loose": {@link PyFrame#back} will be
     * {@code null} as it will not be on any thread's stack.
     *
     * @param locals name space to treat as local variables
     * @return the frame
     */
    PyFrame<? extends PyCode> createFrame(Object locals) {
        return code.createFrame(this, locals);
    }

    // attributes ----------------------------------------------------

    /*
     * XXX From Java it would be convenient to have a type-safe
     * signature, possibly returning null, but the signature from Python
     * has to be Object, treating null in Java as Python None (if that's
     * allowed). This would require the get-set descriptor to support
     * conversion in the way member descriptors do for their reference
     * types, probably disabled by "optional=true".
     */

    /**
     * @return the {@code __code__} object of this function.
     */
    @Getter("__code__")
    PyCode getCode() { return code; }

    /**
     * Set the {@code __code__} object of this function.
     *
     * @param code new code object to assign
     */
    @Setter("__code__")
    void setCode(PyCode code) {
        this.code = checkFreevars(code);
        argParser = code.buildParser().defaults(defaults)
                .kwdefaults(kwdefaults);
    }

    /** @return the {@code __name__} attribute. */
    @Getter("__name__")
    String getName() { return name; }

    /**
     * Set the {@code __name__} attribute.
     *
     * @param name for function
     */
    @Setter("__name__")
    void setName(Object name) {
        this.name = PyUnicode.asString(name,
                v -> _PyUtil.attrMustBeString("__name__", v));
    }

    /** @return the {@code __qualname__}, the qualified name. */
    @Getter("__qualname__")
    String getQualname() { return qualname; }

    /**
     * Set the {@code __qualname__} string.
     *
     * @param qualname for function
     */
    @Setter("__qualname__")
    void setQualname(Object qualname) {
        this.qualname = PyUnicode.asString(qualname,
                v -> _PyUtil.attrMustBeString("__qualname__", v));
    }

    /** @return the positional {@code __defaults__ tuple}. */
    @Getter("__defaults__")
    Object getDefaults() { return tupleOrNone(defaults); }

    /**
     * Set the the positional {@code __defaults__ tuple}.
     *
     * @param defaults to set
     */
    @Setter("__defaults__")
    void setDefaults(PyTuple defaults) {
        this.defaults = defaults.toArray();
        getArgParser().defaults(this.defaults);
    }

    /**
     * @return {@code __kwdefaults__} or {@code None}.
     */
    @Getter("__kwdefaults__")
    Object getKwdefaults() { return kwdefaults; }

    /**
     * Provide the keyword defaults dictionary. Subsequent changes to
     * the dictionary will affect argument parsing, as required for a
     * Python {@link PyFunction function}. (Concurrent access to the
     * mapping is a client issue.)
     *
     * @param kwdefaults specifying {@code __kwdefaults__}
     */
    @Setter("__kwdefaults__")
    void setKwdefaults(PyDict kwdefaults) {
        this.kwdefaults = kwdefaults;
        getArgParser().kwdefaults(this.kwdefaults);
    }

    /**
     * @return the {@code __closure__ tuple} or {@code None}.
     */
    @Getter("__closure__")
    Object getClosure() { return tupleOrNone(closure); }

    /**
     * Set the {@code __closure__} attribute. This is <b>not</b> exposed
     * as a setter method to Python. We use it internally.
     *
     * @param <E> element type
     * @param closure elements with which to populate the closure
     */
    <E> void setClosure(Collection<E> closure) {

        int n = closure == null ? 0 : closure.size();
        int nfree = code.layout().nfreevars();

        if (nfree == 0) {
            if (n == 0)
                this.closure = null;
            else
                throw PyErr.format(PyExc.TypeError,
                        "%s closure must be empty/None", code.name);
        } else {
            if (n == nfree) {
                try {
                    this.closure = closure.toArray(new PyCell[n]);
                } catch (ArrayStoreException e) {
                    // The closure is not a tuple of cells only
                    for (Object o : closure) {
                        if (!(o instanceof PyCell)) {
                            throw Abstract.typeError(
                                    "closure: expected cell, found %s",
                                    o);
                        }
                    }
                    throw new InterpreterError(
                            "Failed to make closure from %s", closure);
                }
            } else
                throw PyErr.format(PyExc.ValueError,
                        "%s requires closure of length %d, not %d",
                        code.name, nfree, n);
        }
    }

    /** @return the {@code __dict__} attribute */
    @Getter("__dict__")
    PyDict __dict__() { return dict; }

    /**
     * Set the {@code __dict__} attribute
     *
     * @param dict to set
     */
    @Setter("__dict__")
    void __dict__(PyDict dict) { this.dict = dict; }

    /** @return the {@code __annotations__} attribute */
    @Getter("__annotations__")
    PyDict getAnnotations() {
        if (annotations == null) { annotations = Py.dict(); }
        return annotations;
    }

    /**
     * Set the {@code __annotations__} attribute, which is always
     * exposed as a Python {@code dict}.
     * <p>
     * In certain cases a {@code tuple} may be supplied as the argument.
     * In order to understand why, study the CPython
     * {@code MAKE_FUNCTION} opcode and {@code func_get_annotation_dict}
     * in {@code funcobject.c}.
     *
     * @param anno specifying the annotations.
     */
    @Setter("__annotations__")
    private void setAnnotations(Object anno) {
        if (anno instanceof PyDict d) {
            annotations = d;
        } else if (anno instanceof PyTuple t) {
            // TODO Consider making into a lazy get as in CPython
            annotations = t.pairsToDict();
        } else {
            // null or wrong type
            throw _PyUtil.attrMustBe("__annotations__", "a dictionary",
                    anno);
        }
    }

    // Special methods -----------------------------------------------

    /**
     * Canonical {@code __call__} slot with Jython conventions, making
     * function implementations callable.
     *
     * @param args all the arguments (position then keyword)
     * @param names of the keyword arguments (or {@code null})
     * @return the return from the call
     * @throws Throwable for errors raised in the function
     */
    Object __call__(Object[] args, String[] names) throws Throwable {
        try {
            return call(args, names);
        } catch (ArgumentError ae) {
            // Translate ArgumentError to Python TypeError
            throw typeError(ae, args, names);
        }
    }

    @SuppressWarnings("unused")
    private Object __repr__() {
        return String.format("<function %.100s at %#x>", qualname,
                Py.id(this));
    }

    // FastCall support ----------------------------------------------

    /*
     * For many of our built-in callables, we provide Java subclasses
     * that specialise one of the call signatures of FastCall to suit
     * the number of parameters expected by the Java implementation
     * method (the Java method found at a handle held by the callable).
     * This is not possible for PyFunction for the simple reason that
     * the code object is replaceable, and so the signature may change,
     * and the identity of the PyFunction object remain the same.
     */

    @Override
    public Object call(Object[] args, String[] names)
            throws ArgumentError, Throwable {
        // Create a loose frame matching the PyCode
        PyFrame<? extends PyCode> frame = code.createFrame(this, null);

        // Custom implementations may have a fast path
        if (names == null || names.length == 0) {
            // Only positional arguments were given
            switch (args.length) {
                case 0:
                    return frame.call();
                case 1:
                    return frame.call(args[0]);
                case 2:
                    return frame.call(args[0], args[1]);
                case 3:
                    return frame.call(args[0], args[1], args[2]);
                case 4:
                    return frame.call(args[0], args[2], args[2],
                            args[3]);
                default:
                    // If this fails, add more cases.
                    assert args.length > FastCall.MAX_POSITIONAL;
                    break;
            }
            // Fall through to the slow path
            names = null;
        }

        // Fill the frame variables and eval() the frame.
        return frame.call(args, names);
    }

    @Override
    public PyBaseException typeError(ArgumentError ae, Object[] args,
            String[] names) {
        // We can use the default message format, adding only the name.
        return FastCall.typeError(code.name, ae, args, names);
    }

    // Plumbing ------------------------------------------------------

    @Override
    public Map<Object, Object> getDict() { return dict; }

    @Override
    public PyType getType() { return TYPE; }

    @Override
    // Compare CPython func_repr in funcobject.c
    public String toString() { return PyUtil.defaultToString(this); }

    /**
     * Get the interpreter that defines the import context, which was
     * current when this function was defined. Not {@code null}.
     *
     * @return interpreter that defines the import context
     */
    Interpreter getInterpreter() { return interpreter; }

    /**
     * Return the argument parser for this function. This parser is
     * derived from the code object last assigned to the function. It is
     * used to create a wrapper on the frame created when the function
     * is called.
     *
     * @return the argParser
     */
    ArgParser getArgParser() { return argParser; }

    /**
     * Check that the number of free variables expected by the given
     * code object matches the length of the existing {@link #closure}
     * (or is zero if {@code closure==null}).
     *
     * @param c object to test (not {@code null}).
     * @return {@code c}
     */
    PyCode checkFreevars(PyCode c) {
        PyUtil.errorIfNull(c, () -> PyErr.format(PyExc.TypeError,
                "__code__ must be set to a code object"));
        int nfree = c.layout().nfreevars();
        int nclosure = closure == null ? 0 : closure.length;
        if (nclosure != nfree) {
            throw PyErr.format(PyExc.ValueError, FREE_VARS, name,
                    nclosure, nfree);
        }
        return c;
    }

    private static String FREE_VARS =
            "%s() requires a code object with %d free vars, not %d";

    /**
     * Present an array as a tuple, or if the expression variable is
     * {@code null}, as a Python {@code None}.
     *
     * @param <E> element type of the array
     * @param a array providing elements or {@code null}
     * @return tuple from argument array or {@code None} if the array
     *     was Java {@code null}.
     */
    protected static <E> Object tupleOrNone(E[] a) {
        return a == null ? Py.None : PyTuple.from(a);
    }
}
