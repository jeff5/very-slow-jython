package uk.co.farowl.vsj3.evo1;

import java.util.HashSet;
import java.util.Set;

import uk.co.farowl.vsj3.evo1.PyUnicode;

/**
 * A program that calls a selected method in the benchmark to explore in
 * isolation the way in which it gets optimised by the JVM. (See
 * {@code idiom.gradle} task {@code exceptionSupplierLoop}.
 */
public class BMExceptionSupplierMainLoop {

    public static void main(String[] args) throws Throwable {
        BMExceptionSupplier test =
                new BMExceptionSupplier();
        Set<String> sum = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            test.vs = "name" + i;
            test.vu = PyUnicode.fromJavaString("name" + i);
            test.vi = i;
            sum.addAll(test.checkAll());
        }
        // Dummy output prevents optimisation to nothing
        System.out.println(String.format("Unique = %10d", sum.size()));
    }
}
