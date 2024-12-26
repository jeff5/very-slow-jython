// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.StringJoiner;

import uk.co.farowl.vsj4.runtime.Exposed.KeywordCollector;
import uk.co.farowl.vsj4.runtime.Exposed.PositionalCollector;
import uk.co.farowl.vsj4.support.MissingFeature;

/**
 * The Python {@code BaseException} and many common Python exceptions
 * (for example {@code TypeError}) are represented by instances of this
 * Java class. A Java subclass of {@code PyBaseException} is defined
 * only where a Python exception subclass adds fields to its parent.
 * <p>
 * The Python type of the exception is represented as a field (see
 * {@link #getType()}). The Python type is re-writable through
 * {@link #setType(Object)} with types represented by the same Java
 * class (which obviously cannot change).
 * <p>
 * A Java {@code try-catch} construct intended to catch Python
 * exceptions should catch {@code PyBaseException}. If it is intended to
 * catch only specific kinds of Python exception it must examine the
 * type and re-throw the unwanted exceptions.
 *
 * @implNote It would have been convenient, when catching exceptions in
 *     Java, if the different classes of Python exception could have
 *     been distinct classes in Java. This is not possible. User-defined
 *     exceptions extending different built-in exceptions allow class
 *     assignment even when they have distinct bases. It follows that
 *     all built-in exception types where this could happen must have
 *     the same representation class in Java.
 */
