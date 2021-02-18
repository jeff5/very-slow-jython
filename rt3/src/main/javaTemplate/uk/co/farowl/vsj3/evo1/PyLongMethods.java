package uk.co.farowl.vsj3.evo1;

import java.math.BigInteger;

import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;

// $OBJECT_TEMPLATE$ PyLongTemplate

/**
* This class contains static methods implementing operations on the
* Python {@code int} object, supplementary to those defined in
* {@link PyLong}.
* <p>
* These methods may cause creation of descriptors in the dictionary of
* the type. Those with reserved names in the data model will also fill
* slots in the {@code Operations} object for the type.
* <p>
* Implementations of binary operations defined here will have
* {@code Object} as their second argument, and should return
* {@link Py#NotImplemented} when the type in that position is not
* supported.
*/
class PyLongMethods {

    private PyLongMethods() {}  // no instances

    // $SPECIAL_METHODS$ --------------------------------------------

    // plumbing ------------------------------------------------------

//    /**
//     * Convert an object to a Java long. Conversion to a long may
//     * raise an exception that is propagated to the caller. If the
//     * method throws the special exception {@link NoConversion},
//     * the caller must catch it, and will normally return
//     * {@link Py#NotImplemented}.
//     * 
//     * @param v to convert
//     * @return converted to {@code long}
//     * @throws NoConversion v is not a {@code float} or {@code int}
//     */
//    private static long convert(Object v) throws NoConversion {
//        // Check against supported types, most likely first
//        if (v instanceof Integer)
//            return ((Integer) v).longValue();
//        else if (v instanceof BigInteger)
//            return ((Integer) v).doubleValue();
//        else if (PyLong.TYPE.check(v))
//            // BigInteger, PyLong, Boolean, etc.
//            return BaseLong.asDouble(v);
//
//        throw PyObjectUtil.NO_CONVERSION;
//    }

    /** {@code -Integer.MIN_VALUE} as a {@code BigInteger} */
    private static BigInteger NEG_INT_MIN =
            BigInteger.valueOf(Integer.MIN_VALUE).negate();

    private static final long BIT31 = 0x8000_0000L;
    private static final long HIGHMASK = 0xFFFF_FFFF_0000_0000L;

    /**
     * Given a long value, return an {@code Integer} or a {@code PyLong}
     * according to size.
     *
     * @param r result of some arithmetic as a long
     * @return suitable object for Python
     */
    private static final Object result(long r) {
        // 0b0...0_0rrr_rrrr_rrrr_rrrr -> Positive Integer
        // 0b1...1_1rrr_rrrr_rrrr_rrrr -> Negative Integer
        if (((r + BIT31) & HIGHMASK) == 0L) {
            return Integer.valueOf((int) r);
        } else {
            // Anything else -> PyLong
            return new PyLong(r);
        }
    }
}
