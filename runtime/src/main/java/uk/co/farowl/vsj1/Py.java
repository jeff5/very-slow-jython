package uk.co.farowl.vsj1;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.lookup;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Hashtable;
import java.util.Map;

import uk.co.farowl.vsj1.BinOpCallSite;
import uk.co.farowl.vsj1.UnaryOpCallSite;
import uk.co.farowl.vsj1.TreePython.expr;
import uk.co.farowl.vsj1.TreePython.operator;
import uk.co.farowl.vsj1.TreePython.unaryop;

/** Runtime support for the interpreter. */
public class Py {

    /** Mapping from Java classes to operation handlers. */
    private static final Map<Class<?>, Operations> opsHandlerMap =
            new Hashtable<>();

    /** Look up <code>Operations</code> for Java class. */
    private static final ClassValue<Operations> opsRegistry =
            new ClassValue<Operations>() {

                @Override
                protected synchronized Operations
                        computeValue(Class<?> c) {
                    return opsHandlerMap.get(c);
                }
            };

    /** Look up <code>Operations</code> for Java class. */
    public static Operations opsFor(Class<?> c) {
        return opsRegistry.get(c);
    }

    /** Assign <code>Operations</code> for Java classes. */
    public static void registerOps(Operations ops, Class<?>... classes) {
        for (Class<?> c : classes) {
            if (opsHandlerMap.putIfAbsent(c, ops) != null) {
                throw new IllegalArgumentException(
                        "Attempt to redefine operations on class " + c);
            }
        }
    }

    /** Remove all <code>Operations</code> from Java classes. */
    public static void deregisterOps() {
        // System.out.println(
        // "Discarding " + opsHandlerMap.size() + " class bindings.");
        for (Class<?> c : opsHandlerMap.keySet()) {
            opsRegistry.remove(c);
        }
        opsHandlerMap.clear();
    }

    /** A (static) method implementing a unary op has this type. */
    public static final MethodType UOP;
    /** Handle of a method returning NotImplemented (any number args). */
    public static final MethodHandle NOT_IMPLEMENTED;
    /** A (static) method implementing a binary op has this type. */
    public static final MethodType BINOP;
    /** Handle of a method testing result == NotImplemented. */
    static final MethodHandle IS_NOT_IMPLEMENTED;
    /** Handle of a method throwing if result == NotImplemented. */
    static final MethodHandle THROW_IF_NOT_IMPLEMENTED;
    /** Handle of Object.getClass. */
    static final MethodHandle GET_CLASS;
    /** Handle of a method for testing class equality. */
    static final MethodHandle CLASS_EQUALS;
    /** Handle of testing that an object has a particular class. */
    static final MethodHandle HAS_CLASS;
    /** Shorthand for <code>Object.class</code>. */
    static final Class<Object> O = Object.class;
    /** Shorthand for <code>Class.class</code>. */
    static final Class<?> C = Class.class;

    private static final Lookup lookup;

    static {
        lookup = lookup();
        NOT_IMPLEMENTED = findStatic(Py.class, "notImplemented",
                MethodType.genericMethodType(0, true));
        UOP = MethodType.methodType(O, O);
        BINOP = MethodType.methodType(O, O, O);
        IS_NOT_IMPLEMENTED = findStatic(Py.class, "isNotImplemented",
                MethodType.methodType(boolean.class, O));
        THROW_IF_NOT_IMPLEMENTED =
                findStatic(Py.class, "throwIfNotImplemented", UOP);
        GET_CLASS = findVirtual(O, "getClass", MethodType.methodType(C));
        CLASS_EQUALS = findVirtual(C, "equals",
                MethodType.methodType(boolean.class, O));
        HAS_CLASS = findStatic(Py.class, "classEquals",
                MethodType.methodType(boolean.class, C, O));
    }

    /** Singleton object for return by unimplemented operations. */
    static final Object NotImplemented = new Object();

    /** True iff argument is NotImplemented. */
    static boolean isNotImplemented(Object u) {
        return NotImplemented.equals(u);
    }

    /**
     * Operation throwing NoSuchMethodError, use as dummy.
     *
     * @throws NoSuchMethodException
     */
    static Object notImplemented(Object... ignored)
            throws NoSuchMethodException {
        throw new NoSuchMethodException();
    }

    /**
     * Binary operation throwing NoSuchMethodError, use as dummy.
     *
     * @throws NoSuchMethodException
     */
    static Object notImplemented(Object v, Object w)
            throws NoSuchMethodException {
        throw new NoSuchMethodException();
    }

    /**
     * Unary operation throwing NoSuchMethodError, use as dummy.
     *
     * @throws NoSuchMethodException
     */
    static Object notImplemented(Object v) throws NoSuchMethodException {
        throw new NoSuchMethodException();
    }

    /**
     * Throw if argument is NotImplemented, else return argument.
     *
     * @throws NoSuchMethodException if passed NotImplemented
     */
    static Object throwIfNotImplemented(Object u)
            throws NoSuchMethodException {
        if (NotImplemented.equals(u)) {
            throw new NoSuchMethodException();
        } else {
            return u;
        }
    }

    @SuppressWarnings("unused") // referenced as HAS_CLASS
    private static boolean classEquals(Class<?> clazz, Object obj) {
        return clazz == obj.getClass();
    }

