package uk.co.farowl.vsj1.example;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import uk.co.farowl.vsj1.example.TreePythonEx1.Node;
import uk.co.farowl.vsj1.example.TreePythonEx1.Visitor;
import uk.co.farowl.vsj1.example.TreePythonEx1.expr;
import uk.co.farowl.vsj1.example.TreePythonEx1.expr_context;
import uk.co.farowl.vsj1.example.TreePythonEx1.operator;

/** Demonstrate a Python interpreter for the AST. */
@SuppressWarnings("javadoc") // Water under the bridge
public class TestEx1 {

    // Visitor to execute the code.
    Evaluator evaluator;

    @Before
    public void setUp() {
        // Create a visitor to execute the code.
        evaluator = new Evaluator();
    }

    @Test
    public void astExec() {
        // x + 1
        // @formatter:off
        Node tree = new expr.BinOp(
                new expr.Name("x", expr_context.Load),
                operator.Add,
                new expr.Constant(1, null));
        // @formatter:on

        // Execute the code for x = 41
        evaluator.variables.put("x", 41);
        Object result = tree.accept(evaluator);
        assertEquals(42, result);
    }

    @Test
    public void astExecShorthand() {
        // x + 1
        Node tree = BinOp(Name("x", Load), Add, Constant(1, null));

        // Execute the code for x = 41
        evaluator.variables.put("x", 41);
        Object result = tree.accept(evaluator);
        assertEquals(42, result);
    }

    /** An interpreter for Python that works by walking the AST. */
    public static class Evaluator implements Visitor<Object> {

        Map<String, Object> variables = new HashMap<>();

        @Override
        public Object visit_BinOp(expr.BinOp binOp) {
            Integer u = (Integer)binOp.left.accept(this);
            Integer v = (Integer)binOp.right.accept(this);
            switch (binOp.op) {
                case Add:
                    return Integer.valueOf(u + v);
                default:
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
