package uk.co.farowl.vsj1.example.treepython;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.lookup;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.co.farowl.vsj1.TreePython.Node;
import uk.co.farowl.vsj1.TreePython.Visitor;
import uk.co.farowl.vsj1.TreePython.expr;
import uk.co.farowl.vsj1.TreePython.expr_context;
import uk.co.farowl.vsj1.TreePython.operator;

/**
 * Demonstrate interpretation of the AST for expressions using two phases:
 * one to link the implementation of the operation for those Java types,
 * and a second to execute that linked method.
 */
public class TestEx4 {

    @BeforeClass
    public static void setUpClass() {
        // Built-in types
        Runtime.registerTypeFor(Integer.class, new IntegerHandler());
        Runtime.registerTypeFor(Double.class, new DoubleHandler());
        Runtime.registerTypeFor(A.class, new AHandler());
        Runtime.registerTypeFor(B.class, new BHandler());
        Runtime.registerTypeFor(C.class, new CHandler());
    }

    // Visitor to execute the code.
    Evaluator evaluator;

    @Before
    public void setUp() {
        // Create a visitor to execute the code.
        evaluator = new Evaluator();
    }

    private Node cubic() {
        // Build the expression tree (unlinked)
        // @formatter:off
        // (x*x-2) * (x+y)
        Node tree =
            BinOp(
                BinOp(
                    BinOp(Name("x", Load), Mult, Name("x", Load)),
                    Sub,
                    Num(2)),
                Mult,
                BinOp(Name("x", Load), Add, Name("y", Load)));
        // @formatter:on
        return tree;
    }

    private Node simple() { // v * w
        return BinOp(Name("v", Load), Mult, Name("w", Load));
    }

    @Test
    public void simpleInt() {
        evaluator.variables.put("v", 6);
        evaluator.variables.put("w", 7);
        assertThat(simple().accept(evaluator), is(42));
    }

    private Node add() { // v + w
        return BinOp(Name("v", Load), Add, Name("w", Load));
    }

    @Test
    public void addAA() {
        evaluator.variables.put("v", new A());
        evaluator.variables.put("w", new A());
        assertThat(add().accept(evaluator), is(instanceOf(A.class)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void addAB() {
        evaluator.variables.put("v", new A());
        evaluator.variables.put("w", new B());
        add().accept(evaluator);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addBA() {
        evaluator.variables.put("v", new B());
        evaluator.variables.put("w", new A());
        add().accept(evaluator);
    }

    @Test
    public void addAC() {
        evaluator.variables.put("v", new A());
        evaluator.variables.put("w", new C());
        assertThat(add().accept(evaluator), is(instanceOf(C.class)));
    }

    @Test
    public void addCA() {
        evaluator.variables.put("v", new C());
        evaluator.variables.put("w", new A());
        assertThat(add().accept(evaluator), is(instanceOf(C.class)));
    }

    @Test
    public void testInt() {
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3);
        assertThat(cubic().accept(evaluator), is(42));
    }

    @Test
    public void testFloatInt() {
        // (x*x-2) exercises sub(Double, Integer)
        evaluator.variables.put("x", 3.);
        evaluator.variables.put("y", 3.);
        assertThat(cubic().accept(evaluator), is(42.));
    }

    @Test
    public void testIntFloat() {
        // (x+y) exercises add(Integer, Double) (float.__radd__)
        // (x*x-2)*(x+y) exercises mul(Integer, Double) (float.__rmul__)
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3.);
        assertThat(cubic().accept(evaluator), is(42.));
    }

    @Test
    public void testIntRepeat() {
        Node tree = cubic();
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42));
        evaluator.variables.put("x", 4);
        evaluator.variables.put("y", -1);
        assertThat(tree.accept(evaluator), is(42));
        evaluator.variables.put("x", 2);
        evaluator.variables.put("y", 19);
        assertThat(tree.accept(evaluator), is(42));
        evaluator.variables.put("x", 6);
        evaluator.variables.put("y", 7);
        assertThat(tree.accept(evaluator), is(442));
    }

    @Test
    public void testFloatRepeat() {
        Node tree = cubic();
        evaluator.variables.put("x", 3.);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42.));
        evaluator.variables.put("x", 4.);
        evaluator.variables.put("y", -1);
        assertThat(tree.accept(evaluator), is(42.));
        evaluator.variables.put("x", 2.);
        evaluator.variables.put("y", 19);
        assertThat(tree.accept(evaluator), is(42.));
        evaluator.variables.put("x", 6.);
        evaluator.variables.put("y", 7);
        assertThat(tree.accept(evaluator), is(442.));
    }

