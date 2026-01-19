// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import static java.lang.invoke.MethodHandles.*;
import static uk.co.farowl.vsj4.support.JavaClassShorthand.C;
import static uk.co.farowl.vsj4.support.JavaClassShorthand.O;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

import uk.co.farowl.vsj4.internal.EmptyException;
import uk.co.farowl.vsj4.kernel.BaseType;
import uk.co.farowl.vsj4.kernel.BinopGrid;
import uk.co.farowl.vsj4.kernel.Representation;
import uk.co.farowl.vsj4.kernel.SpecialMethod;
import uk.co.farowl.vsj4.kernel.TypeRegistry;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.types.TypeFlag;
import uk.co.farowl.vsj4.types.WithClass;

/**
 * {@link PyRT} provides run-time support for Python that has been
 * compiled to Java byte code, primarily for {@code invokedynamic} call
 * sites. In some ways, this supersedes methods in {@link Abstract} that
 * support the interpretation of Python byte code. Like those methods,
 * these call sites wrap a call on a particular special method (like
 * {@code __neg__} and {@code __add__}). Call sites in Java code should
 * behave exactly as their counterparts in {@link Abstract}.
 * <p>
 * The use of {@code invokedynamic} call sites has the potential to
 * unlock dynamic optimisation through specialisation to the actual Java
 * classes encountered in a given place in the compiled code. It does
 * not benefit widely used code that receives calls with many different
 * object types (termed <i>megamutable</i>).
 * <p>
 * For this reason, not all the methods in {@link Abstract}, nor all the
 * special methods, need corresponding call sites. Those like
 * {@link Abstract#repr(Object)} or {@link Abstract#size(Object)},
 * wrapping {@code __repr__} or {@code __len__}, exist only to support
 * built-in methods ({@code repr()} and {@code len()}). A call site to
 * replace one of those would quickly become megamutable.
 * <p>
 * Specialisation takes place on Java class rather than Python type.
 * This means that the call site will read and embed (under a
 * class-guard) the method handle it finds via the representation class
 * of objects presented as the {@code self} argument. This has several
 * implications:
 * <ol>
 * <li>Primitive operations on immutable types with representations that
 * are unique to them, largely types defined in Java, dispatch quickly
 * to their exact target implementation.</li>
 * <li>Operations on Python types that share an implementation class,
 * largely replaceable types defined in Python, must find their target
 * in a second step via the Python type of {@code self}.</li>
 * </ol>
 * In the second case, the handle found (and embedded for the class) is
 * a "bounce" handle that will dynamically invoke the corresponding
 * special method on {@link WithClass#getType() self.getType()}.
 */
public class PyRT {

    /** A method implementing a unary op has this type. */
    static final MethodType UOP = SpecialMethod.Signature.UNARY.type;
    /** A method implementing a binary op has this type. */
    static final MethodType BINOP = SpecialMethod.Signature.BINARY.type;
    /** Handle testing an object has a particular class. */
    static final MethodHandle CLASS_GUARD;
    /** Handle testing two object have a particular classes. */
    static final MethodHandle CLASS2_GUARD;
    /** Handle testing an object is not {@code NotImplemented}. */
    static final MethodHandle IMPLEMENTED_GUARD;
    /** Lookup with the rights of the run-time system. */
    private static final Lookup lookup;

    static {
        lookup = MethodHandles.lookup();
        try {
            CLASS_GUARD = lookup.findStatic(PyRT.class, "classEquals",
                    MethodType.methodType(boolean.class, C, O));
            CLASS2_GUARD = lookup.findStatic(PyRT.class, "classEquals",
                    MethodType.methodType(boolean.class, C, C, O, O));
            IMPLEMENTED_GUARD =
                    lookup.findStatic(PyRT.class, "isImplemented",
                            MethodType.methodType(boolean.class, O));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw staticInitError(e, PyRT.class);
        }
    }

    /**
     * Single registry from which we get {@code Representations}. A side
     * effect of this shorthand is to ensure that the {@link TypeSystem}
     * is statically initialised before we use any API method.
     */
    private static final TypeRegistry registry = TypeSystem.registry;

