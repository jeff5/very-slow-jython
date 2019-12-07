package uk.co.farowl.vsj1.example;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.lookup;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.co.farowl.vsj1.example.TreePythonEx6.Node;
import uk.co.farowl.vsj1.example.TreePythonEx6.Visitor;
import uk.co.farowl.vsj1.example.TreePythonEx6.expr;
import uk.co.farowl.vsj1.example.TreePythonEx6.expr_context;
import uk.co.farowl.vsj1.example.TreePythonEx6.operator;
import uk.co.farowl.vsj1.example.TreePythonEx6.unaryop;

/**
 * Dealing with many implementations of the integer type
 * (<code>Byte</code>, <code>Integer</code>, <code>Long</code> etc., and
 * <code>BigInteger</code>) in the interpretation of the AST. Emphasis on
 * sensible use of <code>Number</code> to avoid an explosion of
 * implementations of the binary operations.
 */
public class TestEx8 {

    @BeforeClass
    public static void setUpClass() {
        // Built-in types
        TypeHandler integerHandler = new IntegerHandler();
        Runtime.registerTypeFor(Byte.class, integerHandler);
        Runtime.registerTypeFor(Short.class, integerHandler);
        Runtime.registerTypeFor(Integer.class, integerHandler);
        Runtime.registerTypeFor(Long.class, new LongHandler());
        Runtime.registerTypeFor(BigInteger.class, new BigIntegerHandler());

        TypeHandler doubleHandler = new DoubleHandler();
        Runtime.registerTypeFor(Float.class, doubleHandler);
        Runtime.registerTypeFor(Double.class, doubleHandler);

        Runtime.registerTypeFor(String.class, new StringHandler());
    }

    // Visitor to execute the code.
    Evaluator evaluator;

    @Before
    public void setUp() {
        // Create a visitor to execute the code.
        evaluator = new Evaluator();
    }

