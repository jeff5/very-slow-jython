package uk.co.farowl.vsj3dybm.evo1;

import java.math.BigInteger;

/**
 * A program that calls a selected method in the benchmark so that we
 * can explore it with the debugger, outside the JMH framework.
 */
public class DebugLongBinary {

    public static void main(String[] args) throws Throwable {

        PyLongBinary test = new PyLongBinary();
        Object r = test.addbig();

        // Second time the ClassValue should be ready for us
        test.bigv = BigInteger.valueOf(Integer.MIN_VALUE + 1);
        test.bigw = BigInteger.valueOf(-1);
        r = test.addbig();

        System.out.println(String.format("Result = %s (%s)", r,
                r.getClass().getSimpleName()));
    }
}
