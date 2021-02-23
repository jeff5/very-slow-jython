package uk.co.farowl.vsj3.evo1;

import java.math.BigInteger;
import static java.math.BigInteger.ZERO;
import static java.math.BigInteger.ONE;

import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;

// $OBJECT_GENERATOR$ PyLongGenerator

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

    // $SPECIAL_METHODS$ ---------------------------------------------

    // plumbing ------------------------------------------------------

    /**
     * Convert an {@code int} or its sub-class to a Java
     * {@code BigInteger}. Conversion may raise an exception that is
     * propagated to the caller. If the Java type of the {@code int} is
     * declared, generally there is a better option than this method. We
     * only use it for {@code Object} arguments. If the method throws
     * the special exception {@link NoConversion}, the caller must catch
     * it, and will normally return {@link Py#NotImplemented}.
     * 
     * @param v to convert
     * @return converted to {@code BigInteger}
     * @throws NoConversion v is not an {@code int}
     */
    private static BigInteger toBig(Object v) throws NoConversion {
        // Check against supported types, most likely first
        if (v instanceof Integer)
            return BigInteger.valueOf(((Integer) v).longValue());
        else if (v instanceof BigInteger)
            return (BigInteger) v;
        else if (v instanceof Boolean)
            return (Boolean) v ? ONE : ZERO;

        throw PyObjectUtil.NO_CONVERSION;
    }

    /**
     * Reduce a {@code BigInteger} result to {@code Integer} if
     * possible.
     * 
     * @param r to reduce
     * @return equal value
     */
    private static Object toInt(BigInteger r) {
        try {
            return r.intValueExact();
        } catch (ArithmeticException e) {
            return r;
        }
    }
}
