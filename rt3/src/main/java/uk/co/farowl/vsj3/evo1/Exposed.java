package uk.co.farowl.vsj3.evo1;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotations that may be placed on elements of a Java class intended
 * as the implementation of a Python type, and that the {@link Exposer}
 * will look for when during the definition of a {@link PyType}.
 */
interface Exposed {

    /**
     * Identify a Python instance method of a type or module defined in
     * Java and exposed to Python. The signature must be a supported
     * type for which coercions can be found for its parameters.
     * <p>
     * When found in the classes that define a built-in type, this
     * annotation results in a method definition, then a descriptor in
     * the dictionary of the type. When found in the class that defines
     * a built-in module, this annotation results in a method definition
     * in the module specification, and a bound method in the dictionary
     * of each module instance created from it.
     * <p>
     * Annotations may appear on the parameters of a method annotated
     * with {@code PythonMethod}. These further describe the method,
     * defining the parameters as positional-only parameters, or
     * providing default values. A method may also be annotated with a
     * documentation string (in the Python sense), by means of the
     * &#064;{@link DocString} annotation.
     * <p>
     * In types that accept multiple implementations, more than one
     * method of the same name may be annotated {@code PythonMethod}.
     * Only one may be the primary definition (see
     * {@link PythonMethod#primary()}, and only in that one are the
     * documentation string and parameter annotations effective. (It
     * need not be the first definition.) These annotations on the
     * primary definition define the signature that Python sees.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(METHOD)
    @interface PythonMethod {

        /**
         * Exposed name of the method if different from the declaration.
         *
         * @return name of the method
         */
        String value() default "";

        /**
         * The element {@code primary=false} is used to indicate that
         * the annotated method is <b>not</b> the primary definition.
         *
         * @return {@code true} (the default) if and only if this is the
         *     primary definition of the method
         */
        boolean primary() default true;

