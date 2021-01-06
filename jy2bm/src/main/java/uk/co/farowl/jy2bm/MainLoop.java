package uk.co.farowl.jy2bm;

import org.python.core.Py;
import org.python.core.PyObject;

/**
 * A program that calls a selected method in the benchmark to explore in
 * isolation the way in which it gets optimised by the JVM. (See
 * {@code jy2bm.gradle} task {@code mainloop}.
 */
public class MainLoop {

    public static void main(String[] args) {
        PyFloatBinary test = new PyFloatBinary();
        double sum = 0.0;
        for (int i = 0; i < 10_000; i++) {
            test.fvo = Py.newFloat(i * 2.001);
            test.fwo = Py.newFloat(i * 1.001);
            PyObject r = test.quartic();
            //System.out.println(String.format("\nPartial = %s\n", r));
            sum += r.asDouble();
        }
        System.out.println(String.format("Sum = %14.3e", sum));
    }
}
