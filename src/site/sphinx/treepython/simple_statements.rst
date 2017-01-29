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
             | Expr(expr value)
             | Pass

        expr = 
               BinOp(expr left, operator op, expr right)
             | UnaryOp(unaryop op, expr operand)
             | Call(expr func, expr* args, keyword* keywords)
             | Num(object n)

             -- the following expression can appear in assignment context
             | Name(identifier id, expr_context ctx)

        operator = Add | Sub | Mult | Div
        unaryop = UAdd | USub
        expr_context = Load | Store | Del

        arguments = (arg* args, arg? vararg, arg* kwonlyargs, expr* kw_defaults,
                     arg? kwarg, expr* defaults)

        arg = (identifier arg, expr? annotation)
        keyword = (identifier? arg, expr value)
    }

We can see that, since assignment is a statement,
we have to address the interpretation of sequences of statements.
This is what leads us into careful consideration of data structures
around the interpreter.

We've included function definition in the ASDL,
not because we're ready to tackle functions properly,
but because important aspects of binding variable names only emerge
once nested scopes are considered.
It turns out that ``pass`` is a useful statement in generated examples.
It shouldn't be difficult to implement.

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
            default T visit_Expr(stmt.Expr _Expr){ return null; }
            default T visit_Pass(stmt.Pass _Pass){ return null; }
            default T visit_BinOp(expr.BinOp _BinOp){ return null; }
            default T visit_UnaryOp(expr.UnaryOp _UnaryOp){ return null; }
            default T visit_Call(expr.Call _Call){ return null; }
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
we will try to replicate the CPython interpreter,
:c:func:`PyEval_EvalCode` (or :c:func:`PyEval_EvalCodeEx`),
using the visitor internally.



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
Some "local" variables may be simultaneously local in multiple frames:
these are called cell variables.
They exist "off frame" in a holder object of type ``PyCell`` ,
and are accessed indirectly.
These name spaces are available as dictionaries (mappings)
through the functions :py:func:`local` and :py:func:`global`,
but usually code refers to variables through their names directly.
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
    def f():
        def g():
            def h():
                nonlocal x
                x = 42000
            pass
        x = 420
    x = 42
    """

In this program,
the name ``x`` in the scope defined by ``h``
refers to the same variable called ``x`` in the scope defined by ``f``,
but this is distinct from the ``x`` at the outermost (module) level.

As it was with the AST,
the CPython compiler is happy to show us its working out,
when it comes determining the scope of names.
The module that helps us out here is ``symtable``.
It will compile this source for us and return the symbol tables.
There is a symbol table for each scope,
and these tables nest in the same pattern as the scopes in the source::

    >>> import symtable
    >>> mst = symtable.symtable(prog, '<module>', 'exec')
    >>> mst
    <SymbolTable for top in <module>>
    >>> mst.lookup('x')
    <symbol 'x'>
    >>> mst.lookup('x').is_global()
    False
    >>> mst.get_children()[0].lookup('x')
    <symbol 'x'>

It may be surprising that ``x`` at the top level is not global,
but this is indeed the behaviour of Python.
If we take the trouble to supply local and global name spaces separately,
when we execute the code,
we can see the effect::

    >>> gbl, loc = {}, {}
    >>> exec(prog, gbl, loc)
    >>> loc
    {'f': <function f at 0x000001F08F9861E0>, 'x': 42}

Name access at the top level compiles to ``*_NAME`` instructions
that store locally and load from local, global or built-in name spaces::

    >>> import dis
    >>> dis.dis(compile(prog, '<module>', 'exec'))
      1           0 LOAD_CONST               0 (<code object f at 0x000001F08F98AE40, file "<module>", line 1>)
                  3 LOAD_CONST               1 ('f')
                  6 MAKE_FUNCTION            0
                  9 STORE_NAME               0 (f)
    
      8          12 LOAD_CONST               2 (42)
                 15 STORE_NAME               1 (x)
                 18 LOAD_CONST               3 (None)
                 21 RETURN_VALUE

Navigating the symbol tables by hand is tedious,
so there is a module at ``~/src/test/python/symbolutil.py``
that will help::

    >>> import symbolutil as su
    >>> su.show_module(mst)
    <SymbolTable for top in <module>>
      "f" : LOCAL, assigned, local, namespace
      "x" : LOCAL, assigned, local
    <Function SymbolTable for f in <module>>
      locals : ('x', 'g')
      "x" : CELL, assigned, local
      "g" : LOCAL, assigned, local, namespace
    <Function SymbolTable for g in <module>>
      locals : ('h',)
      frees : ('x',)
      "x" : FREE, free
      "h" : LOCAL, assigned, local, namespace
    <Function SymbolTable for h in <module>>
      frees : ('x',)
      "x" : FREE, assigned, free, local

We can see for each name in each scope the conclusion (in capitals)
reached by the CPython compiler about the scope of that name.
The five possibilities are:
``FREE``, ``LOCAL``, ``GLOBAL_IMPLICIT``, ``GLOBAL_EXPLICIT``, ``CELL``.
The other information (lowercase)
is the result of calling the informational methods e.g. ``is_assigned()``
on the symbol.
These are,
roughly-speaking,
the observations made by the compiler on how the name is used in that scope.
An interesting feature of this example is that,
although ``x`` is not mentioned at all in the scope of ``g``,
``x`` ends up a free variable in its symbol table,
because it is free in an enclosed scope.

We have to reproduce the correct logic in our processing of the AST.
We generate numerous little programs like the one above,
with various combinations of assignment, use and declaration,
and submit them to ``symtable``.
Thus we produce reference answers for all interesting combinations.
There is a program in the test source tree that does this
at ``~/src/test/python/symtable_testgen.py``,
and it generates the test material for
``~/src/test/.../treepython/TestInterp1.java``.

Deducing the scope of names
===========================
We can easily reproduce in Java the sort of data structures exposed by
``symtable``.
We take two passes over the source to resolve the scope of each name,
since we have to see all the uses of a name in order to decide.
The first pass is a visitor on the AST,
that builds the symbol tables and their observations.
An example of the processing is:

..  code-block::    java

    static class SymbolVisitor extends AbstractTreeVisitor<Void> {

        /** Description of the current block (symbol table). */
        protected SymbolTable current;
        /** Map from nodes that are blocks to their symbols. */
        final Map<Node, SymbolTable> blockMap;

        //...
        @Override
        public Void visit_FunctionDef(stmt.FunctionDef functionDef) {
            // Start a nested block
            FunctionSymbolTable child =
                    new FunctionSymbolTable(functionDef, current);
            blockMap.put(functionDef, child);
            // Function definition binds the name
            current.addChild(child);
            // Process the statements in the block
            current = child;
            try {
                return super.visit_FunctionDef(functionDef);
            } finally {
                // Restore context
                current = current.parent;
            }
        }

        // ...
        @Override
        public Void visit_Name(expr.Name name) {
            if (name.ctx == Load) {
                current.add(name.id, SymbolTable.Symbol.REFERENCED);
            } else {
                current.add(name.id, SymbolTable.Symbol.ASSIGNED);
            }
            return null;
        }

        // ...
    }

Here the ``blockMap`` member is a map
that will allow us to go from particular AST nodes,
during a subsequent walk of the AST,
to the symbol table for a given scope.
It is a non-intrusive way of extending the data available at each node.
``SymbolTable.add()`` makes a new entry if necessary,
but importantly it keeps track of how the name has been used.

The second pass is actually a walk of the symbol table tree itself,
and it picks up the observations made in the first pass.
Some observations are decisive, just looking at the symbol table entry:

..  code-block::    java

        static class Symbol {

            final String name;
            /** Properties collected by scanning the AST for uses. */
            int flags;
            /** The final decision how the variable is accessed. */
            ScopeType scope = null;
            // ...

            boolean resolveScope() {
                if ((flags & GLOBAL) != 0) {
                    scope = ScopeType.GLOBAL_EXPLICIT;
                } else if ((flags & NONLOCAL) != 0) {
                    scope = ScopeType.LOCAL;
                    return false;
                } else if ((flags & BOUND) != 0) {
                    scope = ScopeType.LOCAL; // or CELL ultimately
                }
                return scope != null;
            }
        }

However, when that method can't decide (returns ``false``),
we must walk up the enclosing scopes looking for a valid referent.
This happens in the ``SymbolTable`` class itself:

..  code-block::    java

    static abstract class SymbolTable {

        abstract boolean fixupFree(String name);

        void resolveAllSymbols() {
            for (SymbolTable.Symbol s : symbols.values()) {
                // The use in this block may resolve itself immediately
                if (!s.resolveScope()) {
                    // Not resolved: used free or is explicitly nonlocal
                     if (isNested() && parent.fixupFree(s.name)) {
                        // Appropriate referent exists in outer scopes
                        s.setScope(ScopeType.FREE);
                    } else if ((s.flags & Symbol.NONLOCAL) != 0) {
                        // No cell variable found: but declared non-local
                        throw new IllegalArgumentException(
                                "undefined non-local " + s.name);
                    } else {
                        // No cell variable found: assume global
                        s.setScope(ScopeType.GLOBAL_IMPLICIT);
                    }
                }
            }
        }

        /**
         * Apply {@link #resolveAllSymbols()} to the current scope and then
         * to child scopes recursively. Applied to a module, this completes
         * free variable fix-up for symbols used throughout the program.
         */
        protected void resolveSymbolsRecursively() {
            resolveAllSymbols();
            for (SymbolTable st : children) {
                st.resolveSymbolsRecursively();
            }
        }

    }

``SymbolTable`` has different subclasses for a module and a function definition
(and there would be one for class definition if we were ready for it).
The abstract method ``fixupFree`` takes care of the difference in search rules.
It is only interesting in the case of a function scope:

..  code-block::    java

    static class FunctionSymbolTable extends SymbolTable {
        // ...
        @Override
        boolean fixupFree(String name) {
            // Look up in this scope
            SymbolTable.Symbol s = symbols.get(name);
            if (s != null) {
                /*
                 * Found name in this scope: but only CELL, FREE or LOCAL
                 * are allowable.
                 */
                switch (s.scope) {
                    case CELL:
                    case FREE:
                        // Name is CELL here or in an enclosing scope
                        return true;
                    case LOCAL:
                        // Bound here, make it CELL in this scope
                        s.setScope(ScopeType.CELL);
                        return true;
                    default:
                        /*
                         * Any other scope value is not compatible with the
                         * alleged non-local nature of this name in the
                         * original scope.
                         */
                        return false;
                }
            } else {
                /*
                 * The name is not present in this scope. If it can be
                 * found in some enclosing scope then we will add it FREE
                 * here.
                 */
                if (parent.fixupFree(name)) {
                    s = add(name, 0);
                    s.setScope(ScopeType.FREE);
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

This is the bit of code that ensures intervening scopes are given
free copies of variables that are FREE in enclosed scopes
and CELL in an enclosing scope.

Critical Structures -- First Implementation
*******************************************
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
we'll try to cvreate an implementation that supports this multiplicity,
although we will not test thoroughly that we've achieved that.


``PyCode``
==========
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
It holds the linked list of frames,
and a reference to the interpreter state.
Most importantly, it is the double of an operating system thread.
At many places in CPython,
the C code does not carry interpreter context as an argument,
but relies on a global pointer to the current ``PyThreadState``,
that changes according to the OS thread that holds the GIL
(Global Interpreter Lock).
It is relatively easy to see in CPython how to do this correctly,
since only one thread may execute Python code at a time:
the one holding the GIL.
There are a few carefully-chosen places where the GIL is surrendered.
In a JVM implementation of Python,
we must achieve the same quality of correctness
(in relation to ``java.lang.Thread`` instances),
but this is more difficult since
Jython aims for a much less restrictive kind of concurrency.

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
Jython may have chosen the more accurate name,
since it also implements the ``sys`` module in that class.)
The interpreter state holds references to key universal name spaces,
the global name space,
the ``sys`` module,
the module list itself, and
standard codecs.
In principle, there could be multiple instances concurrently.
This is rare in CPython
but quite likely when Jython is used in a Java application server.

