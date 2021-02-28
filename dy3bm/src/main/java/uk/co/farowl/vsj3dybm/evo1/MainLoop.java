package uk.co.farowl.vsj3dybm.evo1;

import uk.co.farowl.vsj3.evo1.Py;
import uk.co.farowl.vsj3.evo1.PyFloat;

/**
 * A program that calls a selected method in the benchmark to explore in
 * isolation the way in which it gets optimised by the JVM. (See
 * {@code rt3bm.gradle} task {@code mainloop}.
 */
public class MainLoop {

    public static void main(String[] args) throws Throwable {
        PyFloatBinary test = new PyFloatBinary();
        double sum = 0.0;
        for (int i = 0; i < 10_000; i++) {
            test.v = i * 2.001;
            test.w = i * 1.001;
            // Object r = test.quartic();

            Object r = test.add_float_int_java();

            // System.out.println(String.format("\nPartial = %s\n", r));
            sum += PyFloat.doubleValue(r);
        }
        System.out.println(String.format("Sum = %14.3e", sum));
    }
}
