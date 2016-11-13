package uk.co.farowl.vsj1;
// TreePython.java

// Generated by Compile
// Generated from scraps\20161021\TreePython.asdl
//

/**
 * Outer class scoping the types defined in the ASDL file
 * <code>scraps\20161021\TreePython.asdl</code>, module <code>TreePython</code>. <pre>
 * expr = BinOp(expr left, operator op, expr right)
 *      | Num(object n)
 *      | Name(identifier id)
 *
 * operator = Add | Sub | Mult | Div
 * </pre>
 */
public abstract class TreePython {

    /**
     * All definitions in this module implement this interface to support a generic approach to
     * walking the tree.
     */
    public interface Node {

        /**
         * Allows a <code>Visitor</code> to visit this <code>Node</code> without knowing its exact
         * type. The <code>Node</code> dispatches to the appropriate type-specific method of the
         * visitor.
         */
        default <T> T accept(Visitor<T> visitor) {
            return null;
        }
    }

    public static abstract class expr implements Node {

        public static class BinOp extends expr {

            public expr left;
            public operator op;
            public expr right;

            public BinOp(expr left, operator op, expr right) {
                this.left = left;
                this.op = op;
                this.right = right;
            }

            @Override
            public <T> T accept(Visitor<T> visitor) {
                return visitor.visit_BinOp(this);
            }
        }

        public static class Num extends expr {

            public Object n;

            public Num(Object n) {
                this.n = n;
            }

            @Override
            public <T> T accept(Visitor<T> visitor) {
                return visitor.visit_Num(this);
            }
        }

        public static class Name extends expr {

            public String id;
            public expr_context ctx;

            public Name(String id, expr_context ctx) {
                this.id = id;
                this.ctx = ctx;
            }

            @Override
            public <T> T accept(Visitor<T> visitor) {
                return visitor.visit_Name(this);
            }
        }
    }

    public enum operator implements Node {
        Add, Sub, Mult, Div
    }

    public enum expr_context implements Node {
        Load, Store, Del
    }

    public interface Visitor<T> {

        T visit_BinOp(expr.BinOp _BinOp);

        T visit_Num(expr.Num _Num);

        T visit_Name(expr.Name _Name);
    }
}