// @Test
    public void testChangeType() {
        Node tree = cubic();
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42));
        evaluator.variables.put("x", 4);
        evaluator.variables.put("y", -1);
        assertThat(tree.accept(evaluator), is(42));
        // Suddenly x and y are floats
        evaluator.variables.put("x", 2.);
        evaluator.variables.put("y", 19.);
        // And now x is an int again
        assertThat(tree.accept(evaluator), is(42.));
        evaluator.variables.put("x", 6);
        evaluator.variables.put("y", 7.);
        assertThat(tree.accept(evaluator), is(442.));
    }

    /**
     * An interpreter for Python that works by walking the AST, and uses
     * look-up to find the type object corresponding to the Java native
     * type.
     */
    static class Evaluator implements Visitor<Object> {

        Map<String, Object> variables = new HashMap<>();

        @Override
        public Object visit_BinOp(expr.BinOp binOp) {
            // This must be a first visit
            Object v = binOp.left.accept(this);
            Object w = binOp.right.accept(this);
            try {
                MethodHandle mh = Runtime.findBinOp(v.getClass(), binOp.op,
                        w.getClass());
                return mh.invokeExact(v, w);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Implementation returned NotImplemented or equivalent
                throw notDefined(v, binOp.op, w);
            } catch (Throwable e) {
                // Something else went wrong
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public Object visit_Num(expr.Num num) {
            return num.n;
        }

        @Override
        public Object visit_Name(expr.Name name) {
            return variables.get(name.id);
        }

        /** Convenience method to create an IllegalArgumentException. */
        private static IllegalArgumentException notDefined(Object v,
                operator op, Object w) {
            String msg = "Operation %s not defined between %s and %s";
            String V = v.getClass().getSimpleName();
            String W = w.getClass().getSimpleName();
            throw new IllegalArgumentException(
                    String.format(msg, op, V, W));
        }
    }

    /** Runtime support for the interpreter. */
    static class Runtime {

        /** Support class mapping from Java classes to handlers. */
        private static final Map<Class<?>, TypeHandler> typeRegistry =
                new Hashtable<>();

        /** Look up <code>TypeHandler</code> for Java class. */
        public static final ClassValue<TypeHandler> handlers =
                new ClassValue<TypeHandler>() {

                    @Override
                    protected synchronized TypeHandler
                            computeValue(Class<?> c) {
                        return typeRegistry.get(c);
                    }
                };

        /** Look up <code>TypeHandler</code> for Java class. */
        public static TypeHandler typeFor(Class<?> c) {
            return handlers.get(c);
        }

        /** Assign <code>TypeHandler</code> for Java class (once only). */
        public static void registerTypeFor(Class<?> c, TypeHandler type) {
            if (typeRegistry.putIfAbsent(c, type) != null) {
                throw new IllegalArgumentException(
                        "Attempt to redefine type handling class " + c);
            }
        }

        /** A (static) method implementing a unary op has this type. */
        protected static final MethodType UOP;
        /** A (static) method implementing a binary op has this type. */
        protected static final MethodType BINOP;
        /** Handle of a method returning NotImplemented. */
        static final MethodHandle BINOP_NOT_IMPLEMENTED;
        /** Handle of a method testing result == NotImplemented. */
        static final MethodHandle IS_NOT_IMPLEMENTED;
        /** Handle of a method throwing if result == NotImplemented. */
        static final MethodHandle THROW_IF_NOT_IMPLEMENTED;
        /** Shorthand for <code>Object.class</code>. */
        static final Class<Object> O = Object.class;

        private static final Lookup lookup;

        static {
            lookup = lookup();
            UOP = MethodType.methodType(O, O);
            BINOP = MethodType.methodType(O, O, O);
            BINOP_NOT_IMPLEMENTED =
                    findStatic(Runtime.class, "notImplemented", BINOP);
            IS_NOT_IMPLEMENTED =
                    findStatic(Runtime.class, "isNotImplemented",
                            MethodType.methodType(boolean.class, O));
            THROW_IF_NOT_IMPLEMENTED = findStatic(Runtime.class,
                    "throwIfNotImplemented", UOP);
        }

        /** Singleton object for return by unimplemented operations. */
        static final Object NotImplemented = new Object();

        /** True iff argument is NotImplemented. */
        static boolean isNotImplemented(Object u) {
            return NotImplemented.equals(u);
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

        /**
         * Convenience function wrapping
         * {@link Lookup#findStatic(Class, String, MethodType)}, throwing
         * {@code ExceptionInInitializerError} if the method cannot be
         * found, an unchecked exception, wrapping the real cause.
         *
         * @param refc class in which to find the method
         * @param name method name
         * @param type method type
         * @return handle to the method
         * @throws ExceptionInInitializerError if the method was not found
         */
        private static MethodHandle findStatic(Class<?> refc, String name,
                MethodType type) throws ExceptionInInitializerError {
            try {
                return lookup.findStatic(refc, name, type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        /**
         * Convenience function wrapping
         * {@link Lookup#findVirtual(Class, String, MethodType)}, throwing
         * {@code ExceptionInInitializerError} if the method cannot be
         * found, an unchecked exception, wrapping the real cause.
         *
         * @param refc class in which to find the method
         * @param name method name
         * @param type method type
         * @return handle to the method
         * @throws ExceptionInInitializerError if the method was not found
         */
        private static MethodHandle findVirtual(Class<?> refc, String name,
                MethodType type) throws ExceptionInInitializerError {
            try {
                return lookup.findVirtual(refc, name, type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        static MethodHandle findBinOp(Class<?> leftClass, operator op,
                Class<?> rightClass)
                throws NoSuchMethodException, IllegalAccessException {
            TypeHandler V = Runtime.typeFor(leftClass);
            TypeHandler W = Runtime.typeFor(rightClass);
            MethodHandle mhV = V.findBinOp(op, W);
            if (W == V) {
                return mhV;
            }
            MethodHandle mhW = W.findBinOp(V, op); // reversed op
            if (mhW == BINOP_NOT_IMPLEMENTED) {
                return mhV;
            } else if (mhV == BINOP_NOT_IMPLEMENTED) {
                return mhW;
            } else if (mhW.equals(mhV)) {
                return mhV;
            } else if (W.isSubtypeOf(V)) {
                return firstImplementer(mhW, mhV);
            } else {
                return firstImplementer(mhV, mhW);
            }
        }

        /**
         * An adapter for two method handles <code>a</code> and
         * <code>b</code> such that when invoked, first <code>a</code> is
         * invoked, then if it returns <code>NotImplemented</code>,
         * <code>b</code> is invoked on the same arguments. If it also
         * returns <code>NotImplemented</code> then
         * <code>NoSuchMethodException</code> will be thrown. This
         * corresponds to the way Python implements binary operations when
         * each operand offers a different implementation.
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

    /**
     * <code>TypeHandler</code> is the base class of the classes that
     * collect together the operations on each Python type. These
     * subclasses should be singletons.
     */
    static abstract class TypeHandler {

        /** A method implementing a binary operation has this type. */
        protected static final MethodType BINOP = Runtime.BINOP;
        /** Shorthand for <code>Object.class</code>. */
        static final Class<Object> O = Object.class;

        /**
         * Compose the function name (in this handler) for an operation or
         * its reverse.
         *
         * @param op binary operation e.g. Mult
         * @return the name e.g. mul
         */
        protected String composeNameFor(operator op) {
            String opstr = op.toString();
            int len = Math.min(3, opstr.length());
            return opstr.substring(0, len).toLowerCase();
        }

        /**
         * Convenience function wrapping
         * {@link Lookup#findStatic(Class, String, MethodType)}, and typing
         * as a binary operation, returning null if the method cannot be
         * found.
         *
         * @param refc class in which to find the method
         * @param name method name
         * @param type method type
         * @return handle to the method or null
         */
        private MethodHandle findStaticOrNull(Class<?> refc, String name,
                MethodType type) {
            try {
                MethodHandle mh = lookup.findStatic(refc, name, type);
                return mh.asType(BINOP);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return null;
            }
        }

        /**
         * Return the method handle of the implementation of
         * <code>left op right</code>, where left is an object of this
         * handler's type.
         *
         * @param op the binary operation to find
         * @param rightType
         * @return
         */
        public MethodHandle findBinOp(operator op, TypeHandler rightType) {
            String name = composeNameFor(op);
            Class<?> here = this.getClass();
            Class<?> leftClass = this.javaClass;
            Class<?> rightClass = rightType.javaClass;

            // Look for an exact match with the actual types
            MethodType mt =
                    MethodType.methodType(O, leftClass, rightClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            // Look for a match with (T, Object)
            if (mh == null) {
                mt = MethodType.methodType(O, leftClass, O);
                mh = findStaticOrNull(here, name, mt);
            }

            // Look for a match with (Object, Object)
            if (mh == null) {
                mh = findStaticOrNull(here, name, BINOP);
            }

            return mh == null ? Runtime.BINOP_NOT_IMPLEMENTED : mh;
        }

        /**
         * Return the method handle of the (reverse) implementation of
         * <code>left op right</code>, where right is an object of this
         * handler's type.
         *
         * @param leftType
         * @param op the binary operation to find
         * @return
         */
        public MethodHandle findBinOp(TypeHandler leftType, operator op) {
            String name = composeNameFor(op);
            Class<?> here = this.getClass();
            Class<?> leftClass = leftType.javaClass;
            Class<?> rightClass = this.javaClass;

            // Look for an exact match with the actual types
            MethodType mt =
                    MethodType.methodType(O, leftClass, rightClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            // Look for a match with (Object, T)
            if (mh == null) {
                mt = MethodType.methodType(O, O, rightClass);
                mh = findStaticOrNull(here, name, mt);
            }

            // Look for a match with (Object, Object)
            if (mh == null) {
                mh = findStaticOrNull(here, name, BINOP);
            }

            return mh == null ? Runtime.BINOP_NOT_IMPLEMENTED : mh;
        }

        /** The lookup rights object of the implementing class. */
        private final Lookup lookup;

        /** The implementing class served by this type handler. */
        public final Class<?> javaClass;

        protected TypeHandler(Lookup lookup, Class<?> javaClass) {
            this.lookup = lookup;
            this.javaClass = javaClass;
        }

        /**
         * Whether this type is a sub-type of the other for the purposes of
         * method resolution.
         */
        public boolean isSubtypeOf(TypeHandler other) {
            return false;
        }
    }

    /**
     * Cclass defining the operations for a Java <code>Integer</code>, so
     * as to make it a Python <code>int</code>.
     */
    @SuppressWarnings(value = {"unused"})
    static class IntegerHandler extends TypeHandler {

        // @formatter:off
        IntegerHandler() { super(lookup(), Integer.class); }

        private static Object add(Integer v, Integer w) { return v+w; }
        private static Object sub(Integer v, Integer w) { return v-w; }
        private static Object mul(Integer v, Integer w) { return v*w; }
        private static Object div(Integer v, Integer w) {
            return v.doubleValue() / w.doubleValue();
        }
        // @formatter:on
    }

    /**
     * Class defining the operations for a Java <code>Double</code>, so as
     * to make it a Python <code>float</code>.
     */
    @SuppressWarnings(value = {"unused"})
    static class DoubleHandler extends TypeHandler {

        // @formatter:off
        DoubleHandler() { super(lookup(), Double.class); }

        private static Object add(Double v, Integer w) { return v+w; }
        private static Object add(Integer v, Double w) { return v+w; }
        private static Object add(Double v, Double w)  { return v+w; }
        private static Object sub(Double v, Integer w) { return v-w; }
        private static Object sub(Integer v, Double w) { return v-w; }
        private static Object sub(Double v, Double w)  { return v-w; }
        private static Object mul(Double v, Integer w) { return v*w; }
        private static Object mul(Integer v, Double w) { return v*w; }
        private static Object mul(Double v, Double w)  { return v*w; }
        private static Object div(Double v, Integer w) { return v/w; }
        private static Object div(Integer v, Double w) { return v/w; }
        private static Object div(Double v, Double w)  { return v/w; }
        // @formatter:on
    }

    // @formatter:off
    static class A {}
    static class AHandler extends TypeHandler {
        AHandler() {
            super(lookup(), A.class); }
        @SuppressWarnings(value = {"unused"})
        private static Object add(Object v, Object w) {
            if (v instanceof A && w instanceof A) { return new A(); }
            return Runtime.NotImplemented;
        }
    }

    static class B {}
    static class BHandler extends TypeHandler {
        BHandler() { super(lookup(), B.class); }
        @SuppressWarnings(value = {"unused"})
        private static Object add(Object v, Object w) {
            if (v instanceof B && w instanceof B) { return new B(); }
            return Runtime.NotImplemented;
        }
    }

    static class C {}
    static class CHandler extends TypeHandler {

        private CHandler() { super(lookup(), C.class); }

        @Override
        public boolean isSubtypeOf(TypeHandler other) {
             return other.getClass() == AHandler.class;
        }

        // C knows how to do addition with an A
        @SuppressWarnings(value = {"unused"})
        private static Object add(Object v, Object w) {
            if (v instanceof C) {
                // __add__(self, w)
                if (w instanceof C || w instanceof A) {
                    return new C();
                }
            } else if (w instanceof C) {
                // __radd__(self, v)
                if (v instanceof A || v instanceof C) {
                    return new C();
                }
            }
            return Runtime.NotImplemented;
        }
    }
    // @formatter:on

    // @formatter:off
    public static final operator Add = operator.Add;
    public static final operator Sub = operator.Sub;
    public static final operator Mult = operator.Mult;
    public static final operator Div = operator.Div;
    public static final expr_context Load = expr_context.Load;
    public static final expr Name(String id, expr_context ctx)
        {return new expr.Name(id, ctx); }
    public static final expr Num(Object n) {return new expr.Num(n); }
    public static final expr BinOp(expr left, operator op, expr right)
        {return new expr.BinOp(left, op, right); }
    // @formatter:on

}
