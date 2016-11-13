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
In the C implementation of Python,
code is generated for a Python Virtual Machine.
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

The objects in this structure
are officially described in the standard library documentation,
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
Moreover,
we probably want to follow Jython's lead and generate Java bytecode ultimately.
So we're taking a different course here for the toy implementation:
we imagine executing the AST directly.
This will get us quickly to some of the implementation questions
central to the interpreter.

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
The function call ``astutil.pretty_java(tree)``
turns the AST of the sample program into:

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
The definitions that make it possible to write simply
``BinOp(Name("x", Load), Add, Num(1))`` are:

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

The expressions we can now write (or generate) in Java
do not evaluate the Python expression:
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

We also have to provide an ``Evaluator`` class
that implements ``TreePython.Visitor``,
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
we get a ``ClassCastException``
"java.lang.Double cannot be cast to java.lang.Integer",
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
Every object contains storage for its state and a pointer to an instance of
``PyTypeObject``.
It is not easy to implement an adequate type system.
The support in CPython is found at ``typeobject.c``,
which runs to more than 7500 lines,
has been the subject of nearly 1000 change sets,
and contains an offer of free beer for finding a bug (in ``update_one_slot()``,
in case you're thirsty).

The core mechanism as it affects binary operations (our first target)
is as follows.
When the CPython bytecode interpreter encounters (say)
the ``BINARY_ADD`` opcode,
and the operands are both ``int``,
it finds its way indirectly to a `PyTypeObject (API)`_
that contains a table of pointers to functions, the "slot functions".
At the position in that table reserved for addition operations,
there is a pointer to a function that knows how to implement addition
when the left operand is an integer.
When the operands differ in type,
the interpreter will try one implementation
(whether left or right depends on class hierarchy),
and if that signals failure it will try the right operand's implementation.

This is quite similar to the virtual function table
that is implicit in every Java ``Class`` object.
It also differs in significant ways, including:

* The manner of filling the table is unique to Python,
  supporting "diamond" inheritance.
* The manner of choosing the function finally called is unique to Python.
* The table may be changed at runtime,
  when (for example) ``__add__`` is defined for a type.
* The structure is fixed by the needs of the interpreter:
  user-defined methods are not added here but in the dictionary.

Type and Operation Dispatch in Jython 2.7.1
===========================================

The approach of the current Jython implementation
is to make use of virtual function dispatch.
All Python objects are derived from ``org.python.core.PyObject``,
which has an ``_add(PyObject)`` method for use by the interpreter,
or rather by the Java bytecode compiled from Python source.

``PyObject._add(PyObject)`` is ``final``,
but dispatches to ``__add__`` or ``__radd__``,
which may be overridden by built-in types (defined in Java).
If the type is defined in Python,
by means of a class declaration, say,
``__add__`` and ``__radd__`` are both overidden,
to check for the existence of ``__add__`` and ``__radd__`` respectively,
defined in Python, in the type's dictionary.

The final assembly is quite complex, as is the corresponding CPython code,
but we may be sure it implements the Python rules well enough,
since it passes the extensive Python regression tests.
The general path through the code (for derived types defined in Python)
appears slow,
since multiple nested calls occur.
If the JVM JIT compiler is able to infer (or speculate on) argument types,
it is quite possible the original call site could be in-lined,
if the code is hot,
and would collapse to one of the many special cases.

Where a complex pure Java object is handled in Python,
the interpreter handles it via a proxy that is Java-derived from ``PyObject``.
Expressions that have simple Java types are converted to and from Jython built-in types
(``PyString``, ``PyFloat``, and so on)
at the boundary between Python and Java,
for example when passing arguments to a method.

A Java Object Approach
======================

An alternative approach may be imagined in which compiled Python code
operates directly on Java types.
That is, a Python ``float`` object is simply a ``java.lang.Double``,
a Python ``int`` is just a ``java.math.BigInteger``,
and generically,
an arbitrary object may only be assumed to be a ``java.lang.Object``,
not a ``PyObject`` with language-specific properties.
Counter-intuitively perhaps,
a Python ``object`` is not *implemented* by ``java.lang.Object``,
since it carries additional state.

We're going to explore this approach
in preference to reproducing the Jython approach,
partly for its potential to reduce indirection,
partly because the `cookbook`_ (slide 34, minute 53 in the `cookbook video`_)
on dynamic JVM languages recommends it.

..  _cookbook: http://www.wiki.jvmlangsummit.com/images/9/93/2011_Forax.pdf
..  _cookbook video: http://medianetwork.oracle.com/video/player/1113248965001

The first challenge in this approach is
how to locate the operations the interpreter needs.
These correspond to the slots in a CPython type object,
and there is a finite repertoire of them.

Dispatch via overloading in Java
--------------------------------

Suppose we want to support the ``Add`` and ``Mult`` binary operations.
The implementation must differ according to the types of the arguments.
CPython dispatches primarily on a single argument,
via the slots table,
combining two alternatives according to language rules for binary operations.

The single dispatch fits naturally with direct use of overloading in Java.
We cannot give ``java.lang.Integer`` itself any new behaviour,
and so we must map that type to a handler object with the extra capabilities.
In support, we need a registry of class to handler mappings,
that for now we may initialise statically.
Java provides a class for thread-safe lookup in ``java.lang``
called ``ClassValue``.
We'll use this inside a runtime support class like this:

..  code-block:: java

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

The alert reader will notice that the classes shown are often ``static``,
nested classes.
This has no functional significance.
Simply we wish to hide them within the test class that demonstrates them.
Once they mature, we'll create public- or package-visible classes.

Each handler must be capable of all the operations the interpreter might need,
but for now we'll be satisfied with two arithmetic operations:

..  code-block:: java

    // ...
    interface TypeHandler {
        Object add(Object v, Object w);
        Object multiply(Object v, Object w);
    }

We can see how the interface could be extended (for sequences, etc.).
Note that the methods take and return ``Object``
as there is no restriction on the type of arguments and returns in Python,
or the equivalent slot functions in CPython.

Our attempt at implementing the operations for ``int`` then looks like this:

..  code-block:: java

    static class IntegerHandler extends TypeHandler {

        @Override
        public Object add(Object vobj, Object wobj) {
            Class<?> cv = vobj.getClass();
            Class<?> cw = wobj.getClass();
            if (cv == Integer.class&&cw == Integer.class) {
                return (Integer)vobj + (Integer)wobj;
            } else {
                return null;
            }
        }

        @Override
        public Object multiply(Object vobj, Object wobj) {
            Class<?> cv = vobj.getClass();
            Class<?> cw = wobj.getClass();
            if (cv == Integer.class&&cw == Integer.class) {
                return (Integer)vobj * (Integer)wobj;
            } else {
                return null;
            }
        }
    }

Notice that the handler for integers
only knows how to do arithmetic with integers.
It returns ``null`` if it cannot deal with the types passed in.
The handler for floating point also accepts integers,
in accordance with Python conventions for widening:

..  code-block:: java

    static class DoubleType extends TypeHandlers implements TypeHandler {

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
            // ... similar code
        }
    }

Then within the definition of ``Evaluator.visit_BinOp``
we use what we've provided like this:

..  code-block:: java

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
        // ...
    }


The pattern used follows that of CPython.
The type (``V``) of the left argument gets the first go at evaluation.
If that fails (returns ``null`` here),
then the type (``W``) of the right operand gets a chance.
There should be another consideration here:
if ``W`` is a (Python) sub-class of ``V``,
then ``W`` should get the first chance,
but we're not ready to deal with inheritance.

This gets the right answer,
no matter how we mix the types ``float`` and ``int``.
It has a drawback:
it cannot deal easily with Python objects that define ``__add__``.
The approach taken by Jython
is to give objects defined in Python a special handler
(e.g. ``PyIntegerDerived``),
in which each operation checks for the corresponding definition
in the dictionary of the Python class.