    /**
     * Bootstrap method mapping a name to a corresponding call site type
     * and returning an instance of that call site.
     *
     * @param lookup rights of the caller
     * @param name encoding the operation
     * @param type signature of the operation
     * @return call site for the operation
     * @throws NoSuchMethodException if name cannot be mapped
     */
    public static CallSite bootstrap(Lookup lookup, String name,
            MethodType type) throws NoSuchMethodException {
        CallSite site = switch (name) {
            // TODO Maybe use AST node names/enum for call sites?
            case "negative" -> new UnaryOpCallSite(
                    SpecialMethod.op_neg);
            case "positive" -> new UnaryOpCallSite(
                    SpecialMethod.op_pos);
            case "absolute" -> new UnaryOpCallSite(
                    SpecialMethod.op_abs);
            case "add" -> new BinaryOpCallSite(SpecialMethod.op_add);
            case "multiply" -> new BinaryOpCallSite(
                    SpecialMethod.op_mul);
            case "subtract" -> new BinaryOpCallSite(
                    SpecialMethod.op_sub);
            default -> null;
        };

        if (site == null) { throw new NoSuchMethodException(name); }
        return site;
    }

    /**
     * A call site for unary Python operations. The call site is
     * constructed from a slot such as {@link SpecialMethod#op_neg}. It
     * obtains a method handle from the {@link Representation} of each
     * distinct class observed as the argument, and maintains a cache of
     * method handles guarded on those classes.
     */
    static class UnaryOpCallSite extends MutableCallSite {

        /** Limit on {@link #chainLength}. */
        public static final int MAX_CHAIN = 4;

        /**
         * Handle to {@link #fallback(Object)}, which is the behaviour
         * for this call site when the class of {@code self} does not
         * match any of the embedded guards.
         */
        private static final MethodHandle fallbackMH;
        static {
            try {
                fallbackMH = lookup.findVirtual(UnaryOpCallSite.class,
                        "fallback", UOP);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw staticInitError(e, UnaryOpCallSite.class);
            }
        }

        /** The {@link SpecialMethod} to be applied by the site. */
        final SpecialMethod op;

        /**
         * The number of times this site has used
         * {@link #fallback(Object) fallback}, used to observe internal
         * working and potentially for de-optimisation decisions.
         */
        int fallbackCount;

        /**
         * The number of guarded invocations cached in the target of
         * this site by {@link #fallback(Object) fallback}, used to
         * observe internal working and potentially for de-optimisation
         * decisions.
         */
        int chainLength;

        /**
         * Construct a call site with the specific unary operation.
         *
         * @param op unary operation to execute
         */
        public UnaryOpCallSite(SpecialMethod op) {
            super(UOP);
            this.op = op;
            setTarget(fallbackMH.bindTo(this));
        }

        @Override
        public String toString() {
            return String.format(
                    "UnaryOpCallSite[%s fallbacks=%s chain=%s]",
                    op.name(), fallbackCount, chainLength);
        }

        /**
         * Compute the result of the call for this particular argument,
         * and update the site to do this efficiently for the same class
         * in the future, if it is safe and effective to do so. We call
         * this when the class of {@code self} did not match any of the
         * embedded guards.
         *
         * @param self operand
         * @return {@code self.op()}
         * @throws Throwable from the implementation of {@link #op}
         */
        @SuppressWarnings("unused")
        private Object fallback(Object self) throws Throwable {
            fallbackCount += 1;

            Class<?> selfClass = self.getClass();
            Representation rep = registry.get(selfClass);

            // A handle on the implementation of op in rep
            MethodHandle mh = op.handle(rep), targetMH, guardMH;

            /*
             * If the operation throws, it throws here and we do not
             * bind a new target. If it's a value-dependent one-off,
             * we'll get another go.
             */
            Object result;
            try {
                result = mh.invokeExact(self);
            } catch (EmptyException e) {
                // Method not defined. Raise a Python TypeError.
                result = op.errorHandle().invokeExact(self);
            }

            /**
             * If the type has chosen a generic handle, it is because
             * the meaning of the special method may change.
             */
            if (mh != op.generic && chainLength < MAX_CHAIN) {
                // MH for guarded invocation (becomes new target)
                guardMH = CLASS_GUARD.bindTo(selfClass);
                targetMH = guardWithTest(guardMH, mh, getTarget());
                setTarget(targetMH);
                chainLength += 1;
            }
            return result;
        }
    }

