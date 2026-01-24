// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

// $OBJECT_GENERATOR$ PyLongGenerator

import java.math.BigInteger;
import static java.math.BigInteger.ZERO;
import static java.math.BigInteger.ONE;
import static uk.co.farowl.vsj4.core.PyLongMethods.toInt;
import static uk.co.farowl.vsj4.core.PyLongMethods.divide;
import static uk.co.farowl.vsj4.core.PyLongMethods.modulo;
import static uk.co.farowl.vsj4.core.PyLongMethods.divmod;
import static uk.co.farowl.vsj4.core.PyLongMethods.trueDivide;

/**
 * This class contains static methods implementing operations on the
 * Python {@code long} object, supplementary to those defined in
 * {@link PyLong} and {@link PyLongMethods}, and used internally by the
 * run-time system to create call sites. The class is {@code public}
 * only for technical reasons.
 */
public class PyLongBinops {
    /*
     * Implementations are not allowed to return NotImplemented. If a
     * binary operation is not defined here, for the pair of Java
     * classes, the operation is not specialised, and behaviour is
     * defined by a method elsewhere with signature like
     * (PyLong,Object).
     * 
     * Reflected binary operations will not be defined here because when
     * the classes of the operands are known, the operation is defined
     * already, so we have __sub__ but not __rsub__. Further,
     * definitions like __sub__(Boolean, PyLong) are allowed, even
     * though that would be handled by __rsub__, with swapped operands,
     * but for the definition here.
     */
    private PyLongBinops() {}  // no instances

    // $SPECIAL_BINOPS$ --------------------------------------------
}
