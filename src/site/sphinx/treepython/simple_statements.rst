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

        stmt = FunctionDef(identifier name, arguments args,
                           stmt* body, expr* decorator_list, expr? returns)
             | Delete(expr* targets)
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

        arguments = (arg* args, arg? vararg, arg* kwonlyargs, expr* kw_defaults,
                     arg? kwarg, expr* defaults)

        arg = (identifier arg, expr? annotation)
    }

We can see that, since assignment is a statement,
we have to address the interpretation of sequences of statements.
This is what leads us into careful consideration of data structures
around the interpreter.

We've included function definition in the ASDL,
not because we're ready to tackle functions properly,
but because important aspects of binding variable names only emerge
once nested scopes are considered.

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
            default T visit_FunctionDef(stmt.FunctionDef _FunctionDef)
                { return null; }
            default T visit_Delete(stmt.Delete _Delete){ return null; }
            default T visit_Assign(stmt.Assign _Assign){ return null; }
            default T visit_Global(stmt.Global _Global){ return null; }
            default T visit_Nonlocal(stmt.Nonlocal _Nonlocal){ return null; }
            default T visit_BinOp(expr.BinOp _BinOp){ return null; }
            default T visit_UnaryOp(expr.UnaryOp _UnaryOp){ return null; }
            default T visit_Num(expr.Num _Num){ return null; }
            default T visit_Name(expr.Name _Name){ return null; }
            default T visit_arguments(arguments _arguments){ return null; }
            default T visit_arg(arg _arg){ return null; }
            default T visit_keyword(keyword _keyword){ return null; }
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
  the import mechanism, modules list, and the character encoding registry.

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

    class PyCode {

        final Node ast;

        PyCode(Node ast) {
            this.ast = ast;
        }
    }


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
We're not ready to execute functions yet,
but it will help when the time comes
if we deal correctly with local and global variable from the start.
This distinction is implicit in the arguments
to the :py:func:`exec` built-in function.

..  code-block::    java

    class PyFrame {
        /** Frames form a stack by chaining through the back pointer. */
        PyFrame f_back;
        /** Code this frame is to execute. */
        final PyCode f_code;
        /** Global context (name space) of execution. */
        Map<String, Object> f_globals;
        /** Local context (name space) of execution. */
        Map<String, Object> f_locals;
        // ...
    }

It is attractive to identify interpreter actions
as methods on the ``frame`` object,
rather than as global functions as one is forced to in C.
We'll do this in the experiment by using a sub-class of ``PyFrame``:

..  code-block::    java

    private static class ExecutionFrame extends PyFrame
            implements Visitor<Object> {

        Object eval() { return f_code.ast.accept(this); }
        // ...
    }


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

    class ThreadState {
        /** Interpreter to which this <code>ThreadState</code> belongs. */
        final SystemState interp;
        /** Top of execution frame stack. */
        PyFrame frame;
        /**
         * Construct a ThreadState in the context of an owning interpreter
         * and the current Java <code>Thread</code>.
         */
        private ThreadState(SystemState interp) {
            this.interp = interp;
            this.frame = null;
            interp.threads.add(this);
        }
    }


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


Access to Variables
*******************
In the section on expressions,
we took a simplistic approach to variable access.
We only needed to read the value from a variable that already existed, and
we paid no attention to whether the variable was local or global.
Expressions were evaluated as they might be in a statement at module level, or
in a statement typed into the Python interpreter.
We simply had to look up a name in a single name space of variables,
pre-assigned by the test method.

In Python,
the execution context of a block of code is equipped with two name spaces:
local and global.
These name spaces are available as dictionaries (mappings)
through the functions :py:func:`local` and :py:func:`global`,
but usually code refers to variables through their name directly.
At module level,
the global and local name spaces are the same dictionary,
and then in a nested block (function body, say)
the global name space remains that of the module that defined it,
and the local name space is defined by the ``frame`` of execution.
However,
one can supply separate local and global dictionaries explicitly,
when executing any code using the :py:func:`eval` function.

