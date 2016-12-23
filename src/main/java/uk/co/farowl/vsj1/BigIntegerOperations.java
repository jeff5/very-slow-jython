package uk.co.farowl.vsj1;

import static java.lang.invoke.MethodHandles.lookup;

import java.math.BigInteger;

/**
 * Class defining the operations for a Java <code>BigInteger</code>, so as
 * to make it a Python <code>int</code>. <code>Number</code> types
 * <code>Byte</code>, <code>Short</code>, <code>Integer</code> and
 * <code>Long</code> are also acceptable as arguments to the arithmetic
 * operations.
 */
@SuppressWarnings(value = {"unused"})
public class BigIntegerOperations extends MixedNumberOperations {

    // @formatter:off
    public BigIntegerOperations() { super(lookup()); }

    private static Object add(BigInteger v, BigInteger w)
        { return v.add(w); }
    private static Object sub(BigInteger v, BigInteger w)
        { return v.subtract(w); }
    private static Object mul(BigInteger v, BigInteger w)
        { return v.multiply(w); }
    // Delegate to div(Number, Number): same for all types
    private static Object div(Number v, Number w)
        { return v.doubleValue() / w.doubleValue(); }

    private static Object neg(BigInteger v) { return v.negate(); }
    // Delegate to pos(Number) as just returning self
    private static Object pos(Number v) { return v; }

    // Accept any integer as w by widening to BigInteger
    private static Object add(BigInteger v, Number w)
        { return v.add(BigInteger.valueOf(w.longValue())); }
    private static Object sub(BigInteger v, Number w)
        { return v.subtract(BigInteger.valueOf(w.longValue())); }
    private static Object mul(BigInteger v, Number w)
        { return v.multiply(BigInteger.valueOf(w.longValue())); }

    // Accept any integer as v by widening to BigInteger
    private static Object add(Number v, BigInteger w)
        { return BigInteger.valueOf(v.longValue()).add(w); }
    private static Object sub(Number v, BigInteger w)
        { return BigInteger.valueOf(v.longValue()).subtract(w); }
    private static Object mul(Number v, BigInteger w)
        { return BigInteger.valueOf(v.longValue()).multiply(w); }

    // Accept any integers as v, w by widening to BigInteger
    private static Object add(Number v, Number w) {
        return BigInteger.valueOf(v.longValue())
                .add(BigInteger.valueOf(w.longValue()));
    }
    private static Object sub(Number v, Number w) {
        return BigInteger.valueOf(v.longValue())
                .subtract(BigInteger.valueOf(w.longValue()));
    }
    private static Object mul(Number v, Number w) {
        return BigInteger.valueOf(v.longValue())
                .multiply(BigInteger.valueOf(w.longValue()));
    }

    private static Object neg(Number v) {
        return BigInteger.valueOf(v.longValue()).negate();
    }
    // @formatter:on

    @Override
    protected boolean acceptable(Class<?> oClass) {
        return oClass == Byte.class || oClass == Short.class
                || oClass == Integer.class || oClass == Long.class;
    }
}