    /**
     * Convenience function wrapping
     * {@link Lookup#findStatic(Class, String, MethodType)}, throwing
     * {@code RuntimeException} if the method cannot be found, an unchecked
     * exception, wrapping the real cause.
     *
     * @param refc class in which to find the method
     * @param name method name
     * @param type method type
     * @return handle to the method
     * @throws RuntimeException if the method was not found
     */
    static MethodHandle findStatic(Class<?> refc, String name,
            MethodType type) throws RuntimeException {
        try {
            return lookup.findStatic(refc, name, type);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw lookupRTE(refc, name, type, false, e);
        }
    }

    /**
     * Convenience function wrapping
     * {@link Lookup#findVirtual(Class, String, MethodType)}, throwing
     * {@code RuntimeException} if the method cannot be found, an unchecked
     * exception, wrapping the real cause.
     *
     * @param refc class in which to find the method
     * @param name method name
     * @param type method type
     * @return handle to the method
     * @throws RuntimeException if the method was not found
     */
    static MethodHandle findVirtual(Class<?> refc, String name,
            MethodType type) throws RuntimeException {
        try {
            return lookup.findVirtual(refc, name, type);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw lookupRTE(refc, name, type, true, e);
        }
    }

    /** Convenience method to create a lookup RuntimeException. */
    private static RuntimeException lookupRTE(Class<?> refc, String name,
            MethodType type, boolean isVirtual, Throwable t) {
        final String modeString = isVirtual ? "virtual" : "static";
        String fmt = "In runtime looking up %s %s#%s with type %s";
        String msg = String.format(fmt, modeString, refc.getSimpleName(),
                name, type);
        return new RuntimeException(msg, t);
    }

    /**
     * Bootstrap a simulated invokedynamic call site for a unary operation.
     *
     * @throws IllegalAccessException
     * @throws NoSuchMethodException
     */
    public static CallSite bootstrap(Lookup lookup, expr.UnaryOp unaryOp)
            throws NoSuchMethodException, IllegalAccessException {
        return new UnaryOpCallSite(lookup, unaryOp.op);
    }

    /**
     * Provide (as a method handle) an appropriate implementation of the
     * given operation, on a a target Java type.
     *
     * @param op operator to apply
     * @param vClass Java class of operand
     * @return MH representing the operation
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     */
    public static MethodHandle findUnaryOp(unaryop op, Class<?> vClass)
            throws NoSuchMethodException, IllegalAccessException {
        Operations V = Py.opsFor(vClass);
        MethodHandle mhV = V.findUnaryOp(op, vClass);
        return mhV.asType(UOP);
    }

    /**
     * Bootstrap a simulated invokedynamic call site for a binary
     * operation.
     *
     * @throws IllegalAccessException
     * @throws NoSuchMethodException
     */
    public static CallSite bootstrap(Lookup lookup, expr.BinOp binOp)
            throws NoSuchMethodException, IllegalAccessException {
        return new BinOpCallSite(lookup, binOp.op);
    }

    /**
     * Provide (as a method handle) an appropriate implementation of the
     * given operation, between operands of two Java types, conforming to
     * Python delegation rules.
     *
     * @param vClass Java class of left operand
     * @param op operator to apply
     * @param wClass Java class of right operand
     * @return MH representing the operation
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     */
    public static MethodHandle findBinOp(Class<?> vClass, operator op,
            Class<?> wClass)
            throws NoSuchMethodException, IllegalAccessException {
        Operations V = Py.opsFor(vClass);
        Operations W = Py.opsFor(wClass);
        MethodHandle mhV = V.findBinOp(vClass, op, wClass);
        MethodHandle opV = mhV.asType(BINOP);
        if (W == V) {
            return opV;
        }
        MethodHandle mhW = W.findBinOp(vClass, op, wClass);
        if (mhW == NOT_IMPLEMENTED) {
            return opV;
        }
        MethodHandle opW = mhW.asType(BINOP);
        if (mhV == NOT_IMPLEMENTED || mhW.equals(mhV)) {
            return opW;
        } else if (W.isSubtypeOf(V)) {
            return firstImplementer(opW, opV);
        } else {
            return firstImplementer(opV, opW);
        }
    }

    /**
     * An adapter for two method handles <code>a</code> and <code>b</code>
     * such that when invoked, first <code>a</code> is invoked, then if it
     * returns <code>NotImplemented</code>, <code>b</code> is invoked on
     * the same arguments. If it also returns <code>NotImplemented</code>
     * then <code>NoSuchMethodException</code> will be thrown. This
     * corresponds to the way Python implements binary operations when each
     * operand offers a different implementation.
     */
    private static MethodHandle firstImplementer(MethodHandle a,
            MethodHandle b) {
        // apply_b = λ(x,y,z): b(y,z)
        MethodHandle apply_b = MethodHandles.filterReturnValue(
                dropArguments(b, 0, O), THROW_IF_NOT_IMPLEMENTED);
        // keep_a = λ(x,y,z): x
        MethodHandle keep_a = dropArguments(identity(O), 1, O, O);
        // x==NotImplemented ? b(y,z) : a(y,z)
        MethodHandle guarded =
                guardWithTest(IS_NOT_IMPLEMENTED, apply_b, keep_a);
        // The functions above apply to (a(y,z), y, z) thanks to:
        return foldArguments(guarded, a);
    }
}