package uk.co.farowl.vsj1.prog;

import java.util.HashMap;
import java.util.Map;

import uk.co.farowl.vsj1.TreePython.Node;
import uk.co.farowl.vsj1.TreePython.Visitor;
import uk.co.farowl.vsj1.TreePython.expr;
import uk.co.farowl.vsj1.TreePython.operator;

public class ExecTree {

    public static void main(String[] args) {
        // x + 1
        Node expression = new expr.BinOp(new expr.Name("x"), operator.Add, new expr.Num(1));

        // Create a visitor to execute the code. (Initialise the variable referenced as x.)
        Evaluator evaluator = new Evaluator();
        evaluator.variables.put("x", 41);

        // Execute the code.
        Object result = expression.accept(evaluator);
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
