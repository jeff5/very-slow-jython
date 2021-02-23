package uk.co.farowl.vsj3.evo1;

// $OBJECT_GENERATOR$ PyLongGenerator

import java.math.BigInteger;
import static java.math.BigInteger.ZERO;
import static java.math.BigInteger.ONE;

/**
 * This class contains static methods implementing operations on the
 * Python {@code long} object, supplementary to those defined in
 * {@link PyLong} and {@link PyLongMethods}. These exist in order to
 * support {@code CallSite} creation.
 * <p>
 * Implementations are not allowed to return {@link Py#NotImplemented}.
 * If a binary operation is not defined here, for the pair of Java
 * classes that type the arguments, the operation is not defined for the
 * pair of their Python types.
 * <p>
 * When matching, we allow a signature here to match if it matches a
 * type assignable in Java to the class of the argument in question.
 * Types providing this compatibility must not be too broad,
 * {@code Object} for example, as this would make it necessary to return
 * {@link Py#NotImplemented}.
 * <p>
 * It follows that if a signature {@code f(A, B)} appears, where
 * {@code A} is an accepted implementation of Python type {@code P}, and
 * {@code B} is an accepted implementation of Python type {@code Q},
 * {@code f(a, b)} must be present, or a signature with compatible
 * types, for every accepted implementation {@code a} of {@code P} and
 * {@code b} of {@code Q}. It is the responsibility of the script
 * generating this class to ensure this condition is satisfied.
 */
class PyLongBinops {

    private PyLongBinops() {}  // no instances

    // $SPECIAL_BINOPS$ --------------------------------------------
}
