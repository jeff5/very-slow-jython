package uk.co.farowl.vsj1.example.treepython;

import static java.lang.invoke.MethodHandles.lookup;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.co.farowl.vsj1.BigIntegerOperations;
import uk.co.farowl.vsj1.BinOpCallSite;
import uk.co.farowl.vsj1.DoubleOperations;
import uk.co.farowl.vsj1.IntegerOperations;
import uk.co.farowl.vsj1.LongOperations;
import uk.co.farowl.vsj1.Operations;
import uk.co.farowl.vsj1.Operations.BinOpInfo;
import uk.co.farowl.vsj1.Operations.UnaryOpInfo;
import uk.co.farowl.vsj1.Py;
import uk.co.farowl.vsj1.TreePython.Node;
import uk.co.farowl.vsj1.TreePython.Visitor;
import uk.co.farowl.vsj1.TreePython.expr;
import uk.co.farowl.vsj1.TreePython.expr_context;
import uk.co.farowl.vsj1.TreePython.operator;
import uk.co.farowl.vsj1.TreePython.unaryop;
import uk.co.farowl.vsj1.UnaryOpCallSite;

/**
 * <code>TestEx9</code> reproduces {@link TestEx8}, the high point for now
 * of our adventures in expression evaluation, but with the supporting
 * classes moved out to the vsj1 core, considered no longer experimental,
 * at least in the features necessary to support this program.
 */
public class TestEx9 {

    @BeforeClass
    public static void setUpClass() {
        // Built-in types
        Py.registerOps(new IntegerOperations(), Byte.class, Short.class,
                Integer.class);
        Py.registerOps(new LongOperations(), Long.class);
        Py.registerOps(new BigIntegerOperations(), BigInteger.class);
        Py.registerOps(new DoubleOperations(), Float.class, Double.class);
        Py.registerOps(new StringOperations(), String.class);
    }

    @AfterClass
    public static void tearDownClass() {
        Py.deregisterOps();
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

    private static int unaryFallbackCalls() {
        return UnaryOpCallSite.fallbackCalls;
    }

    private static int binaryFallbackCalls() {
        return BinOpCallSite.fallbackCalls;
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

    private Node uncubic() {
        // Build the expression tree (unlinked)
        // @formatter:off
        // (x*x-2) * -(-x-y)
        Node tree =
                BinOp(
                    BinOp(
                        BinOp(Name("x", Load), Mult, Name("x", Load)),
                        Sub,
                        Num(2)),
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
        assertThat(unaryFallbackCalls(), is(0));
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
        assertThat(unaryFallbackCalls(), is(0));
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
        assertThat(binaryFallbackCalls(), is(1));
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
        assertThat(binaryFallbackCalls(), is(0));
        assertThat(unaryFallbackCalls(), is(0));
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
        assertThat(binaryFallbackCalls(), is(2));
        assertThat(unaryFallbackCalls(), is(1));

        // x*x, -x and (-x)-y and promote Short to Integer
        resetFallbackCalls();
        evaluator.variables.put("x", (short)2);
        evaluator.variables.put("y", (short)19);
        result = tree.accept(evaluator);
        assertThat(result, is(42));
        assertThat(result, is(instanceOf(Integer.class)));
        assertThat(binaryFallbackCalls(), is(2));
        assertThat(unaryFallbackCalls(), is(1));
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
        assertThat(binaryFallbackCalls(), is(1));
        assertThat(unaryFallbackCalls(), is(0));

        // (-x)-y promotes Short to BigInteger
        resetFallbackCalls();
        evaluator.variables.put("y", (short)3);
        result = tree.accept(evaluator);
        assertThat(result, is(BIG_42));
        assertThat(result, is(instanceOf(BigInteger.class)));
        assertThat(binaryFallbackCalls(), is(1));
        assertThat(unaryFallbackCalls(), is(0));

        // x*x promotes Long to BigInteger (but +, - etc do not)
        resetFallbackCalls();
        evaluator.variables.put("x", 100000002L);
        evaluator.variables.put("y", 100000019L);
        BigInteger expected = new BigInteger("2000000290000008800000042");
        result = tree.accept(evaluator);
        assertThat(result, is(expected));
        assertThat(binaryFallbackCalls(), is(4));
        assertThat(unaryFallbackCalls(), is(2));
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
                    binOp.site = Py.bootstrap(lookup, binOp);
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
        public Object visit_Num(expr.Num num) {
            return num.n;
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
                    unaryOp.site = Py.bootstrap(lookup, unaryOp);
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

    /**
     * Class defining the operations for a Java <code>String</code>, so as
     * to make it (almost entirely, but not quite, totally unlike) a Python
     * <code>str</code>.
     */
    @SuppressWarnings(value = {"unused"})
    private static class StringOperations extends Operations {

        protected StringOperations() {
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
    public static final expr Num(Object n) { return new expr.Num(n); }
    public static final expr BinOp(expr left, operator op, expr right)
        { return new expr.BinOp(left, op, right); }
    public static final expr UnaryOp(unaryop op, expr operand)
        { return new expr.UnaryOp(op, operand); }
    // @formatter:on

}
