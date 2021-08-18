package uk.co.farowl.vsj3.evo1;

import static uk.co.farowl.vsj3.evo1.ClassShorthand.O;
import static uk.co.farowl.vsj3.evo1.ClassShorthand.OA;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import uk.co.farowl.vsj3.evo1.Slot.EmptyException;

/**
 * The {@code enum MethodSignature} enumerates the method signatures for
 * which an optimised implementation is possible. There are sub-classes
 * of {@link PyJavaMethod} and {@link PyMethodDescr} corresponding to
 * (at least some of) these values. The {@code enum} is used internally
 * to choose between these sub-classes.
 */
// Compare CPython METH_* constants in methodobject.h
enum MethodSignature {
    NOARGS(O), // No arguments allowed after self (METH_NOARGS)
    O1(O, O),     // One argument allowed after self (METH_O)
    O2(O, O, O), // Two arguments allowed after self
    O3(O, O, O, O), // Three arguments allowed after self
    POSITIONAL(O, OA), // Only positional arguments allowed
    GENERAL(O, OA); // Full generality of ArgParser allowed

    /** The type of method handles matching this method signature. */
    final MethodType methodType;
    /**
     * Handle to throw a {@link Slot.EmptyException}, and having the
     * signature {@code (O,[O])O}.
     */
    final MethodHandle empty;
    /** The second parameter is an object array. */
    final boolean useArray;

    private MethodSignature(Class<?>... ptypes) {
        this.methodType = MethodType.methodType(O, ptypes);
        this.empty = MethodHandles.dropArguments(Util.THROW_EMPTY, 0,
                ptypes);
        this.useArray = ptypes.length >= 2 && ptypes[1] == OA;
    }

    /** Handle utilities, supporting signature creation. */
    private static class Util {

        /** Single re-used instance of {@link Slot.EmptyException} */
        private static final EmptyException EMPTY =
                new EmptyException();

        /**
         * A handle with signature {@code ()O} that throws a single
         * re-used instance of {@code Slot.EmptyException}. We use this
         * in sub-class constructors when given a {@code null} raw
         * method handle, to ensure it is always safe to invoke
         * {@link PyMethodDescr#method}. If the signature is to be
         * believed, {@code EMPTY} returns {@code Object}, although it
         * never actually returns at all.
         */
        static final MethodHandle THROW_EMPTY = MethodHandles
                .throwException(O, Slot.EmptyException.class)
                .bindTo(EMPTY);
    }

    /**
     * Choose a {@code MethodSignature} based on the argument parser.
     * Note that in a {@link PyMethodDescr}, the {@link ArgParser}
     * describes the arguments after {@code self}, even if the
     * implementation is declared {@code static} in Java, so that the
     * {@code self} argument is explicit. .
     *
     * @param ap argument parser describing the method
     * @return
     */
    static MethodSignature fromParser(ArgParser ap) {
        if (ap.hasVarArgs() || ap.hasVarKeywords()) {
            /*
             * Signatures that have collector parameters for excess
             * arguments given by position or keyword are not worth
             * optimising (we assume).
             */
            return GENERAL;
        } else if (ap.posonlyargcount < ap.regargcount) {
            /*
             * Signatures that allow keyword arguments are too difficult
             * to optimise (we assume).
             */
            return GENERAL;
        } else {
            // Arguments may only be given by position
            return positional(ap.regargcount);
        }
    }

    /**
     * Select a (potential) optimisation for a method that accepts
     * arguments only by position. Signatures that allow only positional
     * arguments (optionally with default values for trailing parameters
     * not filled by the argument) may be optimised if the number is not
     * too great.
     *
     * @param n number of arguments
     * @return chosen method signature
     */
    private static MethodSignature positional(int n) {
        switch (n) {
            case 0:
                return NOARGS;
            case 1:
                return O1;
            case 2:
                return O2;
            case 3:
                return O3;
            default:
                return POSITIONAL;
        }
    }
}