    private static void resetFallbackCalls() {
        BinOpCallSite.fallbackCalls = 0;
        UnaryOpCallSite.fallbackCalls = 0;
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
                    Constant(2, null)),
                Mult,
                BinOp(Name("x", Load), Add, Name("y", Load)));
        // @formatter:on
        return tree;
    }

    private Node uncubic() {
        // Build the expression tree (unlinked)
        // @formatter:off
        // (x*x-2) * -(-x-y)
        Node tree =
                BinOp(
                    BinOp(
                        BinOp(Name("x", Load), Mult, Name("x", Load)),
                        Sub,
                        Constant(2, null)),
                    Mult,
                    UnaryOp(
                        USub,
                        BinOp(
                            UnaryOp(USub, Name("x", Load)),
                            Sub,
                            Name("y", Load))));
        // @formatter:on
        return tree;
    }

    @Test
    public void simpleUnaryInt() {
        Node tree = UnaryOp(USub, Name("v", Load));
        evaluator.variables.put("v", 6);
        assertThat(tree.accept(evaluator), is(-6));

        // Test promotion in unary -
        resetFallbackCalls();
        evaluator.variables.put("v", Integer.MIN_VALUE);
        Long expected = Long.valueOf(-(long)Integer.MIN_VALUE);
        assertThat(tree.accept(evaluator), is(expected));
        assertThat(UnaryOpCallSite.fallbackCalls, is(0));
    }

    @Test
    public void simpleUnaryByte() {
        Node tree = UnaryOp(USub, Name("v", Load));
        evaluator.variables.put("v", (byte)6);
        assertThat(tree.accept(evaluator), is(-6));
    }

    @Test
    public void simpleUnaryLong() {
        Node tree = UnaryOp(USub, Name("v", Load));
        evaluator.variables.put("v", 6L);
        assertThat(tree.accept(evaluator), is(-6L));

        // Test promotion in unary -
        resetFallbackCalls();
        evaluator.variables.put("v", Long.MIN_VALUE);
        BigInteger expected =
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        assertThat(tree.accept(evaluator), is(expected));
        assertThat(UnaryOpCallSite.fallbackCalls, is(0));
    }

    @Test
    public void simpleUnaryBigInt() {
        Node tree = UnaryOp(USub, Name("v", Load));
        evaluator.variables.put("v", BigInteger.valueOf(-42));
        assertThat(tree.accept(evaluator), is(BIG_42));
    }

    private static final BigInteger BIG_2 = BigInteger.valueOf(2);
    private static final BigInteger BIG_3 = BigInteger.valueOf(3);
    private static final BigInteger BIG_19 = BigInteger.valueOf(19);
    private static final BigInteger BIG_21 = BigInteger.valueOf(21);
    private static final BigInteger BIG_42 = BigInteger.valueOf(42);

    @Test
    public void simpleBinaryBigInt() {
        Node tree = BinOp(Name("v", Load), Mult, Name("w", Load));
        evaluator.variables.put("v", 6);
        evaluator.variables.put("w", 7);
        assertThat(tree.accept(evaluator), is(42));

        resetFallbackCalls();
        evaluator.variables.put("v", BIG_21);
        evaluator.variables.put("w", BIG_2);
        assertThat(tree.accept(evaluator), is(BIG_42));
        assertThat(BinOpCallSite.fallbackCalls, is(1));
    }

    @Test
    public void testByte() {
        Node tree = uncubic();
        evaluator.variables.put("x", (byte)3);
        evaluator.variables.put("y", (byte)3);
        assertThat(tree.accept(evaluator), is(42));
    }

    @Test
    public void testShort() {
        Node tree = uncubic();
        evaluator.variables.put("x", (short)3);
        evaluator.variables.put("y", (short)3);
        assertThat(tree.accept(evaluator), is(42));
    }

    @Test
    public void testInt() {
        Node tree = uncubic();
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42));
    }

    @Test
    public void testLong() {
        Node tree = uncubic();
        evaluator.variables.put("x", 3L);
        evaluator.variables.put("y", 3L);
        assertThat(tree.accept(evaluator), is(42L));
    }

    @Test
    public void testBigInt() {
        Node tree = uncubic();
        evaluator.variables.put("x", BIG_3);
        evaluator.variables.put("y", BIG_3);
        assertThat(tree.accept(evaluator), is(BIG_42));
    }

    @Test
    public void testByteInt() {
        Node tree = uncubic();
        evaluator.variables.put("x", (byte)3);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42));
    }

    @Test
    public void testLongInt() {
        Node tree = uncubic();
        evaluator.variables.put("x", 3L);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42L));
    }

    @Test
    public void testBigIntInt() {
        Node tree = uncubic();
        evaluator.variables.put("x", BIG_3);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(BIG_42));
    }

    @Test
    public void testByteLong() {
        Node tree = uncubic();
        evaluator.variables.put("x", (byte)3);
        evaluator.variables.put("y", 3L);
        assertThat(tree.accept(evaluator), is(42L));
    }

    @Test
    public void testIntLong() {
        Node tree = uncubic();
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3L);
        assertThat(tree.accept(evaluator), is(42L));
    }

    @Test
    public void testBigIntLong() {
        Node tree = uncubic();
        evaluator.variables.put("x", BIG_3);
        evaluator.variables.put("y", 3L);
        assertThat(tree.accept(evaluator), is(BIG_42));
    }

    @Test
    public void testByteBigInt() {
        Node tree = uncubic();
        evaluator.variables.put("x", (byte)2);
        evaluator.variables.put("y", BIG_19);
        assertThat(tree.accept(evaluator), is(BIG_42));
    }

    @Test
    public void testIntBigInt() {
        Node tree = uncubic();
        evaluator.variables.put("x", 2);
        evaluator.variables.put("y", BIG_19);
        assertThat(tree.accept(evaluator), is(BIG_42));
    }

    @Test
    public void testLongBigInt() {
        Node tree = uncubic();
        evaluator.variables.put("x", 3L);
        evaluator.variables.put("y", BIG_3);
        assertThat(tree.accept(evaluator), is(BIG_42));
    }

    @Test
    public void testIntByte() {
        Node tree = uncubic();
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", (byte)3);
        assertThat(tree.accept(evaluator), is(42));
    }

    @Test
    public void testFloatBigInt() {
        // x*x exercises mul(Float, Float)
        // (x*x-2) exercises sub(Double, Integer)
        evaluator.variables.put("x", 3f);
        evaluator.variables.put("y", BIG_3);
        assertThat(cubic().accept(evaluator), is(42.));
    }

    @Test
    public void testDoubleBigInt() {
        // x*x exercises mul(Double, Double)
        // (x*x-2) exercises sub(Double, Integer)
        evaluator.variables.put("x", 3d);
        evaluator.variables.put("y", BIG_3);
        assertThat(cubic().accept(evaluator), is(42.));
    }

    @Test
    public void testBigIntFloat() {
        // (x+y) exercises add(Integer, Double) (float.__radd__)
        // (x*x-2)*(x+y) exercises mul(Integer, Double) (float.__rmul__)
        evaluator.variables.put("x", BIG_3);
        evaluator.variables.put("y", 3f);
        assertThat(cubic().accept(evaluator), is(42.));
    }

    @Test
    public void testBigIntDouble() {
        // (x+y) exercises add(Integer, Double) (float.__radd__)
        // (x*x-2)*(x+y) exercises mul(Integer, Double) (float.__rmul__)
        evaluator.variables.put("x", BIG_3);
        evaluator.variables.put("y", 3d);
        assertThat(cubic().accept(evaluator), is(42.));
    }

    @Test
    public void testBigIntRepeat() {
        Node tree = uncubic();
        evaluator.variables.put("x", BIG_3);
        evaluator.variables.put("y", BIG_3);
        assertThat(tree.accept(evaluator), is(BIG_42));

        resetFallbackCalls();
        evaluator.variables.put("x", BigInteger.valueOf(4));
        evaluator.variables.put("y", BigInteger.valueOf(-1));
        assertThat(tree.accept(evaluator), is(BIG_42));

        evaluator.variables.put("x", BIG_2);
        evaluator.variables.put("y", BIG_19);
        assertThat(tree.accept(evaluator), is(BIG_42));

        evaluator.variables.put("x", BigInteger.valueOf(6));
        evaluator.variables.put("y", BigInteger.valueOf(7));
        assertThat(tree.accept(evaluator), is(BigInteger.valueOf(442)));
        assertThat(BinOpCallSite.fallbackCalls, is(0));
        assertThat(UnaryOpCallSite.fallbackCalls, is(0));
    }

    // @Test
    public void smallToInteger() {
        // (x*x-2) * -(-x-y)
        Node tree = uncubic();

        // All calculations are Integer
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42));

        // x*x, -x and (-x)-y and promote Byte to Integer
        resetFallbackCalls();
        evaluator.variables.put("x", (byte)3);
        evaluator.variables.put("y", (byte)3);
        Object result = tree.accept(evaluator);
        assertThat(result, is(42));
        assertThat(result, is(instanceOf(Integer.class)));
        assertThat(BinOpCallSite.fallbackCalls, is(2));
        assertThat(UnaryOpCallSite.fallbackCalls, is(1));

        // x*x, -x and (-x)-y and promote Short to Integer
        resetFallbackCalls();
        evaluator.variables.put("x", (short)2);
        evaluator.variables.put("y", (short)19);
        result = tree.accept(evaluator);
        assertThat(result, is(42));
        assertThat(result, is(instanceOf(Integer.class)));
        assertThat(BinOpCallSite.fallbackCalls, is(2));
        assertThat(UnaryOpCallSite.fallbackCalls, is(1));
    }

    @Test
    public void integerToBigInteger() {
        // (x*x-2) * -(-x-y)
        Node tree = uncubic();

        // All calculations are BigInteger
        evaluator.variables.put("x", BIG_3);
        evaluator.variables.put("y", BIG_3);
        assertThat(tree.accept(evaluator), is(BIG_42));

        // (-x)-y promotes Byte to BigInteger
        resetFallbackCalls();
        evaluator.variables.put("y", (byte)3);
        Object result = tree.accept(evaluator);
        assertThat(result, is(BIG_42));
        assertThat(result, is(instanceOf(BigInteger.class)));
        assertThat(BinOpCallSite.fallbackCalls, is(1));
        assertThat(UnaryOpCallSite.fallbackCalls, is(0));

        // (-x)-y promotes Short to BigInteger
        resetFallbackCalls();
        evaluator.variables.put("y", (short)3);
        result = tree.accept(evaluator);
        assertThat(result, is(BIG_42));
        assertThat(result, is(instanceOf(BigInteger.class)));
        assertThat(BinOpCallSite.fallbackCalls, is(1));
        assertThat(UnaryOpCallSite.fallbackCalls, is(0));

        // x*x promotes Long to BigInteger (but +, - etc do not)
        resetFallbackCalls();
        evaluator.variables.put("x", 100000002L);
        evaluator.variables.put("y", 100000019L);
        BigInteger expected = new BigInteger("2000000290000008800000042");
        result = tree.accept(evaluator);
        assertThat(result, is(expected));
        assertThat(BinOpCallSite.fallbackCalls, is(4));
        assertThat(UnaryOpCallSite.fallbackCalls, is(2));
    }

    @Test
    public void stringAdd() {
        Node tree = BinOp(Name("x", Load), Add, Name("y", Load));
        evaluator.variables.put("x", "Hello");
        evaluator.variables.put("y", " world!");
        assertThat(tree.accept(evaluator), is("Hello world!"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void stringSubException() {
        Node tree = BinOp(Name("x", Load), Sub, Name("y", Load));
        evaluator.variables.put("x", "Hello");
        evaluator.variables.put("y", " world!");
        tree.accept(evaluator);
    }

    @Test(expected = IllegalArgumentException.class)
    public void stringNegException() {
        Node tree = UnaryOp(USub, Name("x", Load));
        evaluator.variables.put("x", "Hello");
        tree.accept(evaluator);
    }

    /**
     * An interpreter for Python that works by walking the AST, and uses
     * look-up to find the type object corresponding to the Java native
     * type.
     */
    static class Evaluator implements Visitor<Object> {

        Map<String, Object> variables = new HashMap<>();
        Lookup lookup = lookup();

        @Override
        public Object visit_BinOp(expr.BinOp binOp) {
            // Evaluate sub-trees
            Object v = binOp.left.accept(this);
            Object w = binOp.right.accept(this);
            // Evaluate the node
            try {
                if (binOp.site == null) {
                    // This must be a first visit
                    binOp.site = Runtime.bootstrap(lookup, binOp);
                }
                MethodHandle mh = binOp.site.dynamicInvoker();
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
        public Object visit_Constant(expr.Constant constant) {
            return constant.value;
        }

        @Override
        public Object visit_Name(expr.Name name) {
            return variables.get(name.id);
        }

        @Override
        public Object visit_UnaryOp(expr.UnaryOp unaryOp) {
            // Evaluate sub-tree
            Object v = unaryOp.operand.accept(this);
            // Evaluate the node
            try {
                if (unaryOp.site == null) {
                    // This must be a first visit
                    unaryOp.site = Runtime.bootstrap(lookup, unaryOp);
                }
                MethodHandle mh = unaryOp.site.dynamicInvoker();
                return mh.invokeExact(v);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Implementation returned NotImplemented or equivalent
                throw notDefined(v, unaryOp.op);
            } catch (Throwable e) {
                // Something else went wrong
                e.printStackTrace();
                return null;
            }
        }

        /** Binary operation: create an IllegalArgumentException. */
        private static IllegalArgumentException notDefined(Object v,
                operator op, Object w) {
            String msg =
                    "unsupported operand type(s) for %s : '%s' and '%s'";
            String s = BinOpInfo.forOp(op).symbol;
            String V = v.getClass().getSimpleName();
            String W = w.getClass().getSimpleName();
            return new IllegalArgumentException(
                    String.format(msg, s, V, W));
        }

        /** Unary operation: create an IllegalArgumentException. */
        private static IllegalArgumentException notDefined(Object v,
                unaryop op) {
            String msg = "bad operand type for unary %s : '%s'";
            String s = UnaryOpInfo.forOp(op).symbol;
            String V = v.getClass().getSimpleName();
            return new IllegalArgumentException(String.format(msg, s, V));
        }
    }

    static class BinOpCallSite extends MutableCallSite {

        final operator op;
        final Lookup lookup;
        final MethodHandle fallbackMH;

        static int fallbackCalls = 0;

        public BinOpCallSite(Lookup lookup, operator op)
                throws NoSuchMethodException, IllegalAccessException {
            super(Runtime.BINOP);
            this.op = op;
            this.lookup = lookup;
            fallbackMH = lookup().bind(this, "fallback", Runtime.BINOP);
            setTarget(fallbackMH);
        }

        @SuppressWarnings("unused")
        private Object fallback(Object v, Object w) throws Throwable {
            fallbackCalls += 1;
            Class<?> V = v.getClass();
            Class<?> W = w.getClass();
            MethodType mt = MethodType.methodType(Object.class, V, W);
            // MH to compute the result for these classes
            MethodHandle resultMH = Runtime.findBinOp(V, op, W);
            // MH for guarded invocation (becomes new target)
            MethodHandle guarded = makeGuarded(V, W, resultMH, fallbackMH);
            setTarget(guarded);
            // Compute the result for this case
            return resultMH.invokeExact(v, w);
        }

        /**
         * Adapt two method handles, one that computes the desired result
         * specialised to the given classes, and a fall-back appropriate
         * when the arguments (when the handle is invoked) are not the
         * given types.
         *
         * @param V Java class of left argument of operation
         * @param W Java class of right argument of operation
         * @param resultMH computes v op w, where v&in;V and w&in;W.
         * @param fallbackMH computes the result otherwise
         * @return method handle computing the result either way
         */
        private MethodHandle makeGuarded(Class<?> V, Class<?> W,
                MethodHandle resultMH, MethodHandle fallbackMH) {
            MethodHandle testV, testW, guardedForW, guarded;
            testV = Runtime.HAS_CLASS.bindTo(V);
            testW = Runtime.HAS_CLASS.bindTo(W);
            testW = dropArguments(testW, 0, Object.class);
            guardedForW = guardWithTest(testW, resultMH, fallbackMH);
            guarded = guardWithTest(testV, guardedForW, fallbackMH);
            return guarded;
        }
    }

    static class UnaryOpCallSite extends MutableCallSite {

        final unaryop op;
        final Lookup lookup;
        final MethodHandle fallbackMH;

        static int fallbackCalls = 0;

        public UnaryOpCallSite(Lookup lookup, unaryop op)
                throws NoSuchMethodException, IllegalAccessException {
            super(Runtime.UOP);
            this.op = op;
            this.lookup = lookup;
            fallbackMH = lookup().bind(this, "fallback", Runtime.UOP);
            setTarget(fallbackMH);
        }

        @SuppressWarnings("unused")
        private Object fallback(Object v) throws Throwable {
            fallbackCalls += 1;
            Class<?> V = v.getClass();
            MethodType mt = MethodType.methodType(Object.class, V);
            // MH to compute the result for this class
            MethodHandle resultMH = Runtime.findUnaryOp(op, V);
            // MH for guarded invocation (becomes new target)
            MethodHandle testV = Runtime.HAS_CLASS.bindTo(V);
            setTarget(guardWithTest(testV, resultMH, fallbackMH));
            // Compute the result for this case
            return resultMH.invokeExact(v);
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
        /** Handle of a method returning NotImplemented (1 arg). */
        static final MethodHandle UOP_NOT_IMPLEMENTED;
        /** A (static) method implementing a binary op has this type. */
        protected static final MethodType BINOP;
        /** Handle of a method returning NotImplemented (2 args). */
        static final MethodHandle BINOP_NOT_IMPLEMENTED;
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
            UOP = MethodType.methodType(O, O);
            UOP_NOT_IMPLEMENTED =
                    findStatic(Runtime.class, "notImplemented", UOP);
            BINOP = MethodType.methodType(O, O, O);
            BINOP_NOT_IMPLEMENTED =
                    findStatic(Runtime.class, "notImplemented", BINOP);
            IS_NOT_IMPLEMENTED =
                    findStatic(Runtime.class, "isNotImplemented",
                            MethodType.methodType(boolean.class, O));
            THROW_IF_NOT_IMPLEMENTED = findStatic(Runtime.class,
                    "throwIfNotImplemented", UOP);
            GET_CLASS =
                    findVirtual(O, "getClass", MethodType.methodType(C));
            CLASS_EQUALS = findVirtual(C, "equals",
                    MethodType.methodType(boolean.class, O));
            // HAS_CLASS = filterArguments(CLASS_EQUALS, 0, GET_CLASS);
            HAS_CLASS = findStatic(Runtime.class, "classEquals",
                    MethodType.methodType(boolean.class, C, O));
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
         * Unary operation throwing NoSuchMethodError, use as dummy.
         *
         * @throws NoSuchMethodException
         */
        static Object notImplemented(Object v)
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

        @SuppressWarnings("unused") // referenced as HAS_CLASS
        private static boolean classEquals(Class<?> clazz, Object obj) {
            return clazz == obj.getClass();
        }

        /**
         * Convenience function wrapping
         * {@link Lookup#findStatic(Class, String, MethodType)}, throwing
         * {@code RuntimeException} if the method cannot be found, an
         * unchecked exception, wrapping the real cause.
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
         * {@code RuntimeException} if the method cannot be found, an
         * unchecked exception, wrapping the real cause.
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
        private static RuntimeException lookupRTE(Class<?> refc,
                String name, MethodType type, boolean isVirtual,
                Throwable t) {
            final String modeString = isVirtual ? "virtual" : "static";
            String fmt = "In runtime looking up %s %s#%s with type %s";
            String msg = String.format(fmt, modeString,
                    refc.getSimpleName(), name, type);
            return new RuntimeException(msg, t);
        }

        /**
         * Bootstrap a simulated invokedynamic call site for a unary
         * operation.
         *
         * @throws IllegalAccessException
         * @throws NoSuchMethodException
         */
        static CallSite bootstrap(Lookup lookup, expr.UnaryOp unaryOp)
                throws NoSuchMethodException, IllegalAccessException {
            return new UnaryOpCallSite(lookup, unaryOp.op);
        }

        /**
         * Provide (as a method handle) an appropriate implementation of
         * the given operation, on a a target Java type.
         *
         * @param op operator to apply
         * @param vClass Java class of operand
         * @return MH representing the operation
         * @throws NoSuchMethodException
         * @throws IllegalAccessException
         */
        static MethodHandle findUnaryOp(unaryop op, Class<?> vClass)
                throws NoSuchMethodException, IllegalAccessException {
            TypeHandler V = Runtime.typeFor(vClass);
            MethodHandle mhV = V.findUnaryOp(op, vClass);
            return mhV;
        }

        /**
         * Bootstrap a simulated invokedynamic call site for a binary
         * operation.
         *
         * @throws IllegalAccessException
         * @throws NoSuchMethodException
         */
        static CallSite bootstrap(Lookup lookup, expr.BinOp binOp)
                throws NoSuchMethodException, IllegalAccessException {
            return new BinOpCallSite(lookup, binOp.op);
        }

        /**
         * Provide (as a method handle) an appropriate implementation of
         * the given operation, between operands of two Java types,
         * conforming to Python delegation rules.
         *
         * @param vClass Java class of left operand
         * @param op operator to apply
         * @param wClass Java class of right operand
         * @return MH representing the operation
         * @throws NoSuchMethodException
         * @throws IllegalAccessException
         */
        static MethodHandle findBinOp(Class<?> vClass, operator op,
                Class<?> wClass)
                throws NoSuchMethodException, IllegalAccessException {
            TypeHandler V = Runtime.typeFor(vClass);
            TypeHandler W = Runtime.typeFor(wClass);
            MethodHandle mhV = V.findBinOp(vClass, op, wClass);
            if (W == V) {
                return mhV;
            }
            MethodHandle mhW = W.findBinOp(vClass, op, wClass);
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
            MethodHandle apply_b = filterReturnValue(
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

    /** Mapping from unary operation to function names. */
    enum UnaryOpInfo {

        POS(unaryop.UAdd, "+", "pos"),

        NEG(unaryop.USub, "-", "neg");

        /** Unary operation represented e.g. USub for NEG. */
        public final unaryop op;
        /** Symbol for the operation e.g. "-" for NEG. */
        public final String symbol;
        /** Function name implementing the operation e.g. "neg". */
        public final String name;

        private UnaryOpInfo(unaryop op, String symbol, String name) {
            this.op = op;
            this.symbol = symbol;
            this.name = name;
            // Must have same ordinal value.
            assert this.ordinal() == op.ordinal();
        }

        /** Get the information for the given {@link unaryop}. */
        static final UnaryOpInfo forOp(unaryop op) {
            return UnaryOpInfo.values()[op.ordinal()];
        }
    };

    /** Mapping from binary operation to function names. */
    enum BinOpInfo {

        ADD(operator.Add, "+", "add"),

        SUB(operator.Sub, "-", "sub"),

        MUL(operator.Mult, "*", "mul"),

        DIV(operator.Div, "/", "div");

        /** Binary operation represented e.g. MULT for MUL. */
        public final operator op;
        /** Symbol for the operation e.g. "*" for MUL. */
        public final String symbol;
        /** Function name implementing the operation e.g. "mul". */
        public final String name;

        private BinOpInfo(operator op, String symbol, String name) {
            this.op = op;
            this.symbol = symbol;
            this.name = name;
            // Must have same ordinal value.
            assert this.ordinal() == op.ordinal();
        }

        /** Get the information for the given {@link operator}. */
        static final BinOpInfo forOp(operator op) {
            return BinOpInfo.values()[op.ordinal()];
        }
    };

    /**
     * <code>TypeHandler</code> is the base class of the classes that
     * collect together the operations on each Python type. These
     * subclasses should be singletons.
     */
    static abstract class TypeHandler {

        /** A method implementing a unary operation has this type. */
        protected static final MethodType UOP = Runtime.UOP;
        /** A method implementing a binary operation has this type. */
        protected static final MethodType BINOP = Runtime.BINOP;
        /** Shorthand for <code>Object.class</code>. */
        static final Class<Object> O = Object.class;

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
        protected MethodHandle findStaticOrNull(Class<?> refc, String name,
                MethodType type) {
            try {
                MethodHandle mh = lookup.findStatic(refc, name, type);
                return mh;
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return null;
            }
        }

        /**
         * Return the method handle of the implementation of
         * <code>op v</code>, where <code>v</code> is an object of this
         * handler's type.
         *
         * @param op the binary operation to find
         * @param vClass Java class of operand
         * @return
         */
        public MethodHandle findUnaryOp(unaryop op, Class<?> vClass) {
            String name = UnaryOpInfo.forOp(op).name;
            Class<?> here = this.getClass();

            // Look for a match with the operand class
            MethodType mt = MethodType.methodType(O, vClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            if (mh == null) {
                return Runtime.UOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(UOP);
            }
        }

        /**
         * Return the method handle of the implementation of
         * <code>v op w</code>, if one exists within this handler.
         *
         * @param vClass Java class of left operand
         * @param op operator to apply
         * @param wClass Java class of right operand
         * @return
         */
        public MethodHandle findBinOp(Class<?> vClass, operator op,
                Class<?> wClass) {
            String name = BinOpInfo.forOp(op).name;
            Class<?> here = this.getClass();

            // Look for an exact match with the actual types
            MethodType mt = MethodType.methodType(O, vClass, wClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            if (mh == null) {
                return Runtime.BINOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(BINOP);
            }
        }

        /** The lookup rights object of the implementing class. */
        private final Lookup lookup;

        protected TypeHandler(Lookup lookup) {
            this.lookup = lookup;
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
     * <code>MixedNumberHandler</code> is the base class of handlers for
     * types that implement <code>Number</code>.
     */
    static abstract class MixedNumberHandler extends TypeHandler {

        /** Shorthand for <code>Number.class</code>. */
        static final Class<Number> N = Number.class;

        protected static final MethodType UOP_N =
                MethodType.methodType(O, N);
        protected static final MethodType BINOP_NN =
                MethodType.methodType(O, N, N);

        protected MixedNumberHandler(Lookup lookup) {
            super(lookup);
        }

        /** Test that the actual class of an operand is acceptable. */
        abstract protected boolean acceptable(Class<?> oClass);

        @Override
        public MethodHandle findUnaryOp(unaryop op, Class<?> vClass) {
            String name = UnaryOpInfo.forOp(op).name;
            Class<?> here = this.getClass();

            // Look for a match with the operand class
            MethodType mt = MethodType.methodType(O, vClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            if (mh == null && acceptable(vClass)) {
                // Look for a match with (Number)
                mh = findStaticOrNull(here, name, UOP_N);
            }

            if (mh == null) {
                return Runtime.UOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(UOP);
            }
        }

        @Override
        public MethodHandle findBinOp(Class<?> vClass, operator op,
                Class<?> wClass) {
            String name = BinOpInfo.forOp(op).name;
            Class<?> here = this.getClass();

            // Look for an exact match with the actual types
            MethodType mt = MethodType.methodType(O, vClass, wClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            if (mh == null) {
                if (acceptable(wClass)) {
                    // Look for a match with (vClass, Number)
                    mt = MethodType.methodType(O, vClass, N);
                    mh = findStaticOrNull(here, name, mt);
                    if (mh == null && acceptable(wClass)) {
                        // Look for a match with (Number, Number)
                        mh = findStaticOrNull(here, name, BINOP_NN);
                    }
                } else if (acceptable(vClass)) {
                    // Look for a match with (Number, wClass)
                    mt = MethodType.methodType(O, N, wClass);
                    mh = findStaticOrNull(here, name, mt);
                }
            }

            if (mh == null) {
                return Runtime.BINOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(BINOP);
            }
        }
    }

    /**
     * Class defining the operations for a Java <code>BigInteger</code>, so
     * as to make it a Python <code>int</code>.
     */
    @SuppressWarnings(value = {"unused"})
    static class BigIntegerHandler extends MixedNumberHandler {

        // @formatter:off
        BigIntegerHandler() { super(lookup()); }

        private static Object add(BigInteger v, BigInteger w)
            { return v.add(w); }
        private static Object sub(BigInteger v, BigInteger w)
            { return v.subtract(w); }
        private static Object mul(BigInteger v, BigInteger w)
            { return v.multiply(w); }
        // Delegate to div(Number, Number): same for all types
        private static Object div(Number v, Number w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object neg(BigInteger v) { return v.negate(); }
        // Delegate to pos(Number) as just returning self
        private static Object pos(Number v) { return v; }

        // Accept any integer as w by widening to BigInteger
        private static Object add(BigInteger v, Number w)
            { return v.add(BigInteger.valueOf(w.longValue())); }
        private static Object sub(BigInteger v, Number w)
            { return v.subtract(BigInteger.valueOf(w.longValue())); }
        private static Object mul(BigInteger v, Number w)
            { return v.multiply(BigInteger.valueOf(w.longValue())); }

        // Accept any integer as v by widening to BigInteger
        private static Object add(Number v, BigInteger w)
            { return BigInteger.valueOf(v.longValue()).add(w); }
        private static Object sub(Number v, BigInteger w)
            { return BigInteger.valueOf(v.longValue()).subtract(w); }
        private static Object mul(Number v, BigInteger w)
            { return BigInteger.valueOf(v.longValue()).multiply(w); }

        // Accept any integers as v, w by widening to BigInteger
        private static Object add(Number v, Number w) {
            return BigInteger.valueOf(v.longValue())
                    .add(BigInteger.valueOf(w.longValue()));
        }
        private static Object sub(Number v, Number w) {
            return BigInteger.valueOf(v.longValue())
                    .subtract(BigInteger.valueOf(w.longValue()));
        }
        private static Object mul(Number v, Number w) {
            return BigInteger.valueOf(v.longValue())
                    .multiply(BigInteger.valueOf(w.longValue()));
        }

        private static Object neg(Number v) {
            return BigInteger.valueOf(v.longValue()).negate();
        }
        // @formatter:on

        @Override
        protected boolean acceptable(Class<?> oClass) {
            return oClass == Byte.class || oClass == Short.class
                    || oClass == Integer.class || oClass == Long.class;
        }
    }

    /**
     * Operations handler applicable to Java integers up to
     * <code>Long</code>, so as to make each a Python <code>int</code>.
     */
    @SuppressWarnings(value = {"unused"})
    static class LongHandler extends MixedNumberHandler {

        private static final long BIT63 = 0x8000_0000_0000_0000L;
        private static final BigInteger BIG_2_63 =
                BigInteger.valueOf(BIT63).negate();
        private static BigInteger BIG_2_64 = BIG_2_63.shiftLeft(1);

        LongHandler() {
            super(lookup());
        }

        private static Object add(Long v, Long w) {
            return long_add(v, w);
        }

        private static Object sub(Long v, Long w) {
            return long_sub(v, w);
        }

        private static Object mul(Long v, Long w) {
            return long_mul(v, w);
        }

        private static Object add(Long v, Number w) {
            return long_add(v, w.longValue());
        }

        private static Object sub(Long v, Number w) {
            return long_sub(v, w.longValue());
        }

        private static Object mul(Long v, Number w) {
            return long_mul(v, w.longValue());
        }

        private static Object add(Number v, Long w) {
            return long_add(v.longValue(), w);
        }

        private static Object sub(Number v, Long w) {
            return long_sub(v.longValue(), w);
        }

        private static Object mul(Number v, Long w) {
            return long_mul(v.longValue(), w);
        }

        private static Object add(Number v, Number w) {
            return long_add(v.longValue(), w.longValue());
        }

        private static Object sub(Number v, Number w) {
            return long_sub(v.longValue(), w.longValue());
        }

        private static Object mul(Number v, Number w) {
            return long_mul(v.longValue(), w.longValue());
        }

        private static Object div(Number v, Number w) {
            return v.doubleValue() / w.doubleValue();
        }

        private static Object neg(Long v) {
            long lv = v;
            return lv != Long.MIN_VALUE ? -lv : BIG_2_63;
        }

        private static Object pos(Long v) {
            return v;
        }

        private static Object pos(Number v) {
            return v;
        }

        @Override
        protected boolean acceptable(Class<?> oClass) {
            return oClass == Byte.class || oClass == Short.class
                    || oClass == Integer.class || oClass == Long.class;
        }

        private static Object long_add(long v, long w) {
            // Compute naive result
            long r = v + w;
            // Detect potential carry into bit 64 by examining sign bits
            if (((v ^ w) & BIT63) != 0L) {
                // Signs were opposite: result must be in range of long
                return r;
            } else if (((v ^ r) & BIT63) == 0L) {
                // Sign of result is same as sign of (both) operands
                return r;
            } else if ((r & BIT63) != 0L) {
                // r is incorrect (negative) by 2**64
                return BigInteger.valueOf(r).add(BIG_2_64);
            } else {
                // r is incorrect (positive) by 2**64
                return BigInteger.valueOf(r).subtract(BIG_2_64);
            }
        }

        private static Object long_sub(long v, long w) {
            // Compute naive result
            long r = v - w;
            // Detect potential carry into bit 64 by examining sign bits
            if (((v ^ w) & BIT63) == 0L) {
                // Signs were the same: result must be in range of long
                return r;
            } else if (((v ^ r) & BIT63) == 0L) {
                // Sign of result is same as first operand: lr is correct
                return r;
            } else if ((r & BIT63) != 0L) {
                // r is incorrect (negative) by 2**64
                return BigInteger.valueOf(r).add(BIG_2_64);
            } else {
                // r is incorrect (positive) by 2**64
                return BigInteger.valueOf(r).subtract(BIG_2_64);
            }
        }

        private static Object long_mul(long v, long w) {
            if (v == 0L || w == 0L) {
                return 0;
            } else {
                // |v| < 2**(64-zv) (even if v=Long.MIN_VALUE)
                int zv = Long.numberOfLeadingZeros(Math.abs(v) - 1L);
                int zw = Long.numberOfLeadingZeros(Math.abs(w) - 1L);
                if (zv + zw >= 65) {
                    // |v||w| < 2**(128-(zv+zw)) <= 2**63 -> Long
                    return v * w;
                } else {
                    return BigInteger.valueOf(v)
                            .multiply(BigInteger.valueOf(w));
                }
            }
        }
    }

    /**
     * Class defining the operations for a Java <code>Integer</code>, so as
     * to make it a Python <code>int</code>.
     */
    @SuppressWarnings(value = {"unused"})
    static class IntegerHandler extends MixedNumberHandler {

        // @formatter:off

        IntegerHandler() { super(lookup()); }

        private static Object add(Integer v, Integer w)
            { return result( (long)v + (long)w); }
        private static Object sub(Integer v, Integer w)
            { return result( (long)v - (long)w); }
        private static Object mul(Integer v, Integer w)
            { return result( (long)v * (long)w); }
        private static Object div(Integer v, Integer w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object neg(Integer v) { return result(-(long)v); }
        private static Object pos(Integer v) { return v; }

        private static Object add(Integer v, Number w)
            { return result( v + w.longValue()); }
        private static Object sub(Integer v, Number w)
            { return result( v - w.longValue()); }
        private static Object mul(Integer v, Number w)
            { return result( v * w.longValue()); }
        private static Object div(Integer v, Number w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object add(Number v, Integer w)
            { return result( v.longValue() + w); }
        private static Object sub(Number v, Integer w)
            { return result( v.longValue() - w); }
        private static Object mul(Number v, Integer w)
            { return result( v.longValue() * w); }
        private static Object div(Number v, Integer w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object add(Number v, Number w)
            { return v.intValue() + w.intValue(); }
        private static Object sub(Number v, Number w)
            { return v.intValue() - w.intValue(); }
        private static Object mul(Number v, Number w)
            { return v.intValue() * w.intValue(); }
        private static Object div(Number v, Number w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object neg(Number v) { return -v.intValue(); }
        private static Object pos(Number v) { return v; }

        // @formatter:on

        @Override
        protected boolean acceptable(Class<?> oClass) {
            return oClass == Byte.class || oClass == Short.class;
        }

        private static final long BIT31 = 0x8000_0000L;
        private static final long HIGHMASK = 0xFFFF_FFFF_0000_0000L;

        private static final Object result(long r) {
            // 0b0...0_0rrr_rrrr_rrrr_rrrr -> Positive Integer
            // 0b1...1_1rrr_rrrr_rrrr_rrrr -> Negative Integer
            // Anything else -> Long
            if (((r + BIT31) & HIGHMASK) == 0L) {
                return Integer.valueOf((int)r);
            } else {
                return Long.valueOf(r);
            }
        }
    }

    /**
     * Class defining the operations for a Java <code>Double</code>, so as
     * to make it a Python <code>float</code>.
     */
    @SuppressWarnings(value = {"unused"})
    static class DoubleHandler extends MixedNumberHandler {

        // @formatter:off
        DoubleHandler() { super(lookup()); }

        private static Object add(Double v, Double w)  { return v+w; }
        private static Object sub(Double v, Double w)  { return v-w; }
        private static Object mul(Double v, Double w)  { return v*w; }
        private static Object div(Double v, Double w)  { return v/w; }

        private static Object neg(Double v) { return -v; }
        private static Object pos(Double v) { return v; }

        private static Object add(Double v, Number w)
            { return v + w.doubleValue(); }
        private static Object sub(Double v, Number w)
            { return v - w.doubleValue(); }
        private static Object mul(Double v, Number w)
            { return v * w.doubleValue(); }
        private static Object div(Double v, Number w)
            { return v / w.doubleValue(); }

        private static Object add(Number v, Double w)
            { return v.doubleValue() + w; }
        private static Object sub(Number v, Double w)
            { return v.doubleValue() - w; }
        private static Object mul(Number v, Double w)
            { return v.doubleValue() * w; }
        private static Object div(Number v, Double w)
            { return v.doubleValue() / w; }

        // Accept any Number types by widening to double
        private static Object add(Number v, Number w)
            { return v.doubleValue() + w.doubleValue(); }
        private static Object sub(Number v, Number w)
            { return v.doubleValue() - w.doubleValue(); }
        private static Object mul(Number v, Number w)
            { return v.doubleValue() * w.doubleValue(); }
        private static Object div(Number v, Number w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object neg(Number v) { return -v.doubleValue(); }
        private static Object pos(Number v) { return v; }

        // @formatter:on

        @Override
        protected boolean acceptable(Class<?> oClass) {
            return oClass == Byte.class || oClass == Short.class
                    || oClass == Integer.class || oClass == Long.class
                    || oClass == BigInteger.class || oClass == Float.class;
        }
    }

    /**
     * Class defining the operations for a Java <code>String</code>, so as
     * to make it (almost entirely, but not quite, totally unlike) a Python
     * <code>str</code>.
     */
    @SuppressWarnings(value = {"unused"})
    private static class StringHandler extends TypeHandler {

        protected StringHandler() {
            super(lookup());
        }

        private static Object add(String v, String w) {
            return v.concat(w);
        }
    }

    // @formatter:off
    public static final operator Add = operator.Add;
    public static final operator Sub = operator.Sub;
    public static final operator Mult = operator.Mult;
    public static final operator Div = operator.Div;
    public static final unaryop UAdd = unaryop.UAdd;
    public static final unaryop USub = unaryop.USub;
    public static final expr_context Load = expr_context.Load;
    public static final expr Name(String id, expr_context ctx)
        { return new expr.Name(id, ctx); }
    public static final expr Constant(Object value, String kind)
        { return new expr.Constant(value, kind); }
    public static final expr BinOp(expr left, operator op, expr right)
        { return new expr.BinOp(left, op, right); }
    public static final expr UnaryOp(unaryop op, expr operand)
        { return new expr.UnaryOp(op, operand); }
    // @formatter:on

}
