package uk.co.farowl.vsj2.evo4;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

interface Exposed {

    @Documented
    @Retention(RUNTIME)
    @Target(METHOD)
    @interface Function {}

    @Documented
    @Retention(RUNTIME)
    @Target({METHOD, FIELD, TYPE})
    @interface DocString { String value(); }

    @Documented
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @interface Args {}

    @Documented
    @Retention(RUNTIME)
    @Target(FIELD)
    @interface Member {

        /** Exposed name of the member if different from the field. */
        String value() default "";

        /** Member is read-only. */
        boolean readonly() default false;

        /** Member can be deleted and not report as None. */
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
         */
        String value() default "";
    }

}
