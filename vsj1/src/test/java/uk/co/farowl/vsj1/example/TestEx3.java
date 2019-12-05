package uk.co.farowl.vsj1.example;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import uk.co.farowl.vsj1.example.TreePythonEx1.Node;
import uk.co.farowl.vsj1.example.TreePythonEx1.Visitor;
import uk.co.farowl.vsj1.example.TreePythonEx1.expr;
import uk.co.farowl.vsj1.example.TreePythonEx1.expr_context;
import uk.co.farowl.vsj1.example.TreePythonEx1.operator;

/**
 * Demonstrate the handling of mixed types (int and float) in the
 * interpretation of the AST. The implementation here simply keys off the
 * Java type to a delegate implementing Python operations for that Java
 * type.
 */
public class TestEx3 {

    // Visitor to execute the code.
    Evaluator evaluator;

    @Before
    public void setUp() {
        // Create a visitor to execute the code.
        evaluator = new Evaluator();
    }

    @Test
    public void testInt() {
        // @formatter:off
        // 24*x - x*10
        Node tree =
                BinOp(
                    BinOp(Constant(24, null), Mult, Name("x", Load)),
                    Sub,
                    BinOp(Name("x", Load), Mult, Constant(10, null)));
        // @formatter:on
        evaluator.variables.put("x", 3);
        // Execute the code.
        Object result = tree.accept(evaluator);
        assertThat(result, is(42));
    }

    @Test
    public void testFloat() {
        // @formatter:off
        // 24.*x - 90./x
        Node tree =
                BinOp(
                    BinOp(Constant(24., null), Mult, Name("x", Load)),
                    Sub,
                    BinOp(Constant(90., null), Div, Name("x", Load)));
        // @formatter:on
        evaluator.variables.put("x", 3.);
        // Execute the code.
        Object result = tree.accept(evaluator);
        assertThat(result, is(42.));
    }

    @Test
    public void testFloatNum() {
        // @formatter:off
        // 24.*x - 90/x
        Node tree =
                BinOp(
                    BinOp(Constant(24., null), Mult, Name("x", Load)),
                    Sub,
                    BinOp(Constant(90, null), Div, Name("x", Load)));
        // @formatter:on
        evaluator.variables.put("x", 3);
        // Execute the code.
        Object result = tree.accept(evaluator);
        assertThat(result, is(42.));
    }

    @Test
    public void testFloatVar() {
        // @formatter:off
        // 24*x - 90/x
        Node tree =
                BinOp(
                    BinOp(Constant(24, null), Mult, Name("x", Load)),
                    Sub,
                    BinOp(Constant(90, null), Div, Name("x", Load)));
        // @formatter:on
        evaluator.variables.put("x", 3.);
        // Execute the code.
        Object result = tree.accept(evaluator);
        assertThat(result, is(42.));
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
            Object v = binOp.left.accept(this);
            Object w = binOp.right.accept(this);
            TypeHandler V = Runtime.handler.get(v.getClass());
            TypeHandler W = Runtime.handler.get(w.getClass());
            Object r = null;
            // Omit the case W is a Python sub-type of V, for now.
            try {
                // Get the implementation for V=type(v).
                MethodHandle mh = V.getBinOp(binOp.op);
                if (mh != null) {
                    r = mh.invokeExact(v, w);
                    if (r == null) {
                        // V.op does not support a W right-hand
                        if (W != V) {
                            // Get implementation of for W=type(w).
                            mh = W.getBinOp(binOp.op);
                            // Arguments *not* reversed unlike __radd__
                            r = mh.invokeExact(v, w);
                        }
                    }
                }
            } catch (Throwable e) {
                // r == null
            }
            String msg = "Operation %s not defined between %s and %s";
            if (r == null) {
                throw new IllegalArgumentException(String.format(msg,
                        binOp.op, v.getClass().getName(),
                        w.getClass().getName()));
            }
            return r;
        }

        @Override
        public Object visit_Constant(expr.Constant constant) {
            return constant.value;
        }