    /**
     * A call site for binary Python operations. The call site is
     * constructed from a slot such as {@link SpecialMethod#op_sub} and
     * its reflection ({@link SpecialMethod#op_sub} in the example).
     *
     * The call site implements the full semantics of the related
     * abstract operation, that is it takes care of selecting and
     * invoking the reflected operation when Python requires it.
     *
     * If either the left or right type defines type-specific binary
     * operations, it will look first for a match with one of those
     * definitions.
     *
     * If that does not succeed, it will use handles in the two
     * {@link Representation} objects
     *
     * It constructs a method handle applicable to each distinct pair of
     * classes observed as the arguments, and maintains a cache of
     * method handles guarded on those classes.
     */
    static class BinaryOpCallSite extends MutableCallSite {

        /** Handle that marks an empty binary operation slot. */
        private static final MethodHandle BINARY_EMPTY =
                SpecialMethod.Signature.BINARY.empty;

        /** Limit on {@link #chainLength}. */
        public static final int MAX_CHAIN = 6;

        /**
         * Handle to {@link #fallback(Object, Object)}, which is the
         * behaviour for this call site when the class of {@code self}
         * does not match any of the embedded guards.
         */
        private static final MethodHandle fallbackMH;

        static {
            try {
                fallbackMH = lookup.findVirtual(BinaryOpCallSite.class,
                        "fallback", BINOP);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw staticInitError(e, BinaryOpCallSite.class);
            }
        }

        /** The {@link SpecialMethod} to be applied by the site. */
        final SpecialMethod op;
        /** The reflected {@link SpecialMethod} to be applied. */
        final SpecialMethod rop;

        /**
         * The number of times this site has used
         * {@link #fallback(Object, Object) fallback}, used to observe
         * internal working and potentially for de-optimisation
         * decisions.
         */
        int fallbackCount;

        /**
         * The number of guarded invocations cached in the target of
         * this site by {@link #fallback(Object, Object) fallback}, used
         * to observe internal working and potentially for
         * de-optimisation decisions.
         */
        int chainLength;

        /**
         * Construct a call site with the given binary operation.
         *
         * @param op a binary operation
         */
        public BinaryOpCallSite(SpecialMethod op) {
            super(BINOP);
            this.op = op;
            this.rop = op.reflected;
            setTarget(fallbackMH.bindTo(this));
        }

        /**
         * Compute the result of the call for this particular pair of
         * arguments, and update the site to do this efficiently for the
         * same classes in the future, if it is safe and effective to do
         * so. We call this when the class of {@code v} did not match
         * any of the embedded guards.
         *
         * @param v left operand
         * @param w right operand
         * @return {@code op(v, w)}
         * @throws Throwable on errors or if not implemented
         */
        @SuppressWarnings("unused")
        private Object fallback(Object v, Object w) throws Throwable {

            fallbackCount += 1;

            Class<?> vClass = v.getClass();
            Representation vRep = registry.get(vClass);
            BaseType vType = vRep.pythonType(v);
            MethodHandle vMH;   // e.g. type(v).__sub__

            Class<?> wClass = w.getClass();
            Representation wRep = registry.get(wClass);
            BaseType wType = wRep.pythonType(w);
            MethodHandle wRH;   // e.g. type(w).__rsub__

            MethodHandle mh, targetMH, guardMH;
            Object result;

            // A Python binary op consults both types in the pattern:
            // if (wType == vType) {
            // ... try v.op only
            // } else {
            // if (wType.isSubTypeOf(vType)) {
            // ... try w.rop then v.op
            // } else {
            // ... try v.op then w.rop
            // }}
            /*
             * We create a method handle, to guard with a pair of
             * classes, that explores only the alternatives that might
             * succeed. This choice depends on whether each class is a
             * shared representation.
             */

            if (vType.hasFeature(TypeFlag.REPLACEABLE)) {
                // class(v) does not fix type(v).
                if (wType.hasFeature(TypeFlag.REPLACEABLE)) {
                    // class(w) does not fix type(w).
                    try {
                        /*
                         * It is complex to create a "double bounce"
                         * handle so we compute the answer but do not
                         * cache the method.
                         */
                        Object r = dynamicResult(vType, v, wType, w);
                        if (r != Py.NotImplemented) { return r; }
                    } catch (EmptyException e) {}
                    // Empty or r=NotImplemented
                    throw op.operandError(v, w);

                } else {
                    // class(w) fixes type(w).
                    vMH = op.handle(vRep);      // = op.bounce
                    if ((wRH = rop.handle(wType)) == rop.empty) { // XXX
                        // We need only consider vMH.
                        mh = vMH;
                    } else {
                        /*
                         * No type represented by class(w) is a sub-type
                         * of type(v), or type(w) would have been
                         * replaceable too. Always try v.op(w) then
                         * w.rop(v)
                         */
                        mh = firstImplementer(vMH, wRH);
                    }
                }

            } else if (wType.hasFeature(TypeFlag.REPLACEABLE)) {
                // class(v) fixes type(v).
                // class(w) does not fix type(w).
                wRH = rop.handle(wRep);     // = op.bounce
                if ((vMH = op.handle(vRep)) == op.empty) { // XXX
                    // We need only consider wRH
                    mh = wRH;
                } else {
                    /*
                     * The types (all of them or none) represented by
                     * class(w) may be proper sub-types of type(v).
                     */
                    if (wType.isSubTypeOf(vType)) {
                        // Try w.rop(v),then v.rop(w).
                        mh = firstImplementer(wRH, vMH);
                    } else {
                        // Try v.op(w) then w.rop(v)
                        mh = firstImplementer(vMH, wRH);
                    }
                }

            } else {
                // class(v) fixes type(v).
                // class(w) fixes type(w).
                vMH = op.handle(vRep);
                if (vType == wType) {
                    // We need only consider vMH
                    mh = vMH;
                } else {
                    wRH = rop.handle(wRep);
                    if (wType.isSubTypeOf(vType)) {
                        // Try w.rop(v),then v.rop(w).
                        mh = firstImplementer(wRH, vMH);
                    } else {
                        // Try v.op(w) then w.rop(v)
                        mh = firstImplementer(vMH, wRH);
                    }
                }
            }

            // MH for guarded invocation (becomes new target)
            // guardMH = insertArguments(CLASS2_GUARD, 0, vClass,
            // wClass);
            // targetMH = guardWithTest(guardMH, mh, getTarget());
            // setTarget(targetMH);
            // chainLength += 1;

            MethodHandle resultMH =
                    firstImplementer(mh, op.errorHandle());
            return resultMH.invokeExact(v, w);
        }