// Compare CPython PyBaseExceptionObject in pyerrors.c
public class PyBaseException extends RuntimeException
        implements WithClassAssignment, WithDict {
    private static final long serialVersionUID = 1L;

    /** The type object of Python {@code BaseException} exceptions. */
    public static final PyType TYPE = PyType.fromSpec(
            new TypeSpec("BaseException", MethodHandles.lookup())
                    .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                    .doc("Common base class for all exceptions"));

    /** Python type of the exception. */
    private PyType type;

    /** The dictionary of associated values on the instance. */
    // XXX dictionary required
    Map<Object, Object> dict;

    // XXX align constructor more directly to CPython.

    /**
     * The arguments given to the constructor, which is also the
     * arguments from {@code __new__} or {@code __init__}. Not
     * {@code null}.
     */
    protected PyTuple args;

    /**
     * A list of the notes added to this exception. Exposed as
     * {@code __notes__}.
     */
    private Object notes;
    /**
     * A writable field that holds the traceback object associated with
     * this exception. Exposed as {@code __traceback__}.
     */
    private Object traceback;
    /**
     * When raising a new exception while another exception is already
     * being handled, the new exception’s {@code __context__} attribute
     * is automatically set to the handled exception.
     */
    private Object context;
    /**
     * The exception following {@code raise ... from}. Exposed as
     * {@code __cause__}.
     */
    private Object cause;
    /**
     * Set to suppress reporting {@code __context__}. Exposed as
     * {@code __suppress_context__}.
     */
    private boolean suppressContext;

    // XXX current format and args could be applied to message.

    /**
     * Constructor specifying Python type and the argument tuple as the
     * associated value. We do this for maximum similarity with CPython,
     * where {@code __new__} does no more than allocate an object and
     * all attribute values are decoded by {@code __init__}.
     *
     * @param type Python type of the exception
     * @param args positional arguments
     */
    public PyBaseException(PyType type, PyTuple args) {
        super();
        // Ensure Python type is valid for Java class.
        this.type = checkClassAssignment(type);
        this.args = args;
    }

    // WithDict interface --------------------------------------------

    @Override
    public Map<Object, Object> getDict() {
        if (dict == null) { dict = new PyDict(); }
        return dict;
    }

    // WithClassAssignment interface ---------------------------------

    @Override
    public PyType getType() { return type; }

    @Override
    public void setType(Object replacementType) {
        type = checkClassAssignment(replacementType);
    }

    // Exception API -------------------------------------------------

    /**
     * If the Python type of this exception is not the {@code wanted}
     * type, immediately re-throw it, with its original stack trace.
     * This may be used at the top of a catch clause to narrow the
     * caught exception almost as if it were a Java type.
     *
     * @param wanted type for which the method returns normally
     */
    public void only(PyType wanted) {
        // XXX Should be isinstance test, accepting sub-types
        if (type != wanted) { throw this; }
    }

    @Override
    public String getMessage() {
        return args.size() > 0 ? PyUnicode.asString(args.get(0)) : "";
    }

    @Override
    public String toString() {
        return String.format("%s: %s", type.getName(), getMessage());
    }

    // special methods -----------------------------------------------

    // Compare CPython BaseException_* in exceptions.c

    /**
     * Create a new instance of the specified a Python exception class
     * {@code type}, which must be {@code BaseException} or a subclass
     * of it. The returned object is an instance of the Java
     * representation class of {@code type}.
     *
     * @param cls actual Python sub-class being created
     * @param args positional arguments
     * @param kwargs keywords (ignored)
     * @return newly-created object
     */
    @Exposed.PythonNewMethod
    static Object __new__(PyType cls, @PositionalCollector PyTuple args,
            @KeywordCollector PyDict kwargs) {
        // FIXME prevent arbitrary type here (but do so in wrapper)
        assert cls.isSubTypeOf(TYPE);
        Class<?> excClass = cls.javaClass();
        Object self;
        if (excClass == PyBaseException.class) {
            // Required type shares the primary representation
            self = new PyBaseException(cls, args);
        } else if (PyBaseException.class.isAssignableFrom(excClass)) {
            /*
             * We need an instance of a Python subclass E of
             * BaseException that needs a Java subclass representation.
             * This will happen if E was defined in Python and has
             * __slots__, or in Java and provides no __new__. This would
             * be ok if we could invoke the correct constructor.
             */
            try {
                // Constructor must be public for this :/
                // TODO Consider other ways to identify constructor
                Constructor<?> cons = cls.javaClass()
                        .getConstructor(CONSTRUCTOR_ARGS);
                self = cons.newInstance(cls, args);
            } catch (ReflectiveOperationException | SecurityException
                    | IllegalArgumentException e) {
                /*
                 * We did not find a constructor like PyBaseException,
                 * and so we do not know how to create an instance. If
                 * there had been a custom cls.__new__ for the type we
                 * would have landed there, not here, and it would
                 * Java-construct the instance.
                 */
                PyBaseException err = PyErr.format(PyExc.TypeError,
                        "Cannot construct a '%s' in %s.__new__ ",
                        cls.getName(), TYPE.getName());
                err.initCause(e);
                throw err;
            }
        } else {
            throw new MissingFeature("Subclass without __new__");
        }
        assert PyType.of(self) == cls;
        return self;
    }

    /**
     * Initialise this instance.
     *
     * @param args values to set as the attribute {@code args}
     * @param kwds keywords (should be empty)
     */
    void __init__(Object[] args, String[] kwds) {
        this.args = PyTuple.from(args);
    }

    /**
     * @return {@code str()} of this Python object.
     * @throws Throwable from getting the {@code str()} of {@code args}
     */
    protected Object __str__() throws Throwable {
        return switch (args.size()) {
            case 0 -> "";
            case 1 -> Abstract.str(args.get(0));
            default -> Abstract.str(args);
        };
    }

    /**
     * @return {@code repr()} of this Python object.
     * @throws Throwable from getting the {@code repr()} of {@code args}
     */
    protected Object __repr__() throws Throwable {
        String prefix = type.getName() + "(";
        StringJoiner sj = new StringJoiner(",", prefix, ")");
        for (Object o : args) {
            sj.add(PyUnicode.asString(Abstract.repr(o)));
        }
        return sj.toString();
    }

    // plumbing -------------------------------------------------------

    /**
     * Signature of {@link #PyBaseException} constructor. We have to
     * assume this signature may be used to construct an instance of the
     * Java representation class of a subclass if it has no
     * {@code __new__}.
     */
    protected static final Class<?>[] CONSTRUCTOR_ARGS =
            {PyType.class, PyTuple.class /* , PyDict.class */};
}
