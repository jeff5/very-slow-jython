package uk.co.farowl.vsj2.evo4;

import static java.lang.annotation.ElementType.METHOD;
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
    @Target(METHOD)
    @interface DocString {
        String value();
    }

    @Documented
    @Retention(RUNTIME)
    @Target(PARAMETER)
    @interface Args {}
}
