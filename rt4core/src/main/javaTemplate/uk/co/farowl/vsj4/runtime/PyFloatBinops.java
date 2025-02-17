// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.math.BigInteger;
import static uk.co.farowl.vsj4.runtime.PyLong.convertToDouble;
import static uk.co.farowl.vsj4.runtime.PyFloat.nonzero;

// $OBJECT_GENERATOR$ PyFloatGenerator

/**
 * This class contains static methods implementing operations on the
 * Python {@code float} object, supplementary to those defined in
 * {@link PyFloat} and {@link PyFloatMethods}, and used internally by
 * the run-time system, and used internally by the run-time system to
 * create call sites. The class is {@code public} only for technical
 * reasons.
 */
public class PyFloatBinops {
    /*
     * Implementations are not allowed to return NotImplemented. If a
     * binary operation is not defined here, for the pair of Java
     * classes that type the arguments, the operation is not defined for
     * the pair of their Python types.
     * 
     * When matching, we allow a signature here to match if it matches a
     * type assignable in Java to the class of the argument in question.
     * Types providing this compatibility must not be too broad, Object
     * for example, as this would make it necessary to return
     * NotImplemented.
     * 
     * It follows that if a signature f(A, B) appears, where A is an
     * accepted implementation of Python type P, and B is an accepted
     * implementation of Python type Q, f(a, b) must be present, or a
     * signature with compatible types, for every accepted
     * implementation a of P and b of Q. It is the responsibility of the
     * script generating this class to ensure this condition is
     * satisfied.
     */
    private PyFloatBinops() {}  // no instances

    // $SPECIAL_BINOPS$ --------------------------------------------
}
