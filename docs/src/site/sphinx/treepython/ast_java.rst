..  treepython/ast_java.rst


An Interpreter in Java for the Python AST
#########################################

The AST of a module is already too complicated for us to start with.
We'll focus on just this part of the AST::

    value=BinOp(left=Name(id='x', ctx=Load()), op=Add(), right=Num(n=1))),

corresponding to a single binary operation ``x + 1`` in the source.
In this section we consider how to create and traverse the same tree inside a Java program.

    Code fragments in this section are taken from
    ``runtime/src/test/java/.../vsj1/example/TestEx1.java``
    in the project source.

Representing the AST in Java
****************************

Python generates its AST node types from a
`compact specification <https://docs.python.org/3/library/ast.html#abstract-grammar>`_
in a language called ASDL.
(For a discussion, see
`Using ASDL to describe ASTs in compilers <http://eli.thegreenplace.net/2014/06/04/using-asdl-to-describe-asts-in-compilers>`_
by Eli Bendersky.)

The Python ASDL generates a lot of classes,
but all we need right now is this small part of the AST for expressions:

..  code-block:: none

    module TreePythonEx1
    {
        expr = BinOp(expr left, operator op, expr right)
             | Constant(constant value, string? kind)
             | Name(identifier id, expr_context ctx)

        operator = Add | Sub | Mult | Div
        expr_context = Load | Store | Del
    }

This is turned into a system of nested classes (for ``expr``)
and enumerated types for ``operator`` and ``expr_context``,
when the ``runtime`` sub-project is built by Gradle.
The skeleton is like this:

..  code-block:: java

    package uk.co.farowl.vsj1.example;

    public abstract class TreePythonEx1 {

        public interface Node { //...

        public static abstract class expr implements Node {

            public static class BinOp extends expr {
                public expr left;
                public operator op;
                public expr right;
                public BinOp(expr left, operator op, expr right){ //...
            }

            public static class Constant extends expr {
                public Object value;
                public String kind;
                public Constant(Object value, String kind){ //...
            }

            public static class Name extends expr {
                public String id;
                public expr_context ctx;
                public Name(String id, expr_context ctx){ //...
            }
        }

        public enum operator {Add, Sub, Mult, Div}
        public enum expr_context {Load, Store, Del}
    }

Each class has the members named in the ASDL source and a constructor to match.


Generating a Java Tree Literal
******************************

With the classes defined in the last section,
it is possible to write an expression whose value is an AST:

..  code-block:: java

    Node tree = new expr.BinOp(
        new expr.Name("x", expr_context.Load),
        operator.Add,
        new expr.Constant(1, null));

However, we can make this a little slicker (and more Pythonic)
by defining functions and constants so that we may write:

..  code-block:: java

    Node tree = BinOp(Name("x", Load), Add, Constant(1, null));

While it is feasible to write this by hand,
it would be nicer if Python could generate it from the source.
It can, of course.
The technique is to use an alternative pretty-printer for the AST.
The function call ``astutil.pretty_java(tree)``
turns the AST of the sample program into:

..  code-block:: java

    Module(
        list(
            Assign(list(Name("x", Store)), Constant(41, null), null),
            Assign(
                list(Name("y", Store)),
                BinOp(Name("x", Load), Add, Constant(1, null)),
                null),
            Expr(
                Call(Name("print", Load), list(Name("y", Load)), list()))),
        list())

All the node types now look like function calls with positional arguments,
and without ``new`` and class name prefixes.
The unusual new feature is ``list()``,
a function that replaces the square brackets notation Python has for lists.
(We don't need ``list`` just yet, or several other node types shown here.)
The definitions that make it possible to write simply
``BinOp(Name("x", Load), Add, Constant(1, null))`` are:

..  code-block:: java

    public static final operator Add = operator.Add;
    public static final operator Mult = operator.Mult;
    public static final expr_context Load = expr_context.Load;
    public static final expr Name(String id, expr_context ctx)
        { return new expr.Name(id, ctx); }
    public static final expr Constant(Object value, String kind)
        { return new expr.Constant(value, kind); }
    public static final expr BinOp(expr left, operator op, expr right)
        { return new expr.BinOp(left, op, right); }


A Visit from the Evaluator
**************************

The expressions we can now write (or generate) in Java
do not evaluate the Python expression:
they merely construct an AST that represents it.
In order to evaluate the expression we must walk the tree,
which we accomplish using a Visitor design pattern.
Parts of the definition of the ``TreePythonEx1`` class, that we missed out above,
provide a ``Visitor`` interface and give ``Node`` an ``accept`` method:

..  code-block:: java

    public abstract class TreePythonEx1 {

        public interface Node {
            default <T> T accept(Visitor<T> visitor) { return null; }
        }

        public static abstract class expr implements Node {

            public static class BinOp extends expr {
                @Override
                public <T> T accept(Visitor<T> visitor) {
                    return visitor.visit_BinOp(this);
                }
            }
            // And so on ...
        }

        public interface Visitor<T> {
            T visit_BinOp(expr.BinOp _BinOp);
            T visit_Constant(expr.Constant _Constant);
            T visit_Name(expr.Name _Name);
        }
        // ...
    }

We also have to provide an ``Evaluator`` class
that implements ``TreePythonEx1.Visitor``,
in which ``visit_BinOp`` performs the arithmetic we need.
As our expression involves a variable ``x``,
we give it a simple ``Map`` store for the values of variables.

We can now demonstrate execution of the tree code to evaluate the expression:

..  code-block:: java

    package uk.co.farowl.vsj1.example;
    // ... imports
    /** Demonstrate a Python interpreter for the AST. */
    public class TestEx1 {

        // Visitor to execute the code.
        Evaluator evaluator;

        @Before
        public void setUp() {
            // Create a visitor to execute the code.
            evaluator = new Evaluator();
        }

        // ...
        @Test
        public void astExecShorthand() {
            // x + 1
            Node tree = BinOp(Name("x", Load), Add, Constant(1, null));
            // Execute the code for x = 41
            evaluator.variables.put("x", 41);
            Object result = tree.accept(evaluator);
            assertEquals(42, result);
        }

        /**
         * An interpreter for Python that works by walking the AST.
         */
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

        public static final operator Add = operator.Add;
        public static final operator Mult = operator.Mult;
        public static final expr_context Load = expr_context.Load;
        public static final expr Name(String id, expr_context ctx)
            { return new expr.Name(id, ctx); }
        public static final expr Constant(Object value, String kind)
            { return new expr.Constant(value, kind); }
        public static final expr BinOp(expr left, operator op, expr right)
            { return new expr.BinOp(left, op, right); }
    }

This works.
It prints ``42``, as all first Python programs should,
but it has at least one unsatisfactory aspect:
the use of casts to force the type of ``u`` and ``v`` in ``visit_BinOp``.
Without the casts, the addition cannot be carried out,
but clearly this is not a generally useful definition of addition.
In fact, it is only necessary to change ``1`` to ``1.0`` in the tree
in order to expose the issue:
we get a ``ClassCastException``
"java.lang.Double cannot be cast to java.lang.Integer",
where we should get ``42.0``.

We must reproduce Python's ability
to adapt its definition of addition to the type of the arguments.
In the next section, we turn to the question of *type* in the interpreter.