        private Object dynamicResult(BaseType vType, Object v,
                BaseType wType, Object w)
                throws EmptyException, Throwable {
            /*
             * We know that the op and rop handles in the Representation
             * objects of class(v) and class(w) are bounce handles, so
             * we use those in their targets in the type objects
             * directly.
             */
            MethodHandle vMH, wRH;
            Object r; // To return

            if (wType == vType) {
                // Same types so only try v.op(w).
                vMH = op.handle(vType);
                r = vMH.invokeExact(v, w);

            } else if (wType.isSubTypeOf(vType)) {
                // type(w) is sub-type of type(v). Try w.rop(v).
                wRH = rop.handle(wType);
                // In the reflected MH, self is second.
                r = wRH.invokeExact(v, w);
                if (r == Py.NotImplemented) {
                    // type(w) does not define w.rop. Try v.op.
                    vMH = op.handle(vType);
                    r = vMH.invokeExact(v, w);
                }
            } else {
                // Try v.op(w) first.
                vMH = op.handle(vType);
                r = vMH.invokeExact(v, w);
                if (r != Py.NotImplemented) {
                    // type(v) does not define v.op. Try w.rop(v).
                    wRH = rop.handle(wType);
                    // In the reflected MH, self is second.
                    r = wRH.invokeExact(v, w);
                }
            }

            if (r == Py.NotImplemented) { throw op.operandError(v, w); }
            return r;
        }

