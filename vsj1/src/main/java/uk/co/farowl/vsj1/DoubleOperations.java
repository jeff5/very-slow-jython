package uk.co.farowl.vsj1;

import static java.lang.invoke.MethodHandles.lookup;

import java.math.BigInteger;

/**
 * Class defining the operations for a Java <code>Double</code>, so as to
 * make it a Python <code>float</code>. <code>Number</code> types
 * <code>Byte</code>, <code>Short</code>, <code>Integer</code>,
 * <code>Long</code>, <code>BigInteger</code> and <code>Float</code> are
 * also acceptable as arguments to the arithmetic operations.
 */
@SuppressWarnings(value = {"unused"})
public class DoubleOperations extends MixedNumberOperations {

    // @formatter:off
    public DoubleOperations() { super(lookup()); }

    private static Object add(Double v, Double w)  { return v+w; }
    private static Object sub(Double v, Double w)  { return v-w; }
    private static Object mul(Double v, Double w)  { return v*w; }
    private static Object div(Double v, Double w)  { return v/w; }

    private static Object neg(Double v) { return -v; }
    private static Object pos(Double v) { return v; }

    private static Object add(Double v, Number w)
        { return v + w.doubleValue(); }
    private static Object sub(Double v, Number w)
        { return v - w.doubleValue(); }
    private static Object mul(Double v, Number w)
        { return v * w.doubleValue(); }
    private static Object div(Double v, Number w)
        { return v / w.doubleValue(); }

    private static Object add(Number v, Double w)
        { return v.doubleValue() + w; }
    private static Object sub(Number v, Double w)
        { return v.doubleValue() - w; }
    private static Object mul(Number v, Double w)
        { return v.doubleValue() * w; }
    private static Object div(Number v, Double w)
        { return v.doubleValue() / w; }

    // Accept any Number types by widening to double
    private static Object add(Number v, Number w)
        { return v.doubleValue() + w.doubleValue(); }
    private static Object sub(Number v, Number w)
        { return v.doubleValue() - w.doubleValue(); }
    private static Object mul(Number v, Number w)
        { return v.doubleValue() * w.doubleValue(); }
    private static Object div(Number v, Number w)
        { return v.doubleValue() / w.doubleValue(); }

    private static Object neg(Number v) { return -v.doubleValue(); }
    private static Object pos(Number v) { return v; }

    // @formatter:on

    @Override
    protected boolean acceptable(Class<?> oClass) {
        return oClass == Byte.class || oClass == Short.class
                || oClass == Integer.class || oClass == Long.class
                || oClass == BigInteger.class || oClass == Float.class;
    }
}