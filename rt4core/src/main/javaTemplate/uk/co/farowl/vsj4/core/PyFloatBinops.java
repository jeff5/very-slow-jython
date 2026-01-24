// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import java.math.BigInteger;
import static uk.co.farowl.vsj4.core.PyLong.convertToDouble;
import static uk.co.farowl.vsj4.core.PyFloat.nonzero;

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
     * classes, the operation is not specialised, and behaviour is
     * defined by a method elsewhere with signature like
     * (PyFloat,Object).
     * 
     * Reflected binary operations will not be defined here because when
     * the classes of the operands are known, the operation is defined
     * already, so we have __sub__ but not __rsub__. Further,
     * definitions like __sub__(Integer, PyFloat) are allowed, even
     * though that would be handled by __rsub__, with swapped operands,
     * but for the definition here.
     */
    private PyFloatBinops() {}  // no instances

    // $SPECIAL_BINOPS$ --------------------------------------------
}
