package uk.co.farowl.vsj1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import uk.co.farowl.vsj1.TreePython.operator;
import uk.co.farowl.vsj1.TreePython.unaryop;

/**
 * <code>MixedNumberOperations</code> is the base class of operation
 * operation handler for types that also accept a defined set of
 * <code>Number</code> sub-classes in arithmetic operations.
 */
public abstract class MixedNumberOperations extends Operations {

    /** Shorthand for <code>Number.class</code>. */
    protected static final Class<Number> N = Number.class;

    protected static final MethodType UOP_N = MethodType.methodType(O, N);
    protected static final MethodType BINOP_NN =
            MethodType.methodType(O, N, N);

    /**
     * Constructor accepting the lookup object of the concrete class
     * implementing the operations, permitting look-up of operations within
     * it. Supported operations should be static methods with the names
     * defined in {@link BinOpInfo} etc..
     *
     * @param lookup of the concrete class implementing the operations.
     */
    protected MixedNumberOperations(Lookup lookup) {
        super(lookup);
    }

    /** Test that the actual class of an operand is acceptable. */
    abstract protected boolean acceptable(Class<?> oClass);

    @Override
    public MethodHandle findUnaryOp(unaryop op, Class<?> vClass) {
        String name = UnaryOpInfo.forOp(op).name;

        // Look for a match with the operand class
        MethodType mt = MethodType.methodType(O, vClass);
        MethodHandle mh = findStatic(name, mt);

        if (mh == NOT_IMPLEMENTED && acceptable(vClass)) {
            // Look for a match with (Number)
            mh = findStatic(name, UOP_N);
        }

        return mh;
    }

    @Override
    public MethodHandle findBinOp(Class<?> vClass, operator op,
            Class<?> wClass) {
        String name = BinOpInfo.forOp(op).name;

        // Look for an exact match with the actual types
        MethodType mt = MethodType.methodType(O, vClass, wClass);
        MethodHandle mh = findStatic(name, mt);

        if (mh == NOT_IMPLEMENTED) {
            if (acceptable(wClass)) {
                // Look for a match with (vClass, Number)
                mt = MethodType.methodType(O, vClass, N);
                mh = findStatic(name, mt);
                if (mh == NOT_IMPLEMENTED && acceptable(wClass)) {
                    // Look for a match with (Number, Number)
                    mh = findStatic(name, BINOP_NN);
                }
            } else if (acceptable(vClass)) {
                // Look for a match with (Number, wClass)
                mt = MethodType.methodType(O, N, wClass);
                mh = findStatic(name, mt);
            }
        }

        return mh;
    }
}