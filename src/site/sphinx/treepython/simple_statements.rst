..  treepython/simple_statements.rst


Interpretation of Simple Statements
###################################
In this section we'll find a way to implement
a subset of assignment statements.
This is a step up from the evaluation of expressions,
in that we deal with *sequences* of statements,
although previous work provides the "right hand side" for our assignments.

Additions to the AST
********************
ASDL
====
In order to represent the data structures of assignment statements
we advance the ASDL of TreePython so::

    module TreePython
    {
        mod = Module(stmt* body)

        stmt =
               Delete(expr* targets)
             | Assign(expr* targets, expr value)
             | Global(identifier* names)
             | Nonlocal(identifier* names)

        expr = 
               BinOp(expr left, operator op, expr right)
             | UnaryOp(unaryop op, expr operand)
             | Num(object n)

             -- the following expression can appear in assignment context
             | Name(identifier id, expr_context ctx)

        operator = Add | Sub | Mult | Div
        unaryop = UAdd | USub
        expr_context = Load | Store | Del
    }

We can see that, since assignment is a statement,
we have to address the interpretation of sequences of statements.
This is what leads us into careful consideration of data structures
around the interpreter.

Visitor
=======
The ASDL above, gives rise to a set of class definitions,
of a predictable type,
and the following visitor interface:

..  code-block:: java

    public abstract class TreePython {
        // ...
        public interface Visitor<T> {
            default T visit_Module(mod.Module _Module){ return null; }
            default T visit_Delete(stmt.Delete _Delete){ return null; }
            default T visit_Assign(stmt.Assign _Assign){ return null; }
            default T visit_Global(stmt.Global _Global){ return null; }
            default T visit_Nonlocal(stmt.Nonlocal _Nonlocal){ return null; }
            default T visit_BinOp(expr.BinOp _BinOp){ return null; }
            default T visit_UnaryOp(expr.UnaryOp _UnaryOp){ return null; }
            default T visit_Num(expr.Num _Num){ return null; }
            default T visit_Name(expr.Name _Name){ return null; }
        }
    }

Previously we evaluated expressions using a class ``Evaluator``
that implemented the visitor interface directly.
This time round,
let's see how closely we can replicate the CPython interpreter,
:c:func:`PyEval_EvalCode` (or :c:func:`PyEval_EvalCodeEx`),
using the visitor internally.

Critical Structures -- First Implementation
===========================================
A brief inspection of the objects critical to keeping state in CPython 3.5
shows them to consist mostly of things we're far from ready to implement.
But we are going to take clues from it,
and from corresponding parts of Jython 2.7.1,
since the object architecture must be conceptually equivalent.
A first attempt to write about this
led a long way beyond what this section needs,
so the (unfinished) discussion has moved to
:doc:`/architecture/interpreter`.

CPython defines four structures,
that Java would consider classes,
in the vicinity of the core interpreter:

* :c:type:`PyCodeObject` is an immutable Python object (type ``code``)
  holding compiled code (CPython byte code)
  and information that may be deduced statically from the source.
* :c:type:`PyFrameObject` is a Python object (type ``frame``)
  that provides the execution context,
  for running a ``PyCode``.
* :c:type:`PyThreadState` holds per-thread state,
  most notably the linked list of frames that forms the Python stack.
* :c:type:`PyInterpreterState` holds state shared between threads,
  including the module list and built-in objects.

Threading is not likely to be important to us in the toy implementation,
still less the possibility of multiple interpreters,
but the choice of data structures here is shot through with these concepts.
In following CPython (and Jython)
we'll void an implementation that precludes this multiplicity.


``PyCode``
----------
The ``PyCodeObject`` (type ``code``) holds compiled code,
such as from a module or function body,
and information that may be deduced statically from the source,
such as the names of local variables and function arguments.
For us it will hold the AST and derived information,
getting richer later when we address functions and classes,
argument and variable names, rationalised constants, and more.

..  code-block::    java



``PyFrame``
-----------

``PyFrameObject`` (type ``frame``) provides the execution context
for one invocation of a function or a module while it executes.
It holds the values of local variables named in the associated code object,
references global, local and built-in dictionaries,
and any state associated with a particular execution of the code.
A ``PyFrame`` may also exist disconnected from the thread state.
The actions of the interpreter are, essentially,
operations on the current ``frame``,
and a call creates a new frame to act upon,
leaving interpreter state suspended in the calling frame.
We're not ready for functions yet,
but it will help when the time comes
if we deal correctly with local and global variable from the start.
This distinction is implicit in the arguments
to the :py:func:`exec` built-in function.
It is attractive to identify interpreter actions
as methods on the ``frame`` object,
rather than as global functions as one is forced to in C.

..  code-block::    java




``ThreadState``
---------------
A ``PyThreadState`` represents a thread of execution.
It holds the linked list of frames (execution context in Python),
and a reference to the interpreter state.
Most importantly, it is the double of an operating system thread.
Many places in CPython,
the C code does not carry around interpreter context as an argument,
but relies on a global pointer to the current ``PyThreadState``,
that changes according to the OS thread that holds the GIL
(Global Interpreter Lock).
In a JVM implementation of Python,
we must achieve the same for ``java.lang.Thread``.
However, for a long time we will only need the one thread.

..  code-block::    java



``PyInterpreterState``
----------------------
In CPython,
:c:type:`PyInterpreterState` aggregates state shared between threads.
(Jython, uses a ``PySystemState`` class in the same way.
Jython may be a more accurate name,
although there it also implements the ``sys`` module.)
The interpreter state holds references to key universal name spaces,
the global name space,
the ``sys`` module,
the module list itself, and
standard codecs.
In principle, there could be multiple instances concurrently.
This is rare in CPython
but quite likely when Jython is used in a Java application server.


Execution of Assignment
***********************
Meaning of Assignment to a Name
===============================






