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

    package uk.co.farowl.vsj1.example.treepython;
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
            Node tree = BinOp(Name("x", Load), Add, Num(1));
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
            public Object visit_Num(expr.Num num) {
                return num.n;
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
            {return new expr.Name(id, ctx); }
        public static final expr Num(Object n) {return new expr.Num(n); }
        public static final expr BinOp(expr left, operator op, expr right)
            {return new expr.BinOp(left, op, right); }
    }

This works.
It prints ``42``, as all first Python programs should,
but it has at least one unsatisfactory aspect:
the use of casts to force the type of ``u`` and ``v`` in ``visit_BinOp``.
Without the casts, the addition cannot be carried out,
but clearly this is not a generally useful definition of addition.
In fact, it is only necessary to change ``Num(1))`` to ``Num(1.0))`` in the tree
in order to expose the issue:
we get a ``ClassCastException`` "java.lang.Double cannot be cast to java.lang.Integer",
where we should get ``42.0``.

We must reproduce Python's ability
to adapt its definition of addition to the type of the arguments.
In the next section, we turn to the question of *type* in the interpreter.

Type in the Interpreter
***********************

Type and Operation Dispatch in CPython
======================================

..  _PyTypeObject (API): https://docs.python.org/3/c-api/typeobj.html

Types are at the heart of the CPython interpreter.
Every object contains storage for its state and a pointer to an instance of ``PyTypeObject``.
``typeobject.c`` runs to more than 7500 lines,
has been the subject of nearly 1000 change sets,
and contains an offer of beer for bugs (in ``update_one_slot()``, in case you're thirsty).

When the CPython bytecode interpreter encounters (say) the ``BINARY_ADD`` opcode,
and the second thing on the stack is an ``int``,
it finds its way indirectly to a `PyTypeObject (API)`_
that contains a table of pointers to functions.
At the position in that table reserved for addition operations,
there is a pointer to a function that knows how to implement addition
when the left operand is an integer.
Adapting to the type of the right operand is a slightly longer story.

This is quite similar to the virtual function table
that is implicit in every Java ``Class`` object.
It also differs in significant ways, including:
* The manner of filling the table is unique to Python, supporting "diamond" inheritance.
* The table may be changed at runtime, when (for example) ``__add__`` is redefined for a type.
* The structure is fixed by the needs of the interpreter: user-defined methods are not added.

Type and Operation Dispatch in Jython 2.7.1
===========================================

The approach of the current Jython implementation is to make use of virtual function dispatch.
All Python objects are derived from ``org.python.core.PyObject``,
which has an ``_add(PyObject)`` method for use by the interpreter,
or rather by the Java bytecode compiled from Python source.

``PyObject._add(PyObject)`` is ``final``,
but dispatches to ``__add__`` or ``__radd__``,
which may be overridden by built-in types (defined in Java).
If the type is defined in Python,
by means of a class declaration, say,
``__add__`` and ``__radd__`` are both overidden,
to check for the existence of ``__add__`` and ``__radd__`` respectively, defined in Python,
in the type dictionary.

The final assembly is quite complex, as is the corresponding CPython code,
but we may be sure it implements the Python rules well enough,
since it passes the extensive Python regression tests.
The general path through the code (for derived types defined in Python) appears slow.
If the JVM JIT compiler is able to infer that arguments have particular built-in types,
it is quite possible the original call site could be in-lined,
and would collapse to one of the many special cases in the code.

Where a complex native Java object is handled in Python,
the interpreter handles it via a proxy that is Java-derived from ``PyObject``.
Expressions that have simple Java types are converted to and from Jython built-in types
(``PyString``, ``PyFloat``, and so on)
at the boundary between Python and Java,
for example when passing arguments to a method.

A Native Object Approach
========================

An alternative approach may be imagined in which compiled Python code
operates directly on Java types.
That is, a Python ``float`` object is simply a ``java.lang.Double``,
a Python ``int`` is just a ``java.math.BigInteger``,
and generally ``object`` is just ``java.langObject``.
We're going to explore this approach in preference to reproducing the Jython approach,
partly for novelty,
partly because Java guidance on the dynamic language features of Java gives us that hint.

The first challenge in this approach is how to locate the operations the interpreter needs.
These correspond to the slots in a CPython type object,
and there is a finite repertoire of them.