        @Override
        public Object visit_Name(expr.Name name) {
            return variables.get(name.id);
        }
    }

    /** Runtime support for the interpreter. */
    static class Runtime {

        /** Support class mapping from Java classes to handlers. */
        private static final Map<Class<?>, TypeHandler> typeRegistry;
        static {
            // Create class mapping from Java classes to handlers.
            typeRegistry = new Hashtable<>();
            typeRegistry.put(Integer.class, IntegerHandler.getInstance());
            typeRegistry.put(Double.class, DoubleHandler.getInstance());
        }

        /** Look up <code>TypeHandler</code> for Java class. */
        public static final ClassValue<TypeHandler> handler =
                new ClassValue<TypeHandler>() {

                    @Override
                    protected synchronized TypeHandler
                            computeValue(Class<?> c) {
                        return typeRegistry.get(c);
                    }
                };
    }

    /**
     * <code>TypeHandler</code> is the base class of the classes that
     * collect together the operations on each Python type. These
     * subclasses should be singletons.
     */
    static abstract class TypeHandler {

        /**
         * A (static) method implementing a binary operation has this type.
         */
        protected static final MethodType MT_BINOP = MethodType
                .methodType(Object.class, Object.class, Object.class);
        /** Number of binary operations supported. */
        protected static final int N_BINOPS = operator.values().length;

        /**
         * Table of binary operations (equivalent of Python
         * <code>nb_</code> slots).
         */
        private MethodHandle[] binOp = new MethodHandle[N_BINOPS];

        /**
         * Look up the (handle of) the method for the given
         * <code>op</code>.
         */
        public MethodHandle getBinOp(operator op) {
            return binOp[op.ordinal()];
        }

        /**
         * Initialise the slots for binary operations in this
         * <code>TypeHandler</code>.
         */
        protected void fillBinOpSlots() {
            fillBinOpSlot(Add, "add");
            fillBinOpSlot(Sub, "sub");
            fillBinOpSlot(Mult, "mul");
            fillBinOpSlot(Div, "div");
        }

        /** The lookup rights object of the implementing class. */
        private final MethodHandles.Lookup lookup;

        protected TypeHandler(MethodHandles.Lookup lookup) {
            this.lookup = lookup;
        }

        /* Helper to fill one binary operation slot. */
        private void fillBinOpSlot(operator op, String name) {
            MethodHandle mh = null;
            try {
                mh = lookup.findStatic(getClass(), name, MT_BINOP);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Let it be null
            }
            binOp[op.ordinal()] = mh;
        };
    }

    /**
     * Singleton class defining the operations for a Java
     * <code>Integer</code>, so as to make it a Python <code>int</code>.
     */
    @SuppressWarnings(value = {"unused"})
    static class IntegerHandler extends TypeHandler {

        private static IntegerHandler instance;

        private IntegerHandler() {
            super(MethodHandles.lookup());
        }

        /** Get (create) the single instance of this class. */
        public static synchronized IntegerHandler getInstance() {
            if (instance == null) {
                instance = new IntegerHandler();
                instance.fillBinOpSlots();
            }
            return instance;
        }

        private static Object add(Object vObj, Object wObj) {
            Integer v = (Integer)vObj;
            Class<?> wclass = wObj.getClass();
            if (wclass == Integer.class) {
                return v + (Integer)wObj;
            } else {
                return null;
            }
        }

        private static Object sub(Object vObj, Object wObj) {
            Integer v = (Integer)vObj;
            Class<?> wclass = wObj.getClass();
            if (wclass == Integer.class) {
                return v - (Integer)wObj;
            } else {
                return null;
            }
        }

        private static Object mul(Object vObj, Object wObj) {
            Integer v = (Integer)vObj;
            Class<?> wclass = wObj.getClass();
            if (wclass == Integer.class) {
                return v * (Integer)wObj;
            } else {
                return null;
            }
        }

        private static Object div(Object vObj, Object wObj) {
            Integer v = (Integer)vObj;
            Class<?> wclass = wObj.getClass();
            if (wclass == Integer.class) {
                return ((double)v) / (Integer)wObj;
            } else {
                return null;
            }
        }
    }

    /**
     * Singleton class defining the operations for a Java
     * <code>Double</code>, so as to make it a Python <code>float</code>.
     */
    @SuppressWarnings(value = {"unused"})
    static class DoubleHandler extends TypeHandler {

        private static DoubleHandler instance;

        private DoubleHandler() {
            super(MethodHandles.lookup());
        }

        public static synchronized DoubleHandler getInstance() {
            if (instance == null) {
                instance = new DoubleHandler();
                instance.fillBinOpSlots();
            }
            return instance;
        }

        private static double convertToDouble(Object o) {
            Class<?> c = o.getClass();
            if (c == Double.class) {
                return ((Double)o).doubleValue();
            } else if (c == Integer.class) {
                return (Integer)o;
            } else {
                throw new IllegalArgumentException();
            }
        }

        private static Object add(Object vObj, Object wObj) {
            try {
                double v = convertToDouble(vObj);
                double w = convertToDouble(wObj);
                return v + w;
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }

        private static Object sub(Object vObj, Object wObj) {
            try {
                double v = convertToDouble(vObj);
                double w = convertToDouble(wObj);
                return v - w;
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }

        private static Object mul(Object vObj, Object wObj) {
            try {
                double v = convertToDouble(vObj);
                double w = convertToDouble(wObj);
                return v * w;
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }

        private static Object div(Object vObj, Object wObj) {
            try {
                double v = convertToDouble(vObj);
                double w = convertToDouble(wObj);
                return v / w;
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }
    }

    // @formatter:off
    public static final operator Add = operator.Add;
    public static final operator Sub = operator.Sub;
    public static final operator Mult = operator.Mult;
    public static final operator Div = operator.Div;
    public static final expr_context Load = expr_context.Load;
    public static final expr Name(String id, expr_context ctx)
        { return new expr.Name(id, ctx); }
    public static final expr Constant(Object value, String kind)
        { return new expr.Constant(value, kind); }
    public static final expr BinOp(expr left, operator op, expr right)
        { return new expr.BinOp(left, op, right); }
    // @formatter:on

}
