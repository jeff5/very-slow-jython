..  treepython/simple_statements.rst


Simple Statements and Assignment
################################

Aims of this Section
********************
In this section we'll find a way to implement sequences of statements,
representing module or function bodies.
We'll concentrate on assignment to local, non-local and global variables.
Previous work provides the "right hand side" for our assignments and
we shall have a simple subset of function calls.

Like most languages,
Python code finds its local variables in a ``frame`` object,
where one frame is created dynamically per function invocation.
The layout of the frame is specific to each function,
and is specified statically in the ``code`` object
that results from compiling that function.
In order to generate the right specification in the ``code`` object,
the compiler has to collect information on each variable
in a ``symtable`` (symbol table) object.

The programme of work for this chapter
is implementation of Java equivalents to these three objects,
taken in reverse order, ``symtable``, ``code`` and ``frame``.


Additions to the AST
********************
ASDL
====
In order to represent the statements we need,
we advance the ASDL of TreePython to this:

..  code-block:: none

    module TreePython
    {
        mod = Module(stmt* body)

        stmt = FunctionDef(identifier name, arguments args,
                             stmt* body, expr* decorator_list, expr? returns)
             | Return(expr? value)
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
             | Str(string s)

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

We can see that modules and function definitions have a body attribute
that is a *sequence* of statements.
This is what leads us into careful consideration of data structures
known in Python as ``code`` objects (to hold the sequence of statements)
and ``frame`` objects (to hold local variable state).

We've included function definition in the ASDL, and call and return,
because important aspects of the binding of variable names only emerge
once nested scopes are considered.
It turns out that ``pass`` is also a useful statement in generated examples,
and not difficult to implement.

Visitor
=======
The ASDL above, gives rise to a set of class definitions,
of a predictable type,
and the following visitor interface:

..  code-block:: java

    public interface Visitor<T> {
        default T visit_Module(mod.Module _Module){ return null; }
        default T visit_FunctionDef(stmt.FunctionDef _FunctionDef){ return null; }
        default T visit_Return(stmt.Return _Return){ return null; }
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
        default T visit_Str(expr.Str _Str){ return null; }
        default T visit_Name(expr.Name _Name){ return null; }
        default T visit_arguments(arguments _arguments){ return null; }
        default T visit_arg(arg _arg){ return null; }
        default T visit_keyword(keyword _keyword){ return null; }
    }

Previously we evaluated expressions using a class ``Evaluator``
that implemented the visitor interface.
We'll do something like that again,
basing work on an examination of the CPython interpreter,
:c:func:`PyEval_EvalCode` (or :c:func:`PyEval_EvalCodeEx`).


A Look at Variable Scope in CPython
***********************************

Local and Global Namespaces
===========================

In Python,
the execution context of a block of code is equipped with two name spaces:
local and global.
Global variables are easily understood: they are always a dictionary,
like the one we used during our experiments to implement expressions.
This is almost always the dictionary of the module containing the code.

These name spaces (global and local) are available as dictionaries (mappings)
through the functions :py:func:`locals` and :py:func:`globals`,
but usually code refers to variables by name directly.

The realisation of local variables differs
according to the context of the source code.
It may be a mapping of names to values,
or be stored as an array directly within the ``frame`` object.
In CPython byte code,
different instructions are used to access local variables,
according to the realisation chosen.
In general, at *module* level,
the Python will choose a mapping for its locals,
and that mapping will be the same dictionary that holds the globals.
A *function* will compile to use the efficient, frame-based array.

When executing any code using the :py:func:`eval` function,
one can supply separate local and global dictionaries explicitly.

Some "local" variables may be local to one frame,
but also accessed by code in lexically-nested scopes that have their own frames.
These are called "cell variables".
They exist "off frame" in a holder object of type ``Cell`` ,
refrenced by every frame that may need them.

Overall, there are roughly 4 types of variable access in Python,
and within each, load, store and delete operations:

+--------+-----------------------------+------------------------------+
| mode   | location                    | interpreter action           |
+========+=============================+==============================+
| name   | search of local, global or  | Load/delete where found      |
|        | the ``__builtins__`` module | (store is always local).     |
|        |                             | Used in compiled module.     |
+--------+-----------------------------+------------------------------+
| fast   | in the ``frame``            | Access locally.              |
|        |                             | Used in compiled function.   |
+--------+-----------------------------+------------------------------+
| cell   | shared between frames       | Access indirectly through    |
|        |                             | ``PyCell``                   |
+--------+-----------------------------+------------------------------+
| global | in the defining module      | Access via global dictionary |
|        |                             | reference (normally module)  |
+--------+-----------------------------+------------------------------+

The bare-essential Java implementation of ``frame`` will look like this:

..  code-block:: java

    private static class Frame {

        /** Frames form a stack by chaining through the back pointer. */
        final Frame f_back;
        /** Code this frame is to execute. */
        final Code f_code;
        /** Global context (name space) of execution. */
        final Map<String, Object> f_globals;
        /** Local context (name space) of execution. (Assign if needed.) */
        Map<String, Object> f_locals = null;
        /** Built-in objects */
        final Map<String, Object> f_builtins;
        /** Local simple variables (corresponds to "varnames"). */
        Object[] fastlocals = null;
        /** Local cell variables: concatenation of cellvars & freevars. */
        Cell[] cellvars = null;
        // ...
    }

We need some processing that decides how to allocate variables in
the ``fastlocals`` and ``cellvars`` arrays.

.. note::

   In order to access the project-specific tools
   used in the Python examples in this section,
   put the directory ``~/src/test/python`` on the ``sys.path``,
   for example via the environment variable ``PYTHONPATH``.

Generating the Layout
*********************

Symbol Tables
=============

Help from the CPython Compiler
------------------------------

When we create an AST node implying a load or store operation,
only the variable name is apparent in the node:
the storage type is not identified.
We have to look at the tree as a whole
in order to work out which mode is appropriate in each place.
The CPython compiler is happy to show us
its decisions about the scope of names (hence the type of access).
See also `Naming and binding`_ in the Python Language Reference.

..  _Naming and binding:
    https://docs.python.org/3/reference/executionmodel.html#naming-and-binding

We need quite a complex example to explore this subject at the Python prompt::

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

It may be surprising that ``x`` at the top level is not global.
If we take the trouble to supply local and global name spaces separately,
when we execute the code,
we can see the effect::

    >>> gbl, loc = {}, {}
    >>> exec(prog, gbl, loc)
    >>> loc
    {'f': <function f at 0x000001F08F9861E0>, 'x': 42}

Name access at the top level compiles to ``*_NAME`` instructions
that load from local, global or built-in name spaces,
but store only into the local one::

    >>> import dis
    >>> dis.dis(compile(prog, '<module>', 'exec'))
      1           0 LOAD_CONST               0 (<code object f ... >)
                  3 LOAD_CONST               1 ('f')
                  6 MAKE_FUNCTION            0
                  9 STORE_NAME               0 (f)

      8          12 LOAD_CONST               2 (42)
                 15 STORE_NAME               1 (x)
                 18 LOAD_CONST               3 (None)
                 21 RETURN_VALUE

Navigating the symbol tables by hand is tedious,
but there is a module at ``~/src/test/python/symbolutil.py``
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

For each name, in each scope, we can see the conclusion (in capitals)
reached by the CPython compiler about the scope of that name.
The five possibilities are:
``FREE``, ``LOCAL``, ``GLOBAL_IMPLICIT``, ``GLOBAL_EXPLICIT``, ``CELL``.
The other information (lowercase)
is the result of calling the informational methods e.g. ``is_assigned()``
on the symbol.
These access the observations made by the compiler
of how the name is used in that lexical scope.
An interesting feature of this example is that,
although ``x`` is not mentioned at all in the scope of ``g``,
``x`` ends up a free variable in its symbol table,
because it is free in an enclosed scope.


Symbol Tables in Java
---------------------

We can easily reproduce in Java the sort of data structures exposed by
``symtable``.
But we have to fill them in using the correct logic on the AST.

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
that will allow us to go from particular AST nodes during a subsequent walk,
to the symbol table for a given scope.
It is a non-intrusive way of extending the data available at each node.
``SymbolTable.add()`` makes a new entry if necessary,
but importantly it keeps track of how the name has been used.

The second pass is actually a walk of the symbol table tree itself,
and it picks up the observations made in the first pass.
Some observations are decisive, by just looking at the symbol table entry:

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

In order to test our work,
we generate numerous little programs like the introductory example,
with various combinations of assignment, use and declaration,
and submit them to ``symtable``.
Thus we produce reference answers for all interesting combinations.
There is a program in the test source tree that does this
at ``~/src/test/python/symtable_testgen.py``,
and it generates the test material for
``~/src/test/.../treepython/TestInterp1.java``,
from which the Java code snippets were taken.


Code Objects
============

Having decided from the AST which names in a given lexical scope
are bound or free, global or local, and where cells must be created,
we have enough information to place them in the frame storage of that scope.
A ``frame`` object only exists while the function executes.
(It is dynamic.)
Therefore the storage specification appears statically in a ``code`` object.

In CPython
----------

We've encountered the Python ``code`` object as the result of compilation,
as the executable form of a module acceptable to :py:func:`exec`.
Turning to our example program again, and its nested functions,
we see that when its compiled code is disassembled,
it only shows instructions for the module level::

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
    >>> progcode = compile(prog, '<module>', 'exec')
    >>> dis.dis(progcode)
      1           0 LOAD_CONST               0 (<code object f ... >)
                  3 LOAD_CONST               1 ('f')
                  6 MAKE_FUNCTION            0
                  9 STORE_NAME               0 (f)

      8          12 LOAD_CONST               2 (42)
                 15 STORE_NAME               1 (x)
                 18 LOAD_CONST               3 (None)
                 21 RETURN_VALUE

Where are the code objects for the nested functions?

``code`` objects form a nested structure,
parallel with the symbol tables we investigated.
The ``code`` object for ``f`` appears as a constant in the first instruction,
pushed to the stack for the ``MAKE_FUNCTION`` instruction to consume.
Remember, a function definition is an executable statement in Python,
in which the suite (body) is a sort of argument,
a structured constant created in the compiler.
In our example ``co_consts[0]`` contains the code for ``f``::

   >>> progcode.co_consts[0]
   <code object f at 0x000001941F6001E0, file "<module>", line 1>
   >>> dis.dis(progcode.co_consts[0])
     2           0 LOAD_CLOSURE             0 (x)
                 3 BUILD_TUPLE              1
                 6 LOAD_CONST               1 (<code object g ... >)
                 9 LOAD_CONST               2 ('f.<locals>.g')
                12 MAKE_CLOSURE             0
                15 STORE_FAST               0 (g)

     7          18 LOAD_CONST               3 (420)
                21 STORE_DEREF              0 (x)
                24 LOAD_CONST               0 (None)
                27 RETURN_VALUE

There is a project-specific tool in ``~/src/test/python/codeutil.py``
(put the directory on ``sys.path``)
that will dump out the tree of ``code`` objects and the attributes
that define each ``frame`` they create::

   >>> import codeutil as cu
   >>> cu.show_code(prog)
   Code block: <module>
       co_argcount  : 0
       co_kwonlyargcount : 0
       co_nlocals   : 0
       co_name      : '<module>'
       co_names     : ('f', 'x')
       co_varnames  : ()
       co_cellvars  : ()
       co_freevars  : ()
   Code block: f
       co_argcount  : 0
       co_kwonlyargcount : 0
       co_nlocals   : 1
       co_name      : 'f'
       co_names     : ()
       co_varnames  : ('g',)
       co_cellvars  : ('x',)
       co_freevars  : ()
   Code block: g
       co_argcount  : 0
       co_kwonlyargcount : 0
       co_nlocals   : 1
       co_name      : 'g'
       co_names     : ()
       co_varnames  : ('h',)
       co_cellvars  : ()
       co_freevars  : ('x',)
   Code block: h
       co_argcount  : 0
       co_kwonlyargcount : 0
       co_nlocals   : 0
       co_name      : 'h'
       co_names     : ()
       co_varnames  : ()
       co_cellvars  : ()
       co_freevars  : ('x',)

The symbol tables we have already created contain the usage information,
each for its own block.

The tuples of names
``co_names``, ``co_varnames``, ``co_cellvars`` and ``co_freevars``
are created from the symbol table to describe the allocation of variables
in the frame.
In CPython byte code,
local variable access uses numerical indexes directly into arrays in the frame.
Only occasionally are the names contained in
``co_varnames``, ``co_cellvars`` and ``co_freevars`` actually consulted.
The names in ``co_names``, in contrast,
*are* used as constants to access globals and built-ins.

In CPython, storage is allocated contiguously
(as a block of ``PyObject *`` pointers)
and the interpreter in ``ceval.c`` defines pointers into it like this:

+-----------------------+-----------------+---------------------------+----------------+
| pointer name          | variables       | use                       | type           |
+=======================+=================+===========================+================+
| ``fastlocals``        | ``co_varnames`` | * positional arguments    | ``PyObject *`` |
|                       |                 | * keyword only arguments  |                |
|                       |                 | * ref varargs tuple       |                |
|                       |                 | * ref keyword dictionary  |                |
|                       |                 | * local variables         |                |
+-----------------------+-----------------+---------------------------+----------------+
| ``freevars``          | ``co_cellvars`` | names referred to in a    | ``PyCell *``   |
| (we call this         |                 | nested scope, for which   |                |
| ``cellvars``)         |                 | this one is outermost     |                |
+-----------------------+-----------------+---------------------------+----------------+
| (from ``TestInterp5`` | ``co_freevars`` | names that refer to       | ``PyCell *``   |
| we will call this     |                 | variables in an enclosing |                |
| ``freevars``          |                 | scope, used here or or    |                |
|                       |                 | in an enclosed scope      |                |
+-----------------------+-----------------+---------------------------+----------------+
| ``stack_pointer``     |                 | value stack               | ``PyObject *`` |
+-----------------------+-----------------+---------------------------+----------------+

We'll do something similar in Java, except
we do not need a value stack (in the frame), and
it suits Java better to have distinct arrays of ``Object``\ s and ``Cell``\ s.
Note that the names (first column) are local variables in the CPython
interpreter,
not names visible to Python.
(We make them interpreter state variables.)


In Java
-------

Our Java implementation object ``Code``
is very similar to the CPython ``PyCodeObject``,
since many of its attributes are visible at the Python level.

..  code-block::    java

    private static class Code {
        static class ASTCode {
            final List<stmt> body;
            final SymbolTable symbolTable;
            final Map<Node, Code> codeMap;
            // ...
        }

        /** Characteristics of a Code (as CPython co_flags). */
        enum Trait { OPTIMIZED, NEWLOCALS, VARARGS, VARKEYWORDS }
        final EnumSet<Trait> traits;

        /** Suite and symbols that are to us the executable code. */
        final ASTCode ast;

        /** Number of positional arguments (not counting varargs). */
        final int argcount;
        /** Number of keyword-only arguments (not counting varkeywords). */
        final int kwonlyargcount;
        /** Number of local variables. */
        final int nlocals;

        final Object[] co_consts;   // constant objects needed by the code
        final String[] co_names;    // names referenced in the code
        final String[] co_varnames; // args and non-cell locals
        final String[] co_freevars; // names ref'd but not defined here
        final String[] co_cellvars; // names def'd here & ref'd elsewhere

        final String co_name; // name of function etc.
        // ... 
    }

The significant difference from CPython is that,
where that has an array of byte code,
we store instead a bundle containing a list of AST statement nodes,
the symbol table, and a mapping (``ASTCode``).

The mapping is from AST nodes to ``code`` objects,
applicable to modules and function definitions.

The symbol table is present so that we can look up the correct mechanism
to access the variable named in the AST node,
but we also need to know its actual address.
For this purpose we add two fields to each symbol:

..  code-block::    java

        static class Symbol {
            // ...
            int index = -1;
            int cellIndex = -1;
            // ...
        }

These are set when we generate the ``Code`` object.
We need two fields because
a parameter that is free in an enclosed block must be a cell as well,
so it has both a cell and a regular location written in the call.

In the AST interpreter, we could almost dispense with the name arrays
because the AST node conains the name,
and our symbol table contains the location.
But they are a visible part of the ``code`` object and we use them for test.

There is a program in the test source tree
with an initial implementation of ``Code`` and a ``CodeGenerator``,
``~/src/test/.../treepython/TestInterp2.java``,
from which the next few Java code snippets were taken.
The program ``~/src/test/python/symtable_testgen.py``
generates the test material found at the end of it.

The code generator is a visitor on the AST,
but it does not generate executable instructions as it might in a real compiler.
It simply accumulates the lists that will become
``co_varnames``, ``co_cellvars``, ``co_freevars`` and ``co_names``,
and collects a few other pieces of data,
and at the end it calls the ``Code`` constructor.

..  code-block::    java

    private static class CodeGenerator extends AbstractVisitor<Void> {

        /** Mapping to the symbol table of any block. */
        private final Map<Node, SymbolTable> scopeMap;

        List<stmt> body;
        SymbolTable symbolTable;
        private String name;

        private final Map<Node, Code> codeMap = new HashMap<>();
        Set<Code.Trait> traits = EnumSet.noneOf(Code.Trait.class);
        int argcount;
        int kwonlyargcount;

        List<Object> consts = new LinkedList<>();
        List<String> names = new LinkedList<>();
        List<String> varnames = new LinkedList<>();
        List<String> cellvars = new LinkedList<>();
        List<String> freevars = new LinkedList<>();

        private int localIndex = 0;
        private int cellIndex = 0;
        private int nameIndex = 0;

        CodeGenerator(Map<Node, SymbolTable> scopeMap) {
            this.scopeMap = scopeMap;
        }

        Code getCode() {
            Code.ASTCode raw = new Code.ASTCode(body, symbolTable, codeMap);
            Code code = new Code( //
                    argcount, kwonlyargcount, localIndex, // sizes
                    traits, // co_flags
                    raw, // co_code
                    consts, // co_consta
                    names, varnames, freevars, cellvars, // co_* names
                    name // co_name
            );
            return code;
        }
        // ...
    }

For this it only needs to dip into module and function definitions.
This visit method for a module,
which is also our entrypoint to the whole process,
is quite simple:

..  code-block::    java

    private static class CodeGenerator extends AbstractVisitor<Void> {
        //...
        @Override
        public Void visit_Module(mod.Module module) {
            // Process the associated block scope from the symbol table
            symbolTable = scopeMap.get(module);
            body = module.body;
            name = "<module>";

            // Walk the child nodes: some define functions
            super.visit_Module(module);

            // Fill the rest of the frame layout from the symbol table
            finishLayout();

            // The code currently generated is the code for this node
            codeMap.put(module, getCode());
            return null;
        }
        //...
    }

When it encounters a function definition,
in order to create a new scope, with its own ``varnames`` etc.,
it creates a new ``CodeGenerator`` starting at the same node.
The visit operation thus has to have two definitions,
selected according to the state.

..  code-block::    java

    private static class CodeGenerator extends AbstractVisitor<Void> {
        //...
        @Override
        public Void visit_FunctionDef(stmt.FunctionDef functionDef) {
            // This has to have two behaviours
            if (symbolTable != null) {
                /*
                 * We arrived here while walking the body of some block.
                 * Start a nested code generator for the function being
                 * defined.
                 */
                CodeGenerator codeGenerator = new CodeGenerator(scopeMap);
                functionDef.accept(codeGenerator);
                // The code object generated is the code for this node
                Code code = codeGenerator.getCode();
                codeMap.put(functionDef, code);
                addConst(code);

            } else {
                /*
                 * We are a nested code generator that just began this
                 * node. The work we do is in the nested scope.
                 */
                symbolTable = scopeMap.get(functionDef);
                body = functionDef.body;
                name = functionDef.name;

                // Local variables will be in arrays not a map
                traits.add(Code.Trait.OPTIMIZED);
                // And the caller won't supply a local variable map
                traits.add(Code.Trait.NEWLOCALS);

                // Visit the parameters, assigning frame locations
                functionDef.args.accept(this);

                /*
                 * Walk the child nodes assigning frame locations to names.
                 * Some statements will define further functions
                 */
                visitAll(functionDef.body);

                // Fill the rest of the frame layout from the symbol table
                finishLayout();
            }
            return null;
        }
        //...
    }

Only the position of parameters may be decided during the walk of
a single ``CodeGenerator``.
(This is invoked by the line ``functionDef.args.accept(this)`` above.)
At the end, when the number of parameters and cell variables is known,
a little more processing is necessary to position the rest.
We saw this called in both the module and function definition visits.

..  code-block::    java

    private static class CodeGenerator extends AbstractVisitor<Void> {
        //...

        private void finishLayout() {

            // Defer FREE variables until after (bound) CELLs.
            List<SymbolTable.Symbol> free = new LinkedList<>();

            // Iterate symbols, assigning their offsets (except if FREE).
            for (SymbolTable.Symbol s : symbolTable.symbols.values()) {
                switch (s.scope) {
                    case CELL:
                        addCell(s);
                        break;
                    case FREE:
                        free.add(s);
                        break;
                    case GLOBAL_EXPLICIT:
                    case GLOBAL_IMPLICIT:
                        if (s.is_assigned() || s.is_referenced()) {
                            addName(s);
                        }
                        break;
                    case LOCAL:
                        // Parameters were already added in the walk
                        if (!s.is_parameter()) {
                            if (symbolTable.isNested()) {
                                addLocal(s);
                            } else {
                                addName(s);
                            }
                        }
                        break;
                }
            }

            // Allocate the FREE variables after the (bound) CELL.
            for (SymbolTable.Symbol s : free) {
                addCell(s);
            }
        }
        //...
    }

The helper functions used are not shown.
For the full story,
visit ``~/src/test/.../treepython/TestInterp2.java`` in the code base.


The Frame
=========

In CPython
----------

As we have seen in the discussion  of ``code`` objects and the symbol table,
when executing code,
the interpreter creates a stack of ``frame`` objects,
each one being the execution context of the current module
or function invocation.

Just for completeness, let's see this in action.
``frame`` objects occur in the stack traces of exceptions,
and as the state of a generator::

   >>> def g(a, b):
           n = a
           while n < b:
               yield n
               n += 1

   >>> x = g(10, 20)
   >>> x.gi_frame.f_locals
   {'a': 10, 'b': 20}

Here we see that the generator instance ``x`` contains a frame.
In its locals are the parameters ``a`` and ``b`` with the values we gave them.
The generator is poised at the start of the function.
(``n`` has not yet been given a value, so does not appear.)
Now let's step that a couple of times::

   >>> next(x)
   10
   >>> next(x)
   11
   >>> x.gi_frame.f_locals
   {'a': 10, 'n': 11, 'b': 20}

The generator is now poised at the end of the second execution of ``yield``,
where ``n`` has the value just yielded through ``next()``.
We'll move on swiftly to the Java implementation.


Java Frame and Execution Loop
-----------------------------

A data structure equivalent to that in CPython is easy enough to define.

..  code-block::    java

    private static class Frame {

        final Frame f_back;
        final Code f_code;
        final Map<String, Object> f_globals;
        Map<String, Object> f_locals = null;
        final Map<String, Object> f_builtins;
        Object[] fastlocals = null;
        Cell[] cellvars = null;

        /** Partial constructor, leaves optional fields null. */
        Frame(Frame back, Code code,
                Map<String, Object> globals) {
            f_code = code;
            f_back = back;
            f_globals = globals;
            f_builtins = new HashMap<>();
        }
    }

A study of the CPython interpreter in ``ceval.c`` shows it, naturally,
to be very busy with the fields of the ``frame``.
It takes several local variables that are pointers into this frame,
or local shadows of its properties, for efficiency.
In the same source are functions that set up frames
for :py:func:`eval`, or :py:func:`exec`, or in support of function calls.
These populate the parameters from argument lists
and prepare the frame for execution.

In Java we try to combine state and behaviour one object.
As evaluation is so intimately involved with the frame,
we shall make the interpreter the *behaviour* of a ``Frame``.
In aid of this, we will make ``Frame`` abstract
and add a constructor and an abst5ract method:

..  code-block::    java

    private static abstract class Frame {
        // ... 

        Frame(Frame back, Code code, Map<String, Object> globals,
                Map<String, Object> locals, List<Cell> closure) {
            this(back, code, globals);
            // ... initialise according to Python rules and code object
        }

        /** Execute the code in this frame. */
        abstract Object eval();
    }

The additional constructor takes on some of the job of preparing the frame,
according to Python rules for several scenarios
(run a module, ``eval(code,globals,locals)``, call a function).
In order to keep the specific implementation private,
from code that handles frames as Python objects,
and to allow alternative implementations to mix,
we'll define a subclass ``ExecutionFrame``.
Its implementation is the subject of the next section.


Extending the AST Interpreter
*****************************

The Java implementation of ``Frame`` is first introduced in
``~/src/test/.../treepython/TestInterp3.java`` in the code base.
The work in this section is demonstrated there, with test material generated by
``~/src/test/python/variable_access_testgen.py``.

Foundation
==========

We have found we can interpret the AST by application of a visitor pattern.
``ExecutionFrame`` therefore not only extends ``Frame`` but implements the
AST visitor methods.
Expression evaluation is taken care of by the code we developed in a previous
chapter, but ``ExecutionFrame`` does not (yet) use dynamic language features
anywhere else.
The foundation consists of constructors for the two main use cases
(module and function call), and an implementation of ``eval``:

..  code-block::    java

    private static class ExecutionFrame extends Frame
            implements Visitor<Object> {

        /** Assigned eventually by return statement (or stays None). */
        Object returnValue = Py.NONE;
        /** Access rights of this class. */
        Lookup lookup = lookup();

        ExecutionFrame(Frame back, Code code, Map<String, Object> globals,
                List<Cell> closure) {
            super(back, code, globals, null, closure);
        }

        ExecutionFrame(Frame back, Code code, Map<String, Object> globals,
                Map<String, Object> locals) {
            super(back, code, globals, locals, null);
        }

        // ... other API  ...
        // ... visit methods ...

        @Override
        Object eval() {
            for (stmt s : f_code.ast.body) s.accept(this);
            return returnValue;
        }
    }

The implementation of ``eval`` takes a list of AST statements
that are the body of a module or function,
and executes them sequentially.
If one of them is a return statement, it will set the method's return value.

We want to call an instance of this to execute a module.
We will supply a global dictionary, as if we were running a program.

..  code-block::    java

    private static void executeTest(mod module, ... ) {

        // Compile the test AST
        Code code = compileAST(module, "<module>");

        // Set up globals to hold result
        Map<String, Object> globals = new HashMap<>();

        // Compare pythonrun.c:run_mod()
        Frame frame = new ExecutionFrame(null, code, globals, globals);
        frame.eval();
    }

In this call,
the back pointer is ``null`` because the stack is empty
and this is to be the first frame.


Assignment
==========

Loading from a name is more complex now that we have frame locals.
A variable is named in the AST node,
but rather than look it up in a simple dictionary,
we must go to the symbol table in order to determine the type of access
and (for local variables) to locate the particular storage in the frame.


..  code-block::    java

    private static class ExecutionFrame extends Frame
            implements Visitor<Object> {
        // ...
        @Override
        public Object visit_Name(expr.Name name) {

            SymbolTable.Symbol symbol = f_code.ast.symbolTable.lookup(name.id);

            // Storage mechanism depends on scope of name & OPTIMIZED trait
            switch (symbol.scope) {
                case LOCAL:
                    if (f_code.traits.contains(Code.Trait.OPTIMIZED)) {
                        return fastlocals[symbol.index];
                    } else { // compare CPython LOAD_NAME opcode
                        Object v = f_locals.get(name.id);
                        if (v == null && f_globals != f_locals) {
                            v = f_globals.get(name.id);
                        }
                        if (v == null) {
                            v = f_builtins.get(name.id);
                        }
                        return v;
                    }
                case CELL:
                case FREE:
                    return cellvars[symbol.cellIndex].obj;
                default: // GLOBAL_*
                    return f_globals.get(name.id);
            }
        }

        // ... other operations
        // ... unary & binary operations as previos work
    }

Notice that code has an attribute that determines what LOCAL access means:
whether we may rely on the ``fastlocals`` array or
have to use a series of dictionaries.
In CPython compiled to byte code, this choice is made at compile time,
and decides between the ``LOAD_FAST`` and ``LOAD_NAME`` instructions.
(Equally, in Jython compiled to Java byte code,
it can be decided at compile time where to seek the value.)

Assignment follows a simiar pattern,
but this time we encapsulate the actual assignment as a private method
for re-use with functions (next).

..  code-block::    java

    private static class ExecutionFrame extends Frame
            implements Visitor<Object> {
        // ...
        @Override
        public Object visit_Assign(Assign assign) {

            Object value = assign.value.accept(this);
            if (assign.targets.size() != 1) {
                throw notSupported("unpacking", assign);
            }
            expr target = assign.targets.get(0);
            if (!(target instanceof expr.Name)) {
                throw notSupported("assignment to complex lvalue", assign);
            }

            assign(((expr.Name)target).id, value);
            return null;
        }

        /** Assign value to name according to its storage type */
        private void assign(String name, Object value) {
            SymbolTable.Symbol symbol =
                    f_code.ast.symbolTable.lookup(name);
            // Storage mechanism depends on scope of name & OPTIMIZED trait
            switch (symbol.scope) {
                case LOCAL:
                    if (f_code.traits.contains(Code.Trait.OPTIMIZED)) {
                        fastlocals[symbol.index] = value;
                    } else {
                        f_locals.put(name, value);
                    }
                    break;
                case CELL:
                case FREE:
                    cellvars[symbol.cellIndex].obj = value;
                    break;
                default: // GLOBAL_EXPLICIT, GLOBAL_IMPLICIT:
                    f_globals.put(name, value);
                    break;
            }
        }
        // ... unary & binary operations as previous work
        // ... other operations
    }


Function Definition
===================

As frequently remarked, function definition is an executable statement:
when execution reaches it, a function object is created and stored.
It is therefore somewhat like assignment,
but the effective right-hand side is a ``function`` object.

A Python ``function`` is created from

 * the ``code`` object,
 * a reference to the current global dictionary,
 * default values supplied in the function heading, and
 * references to (cell) variables free in the function body (the "closure").

Code and globals are straightforward.
We're not ready to support default values (or keywords) yet,
but we have prepared for the creation of the closure with the ``Cell`` type.

..  code-block::    java

    private static class Function implements PyCallable {

        final String name;
        final Code code;
        final Map<String, Object> globals;
        final List<Cell> closure;

        Function(Code code, Map<String, Object> globals, String name,
                List<Cell> closure) {
            this.code = code;
            this.globals = globals;
            this.name = name;
            this.closure = closure;
        }

        @Override
        public Object call(Frame back, Object[] args) {
            // Execution occurs in a new frame
            ExecutionFrame frame =
                    new ExecutionFrame(back, code, globals, closure);
            // In which we initialise the parameters from arguments
            frame.setArguments(args);
            return frame.eval();
        }
        // ...
    }

Also in that listing may be seen
how the function creates a new frame when called.
We'll come back to that.

..  code-block::    java

    private static class ExecutionFrame extends Frame
            implements Visitor<Object> {

        // ...
        @Override
        public Object visit_FunctionDef(FunctionDef def) {
            // The code object is present as a constant in the codeMap.
            Code targetCode = f_code.ast.codeMap.get(def);
            List<Cell> closure = closure(targetCode);
            Function func = new Function(targetCode, f_globals,
                    targetCode.co_name, closure);
            assign(def.name, func);
            return null;
        }

        /**
         * Obtain the cells that should be wrapped into a function
         * definition.
         */
        private List<Cell> closure(Code targetCode) {
            int nfrees = targetCode.co_freevars.length;
            if (nfrees == 0) {
                // No closure necessary
                return null;
            } else {
                SymbolTable localSymbols = f_code.ast.symbolTable;
                List<Cell> closure = new ArrayList<>(nfrees);
                for (int i = 0; i < nfrees; i++) {
                    String name = targetCode.co_freevars[i];
                    SymbolTable.Symbol symbol = localSymbols.lookup(name);
                    int n = symbol.cellIndex;
                    closure.add(cellvars[n]);
                }
                return closure;
            }
        }
        // ...
    }

The only difficult part is the creation of the closure.
The target code block (corresponding to the function created)
names certain variables as free (``co_freevars``).
We have to supply a matching list of cell variables,
by looking up the names in the symbol table.
(This look-up is another piece of work that belongs properly to compile time.)
The function object now holds references to these cells,
and therefore sees the variables in the closure change
as other code modifies them,
even if the frame that created them is garbage-collected.

When we finally call this function, and create its frame,
the frame constructor places these cell variables in the second section of
the cell variables.

Call
====

The final group of visit methods we need to discuss are those used in
actual function calls.
Remember that the ``function`` object implements a Python-callable interface,
and a call method.
We do this to allow ``call`` to be implemented many ways,
but here we are concerned only with functions defined in Python.
The called function therefore sets up a new ``ExecutionFrame``
in which to execute the function body,
and fills in those local variables that are parameters.

..  code-block::    java

    private static class Function implements PyCallable {
        // ...
        @Override
        public Object call(Frame back, List<Object> args) {
            // Execution occurs in a new frame
            ExecutionFrame frame =
                    new ExecutionFrame(back, code, globals, closure);
            // In which we initialise the parameters from arguments
            frame.setArguments(args);
            return frame.eval();
        }
        // ...
    }

The ``ExecutionFrame`` itself provides support for setting arguments.
At present we can only manage fixed numbers of positional arguments,
but it does spot those cases where parameters are also cells.

..  code-block::    java

    private static class ExecutionFrame extends Frame
            implements Visitor<Object> {
        // ...

       void setArguments(List<Object> args) {

            SymbolTable table = f_code.ast.symbolTable;

            // Only fixed number of positional arguments supported so far
            for (int i = 0; i < f_code.argcount; i++) {
                fastlocals[i] = args.get(i);
            }

            // Sometimes arguments are also cell variables.
            for (int i = 0; i < f_code.argcount; i++) {
                String name = f_code.co_varnames[i];
                SymbolTable.Symbol symbol = table.lookup(name);
                if (symbol.scope == SymbolTable.ScopeType.CELL) {
                    cellvars[symbol.cellIndex].obj = fastlocals[i];
                }
            }
        }

        @Override
        public Object visit_Call(Call call) {
            // Evaluating the expression should return a callable object
            Object funcObj = call.func.accept(this);
            if (funcObj instanceof PyCallable) {
                PyCallable callable = (PyCallable)funcObj;
                // Only fixed number of positional arguments supported
                int n = call.args.size();
                Object[] argValues = new Object[n];
                // Visit the values of positional args
                for (int i = 0; i < n; i++) {
                    argValues[i] = call.args.get(i).accept(this);
                }
                return callable.call(this, argValues);
            } else {
                throw notSupported("target not callable", call);
            }
        }

        @Override
        public Object visit_Return(Return returnStmt) {
            returnValue = returnStmt.value.accept(this);
            return null;
        }
    }

A return AST node is fairly simple:
the value of the expression (sub-tree) gets posted to the field ``returnValue``.
Not all functions contain an explicit ``return``,
in which case ``returnValue`` still contains its initial value ``None``.

The Test Program ``TestInterp3.java``
=====================================

We have now completed a tour of the new elements in ``TestInterp3``.
These have been prototyped as inner classes of
``uk.co.farowl.vsj1.example.treepython.TestInterp3``,
which is also a JUnit test.

The principle of this test is to use ASTs and reference results
from a series of simple programs,
compiled and executed by ``~/src/test/python/variable_access_testgen.py``.
The programe generates Java code for the AST and a class instance.
For example we specify (as a string)::

   closprog1 = """\
   # Program requiring closures made of local variables
   def p(a, b):
       x = a + 1 # =2
       def q(c):
           y = x + c # =4
           def r(d):
               z = y + d # =6
               def s(e):
                   return (e + x + y - 1) * z # =42
               return s(d)               
           return r(c)
       return q(b)

   result = p(1, 2)
   """

In the Python program we effectively compute::

   >>> glob={}
   >>> exec(closprog1, glob)
   >>> del glob['__builtins__']
   >>> glob
   {'p': <function p at 0x0000016EEB9FAD90>, 'result': 42}

We express the result (and the AST) as Java,
which we paste into the end of ``TestInterp3.java``.
For ``closprog1`` this looks like:

..  code-block::    java

    @Test
    public void closprog1() {
        // @formatter:off
        // # Program requiring closures made of local variables
        // def p(a, b):
        //     x = a + 1 # =2
        //     def q(c):
        //         y = x + c # =4
        //         def r(d):
        //             z = y + d # =6
        //             def s(e):
        //                 return (e + x + y - 1) * z # =42
        //             return s(d)
        //         return r(c)
        //     return q(b)
        //
        // result = p(1, 2)
        mod module = Module(
    list(
        FunctionDef(

            // ... many lines of AST omitted ...

            ),
        Assign(list(Name("result", Store)), Call(Name("p", Load), list(Num(1), 
                Num(2)), list()))));
        // @formatter:on
        Map<String, Object> state = new HashMap<>();
        state.put("result", 42);
        executeTest(module, state); // closprog1
    }

``executeTest`` runs the AST
and compares the strings and numbers left globals at the end,
with the reference result in ``state``.
In the ``closprog`` example, the result is ``{p=<function p>, result=42}``.
This constitutes success.

Efficient load and store
************************

    Code fragments in this section are taken from
    ``~/src/test/java/.../vsj1/example/treepython/TestInterp4.java``
    in the project source.

A ``CallSite`` for Loading a Variable
=====================================

In the design so far,
each time control enters the AST node for a name,
we look that name up in the symbol table,
in order to discover where it is kept and (if in the frame) at what index.
We could easily cache that result in the node.
However, since we have a ``CallSite`` field,
this provides an interesting way to embed the symbol table result.

The first step is to separate finding the location of the variable
from retrieving the value. We re-write the visit method to look like this:

..  code-block::    java

    private static class ExecutionFrame extends Frame
            implements Visitor<Object> {
        // ...
        @Override
        public Object visit_Name(expr.Name name) {
            if (name.site == null) {
                // This must be a first visit
                try {
                    name.site = new ConstantCallSite(loadMH(name.id));
                } catch (ReflectiveOperationException e) {
                    throw linkageFailure(name.id, name, e);
                }
            }

            MethodHandle mh = name.site.dynamicInvoker();

            try {
                return mh.invokeExact(this);
            } catch (Throwable e) {
                throw invocationFailure("=" + name.id, name, e);
            }
        }
        // ...
    }

It is important to understand that,
since there is a new frame for each function call,
and the same AST code is used with all of them,
we must create a mapping from ``ExecutionFrame`` to ``Object``:
a mapping that retrieves not exactly the same object each time,
but whatever object has that name, interpreted for the given frame.
We cannot therefore cache a reference to the specific array or dictionary,
only a sort of "pointer to member" of the given *class* of frame.
In the call ``mh.invokeExact(this)``,
``this`` is the current ``ExecutionFrame`` and
``mh`` holds the rest of the information.

The method handle we create once, and cache in the call site,
is chosen according to the entry in the symbol table like this:

..  code-block::    java

        private MethodHandle loadMH(String id)
                throws ReflectiveOperationException,
                IllegalAccessException {

            // How is the id used?
            SymbolTable.Symbol symbol = f_code.ast.symbolTable.lookup(id);

            // Mechanism depends on scope & OPTIMIZED trait
            switch (symbol.scope) {
                case LOCAL:
                    if (f_code.traits.contains(Code.Trait.OPTIMIZED)) {
                        return loadFastMH(symbol.index);
                    } else if (f_locals == f_globals) {
                        return loadNameMH(id, "loadNameGB");
                    } else {
                        return loadNameMH(id, "loadNameLGB");
                    }
                case CELL:
                case FREE:
                    return loadCellMH(symbol.cellIndex);
                default: // GLOBAL_*
                    return loadNameMH(id, "f_globals");
            }
        }

We choose amongst three basic method handle structures,
according to the scope of the symbol.
There are four modes, but
the mechanism for lookup in a map covers both globals and dictionary locals.
By way of illustration, here is the implementation of ``fastlocals`` access:

..  code-block::    java

        MethodHandle loadFastMH(int index)
                throws ReflectiveOperationException,
                IllegalAccessException {

            Class<Object[]> OA = Object[].class;
            Class<ExecutionFrame> EF = ExecutionFrame.class;

            // fast = λ(f) : f.fastlocals
            MethodHandle fast = lookup.findGetter(EF, "fastlocals", OA);
            // get = λ(a,i) : a[i]
            MethodHandle get = arrayElementGetter(OA);
            // atIndex = λ(a) : a[index]
            MethodHandle atIndex = insertArguments(get, 1, index);
            // λ(f) : f.fastlocals[index]
            return collectArguments(atIndex, 0, fast);
        }

One could consider that this creates
the equivalent to a ``LOAD_FAST`` opcode, together with its argument.

Let us reflect on what we've achieved here for a moment.
One difference from the previous work with unary and binary operations,
is the use of a ``ConstantCallSite``.
Once linked, the target does not need to be reconsidered.
This is because it is not dependent on
the class of object presented at run-time:
it depends only on information available in the symbol table,
and which is known during compilation.
(With the minor exception of whether locals and globals are the same.)
Our need to use this logic at all at run-time
stems from the fact that we are interpreting the AST,
rather than generating code.
In a Jython compiler that generates Java byte code,
we would generate instructions to access fast locals, cells or a map directly,
and the index or name would be a constant in that code.

Another obvious optimisation would be
to have merged identical ``Name`` nodes, so that
we do not have to repeat the work for each use of the name in the program text.
We will not implement that, given the observation that
this entire class of work belongs to compile time activity.


A ``CallSite`` for Assignment to a Variable
===========================================

A complementary use of method handles may be made for assignment.
This is very similar to the load operation,
except that the returned handle takes an extra argument (the value to store).
Here is the method handle equivalent to a ``STORE_FAST`` opcode:

..  code-block::    java

        MethodHandle storeFastMH(int index)
                throws ReflectiveOperationException,
                IllegalAccessException {

            Class<Object[]> OA = Object[].class;
            Class<ExecutionFrame> EF = ExecutionFrame.class;

            // fast = λ(f) : f.fastlocals
            MethodHandle fast = lookup.findGetter(EF, "fastlocals", OA);
            // store = λ(a,k,v) : a[k] = v
            MethodHandle store = arrayElementSetter(OA);
            // storeFast = λ(f,k,v) : (f.fastlocals[k] = v)
            MethodHandle storeFast = collectArguments(store, 0, fast);
            // mh = λ(f,v) : (f.fastlocals[index] = v)
            return insertArguments(storeFast, 1, index);
        }

However, the same comment applies,
that in a compiler that generates Java byte code,
the opportunity to generate efficient code at compile time
makes it unnecessary to optimise like this at run-time.


Optimisation of Function Calls
******************************

Argument Transfer
=================

As implemented, arguments are computed and stored in a list,
passed in the call.
(This reflects the generalised signature ``f(*args, **kwargs)``.)
The function object then creates the frame and loads the arguments
into the variables that are the parameters.

It is sensible to have the called object create the frame,
because the frame's specification is implied by the code the callable holds.
In fact,
that there is a frame at all, or that it is an ``ExecutionFrame`` anyway,
is a consequence of the callable having been created by ``ExecutionFrame``.
Other callable objects could use a different type of frame, or none.
But if the frame exists and is an ``ExecutionFrame`,
then as soon as the called object is known,
we could place the arguments as they are produced,
and avoid the intermediate array.

CPython has a comparable optimisation
in ``ceval.c`` at ``_PyFunction_FastCall``:
when the target is a function defined in Python,
is simple enough (e.g. uses fast locals), and
the call has a fixed argument list (no keywords or starred arguments),
it creates the frame and populates it from the interpreter stack,
going straight to ``PyEval_EvalFrameEx(f,0)``.

This idea leads to a version of the ``visit_Call`` that looks like this:

..  code-block::    java

    private static class ExecutionFrame extends Frame
            implements Visitor<Object> {
        // ...

        @Override
        public Object visit_Call(Call call) {
            // Evaluating the expression should return a callable object
            Object funcObj = call.func.accept(this);

            if (funcObj instanceof PyGetFrame) {
                return functionCall((PyGetFrame)funcObj, call.args);
            } else if (funcObj instanceof PyCallable) {
                return generalCall((PyCallable)funcObj, call.args);
            } else {
                throw notSupported("target not callable", call);
            }
        }

        /** Call to {@link PyGetFrame} style of function. */
        private Object functionCall(PyGetFrame func, List<expr> args) {

            // Create the destination frame
            ExecutionFrame targetFrame = func.getFrame(this);

            // Only fixed number of positional arguments supported
            int n = args.size();
            assert n == targetFrame.f_code.argcount;

            // Visit the values of positional args
            for (int i = 0; i < n; i++) {
                targetFrame.setArgument(i, args.get(i).accept(this));
            }
            // Execute with the prepared frame
            return targetFrame.eval();
        }

        // ... generalCall as previous visit_Call

        private void setArgument(int position, Object value) {

            fastlocals[position] = value;

            // Sometimes an argument is also a cell variable.
            SymbolTable table = f_code.ast.symbolTable;
            String name = f_code.co_varnames[position];
            SymbolTable.Symbol symbol = table.lookup(name);
            if (symbol.scope == SymbolTable.ScopeType.CELL) {
                cellvars[symbol.cellIndex].obj = value;
            }
        }
        // ...
    }

Later, we shall have to tackle the full semantics of calls,
and will see how this idea survives the extra complications.


Re-thinking Closures
====================

    Code fragments in this section are taken from
    ``~/src/test/java/.../vsj1/example/treepython/TestInterp5.java``
    in the project source.

We have followed CPython in using the same code
(corresponding to CPython's ``*_DEREF`` opcodes)
to access the variables named in the ``co_cellvars`` tuple of ``code``,
and those named in the ``co_freevars`` tuple.
For this reason, both are in one array.
However, this layout is private to the interpreter, so we have a free choice.
(We already parted company with CPython
in not making one contiguous array of all locals.)

While the variables named in ``co_cellvars``
are new blank cells created in the called frame,
those in ``co_freevars`` are from the closure array in the ``function``.
This involves making a copying that array (of references) on every call.
We could save storage and data movement
by referring to the closure in the function.

Adjustments to the Frame
------------------------

This involves minor change to the ``ExecutionFrame``
and how we allocate storage at the end of ``CodeGenerator``.
The main changes are to ``ExecutionFrame`` itself.

We'll take this opportunity to separate more clearly those fields that have
direct analogues in CPython (holding them in the ``Frame`` class),
and those that are interpreter state,
holding them in the ``ExecutionFrame`` class.
There's a corresponding adjustment to be made to the constructor,
and how we compose the closure for a function definition.
(In an implementation that generates Java byte code,
composing the closure would be generated code.)

..  code-block::    java

    private static abstract class Frame {

        final Frame f_back;
        final Code f_code;
        final Map<String, Object> f_globals;
        Map<String, Object> f_locals = null;
        // ... constructors, etc.
    }

    private static class ExecutionFrame extends Frame
            implements Visitor<Object> {
        final Cell[] freevars;
        final Cell[] cellvars;
        final Object[] fastlocals;
        Object returnValue = Py.NONE;
        Lookup lookup = lookup();

        /** Constructor suitable to run a function (with closure). */
        ExecutionFrame(Frame back, Code code, Map<String, Object> globals,
                Cell[] closure) {
            super(back, code, globals, null);

            if (code.traits.contains(Code.Trait.OPTIMIZED)) {
                fastlocals = new Object[code.nlocals];
            } else {
                fastlocals = null;
            }

            // Names free in this code form the function closure.
            this.freevars = closure;

            // Create cell variables locally for nested blocks to access.
            int ncells = code.co_cellvars.length;
            if (ncells > 0) {
                // Initialise the cells that have to be created here
                cellvars = new Cell[ncells];
                for (int i = 0; i < ncells; i++) {
                    cellvars[i] = new Cell(null);
                }
            } else {
                cellvars = Cell.EMPTY_ARRAY;
            }
        }

        //...
        private Cell[] closure(Code targetCode) {
            int nfrees = targetCode.co_freevars.length;
            if (nfrees == 0) {
                // No closure necessary
                return Cell.EMPTY_ARRAY;
            } else {
                SymbolTable localSymbols = f_code.ast.symbolTable;
                Cell[] closure = new Cell[nfrees];
                for (int i = 0; i < nfrees; i++) {
                    String name = targetCode.co_freevars[i];
                    SymbolTable.Symbol symbol = localSymbols.lookup(name);
                    boolean isFree =
                            symbol.scope == SymbolTable.ScopeType.FREE;
                    int n = symbol.cellIndex;
                    closure[i] = (isFree ? freevars : cellvars)[n];
                }
                return closure;
            }
        // ...
        }
    }


Access to Variables
-------------------

The allocation of storage is actually a little simpler than before.
Now that there are two arrays of cells,
a method handle that accesses a cell variable has to be bound to the right one.
The handle to store a cell variable is now constructed
separately for the ``CELL`` and ``FREE`` clauses, naming the array:

..  code-block::    java

    private static class ExecutionFrame extends Frame
            implements Visitor<Object> {
        // ...

        private MethodHandle storeMH(String id)
                throws ReflectiveOperationException,
                IllegalAccessException {

            // How is the id used?
            SymbolTable.Symbol symbol = f_code.ast.symbolTable.lookup(id);

            // Mechanism depends on scope & OPTIMIZED trait
            switch (symbol.scope) {
                case LOCAL:
                    if (f_code.traits.contains(Code.Trait.OPTIMIZED)) {
                        return storeFastMH(symbol.index);
                    } else {
                        return storeNameMH(id, "f_locals");
                    }
                case CELL:
                    return storeCellMH(symbol.cellIndex, "cellvars");
                case FREE:
                    return storeCellMH(symbol.cellIndex, "freevars");
                default: // GLOBAL_*
                    return storeNameMH(id, "f_globals");
            }
        }

        MethodHandle storeCellMH(int index, String arrayName)
                throws ReflectiveOperationException,
                IllegalAccessException {

            Class<Object> O = Object.class;
            Class<Cell[]> CA = Cell[].class;
            Class<ExecutionFrame> EF = ExecutionFrame.class;

            // get = λ(a,i) : a[i]
            MethodHandle get = arrayElementGetter(CA);
            // cells = λ(f) : f.(arrayName)
            MethodHandle cells = lookup.findGetter(EF, arrayName, CA);
            // getCell = λ(f,i) : f.cellvars[i]
            MethodHandle getCell = collectArguments(get, 0, cells);

            // setObj = λ(c,v) : (c.obj = v)
            MethodHandle setObj = lookup.findSetter(Cell.class, "obj", O);
            // setCellObj = λ(f,i,v) : (f.(arrayName)[i] = v)
            MethodHandle putCell = collectArguments(setObj, 0, getCell);
            // λ(f,v) : (f.(arrayName)[index].obj = v)
            return insertArguments(putCell, 1, index);
        }
        // ...
    }



Parameters that are Cells
-------------------------

The call itself is largely as we have seen it before,
except that by delaying the problem of parameters that are cells to the
``eval()`` method, the code to load the frame is now simplified.
The code added to ``eval()`` would be generated by the compiler:

..  code-block::    java

        /** Call to {@link PyGetFrame} style of function. */
        private Object functionCall(PyGetFrame func, List<expr> args) {
            // ...
            // Visit the values of positional args
            for (int i = 0; i < n; i++) {
                targetFrame.fastlocals[i] = args.get(i).accept(this);
            }
            // Execute with the prepared frame
            return targetFrame.eval();
        }

        // ...
        @Override
        Object eval() {
            // Some arguments may be cell variables instead.
            SymbolTable table = f_code.ast.symbolTable;
            for (int i = 0; i < f_code.argcount; i++) {
                String name = f_code.co_varnames[i];
                SymbolTable.Symbol symbol = table.lookup(name);
                if (symbol.scope == SymbolTable.ScopeType.CELL) {
                    cellvars[symbol.cellIndex].obj = fastlocals[i];
                }
            }

            // Execute the body of statements
            for (stmt s : f_code.ast.body) {
                s.accept(this);
            }
            return returnValue;
        }
        // ...
    }


A ``CallSite`` for a Function Call?
===================================

Although the use of method handles to streamline assignment has doubtful value,
the function call is a different matter.
Why think this?
The activity that takes place when execution arrives at a call site
is determined by two sets of characteristics:

* the pattern of arguments supplied at the call site; and
* the signature of the function called (``function`` or ``code`` object).

Python supports a rich diversity in both.
Additionally,
while the characteristics of the call site are evident to the compiler,
it is not able to predict the type of object it will call:
in general the called object is the result of an arbitrary expression,
and perhaps does not yield a ``function`` object,
but something else with a ``__call__`` method, unknown until run-time.
This is a distinction we could manage with a guarded method handle,
calling ``functionCall`` or ``generalCall`` as appropriate.

Because of this richness,
Python has to be prepared to work hard during a call,
matching actual arguments to parameters in the signature,
and positioning these in the frame,
where the particular called object expects them to be.
There are fast paths in the code of CPython for the common cases,
and a scheme in which discarded frames of the right "shape"
are cached on the function that needs them ("zombie frames").

We observe that:

*  The pattern is fixed for the call site
   (meaning the positional and keyword arguments,
   and the starred array and dictionary arguments).
*  The identity of the function is frequently the same from call to call.
   (It is a built-in or module-level function,
   or a monomorphic instance method.)

These two factors suggest that a cache at each site,
keyed to the *identity* (not just class) of the function,
would be a worthwhile optimisation.
As this is only testable when we have a variety of callable types,
it will wait until a later chapter.