        /**
         * Compute the result of the call for this particular pair of
         * arguments, and update the site to do this efficiently for the
         * same class in the future, if it is safe and effective to do
         * so. We call this when the class of {@code v} did not match
         * any of the embedded guards.
         *
         * @param v left operand
         * @param w right operand
         * @return {@code op(v, w)}
         * @throws Throwable on errors or if not implemented
         */
        private Object fallback_saved(Object v, Object w)
                throws Throwable {
            // TODO binary call site with shared representations
            /*
             * There is a problem with the logic of this in cases where
             * v and w have the same representation, that may represent
             * multiple types (a shared representation). Typically these
             * are classes defined in Python. The site will be guarded
             * on class, but precedence, whether we consult the left or
             * right operand first, depends on the Python type. We can
             * choose a precedence for the particular objects at hand
             * using their types, but we cannot validly cache the
             * decision for the pair of classes.
             *
             * If the two arguments have the same representation, and it
             * is not a SharedRepresentation, they have the same type.
             * If the representations differ, because the classes
             * differ, the sub-type relationship will apply to all pairs
             * of objects from the two classes.
             */
            fallbackCount += 1;

            Class<?> vClass = v.getClass();
            Representation vRep = registry.get(vClass);
            BaseType vType = vRep.pythonType(v);
            MethodHandle vMH;   // e.g. type(v).__sub__

            Class<?> wClass = w.getClass();
            Representation wRep = registry.get(wClass);
            BaseType wType = wRep.pythonType(w);
            MethodHandle wMH;   // e.g. type(w).__rsub__

            MethodHandle mh, targetMH;

            /*
             * CPython would also test: w.__rop__ == v.__op__ as an
             * optimisation, but that's never the case since we always
             * use distinct __op__ and __rop__ methods.
             */
            if (wType == vType) {
                // Same types so only try the op slot
                mh = singleType(vType, vRep, wRep);

            } else if (!wType.isSubTypeOf(vType)) {
                // Ask left (if not empty) then right.
                mh = leftDominant(vType, vRep, wType, wRep);

            } else {
                // Right is sub-class: ask first (if not empty).
                mh = rightDominant(vType, vRep, wType, wRep);
            }

            /*
             * Compute the result for this case. If the operation
             * throws, it throws here and we do not bind resultMH as a
             * new target. If it's a one-off, we'll get another go.
             */
            Object result = mh.invokeExact(v, w);

            // MH for guarded invocation (becomes new target)
            MethodHandle guardMH =
                    insertArguments(CLASS2_GUARD, 0, vClass, wClass);
            targetMH = guardWithTest(guardMH, mh, getTarget());
            setTarget(targetMH);

            return result;
        }

        /**
         * Compute a method handle in the case where both arguments
         * {@code (v, w)} have the same Python type, although quite
         * possibly different Java classes (in the case where that type
         * has multiple implementations). The returned handle may throw
         * a Python exception when invoked, if that is the correct
         * behaviour, but will not return {@code NotImplemented}.
         *
         * @param type the Python type of {@code v} and {@code w}
         * @param vRep operations of the Java class of {@code v}
         * @param wRep operations of the Java class of {@code w}
         * @return a handle that provides the result (or throws)
         */
        private MethodHandle singleType(BaseType type,
                Representation vRep, Representation wRep) {

            MethodHandle vMH;

            // Does the type define class-specific implementations?
            BinopGrid binops = type.getBinopGrid(op);
            if (binops != null) {
                /*
                 * Are the nominal implementation classes of v, w
                 * supported as operands? These methods are not allowed
                 * to return NotImplemented, so if there's a match, it's
                 * the answer.
                 */
                vMH = binops.get(vRep, wRep);
                if (vMH != BINARY_EMPTY) { return vMH; }
                /*
                 * vType provides class-specific implementations of
                 * op(v,w), but hang on ... both have the same type.
                 */
            } else {
                /*
                 * The type provides no class-specific implementation,
                 * so use the handle in the Representation object.
                 * Typically, this will be strongly-typed on the left
                 * implementation class, but will have to test the
                 * right-hand argument against supported types.
                 */
                vMH = op.handle(vRep);
            }

            if (vMH == BINARY_EMPTY) {
                // Not defined for this type, so will throw
                return op.errorHandle();
            } else {
                /*
                 * vMH is a handle that may return Py.NotImplemented,
                 * which we must turn into an error message.
                 */
                return firstImplementer(vMH, op.errorHandle());
            }
        }

