..  treepython/treepthyon.rst


A Tree-Python Interpreter
#########################

Some Help from the Reference Interpreter
****************************************

We intend to study how one may construct a Python interpreter in Java.
Our interest is in how to *execute* Python,
not so much how we parse and compile it:
we'll have to tackle that too,
but we'll begin with that central problem of execution.

Incidentally, is it "execution" or "interpretation"?
It is largely a matter of perspective.
In the C implementation of Python, code is generated for a Python Virtual Machine.
(The design of the machine,
and the machine language it accepts,
change from one version of the implementation to the next.)
From a hardware perspective (or that of the C programmer),
the virtual machine is an interpreter for this intermediate language.

It will get even more layered with a Java implementation.
From the hardware perspective, the JVM itself is an interpreter,
and our Java programs are compiled to its Java bytecode.
We will (probably) want to translate Python into Java bytecode,
as Jython does.
But we might also write an interpreter in Java,
compiled to Java bytecode,
to interpret an intermediate language compiled from Python.

Python possesses another intermediate form,
other than Python bytecode,
that it is useful to study:
this is the AST (abstract syntax tree).
The Python compiler creates the AST as an intermediate product
between your code and Python bytecode,
but it will also provide it to you as an object.
It is attractive to manipulate the AST in tools because it is:

* recognisably related to your source code and its symbols, and
* standardised in a way the bytecode is not.

Otherwise, one would be compelled to start by writing a Python compiler.

Python Compilation Example
**************************

The following is straightforward with a working implementation of Python.
Suppose we have this very simple program::

    x = 41
    y = x + 1
    print(y)

Then we may compile it to an AST like so::

    >>> import ast, dis
    >>> prog = """\
    x = 41
    y = x + 1
    print(y)
    """
    >>> tree = ast.parse(prog)
    >>> ast.dump(tree)
    "Module(body=[Assign(targets=[Name(id='x', ctx=Store())], value=Num(n=41)),
    Assign(targets=[Name(id='y', ctx=Store())], value=BinOp(left=Name(id='x', ctx=Load()),
    op=Add(), right=Num(n=1))), Expr(value=Call(func=Name(id='print', ctx=Load()),
    args=[Name(id='y', ctx=Load())], keywords=[]))])"
    >>>

Now that last line is not very pretty,
so the first thing in the tool box
(at ``src/main/python/astutil.py`` relative to the project root)
is a pretty-printer::

    >>> import sys, os
    >>> lib = os.path.join('src', 'main', 'python')
    >>> sys.path.insert(0, lib)
    >>> import astutil
    >>> astutil.pretty(tree)
    Module(
     body=[
       Assign(targets=[Name(id='x', ctx=Store())], value=Num(n=41)),
       Assign(
         targets=[Name(id='y', ctx=Store())],
         value=BinOp(left=Name(id='x', ctx=Load()), op=Add(), right=Num(n=1))),
       Expr(
         value=Call(
           func=Name(id='print', ctx=Load()),
           args=[Name(id='y', ctx=Load())],
           keywords=[]))])
    >>>

The objects in this structure are officially described in the standard library documentation,
but there is a fuller explanation in `Green Tree Snakes`_.

..  _Green Tree Snakes: https://greentreesnakes.readthedocs.io/en/latest/

We may compile and run (either the source ``prog`` or the AST) to CPython bytecode like so::

    >>> code = compile(tree, '<prog>', 'exec')
    >>> dis.dis(code)
     2           0 LOAD_CONST               0 (41)
                 3 STORE_NAME               0 (x)

     3           6 LOAD_NAME                0 (x)
                 9 LOAD_CONST               1 (1)
                12 BINARY_ADD
                13 STORE_NAME               1 (y)

     4          16 LOAD_NAME                2 (print)
                19 LOAD_NAME                1 (y)
                22 CALL_FUNCTION            1 (1 positional, 0 keyword pair)
                25 POP_TOP
                26 LOAD_CONST               2 (None)
                29 RETURN_VALUE
    >>> exec(code)
    42
    >>>

We could implement an interpreter in Java for exactly the bytecode Python uses.
However, the bytecode evolves from one release to the next
and is conceived to support the implementation in C.
Moreover, we probably want to follow Jython's lead and generate Java bytecode ultimately.
So we're taking a different course here for the toy implementation:
we imagine executing the AST directly.
This will get us quickly to some of the implementation questions central to the interpreter.

An Interpreter in Java for the Python AST
*****************************************

The example above is already too complicated for us to start with.
We'll focus on just this part of the AST::

    value=BinOp(left=Name(id='x', ctx=Load()), op=Add(), right=Num(n=1))),

corresponding to a single binary operation ``x + 1`` in the source.
In this section we consider how to create and traverse the same tree inside a Java program.

Representing the AST in Java
============================

Python generates its AST node types from a
`compact specification <https://docs.python.org/3/library/ast.html#abstract-grammar>`_
in a language called ASDL.
(For a discussion, see
`Using ASDL to describe ASTs in compilers <http://eli.thegreenplace.net/2014/06/04/using-asdl-to-describe-asts-in-compilers>`_
by Eli Bendersky.)

