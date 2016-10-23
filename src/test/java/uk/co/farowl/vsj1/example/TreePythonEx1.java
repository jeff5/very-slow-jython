package uk.co.farowl.vsj1.example;

import static uk.co.farowl.vsj1.TreePython.Add;
import static uk.co.farowl.vsj1.TreePython.BinOp;
import static uk.co.farowl.vsj1.TreePython.Load;
import static uk.co.farowl.vsj1.TreePython.Name;
import static uk.co.farowl.vsj1.TreePython.Num;

import java.util.HashMap;
import java.util.Map;

import uk.co.farowl.vsj1.TreePython.Node;
import uk.co.farowl.vsj1.TreePython.Visitor;
import uk.co.farowl.vsj1.TreePython.expr;
import uk.co.farowl.vsj1.TreePython.expr_context;
import uk.co.farowl.vsj1.TreePython.operator;

public class TreePythonEx1 {

    public static void main(String[] args) {
        // x + 1
        // @formatter:off
        Node tree1 = new expr.BinOp(
                new expr.Name("x", expr_context.Load),
                operator.Add,
                new expr.Num(1));
        // @formatter:on
        Node tree = BinOp(Name("x", Load), Add, Num(1));

        // Create a visitor to execute the code.
        Evaluator evaluator = new Evaluator();

        // Initialise the variable referenced as x.
        evaluator.variables.put("x", 41);

        // Execute the code.
        Object result = tree.accept(evaluator);
        System.out.println(result);
    }

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
        public Object visit_Num(expr.Num num) {
            return num.n;
        }

        @Override
        public Object visit_Name(expr.Name name) {
            return variables.get(name.id);
        }
    }
}
