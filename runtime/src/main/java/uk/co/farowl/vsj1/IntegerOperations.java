package uk.co.farowl.vsj1;

import static java.lang.invoke.MethodHandles.lookup;

/**
 * Class defining the operations for a Java <code>Integer</code>, so as to
 * make it a Python <code>int</code>. <code>Number</code> types
 * <code>Byte</code> and <code>Short</code> are also acceptable as
 * arguments to the arithmetic operations.
 */
@SuppressWarnings(value = {"unused"})
public class IntegerOperations extends MixedNumberOperations {

    // @formatter:off
    public IntegerOperations() { super(lookup()); }

    private static Object add(Integer v, Integer w)
        { return result( (long)v + (long)w); }
    private static Object sub(Integer v, Integer w)
        { return result( (long)v - (long)w); }
    private static Object mul(Integer v, Integer w)
        { return result( (long)v * (long)w); }
    private static Object div(Integer v, Integer w)
        { return v.doubleValue() / w.doubleValue(); }

    private static Object neg(Integer v) { return result(-(long)v); }
    private static Object pos(Integer v) { return v; }

    private static Object add(Integer v, Number w)
        { return result( v + w.longValue()); }
    private static Object sub(Integer v, Number w)
        { return result( v - w.longValue()); }
    private static Object mul(Integer v, Number w)
        { return result( v * w.longValue()); }
    private static Object div(Integer v, Number w)
        { return v.doubleValue() / w.doubleValue(); }

    private static Object add(Number v, Integer w)
        { return result( v.longValue() + w); }
    private static Object sub(Number v, Integer w)
        { return result( v.longValue() - w); }
    private static Object mul(Number v, Integer w)
        { return result( v.longValue() * w); }
    private static Object div(Number v, Integer w)
        { return v.doubleValue() / w.doubleValue(); }

    private static Object add(Number v, Number w)
        { return v.intValue() + w.intValue(); }
    private static Object sub(Number v, Number w)
        { return v.intValue() - w.intValue(); }
    private static Object mul(Number v, Number w)
        { return v.intValue() * w.intValue(); }
    private static Object div(Number v, Number w)
        { return v.doubleValue() / w.doubleValue(); }

    private static Object neg(Number v) { return -v.intValue(); }
    private static Object pos(Number v) { return v; }

    // @formatter:on

    @Override
    protected boolean acceptable(Class<?> oClass) {
        return oClass == Byte.class || oClass == Short.class;
    }

    private static final long BIT31 = 0x8000_0000L;
    private static final long HIGHMASK = 0xFFFF_FFFF_0000_0000L;

    private static final Object result(long r) {
        // 0b0...0_0rrr_rrrr_rrrr_rrrr -> Positive Integer
        // 0b1...1_1rrr_rrrr_rrrr_rrrr -> Negative Integer
        // Anything else -> Long
        if (((r + BIT31) & HIGHMASK) == 0L) {
            return Integer.valueOf((int)r);
        } else {
            return Long.valueOf(r);
        }
    }
}