The Python ASDL generates a lot of classes,
but all we need right now is this small part of the AST for expressions:

..  code-block:: none

    module TreePython
    {
        expr = BinOp(expr left, operator op, expr right)
             | Num(object n)
             | Name(identifier id, expr_context ctx)

        operator = Add | Sub | Mult | Div
        expr_context = Load | Store | Del
    }

This is easily turned into a system of nested classes (for ``expr``)
and enumerated types for ``operator`` and ``expr_context``.
The skeletal structure is like this:

..  code-block:: java

    package uk.co.farowl.vsj1;

    public abstract class TreePython {

        public interface Node { //...

        public static abstract class expr implements Node {

            public static class BinOp extends expr {
                public expr left;
                public operator op;
                public expr right;
                public BinOp(expr left, operator op, expr right) { //...
            }

            public static class Num extends expr {
                public Object n;
                public Num(Object n) { //...
            }

            public static class Name extends expr {
                public String id;
                public expr_context ctx;
                public Name(String id, expr_context ctx) { //...
            }
        }

        public enum operator implements Node {Add, Sub, Mult, Div}
        public enum expr_context implements Node {Load, Store, Del}
    }

Each class has the members named in the ASDL source and a constructor to match.


Generating a Java Tree Literal
==============================

With the classes defined in the last section,
it is possible to write an expression whose value is an AST:

..  code-block:: java

    Node tree = new expr.BinOp(
        new expr.Name("x", expr_context.Load),
        operator.Add,
        new expr.Num(1));

However, we can make this a little slicker (and more Pythonic)
by defining functions and constants so that we may write:

..  code-block:: java

    Node tree = BinOp(Name("x", Load), Add, Num(1));

While it is feasible to write this by hand,
it would be nicer if Python could generate it from the source.
Basically, the technique is to use an alternative pretty-printer for the AST.
The function call ``astutil.pretty_java(tree)`` turns the AST of the sample program into:

..  code-block:: java

    Module(
        list(
            Assign(list(Name("x", Store)), Num(41)),
            Assign(
                list(Name("y", Store)),
                BinOp(Name("x", Load), Add, Num(1))),
            Expr(
                Call(Name("print", Load), list(Name("y", Load)), list()))))

All the node types now look like function calls with positional arguments,
and without ``new`` and class name prefixes.
The unusual new feature is ``list()``,
a function that replaces the square brackets notation Python has for lists.
(We don't need ``list`` just yet.)
The definitions that make it possible to write simply ``BinOp(Name("x", Load), Add, Num(1))`` are:

..  code-block:: java

    public static final operator Add = operator.Add;
    public static final expr_context Load = expr_context.Load;
    public static final expr Name(String id, expr_context ctx)
        {return new expr.Name(id, ctx); }
    public static final expr Num(Object n) {return new expr.Num(n); }
    public static final expr BinOp(expr left, operator op, expr right)
        {return new expr.BinOp(left, op, right); }


A Visit from the Evaluator
==========================

The expressions we can now write (or generate) in Java do not evaluate the Python expression:
they merely construct an AST that represents it.
In order to evaluate the expression we must walk the tree,
which we accomplish using a Visitor design pattern.
Parts of the definition of the ``TreePython`` class, that we missed out above,
provide a ``Visitor`` interface and to give ``Node`` an ``accept`` method:

..  code-block:: java

    public abstract class TreePython {

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
            T visit_Num(expr.Num _Num);
            T visit_Name(expr.Name _Name);
        }
        // ...
    }

We also have to provide an ``Evaluator`` class that implements ``TreePython.Visitor``,
in which ``visit_BinOp`` performs the arithmetic we need.
As our expression involves a variable ``x``,
we give it a simple ``Map`` store for the values of variables.

We can now demonstrate execution of the tree code to evaluate the expression:

..  code-block:: java

    package uk.co.farowl.vsj1.example;
    // import ...
    public class TreePythonEx1 {

        public static void main(String[] args) {
            // x + 1
            Node tree = BinOp(Name("x", Load), Add, Num(1));

            // Create a visitor to execute the code.
            Evaluator evaluator = new Evaluator();
            evaluator.variables.put("x", 41);
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
            public Object visit_Num(expr.Num num) { return num.n; }

            @Override
            public Object visit_Name(expr.Name name)
                { return variables.get(name.id); }
        }
    }

This works.
It prints ``42``, as all first Python programs should,
but it has at least one unsatisfactory aspect:
the use of casts to force the type of ``u`` and ``v`` in ``visit_BinOp``.
Clearly this is not a generally useful definition of addition.
In fact, it is only necessary to change ``Num(1))`` to ``Num(1.0))`` in the tree
in order to expose the issue:
we get a ``ClassCastException`` "java.lang.Double cannot be cast to java.lang.Integer",
where we should get ``42.0``.

We must reproduce Python's ability
to adapt its definition of addition to the type of the arguments.
In the next section, we turn to the question of *type* in the interpreter.



