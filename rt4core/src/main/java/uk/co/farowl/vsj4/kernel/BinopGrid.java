// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.kernel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.List;

import uk.co.farowl.vsj4.kernel.SpecialMethod.Signature;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * A table of binary operations that may be indexed by a pair of classes
 * (or their {@code Representation} objects). Binary operations, at the
 * same time as appearing as the {@code op} and {@code rop} slots,
 * meaning for example {@link Representation#op_add} and
 * {@link Representation#op_radd}, are optionally given implementations
 * specialised for the Java classes of their arguments. A
 * {@code BinopGrid} describes the
 */
// TODO distribute each row of the BinopGrid to the Representation
// This will (probably) save a look-up on selfClasses.
public class BinopGrid {

    /** The (binary) slot for which this is an operation. */
    final SpecialMethod sm;
    /** the type on which we find this implemented. */
    final BaseType type;
    /**
     * All the implementations, arrayed by argument class. There is a
     * row for each representation class of the type, and a column for
     * each acceptable as {@code self} (representations followed by
     * accepted other classes).
     */
    final MethodHandle[][] mh;

    /**
     * Construct a grid for the given operation and type.
     *
     * @param binop of the binary operation
     * @param type in which the definition is being made
     */
    BinopGrid(SpecialMethod binop, BaseType type) {
        assert binop.signature == Signature.BINARY;
        this.sm = binop;
        this.type = type;
        final int N = type.representations().size();
        final int M = type.selfClasses().size();
        this.mh = new MethodHandle[N][M];
    }

    /**
     * Post the definition for the {@link #sm} applicable to the classes
     * in the method type. The handle must be the "raw" handle to the
     * class-specific implementation, while the posted value (later
     * returned by {@link #get(Class, Class)} will have the signature
     * {@link Signature#BINARY}.
     *
     * @param mh handle to post
     */
    void add(MethodHandle mh)
            throws WrongMethodTypeException, InterpreterError {
        MethodType mt = mh.type();
        // Cast fails if the signature is incorrect for the slot
        mh = mh.asType(sm.getType());
        // Find cell based on argument types
        int i = type.selfClasses().indexOf(mt.parameterType(0));
        int j = type.selfClasses().indexOf(mt.parameterType(1));
        if (i >= 0 && j >= 0) {
            this.mh[i][j] = mh;
        } else {
            /*
             * The arguments to m are not (respectively) an accepted
             * class and an operand class for the type. Type spec and
             * the declared binary ops disagree?
             */
            throw new InterpreterError(
                    "unexpected signature of %s.%s: %s", type.getName(),
                    sm.methodName, mt);
        }
    }

    /**
     * Check that every valid combination of classes has been added
     * (therefore leads to a non-null method handle).
     *
     * @throws InterpreterError if a {@code null} was found
     */
    void checkFilled() throws InterpreterError {
        final int N = mh.length;  // > 0
        final int M = mh[0].length;
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < M; j++) {
                if (mh[i][j] == null) {
                    /*
                     * There's a gap in the table. Type spec and the
                     * declared binary ops disagree?
                     */
                    List<Class<?>> s = type.selfClasses();
                    throw new InterpreterError(
                            "binary op not defined: %s(%s, %s)",
                            sm.methodName, s.get(i).getSimpleName(),
                            s.get(j).getSimpleName());
                }
            }
        }
    }

    /**
     * Get the method handle of an implementation
     * {@code Object op(V v, W w)} specialised to the given classes. If
     * {@code V} is a representation of this type, and {@code W} is an
     * accepted class, the return will be a handle on an implementation
     * of {@code op} matching those classes. If no implementation is
     * available for those classes (which means they are not
     * representation and accepted types for the Python type) an empty
     * slot handle is returned.
     *
     * @param vClass class of first argument to method
     * @param wClass class of second argument to method
     * @return the special-to-class binary operation
     */
    public MethodHandle get(Class<?> vClass, Class<?> wClass) {
        // Find cell based on argument types
        int i = type.selfClasses().indexOf(vClass);
        int j = type.selfClasses().indexOf(wClass);
        if (i >= 0 && j >= 0) {
            return mh[i][j];
        } else {
            return sm.getEmpty();
        }
    }

    /**
     * Convenience method allowing look-up equivalent to
     * {@link #get(Class, Class)}, but using the {@code Representation}
     * objects as a proxy for the actual classes.
     *
     * @param vRep of first argument to method
     * @param wRep of second argument to method
     * @return the special-to-representation binary operation
     */
    public MethodHandle get(Representation vRep, Representation wRep) {
        return get(vRep.javaClass(), wRep.javaClass());
    }
}
