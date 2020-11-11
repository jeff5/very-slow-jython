package uk.co.farowl.vsj2.evo4;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.ElementType.PARAMETER;
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
    }

}