        /**
         * Compute a method handle in the case where the left argument
         * {@code (v)} should be consulted, then the right. The returned
         * handle may throw a Python exception when invoked, if that is
         * the correct behaviour, but will not return
         * {@code NotImplemented}.
         *
         * @param vType the Python type of {@code v}
         * @param vRep operations of the Java class of {@code v}
         * @param wType the Python type of {@code w}
         * @param wRep operations of the Java class of {@code w}
         * @return a handle that provides the result (or throws)
         */
        private MethodHandle leftDominant(BaseType vType,
                Representation vRep, BaseType wType,
                Representation wRep) {

            MethodHandle resultMH, vMH, wMH;

            // Does vType define class-specific implementations?
            BinopGrid binops = vType.getBinopGrid(op);
            if (binops != null) {
                /*
                 * Are the nominal implementation classes of v, w
                 * supported as operands? These methods are not allowed
                 * to return NotImplemented, so if there's a match, it's
                 * the answer.
                 */
                vMH = binops.get(vRep, wRep);
                if (vMH != BINARY_EMPTY) { return vMH; }
                /*
                 * vType provides class-specific implementations of
                 * op(v,w), but the signature we are looking for is not
                 * amongst them.
                 */
                assert (vMH == BINARY_EMPTY);
            } else {
                /*
                 * vType provides no class-specific implementation of
                 * op(v,w). Get the handle from the Representation
                 * object.
                 */
                vMH = op.handle(vRep);
            }

            // Does wType define class-specific rop implementations?
            SpecialMethod rop = op.reflected;
            binops = wType.getBinopGrid(rop);
            if (binops != null) {
                /*
                 * Are the nominal implementation classes of w, v
                 * supported as operands? These methods are not allowed
                 * to return NotImplemented, so if there's a match, it's
                 * the only alternative to smv.
                 */
                wMH = binops.get(wRep, vRep);
                if (wMH != BINARY_EMPTY) {
                    // wType provides a rop(w,v) - note ordering
                    wMH = permuteArguments(wMH, BINOP, 1, 0);
                    if (vMH == BINARY_EMPTY) {
                        // It's the only offer, so it's the answer.
                        return wMH;
                    }
                    /*
                     * smv is also a valid offer, which must be given
                     * first refusal. Only if smv returns
                     * Py.NotImplemented, will we try smw.
                     */
                    return firstImplementer(vMH, wMH);
                }
                /*
                 * wType provides class-specific implementations of
                 * rop(w,v), but the signature we are looking for is not
                 * amongst them.
                 */
                assert (wMH == BINARY_EMPTY);
            } else {
                /*
                 * wType provides no class-specific implementation of
                 * rop(w,v). Get the handle from the Representation
                 * object.
                 */
                wMH = rop.handle(wRep);
            }

            /*
             * If we haven't returned a handle yet, we now have smv and
             * smw, two apparent offers of a handle to compute the
             * result for the classes at hand. Either may be empty.
             * Either may return Py.NotImplemented.
             */
            if (wMH == BINARY_EMPTY) {
                if (vMH == BINARY_EMPTY) {
                    // Easy case: neither slot was defined. We're done.
                    return op.errorHandle();
                } else {
                    // smv was the only one defined
                    resultMH = vMH;
                }
            } else {
                // smw was defined
                wMH = permuteArguments(wMH, BINOP, 1, 0);
                if (vMH == BINARY_EMPTY) {
                    // smv was not, so smw is the only one defined
                    resultMH = wMH;
                } else {
                    // Both were defined, so try them in order
                    resultMH = firstImplementer(vMH, wMH);
                }
            }

            /*
             * resultMH may still return Py.NotImplemented. We use
             * firstImplementer to turn that into an error message.
             * Where we could avoid this, we already returned.
             */
            return firstImplementer(resultMH, op.errorHandle());
        }

