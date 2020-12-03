package uk.co.farowl.vsj1.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
@SuppressWarnings("javadoc") // Water under the bridge
public class TestEx2 {

    // Visitor to execute the code.
    Evaluator evaluator;

    @BeforeEach
    public void setUp() {
        // Create a visitor to execute the code.
        evaluator = new Evaluator();
    }

    @Test
    public void testInt() {
        // 9 + x*11
        Node tree = BinOp(Constant(9, null), Add,
                BinOp(Name("x", Load), Mult, Constant(11, null)));
        // x = 3
        evaluator.variables.put("x", 3);
        // Execute the code.
        Object result = tree.accept(evaluator);
        assertEquals(42, result);
    }

    @Test
    public void testFloat() {
        // 9. + x*11.
        Node tree = BinOp(Constant(9, null), Add,
                BinOp(Name("x", Load), Mult, Constant(11, null)));
        // x = 3.
        evaluator.variables.put("x", 3.);
        // Execute the code.
        Object result = tree.accept(evaluator);
        assertEquals(42., result);
    }

    @Test
    public void testFloatNum() {
        // 9. + x*11
        Node tree = BinOp(Constant(9., null), Add,
                BinOp(Name("x", Load), Mult, Constant(11, null)));
        // x = 3
        evaluator.variables.put("x", 3);
        // Execute the code.
        Object result = tree.accept(evaluator);
        assertEquals(42., result);
    }

    @Test
    public void testFloatVar() {
        // 9 + x*11
        Node tree = BinOp(Constant(9, null), Add,
                BinOp(Name("x", Load), Mult, Constant(11, null)));
        // x = 3.
        evaluator.variables.put("x", 3.);
        // Execute the code.
        Object result = tree.accept(evaluator);
        assertEquals(42., result);
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
            Object r;

            switch (binOp.op) {
                case Add:
                    r = V.add(v, w);
                    if (r == null && W != V) {
                        // V doesn't handle these types. Try W.
                        r = W.add(v, w);
                    }
                    break;
                case Mult:
                    r = V.multiply(v, w);
                    if (r == null && W != V) {
                        // V doesn't handle these types. Try W.
                        r = W.multiply(v, w);
                    }
                    break;
                default:
                    r = null;
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
            typeRegistry.put(Integer.class, new IntegerHandler());
            typeRegistry.put(Double.class, new DoubleHandler());
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
     * subclasses should be singletons.(At present just a few of the binary
     * operations are defined.)
     */
    static abstract class TypeHandler {

        abstract Object add(Object v, Object w);

        abstract Object multiply(Object v, Object w);
    }

    /**
     * Class defining the operations for a Java <code>Integer</code>, so as
     * to make it a Python <code>int</code>.
     */
    static class IntegerHandler extends TypeHandler {

        @Override
        public Object add(Object vobj, Object wobj) {
            Class<?> cv = vobj.getClass();
            Class<?> cw = wobj.getClass();
            if (cv == Integer.class && cw == Integer.class) {
                return (Integer) vobj + (Integer) wobj;
            } else {
                return null;
            }
        }

        @Override
        public Object multiply(Object vobj, Object wobj) {
            Class<?> cv = vobj.getClass();
            Class<?> cw = wobj.getClass();
            if (cv == Integer.class && cw == Integer.class) {
                return (Integer) vobj * (Integer) wobj;
            } else {
                return null;
            }
        }
    }

    /**
     * Class defining the operations for a Java <code>Double</code>, so as
     * to make it a Python <code>float</code>.
     */
    static class DoubleHandler extends TypeHandler {

        private static double convertToDouble(Object o) {
            Class<?> c = o.getClass();
            if (c == Double.class) {
                return ((Double) o).doubleValue();
            } else if (c == Integer.class) {
                return (Integer) o;
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Object add(Object vObj, Object wObj) {
            try {
                double v = convertToDouble(vObj);
                double w = convertToDouble(wObj);
                return v + w;
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }

        @Override
        public Object multiply(Object vObj, Object wObj) {
            try {
                double v = convertToDouble(vObj);
                double w = convertToDouble(wObj);
                return v * w;
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }
    }

    // @formatter:off
    public static final operator Add = operator.Add;
    public static final operator Mult = operator.Mult;
    public static final expr_context Load = expr_context.Load;
    public static final expr Name(String id, expr_context ctx)
        { return new expr.Name(id, ctx); }
    public static final expr Constant(Object value, String kind)
        { return new expr.Constant(value, kind); }
    public static final expr BinOp(expr left, operator op, expr right)
        { return new expr.BinOp(left, op, right); }
    // @formatter:on
}