        /**
         * The element {@code positionalOnly=false} is used to indicate
         * that the arguments in a call to the annotated method may be
         * provided by keyword. This provides the call with the
         * semantics of a method defined in Python, where <pre>
         * def g(a, b, c):
         *     print(a, b, c)
         * </pre> may be called as <pre>
         * &gt;&gt;&gt; g(b=2, c=3, a=1)
         * 1 2 3
         * &gt;&gt;&gt; g(**dict(b=2, c=3, a=1))
         * 1 2 3
         * </pre> It is as if we had annotated an imaginary parameter
         * before the first declared parameter (or {@code self}) with
         * &#064;{@link PositionalOnly}.
         * <p>
         * The default {@code positional=true} is the more frequent case
         * for built-in methods, although it is the opposite of the
         * default for methods defined in Python where it would have to
         * be expressed as {@code def g(a, b, c, /)}.
         *
         * @return {@code true} (the default) if and only if this is the
         *     primary definition of the method
         */
        boolean positionalOnly() default true;
    }

    /**
     * Identify a Python static method of a type or module defined in
     * Java and exposed to Python. The signature must be a supported
     * type for which coercions can be found for its parameters.
     * <p>
     * When found in the classes that define a built-in type, this
     * annotation results in a method definition, then a
     * {@code staticmethod} object in the dictionary of the type. When
     * found in the class that defines a built-in module, this
     * annotation results in a method definition in the module
     * specification, and an unbound method in the dictionary of each
     * module instance created from it.
     * <p>
     * Annotations may appear on the parameters of a method annotated
     * with {@code PythonStaticMethod}. These further describe the
     * method, defining the parameters as positional-only parameters, or
     * providing default values. A method may also be annotated with a
     * documentation string (in the Python sense), by means of the
     * &#064;{@link DocString} annotation.
     * <p>
     * Only one method of the given name, in a given class class, may be
     * annotated as a {@code PythonStaticMethod}.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(METHOD)
    @interface PythonStaticMethod {

        /**
         * Exposed name of the function if different from the
         * declaration.
         *
         * @return name of the function
         */
        String value() default "";

        /**
         * The element {@code positionalOnly=false} is used to indicate
         * that the arguments in a call to the annotated method may be
         * provided by keyword. This provides the call with the
         * semantics of a function defined in Python, where <pre>
         * def g(a, b, c):
         *     print(a, b, c)
         * </pre> may be called as <pre>
         * &gt;&gt;&gt; g(b=2, c=3, a=1)
         * 1 2 3
         * &gt;&gt;&gt; g(**dict(b=2, c=3, a=1))
         * 1 2 3
         * </pre> It is as if we had annotated an imaginary parameter
         * before the first declared parameter (or {@code self}) with
         * &#064;{@link PositionalOnly}.
         * <p>
         * The default {@code positional=true} is the more frequent case
         * for built-in function, although it is the opposite of the
         * default for methods defined in Python where it would have to
         * be expressed as {@code def g(a, b, c, /)}.
         *
         * @return {@code true} (the default) if and only if this is the
         *     primary definition of the method
         */
        boolean positionalOnly() default true;
    }

    /**
     * Specify the documentation string ({@code __doc__}) for a method,
     * field, etc. defined in Java and exposed to Python.
     */
    @Documented
    @Retention(RUNTIME)
    @Target({METHOD, FIELD, TYPE})
    @interface DocString { String value(); }

    /**
     * Override the name of an parameter to a method defined in Java, as
     * it will appear to Python (in generated signatures and error
     * messages). It is preferable to use a name in Java that
     * conventional for Python, and is only necessary to annotate one
     * when the conventional name is impossible (e.g. "new").
     */
    @Documented
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @interface Name { String value(); }

    /**
     * Declare that the annotated parameter is the last positional only
     * parameter. This is equivalent to following it with ", /" in a
     * Python signature.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @interface PositionalOnly {}

    /**
     * Declare that the annotated parameter is the first keyword only
     * parameter. This is equivalent to preceding it with "*, " in a
     * Python signature.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @interface KeywordOnly {}

    /**
     * Provide default value for an parameter. This is equivalent to
     * following it with "=" in a Python signature.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @interface Default { String value(); }

    /**
     * Declare that the annotated parameter is the collector for excess
     * positional arguments. This is equivalent to preceding the name
     * with "*" in a Python signature. The type must be {@link PyTuple}.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @interface PositionalCollector {}

    /**
     * Declare that the annotated parameter is the collector for excess
     * keyword arguments. This is equivalent to preceding the name with
     * "**" in a Python signature. The type must be {@link PyDict}.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @interface KeywordCollector {}

    /**
     * Identify a field (of supported type) of a Python object as an
     * exposed member. Get, set and delete operations are provided
     * automatically on a descriptor that will be entered in the
     * dictionary of the type being defined.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(FIELD)
    @interface Member {

        /**
         * Exposed name of the member if different from the field.
         *
         * @return name of the attribute
         */
        String value() default "";

        /** @return true if read-only. */
        boolean readonly() default false;

        /**
         * Member can be deleted and subsequently it is an
         * {@link AttributeError} to get or delete it, until it is set
         * again. By default, when a member implemented by a reference
         * type is deleted, it behaves as if set to {@code None}.
         *
         * @return true if access following delete will raise an error
         */
        boolean optional() default false;
    }

    /**
     * Identify a method as that to be called during a Python call to
     * {@code __getattribute__} naming an exposed attribute.
     * <p>
     * The signature must be ()PyObject.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(METHOD)
    @interface Getter {

        /**
         * Exposed name of the attribute, if different from the Java
         * method name.
         *
         * {@link Deleter} in a single descriptor.
         *
         * @return name of the attribute
         */
        String value() default "";
    }

    /**
     * Identify a method as that to be called during a Python call to
     * {@code __setattr__} naming an exposed attribute.
     * <p>
     * The signature must be (PyObject)void.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(METHOD)
    @interface Setter {

        /**
         * Exposed name of the attribute, if different from the Java
         * method name.
         *
         * This name will relate the {@link Getter}, {@link Setter} and
         * {@link Deleter} in a single descriptor.
         *
         * @return name of the attribute
         */
        String value() default "";
    }

    /**
     * Identify a method as that to be called during a Python call to
     * {@code __delattr__} naming an exposed attribute.
     * <p>
     * The signature must be {@code ()void}.
     */
    @Documented
    @Retention(RUNTIME)
    @Target(METHOD)
    @interface Deleter {

        /**
         * Exposed name of the attribute, if different from the Java
         * method name.
         *
         * This name will relate the {@link Getter}, {@link Setter} and
         * {@link Deleter} in a single descriptor.
         *
         * @return name of the attribute
         */
        String value() default "";
    }

    /**
     * Documentation-only annotation reminding us that <b>the defining
     * class guarantees not to change the contents.</b> If a new value
     * is assigned, it will be a new array. It is therefore safe to take
     * a reference to this array and treat it as frozen (e.g. to expose
     * it as a tuple). There is no enforcement of this contract at run
     * time.
     */
    @Documented
    @Target(FIELD)
    @interface FrozenArray {}

}