        /**
         * Compute a method handle in the case where the right argument
         * {@code (w)} should be consulted, then the left. The returned
         * handle may throw a Python exception when invoked, if that is
         * the correct behaviour, but will not return
         * {@code NotImplemented}.
         *
         * @param vType the Python type of {@code v}
         * @param vRep operations of the Java class of {@code v}
         * @param wType the Python type of {@code w}
         * @param wRep operations of the Java class of {@code w}
         * @return a handle that provides the result (or throws)
         */
        private MethodHandle rightDominant(BaseType vType,
                Representation vRep, BaseType wType,
                Representation wRep) {

            MethodHandle resultMH, vMH, wMH;

            // Does wType define class-specific rop implementations?
            SpecialMethod rop = op.reflected;
            BinopGrid binops = wType.getBinopGrid(rop);
            if (binops != null) {
                /*
                 * Are the nominal implementation classes of w, v
                 * supported as operands? These methods are not allowed
                 * to return NotImplemented, so if there's a match, it's
                 * the answer.
                 */
                wMH = binops.get(wRep, vRep);
                if (wMH != BINARY_EMPTY) {
                    // wType provides a rop(w,v) - note ordering
                    return permuteArguments(wMH, BINOP, 1, 0);
                }
                /*
                 * wType provides class-specific implementations of
                 * rop(w,v), but the signature we are looking for is not
                 * amongst them.
                 */
                assert wMH == BINARY_EMPTY;
            } else {
                /*
                 * wType provides no class-specific implementation of
                 * rop(w,v). Get the handle from the Representation
                 * object.
                 */
                wMH = rop.handle(wRep);
            }

            // Does vType define class-specific implementations?
            binops = vType.getBinopGrid(op);
            if (binops != null) {
                /*
                 * Are the nominal implementation classes of v, w
                 * supported as operands? These methods are not allowed
                 * to return NotImplemented, so if there's a match, it's
                 * the only alternative to smw.
                 */
                vMH = binops.get(vRep, wRep);
                if (vMH != BINARY_EMPTY) {
                    // vType provides an op(v,w)
                    if (wMH == BINARY_EMPTY) {
                        // It's the only offer, so it's the answer.
                        return vMH;
                    }
                    /*
                     * smw is also a valid offer, which must be given
                     * first refusal. Only if smw returns
                     * Py.NotImplemented, will we try smv.
                     */
                    wMH = permuteArguments(wMH, BINOP, 1, 0);
                    return firstImplementer(wMH, vMH);
                }
                /*
                 * vType provides class-specific implementations of
                 * op(v,w), but the signature we are looking for is not
                 * amongst them.
                 */
                assert vMH == BINARY_EMPTY;
            } else {
                /*
                 * vType provides no class-specific implementation of
                 * op(v,w). Get the handle from the Representation
                 * object.
                 */
                vMH = op.handle(vRep);
            }

            /*
             * If we haven't returned a handle yet, we now have smv and
             * smw, two apparent offers of a handle to compute the
             * result for the classes at hand. Either may be empty.
             * Either may return Py.NotImplemented.
             */
            if (wMH == BINARY_EMPTY) {
                if (vMH == BINARY_EMPTY) {
                    // Easy case: neither slot was defined. We're done.
                    return op.errorHandle();
                } else {
                    // smv was the only one defined
                    resultMH = vMH;
                }
            } else {
                // smw was defined
                wMH = permuteArguments(wMH, BINOP, 1, 0);
                if (vMH == BINARY_EMPTY) {
                    // smw is the only one defined
                    resultMH = wMH;
                } else {
                    // Both were defined, so try them in order
                    resultMH = firstImplementer(wMH, vMH);
                }
            }

            /*
             * resultMH may still return Py.NotImplemented. We use
             * firstImplementer to turn that into an error message.
             * Where we could avoid this, we already returned.
             */
            return firstImplementer(resultMH, op.errorHandle());
        }

        /**
         * An adapter for two method handles, {@code a} and {@code b},
         * such that when the returned handle is invoked, first
         * {@code a} is invoked, and then if it returned
         * {@link Py#NotImplemented}, {@code b} is invoked on the same
         * arguments to replace the result. {@code b} may also return
         * {@code NotImplemented} but this gets no special treatment.
         * This corresponds to a central part of the way Python
         * implements binary operations when each operand offers a
         * different implementation.
         *
         * @param a to invoke unconditionally
         * @param b if {@code a} returns {@link Py#NotImplemented}
         * @return the handle that does these invocations
         */
        private static MethodHandle firstImplementer(MethodHandle a,
                MethodHandle b) {
            // bb = λ(r,v,w): b(v,w)
            MethodHandle bb = dropArguments(b, 0, O);
            // rr = λ(r,v,w): r
            MethodHandle rr = dropArguments(identity(O), 1, O, O);
            // g = λ(r,v,w): if r!=NotImplemented ? r : b(v,w)
            MethodHandle g = guardWithTest(IMPLEMENTED_GUARD, rr, bb);
            // return λ(v,w): g(a(v, w), v, w)
            return foldArguments(g, a);
        }
    }

    @SuppressWarnings("unused") // referenced as CLASS_GUARD
    private static boolean classEquals(Class<?> clazz, Object obj) {
        return clazz == obj.getClass();
    }

    @SuppressWarnings("unused") // referenced as CLASS2_GUARD
    private static boolean classEquals(Class<?> V, Class<?> W, Object v,
            Object w) {
        return V == v.getClass() && W == w.getClass();
    }

    @SuppressWarnings("unused") // referenced as IMPLEMENTED_GUARD
    private static boolean isImplemented(Object obj) {
        return obj != Py.NotImplemented;
    }

    private static InterpreterError staticInitError(Throwable cause,
            Class<?> cls) {
        return new InterpreterError(cause,
                "failed initialisation of %s", cls.getSimpleName());
    }

}
