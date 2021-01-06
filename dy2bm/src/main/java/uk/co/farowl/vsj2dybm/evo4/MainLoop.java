package uk.co.farowl.vsj2dybm.evo4;

import uk.co.farowl.vsj2.evo4.Py;
import uk.co.farowl.vsj2.evo4.PyObject;
import uk.co.farowl.vsj2.evo4.Number;

/**
 * A program that calls a selected method in the benchmark to explore in
 * isolation the way in which it gets optimised by the JVM. (See
 * {@code dy2bm.gradle} task {@code mainloop}.
 */
public class MainLoop {

    public static void main(String[] args) throws Throwable {
        PyFloatBinary test = new PyFloatBinary();
        double sum = 0.0;
        for (int i = 0; i < 10_000; i++) {
            test.fvo = Py.val(i * 2.001);
            test.fwo = Py.val(i * 1.001);
            PyObject r = test.quartic();
            //System.out.println(String.format("\nPartial = %s\n", r));
            sum += Number.toFloat(r).doubleValue();
        }
        System.out.println(String.format("Sum = %14.3e", sum));
    }
}
