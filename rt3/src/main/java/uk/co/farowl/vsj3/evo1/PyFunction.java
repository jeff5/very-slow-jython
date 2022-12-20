package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;

import uk.co.farowl.vsj3.evo1.PyType.Flag;
import uk.co.farowl.vsj3.evo1.base.InterpreterError;

/**
 * Python {@code function} object as created by a function definition
 * and subsequently called.
 */
public abstract class PyFunction<C extends PyCode>
        extends AbstractPyObject implements DictPyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("function", MethodHandles.lookup())
                    .flagNot(Flag.BASETYPE));

    /** The interpreter that defines the import context. */
    final Interpreter interpreter;

    /**
     * Get the interpreter that defines the import context, which was
     * current when this function was defined.
     *
     * @return interpreter that defines the import context
     */
    Interpreter getInterpreter() { return interpreter; }

    /**
     * The {@code __code__} attribute: a code object, which is writable,
     * but only with the right implementation type for the concrete
     * class of the function.
     */
    protected C code;

    /**
     * The read-only {@code __globals__} attribute is a {@code dict}:
     * other mappings won't do.
     */
    @Exposed.Member(value = "__globals__", readonly = true)
    final PyDict globals;

    /**
     * The read-only {@code __builtins__} attribute is a {@code dict}:
     * other mappings won't do.
     */
    @Exposed.Member(value = "__builtins__", readonly = true)
    final PyDict builtins;

    /** The (positional) {@code __defaults__} or {@code null}. */
    protected Object[] defaults;

    /** The {@code __kwdefaults__} or {@code null}. */
    protected PyDict kwdefaults;

    /**
     * The read-only {@code __closure__} attribute, or {@code null}. See
     * {@link #__closure__()} access method
     */
    @Exposed.FrozenArray
    protected PyCell[] closure;

    /** The {@code __doc__} attribute, can be set to anything. */
    // (but only a str prints in help)
    @Exposed.Member("__doc__")
    Object doc;

    /** The function name ({@code __name__} attribute). */
    String name;

    /**
     * The {@code __dict__} attribute, a {@code dict} or {@code null}.
     */
    private PyDict dict;

    /**
     * The {@code __module__} attribute, can be anything or {@code null}
     * meaning {@code None}
     */
    @Exposed.Member(value = "__module__")
    Object module;

    /**
     * The {@code __annotations__} attribute, a {@code dict} or
     * {@code null}.
     */
    PyDict annotations;

    /** The function qualified name ({@code __qualname__} attribute). */
    private String qualname;

    /**
     * Create a simple Python {@code function} with qualified name. This
     * might be the result of a function {@code def} statement. The
     * following are {@code null} after this constructor:
     * {@link #defaults}, {@link #kwdefaults}, {@link #closure},
     * {@link #doc}, {@link #dict}, {@link #module},
     * {@link #annotations}.
     *
     * @param interpreter provides import context
     * @param code compiled code object
     * @param globals at the point of definition
     * @param qualname fully qualified name (or {@code null} to default
     *     to {@code code.name})
     */
    // Compare PyFunction_NewWithQualName in funcobject.c
    // but our design references an interpreter too.
    PyFunction(Interpreter interpreter, C code, PyDict globals,
            String qualname) {
        super(TYPE);
        this.interpreter = interpreter;

        /*
         * Set code object: must check consistency between the code
         * object and numbers of arguments, presence of collector
         * arguments, etc. in the declaration of this function.
         */
        // XXX Might this depend on things only the sub-class knows?
        setCode(code);

        this.globals = globals;
        this.name = code.name;
        this.qualname = qualname != null ? qualname : this.name;
        // Or support code.qualname?

        // Get __doc__ from first constant in code (if str)
        Object doc;
        Object[] consts = code.consts;
        if (consts.length >= 1 && PyUnicode.TYPE.check(doc = consts[0]))
            this.doc = doc;
        else
            this.doc = Py.None;

        // __module__ = globals['__name__'] or null.
        this.module = globals.get("__name__");

        this.builtins = getBuiltinsDict();
    }

    /**
     * Create a PyFunction supplying most of the attributes at
     * construction time.
     * <p>
     * The strongly-typed {@code defaults}, {@code kwdefaults},
     * {@code annotations} and {@code closure} may be {@code null} if
     * they would otherwise be empty. {@code annotations} is always
     * exposed as a {@code dict}, but may be presented to the
     * constructor as a {@code dict} or {@code tuple} of keys and values
     * (or {@code null}).
     *
     * @implNote We differ from CPython in requiring a reference to the
     *     interpreter as an argument. Also, we favour a constructor in
     *     which the attributes are supplied {@code defaults},
     *     {@code kwdefaults}, {@code annotations} and {@code closure}
     *     rather than added after construction.
     *
     * @param interpreter providing the module context
     * @param code to execute
     * @param globals name space to treat as global variables
     * @param defaults default positional argument values or
     *     {@code null}
     * @param kwdefaults default keyword argument values or {@code null}
     * @param annotations type annotations ({@code dict}, {@code null}
     *     or maybe {@code tuple})
     * @param closure variables referenced but not defined here, must be
     *     size expected by code or {@code null} if empty.
     */
    // Compare CPython PyFunction_NewWithQualName in funcobject.c
    PyFunction(Interpreter interpreter, C code, PyDict globals,
            Object[] defaults, PyDict kwdefaults, Object annotations,
            PyCell[] closure) {
        super(TYPE);

        // We differ from CPython in requiring this reference
        this.interpreter = interpreter;

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
        this.builtins = getBuiltinsDict();

        // We differ from CPython in having these in construction
        this.defaults = defaults;
        this.kwdefaults = kwdefaults;
        this.closure = closure;
        if (annotations != null) { setAnnotations(annotations); }

        // Now we can check the code object against the closure etc.
        this.code = checkFreevars(code);
    }

    /**
     * Look in {@code __globals__} then the {@code interpreter} to find
     * the dictionary of built-in objects.
     *
     * @return the {@code __builtins__} of the function
     */
    private PyDict getBuiltinsDict() {
        Object builtins = globals.get("__builtins__");
        if (builtins != null) {
            if (PyModule.TYPE.check(builtins)) {
                return ((PyModule)builtins).getDict();
            }
        }
        /*
         * Difference from CPython: this is always known and will be
         * used by the frame created by a call, not the builtins of a
         * previous frame.
         */
        assert interpreter.builtinsModule.dict != null;
        return interpreter.builtinsModule.dict;
    }

    /**
     * Create a {@code PyFrame} that will execute this
     * {@code PyFunction}.
     *
     * @param locals name space to treat as local variables
     * @return the frame
     */
    abstract PyFrame<C, PyFunction<C>> createFrame(Object locals);

    // attribute access ----------------------------------------

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
    @Exposed.Getter("__code__")
    C getCode() { return code; }

    /**
     * Set the {@code __code__} object of this function.
     *
     * @param code new code object to assign
     */
    @Exposed.Setter("__code__")
    void setCode(C code) {
        this.code = checkFreevars(code);
    }

    /** @return the {@code __name__} attribute. */
    @Exposed.Getter("__name__")
    String getName() { return name; }

    @Exposed.Setter("__name__")
    void setName(Object name) {
        this.name = PyUnicode.asString(name,
                v -> Abstract.attrMustBeString("__name__", v));
    }

    /** @return the {@code __qualname__}, the qualified name. */
    @Exposed.Getter("__qualname__")
    String getQualname() { return qualname; }

    @Exposed.Setter("__qualname__")
    void setQualname(Object qualname) {
        this.qualname = PyUnicode.asString(qualname,
                v -> Abstract.attrMustBeString("__qualname__", v));
    }

    /**
     * @return the positional {@code __defaults__ tuple}.
     */
    @Exposed.Getter("__defaults__")
    Object getDefaults() { return tupleOrNone(defaults); }

    /**
     * Set the the positional {@code __defaults__ tuple}.
     */
    @Exposed.Setter("__defaults__")
    abstract void setDefaults(PyTuple defaults);

    /**
     * @return the keyword dict {@code __kwdefaults__} or {@code None}.
     */
    @Exposed.Getter("__kwdefaults__")
    Object getKwdefaults() { return kwdefaults; }

    @Exposed.Setter("__kwdefaults__")
    abstract void setKwdefaults(PyDict kwdefaults);

    /**
     * @return the {@code __closure__ tuple} or {@code None}.
     */
    @Exposed.Getter("__closure__")
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
        int nfree = code.freevars.length;

        if (nfree == 0) {
            if (n == 0)
                this.closure = null;
            else
                throw new TypeError("%s closure must be empty/None",
                        code.name);
        } else {
            if (n == nfree) {
                try {
                    this.closure = closure.toArray(new PyCell[n]);
                } catch (ArrayStoreException e) {
                    // The closure is not tuple of cells only
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
                throw new ValueError(
                        "%s requires closure of length %d, not %d",
                        code.name, nfree, n);
        }
    }

    /** @return the {@code __dict__} attribute. */
    @Exposed.Getter("__dict__")
    PyDict __dict__() { return dict; }

    @Exposed.Setter("__dict__")
    void __dict__(PyDict dict) { this.dict = dict; }

    /**
     * @return the {@code __annotations__} attribute as a {@code dict}.
     */
    @Exposed.Getter("__annotations__")
    PyDict getAnnotations() {
        if (annotations == null) { annotations = Py.dict(); }
        return annotations;
    }

    /**
     * Set the {@code __annotations__} attribute, which is always
     * exposed as a Python {@code dict}. In certain cases a
     * {@code tuple} may be supplied as the argument.
     *
     * @param anno specifying the annotations.
     */
    @Exposed.Setter("__annotations__")
    void setAnnotations(Object anno) {
        if (anno instanceof PyDict) {
            annotations = (PyDict)anno;
        } else if (anno instanceof PyTuple) {
            annotations = ((PyTuple)anno).pairsToDict();
        } else {
            // null or wrong type
            throw Abstract.attrMustBe("__annotations__", "a dictionary",
                    anno);
        }
    }


    // slot methods --------------------------------------------------

    /**
     * Canonical {@code __call__} slot with Jython conventions, making
     * function implementations callable.
     *
     * @param args all the arguments (position then keyword)
     * @param names of the keyword arguments (or {@code null})
     * @return the return from the call
     * @throws Throwable for errors raised in the function
     */
    abstract Object __call__(Object[] args, String[] names)
            throws Throwable;

    @SuppressWarnings("unused")
    private Object __repr__() { return toString(); }

    @SuppressWarnings("unused")
    private Object __str__() { return toString(); }


    // FastCall support ----------------------------------------------

    // XXX ... is needed.

    // plumbing ------------------------------------------------------

    @Override
    public Map<Object, Object> getDict() { return dict; }

    @Override
    public PyType getType() { return TYPE; }

    @Override
    // Compare CPython func_repr in funcobject.c
    public String toString() {
        return String.format("<function %.100s at %#x>", qualname,
                Py.id(this));
    }

    /**
     * Check that the number of free variables expected by the given
     * code object matches the length of the existing {@link #closure}
     * (or is zero if {@code closure==null}).
     *
     * @param c object to test (not {@code null}).
     * @return {@code c}
     */
    protected C checkFreevars(C c) {
        PyObjectUtil.errorIfNull(c, () -> new TypeError(
                "__code__ must be set to a code object"));
        int nfree = c.freevars.length;
        int nclosure = closure == null ? 0 : closure.length;
        if (nclosure != nfree) {
            throw new ValueError(FREE_VARS, name, nclosure, nfree);
        }
        return c;
    }

    private static String FREE_VARS =
            "%s() requires a code object with %d free vars, not %d";

    /** @return Python {@code None} if Java {@code null}. */
    protected static Object nullIsNone(Object o) {
        return o == null ? Py.None : o;
    }

    /**
     * @return tuple from argument array or {@code None} if Java
     *     {@code null}.
     */
    protected static <E> Object tupleOrNone(E[] a) {
        return a == null ? Py.None : PyTuple.from(a);
    }

}
