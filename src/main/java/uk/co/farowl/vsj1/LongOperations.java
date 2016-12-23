package uk.co.farowl.vsj1;

import static java.lang.invoke.MethodHandles.lookup;

import java.math.BigInteger;

/**
 * Operations operation handler applicable to Java integers up to
 * <code>Long</code>, so as to make each a Python <code>int</code>.
 * <code>Number</code> types <code>Byte</code>, <code>Short</code> and
 * <code>Integer</code> are also acceptable as arguments to the arithmetic
 * operations.
 */
@SuppressWarnings(value = {"unused"})
public class LongOperations extends MixedNumberOperations {

    private static final long BIT63 = 0x8000_0000_0000_0000L;
    private static final BigInteger BIG_2_63 =
            BigInteger.valueOf(BIT63).negate();
    private static BigInteger BIG_2_64 = BIG_2_63.shiftLeft(1);

    // @formatter:off

    public LongOperations() { super(lookup()); }

    private static Object add(Long v, Long w)
        { return long_add(v, w); }
    private static Object sub(Long v, Long w)
        { return long_sub(v, w); }
    private static Object mul(Long v, Long w)
        { return long_mul(v, w); }

    private static Object add(Long v, Number w)
        { return long_add(v, w.longValue()); }
    private static Object sub(Long v, Number w)
        { return long_sub(v, w.longValue()); }
    private static Object mul(Long v, Number w)
        { return long_mul(v, w.longValue()); }

    private static Object add(Number v, Long w)
        { return long_add(v.longValue(), w); }
    private static Object sub(Number v, Long w)
        { return long_sub(v.longValue(), w); }
    private static Object mul(Number v, Long w)
        { return long_mul(v.longValue(), w); }

    private static Object add(Number v, Number w)
        { return long_add(v.longValue(), w.longValue()); }
    private static Object sub(Number v, Number w)
        { return long_sub(v.longValue(), w.longValue()); }
    private static Object mul(Number v, Number w)
        { return long_mul(v.longValue(), w.longValue()); }

    private static Object div(Number v, Number w)
        { return v.doubleValue() / w.doubleValue(); }

    private static Object neg(Long v) {
        long lv = v;
        return lv == Long.MIN_VALUE ? BIG_2_63 : Long.valueOf(-lv);
    }

    private static Object pos(Long v) { return v; }
    private static Object pos(Number v) { return v; }

    // @formatter:on

    @Override
    protected boolean acceptable(Class<?> oClass) {
        return oClass == Byte.class || oClass == Short.class
                || oClass == Integer.class || oClass == Long.class;
    }

    private static Object long_add(long v, long w) {
        // Compute naive result
        long r = v + w;
        // Detect potential carry into bit 64 by examining sign bits
        if (((v ^ w) & BIT63) != 0L) {
            // Signs were opposite: result must be in range of long
            return r;
        } else if (((v ^ r) & BIT63) == 0L) {
            // Sign of result is same as sign of (both) operands
            return r;
        } else if ((r & BIT63) != 0L) {
            // r is incorrect (negative) by 2**64
            return BigInteger.valueOf(r).add(BIG_2_64);
        } else {
            // r is incorrect (positive) by 2**64
            return BigInteger.valueOf(r).subtract(BIG_2_64);
        }
    }

    private static Object long_sub(long v, long w) {
        // Compute naive result
        long r = v - w;
        // Detect potential carry into bit 64 by examining sign bits
        if (((v ^ w) & BIT63) == 0L) {
            // Signs were the same: result must be in range of long
            return r;
        } else if (((v ^ r) & BIT63) == 0L) {
            // Sign of result is same as first operand: lr is correct
            return r;
        } else if ((r & BIT63) != 0L) {
            // r is incorrect (negative) by 2**64
            return BigInteger.valueOf(r).add(BIG_2_64);
        } else {
            // r is incorrect (positive) by 2**64
            return BigInteger.valueOf(r).subtract(BIG_2_64);
        }
    }

    private static Object long_mul(long v, long w) {
        if (v == 0L || w == 0L) {
            return 0;
        } else {
            // |v| < 2**(64-zv) (even if v=Long.MIN_VALUE)
            int zv = Long.numberOfLeadingZeros(Math.abs(v) - 1L);
            int zw = Long.numberOfLeadingZeros(Math.abs(w) - 1L);
            if (zv + zw >= 65) {
                // |v||w| < 2**(128-(zv+zw)) <= 2**63 -> Long
                return v * w;
            } else {
                return BigInteger.valueOf(v)
                        .multiply(BigInteger.valueOf(w));
            }
        }
    }
}