Modes of Access
===============
There are roughly 4 types of variable access in Python,
and within each, load, store and delete operations:

+--------+-------------------------+------------------------------+
| mode   | location                | interpreter action           |
+========+=========================+==============================+
| local  | in the ``frame``        | access locally               |
+--------+-------------------------+------------------------------+
| global | in the defining module  | access via global dictionary |
|        |                         | reference (normally module)  |
+--------+-------------------------+------------------------------+
| name   | local, global or the    | load/delete where found      |
|        | ``__builtins__`` module | (store always local)         |
+--------+-------------------------+------------------------------+
| cell   | shared between frames   | access indirectly through    |
|        |                         | holder object                |
+--------+-------------------------+------------------------------+

The mode is not identified in the AST node describing the load or store;
we have to do some work on the tree as a whole
in order to work out which mode is appropriate in each place.
See also `Naming and binding`_ in the Python Language Reference.

..  _Naming and binding:
    https://docs.python.org/3/reference/executionmodel.html#naming-and-binding

We need quite a complex example to explore this subject.
Let's explore at the Python prompt::

    >>> prog = """\
    qux = 9
    a = 1
    def f():
        global a, g
        b = 10 + a
        c = 20 + b
        def gg():
            global a
            nonlocal b, c
            d = 100 + b + a
            c = 20
            a = 2
        e = 30 + c
        g = gg
    f()
    g()
    """
    >>> glob = dict(foo=7)
    >>> exec(prog, glob)
    >>> from pprint import pprint
    >>> pprint(glob, depth=1)
    {'__builtins__': {...},
     'a': 2,
     'f': <function f at 0x000001D9CC995BF8>,
     'foo': 7,
     'g': <function f.<locals>.gg at 0x000001D9CC995C80>,
     'qux': 9}

Here the example code is executed in the single global dictionary ``glob``,
as it would when executed as a module.
It's obvious where ``a`` and ``qux`` get added to the dictionary.
``f`` is the result of the function definition (this also is assignment).
``g`` is added as the result of executing ``f``,
and ``a=2`` because we execute ``g``.
A reference to ``__builtins__`` is forced in by execution of ``eval``.

A subtlety is revealed when ``prog`` is executed
with a distinct local variable dictionary::

    >>> glob, loc = dict(foo=7), dict(bar=8)
    >>> exec(prog, glob, loc)
    >>> pprint(glob, depth=1)
    {'__builtins__': {...},
     'a': 2,
     'foo': 7,
     'g': <function f.<locals>.gg at 0x000001D9CC995D90>}
    >>> loc
    {'bar': 8, 'qux': 9, 'f': <function f at 0x000001D9CC995D08>}

It is evident that assignment to ``qux`` and the definition of ``f``
each populate the local dictionary by "name" assignment.
``a`` and ``g`` are created by "global" assignment
because they are declared ``global`` *in nested scopes*.
A glance at the generated code shows it to begin::

    >>> from dis import dis
    >>> dis(compile(prog, '<string>', 'exec'))
      1           0 LOAD_CONST               0 (9)
                  3 STORE_NAME               0 (qux)
      2           6 LOAD_CONST               1 (1)
                  9 STORE_GLOBAL             1 (a)
      3          12 LOAD_CONST               2 (<code object f at ... >)
                 15 LOAD_CONST               3 ('f')
                 18 MAKE_FUNCTION            0
                 21 STORE_NAME               2 (f)
        ...

The result of function definition (in CPython) is a constant,
built by the compiler,
that contains the byte code,
and is used from the stack in the ``MAKE_FUNCTION`` opcode to build
a ``PyFunction`` object assigned to ``f``.
In a disassembly of the main module, we don't see its contents,
which is unusual if you're used to the code generated in other languages.
We have to pick apart the function objects ``f`` and ``g`` to see the code.
There is a program in the test source tree that does this,
at ``~/src/test/python/variable_access.py``.



