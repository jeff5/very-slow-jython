..  generated-code/interpreter-cpython-byte-code.rst

An Interpreter for CPython Byte Code
####################################

    Code fragments in this section are taken from
    ``rt2/src/test/java/.../vsj2/PyByteCode1.java``
    in the project source.
    Class and other declarations shown are nested in class ``PyByteCode1``.

In this section we will begin a CPython byte code interpreter
that implements a handful of opcodes for a few built-in data types.
Eventually,
we must cover enough of the instruction set to discover implementations for
operations on all types of data,
attribute access, descriptors,
function definition, call,
class definition, instantiation, inheritance
and much more.

No doubt a great many problems lie in wait for us,
but we have CPython source as a reference
and Jython 2 for inspiration.

Our first challenge is to create the means to interpret any code at all.
We'll be content with simply moving data about:
assigning global variables from constants and from other global variables.
For this,
we need to represent ``code`` and ``frame`` objects,
simple types of Python built-in object (so we can assign instances of them),
and a ``dict`` type to hold them.
If we are to approximate the CPython implementations of ``code`` and ``frame``,
we will need types for ``bytes``, ``str`` and ``tuple``.
This is quite a list.

Fortunately,
we can get away with implementing very few of their operations.
And for now,
we need only manipulate them from their Java API.


``PyObject`` as an Interface
****************************

In Jython 2,
``PyObject`` is a concrete Java class with over 200 public methods.
These support the interpreter, Java API and Python object model.
Everything you might do to a ``PyObject`` at run-time
has a default implementation here.
Much of what would be in ``ceval.c`` or ``abstract.c`` in CPython
is in ``PyObject.java`` in Jython.

We expect to place most of this behaviour elsewhere,
but there will surely be some behaviour all ``PyObject``\s have to support.
It has been suggested that ``PyObject`` should be an interface,
so that universally required behaviour
would appear first as no more than a contract,
to be satisfied by objects that implement the interface.
This is what we shall try here.
A couple of advantages will appear shortly
(in implementing ``dict`` and ``exception``).

In fact, we have nothing for ``PyObject`` to do just yet,
so we declare it like this:

..  code-block:: java

    interface PyObject {}



Basic types ``int``, ``float`` and ``str``
******************************************

We shall need some basic types to manipulate
but for the data movement example,
we need only instantiate and test the objects.
We follow CPython naming,
where for historical reasons
(which might not endure)
``int`` is denoted in the C code by ``PyLong``,
and ``str`` by ``PyUnicode``.

Our ``PyLong`` contains a ``BigInteger``
in order to match the range of ``int``,
but for convenience in the Java API
we give it a second constructor from the primitive ``long``.

..  code-block:: java

    /** The Python {@code int} object. */
    static class PyLong implements PyObject {
        final BigInteger value;
        PyLong(BigInteger value) { this.value = value; }
        PyLong(long value) { this.value = BigInteger.valueOf(value); }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PyLong) {
                PyLong other = (PyLong) obj;
                return other.value.equals(this.value);
            } else
                return false;
        }
    }

    /** The Python {@code float} object. */
    // PyFloat Similar to PyLong

    /** The Python {@code str} object. */
    static class PyUnicode implements PyObject {
        final String value; // only supporting BMP for now
        PyUnicode(String value) { this.value = value; }
        @Override
        public int hashCode() { return value.hashCode(); }
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PyUnicode) {
                PyUnicode other = (PyUnicode) obj;
                return other.value.equals(this.value);
            } else
                return false;
        }
    }

In these definitions we implement ``equals()``
so that the assertions we make in test methods
will compare instances by type and value.
We implement ``hashCode()`` for ``PyUnicode``
so that we may use ``str`` names as the keys in a ``dict``.


Objects for ``bytes`` and ``tuple``
***********************************

``bytes`` and ``tuple`` are not much more complex.
They represent a fixed-length sequence,
which we implement internally as an array.

..  code-block:: java

    /** The Python {@code bytes} object. */
    static class PyBytes implements PyObject {
        final byte[] value;
        PyBytes(byte... value) {
            this.value = new byte[value.length];
            System.arraycopy(value, 0, this.value, 0, value.length);
        }
    }

    /** The Python {@code tuple} object. */
    static class PyTuple implements PyObject {
        final PyObject[] value;
        PyTuple(PyObject... value) {
            this.value = new PyObject[value.length];
            System.arraycopy(value, 0, this.value, 0, value.length);
        }
        public PyObject getItem(int i) { return value[i]; }
    }

Our interpreter will need to access specific elements of ``tuple``,
and so we provide a ``getItem`` method.
There should be similar methods in ``bytes`` and ``str``,
maybe required by a ``Sequence`` interface,
but we don't need them yet.


Implementing ``dict``
*********************

A ``dict`` object is just a Java ``HashMap``,
in which keys and values are ``PyObject``\s.

..  code-block:: java

    static class PyDictionary extends HashMap<PyObject, PyObject>
            implements PyObject {}

The implementing class provides all our Java API directly.
This is the first place where
making ``PyObject`` an interface seems to have paid off:
if we had had to inherit from a class ``PyObject``,
we would have been forced to make our ``HashMap`` a field.
Then we should have had to implement the ``Map`` interface,
as a number of methods delegating to that field.
We see this again in ``exception``.


Making Python ``exception`` a Java ``Throwable``
************************************************

In Jython 2, exceptions are somewhat awkward in their Java API:
a ``PyException`` is a Java exception,
but not a ``PyObject``.
Rather, it wraps the actual Python exception object as a value.

It is more natural if we throw in Java the same object we raise in Python.
We are prevented from this in Jython 2 because that object class cannot
inherit both from ``PyObject`` and ``Throwable``.
With ``PyObject`` an interface we may write:

..  code-block:: java

    /** The Python {@code exception} object. */
    static class PyException extends RuntimeException implements PyObject {
        public PyException(String msg, Object... args) {
            super(String.format(msg, args));
        }
    }

We won't develop this very far at present:
the full version would have to be more complex,
providing for a (Python) traceback and
arguments used other than by string formatting.


Runtime Suport: ``Py``
**********************

We can expect to need a number of run-time support methods,
and so we define a class ``Py`` to contain them.
For now, it just holds the singleton object ``None``.

..  code-block:: java

    /** Runtime */
    static class Py {
        private static class Singleton implements PyObject {
            String name;
            Singleton(String name) { this.name = name; }
            @Override
            public String toString() { return name; }
        }
        /** Python {@code None} object. */
        static final PyObject None = new Singleton("None");
    }


Implementing ``code``
*********************

A Python ``code`` object is a holder for static information derived
by the compiler from a module, class or function body.
Although there is quite a lot of this information,
in CPython there is little associated behaviour or encapsulation
(functions named ``PyCode_*``),
beyond those that construct it.
Most of the use of these fields in CPython is by other code that,
this being a C struct,
is able to reach in to read the fields directly
(see ``ceval.c`` and ``frameobject.c``).
We'll leave our ``PyCode`` like that for now: a mass of ``final`` fields.

As a data structure, ``PyCode``:

* provides the executable code as CPython byte code
  (in some version not indicated).
* enumerates the constants that appear in code,
  an important case of which are the code objects of nested scopes.
* enumerates the global names referenced in the code.
* lays out the local variables in the frame when the code executes:
  the arguments, plain variables and cell variables defined or referenced
* dimensions the frame used to execute the code
  (stack size needed and the number of each kind of argument).
* supplies traits that affect the construction and behaviour of
  the frame used to execute the code (e.g. whether a generator, or nested).
* maps the executable instructions to source lines, e.g. for error messages.

In our model,
although we do not presently need most of them,
we will define all the fields CPython has,
and a full-fat constructor whose signature is:

..  code-block:: java

    PyCode(int argcount, int posonlyargcount, int kwonlyargcount, int nlocals,
        int stacksize, int flags,
        PyBytes code, PyTuple consts, PyTuple names,
        PyTuple varnames, PyTuple freevars, PyTuple cellvars,
        PyUnicode filename, PyUnicode name,
        int firstlineno, PyBytes lnotab)

We follow CPython in our choice of which arguments are ``PyObject``,
and which are native (Java ``int``),
although we do ask Java to police the sub-type of ``PyObject``.
Presently we have no guide as to whether things like the name arrays
would be better as Java native (or ``java.util``) types.
When the ``code`` object becomes accessible from Python,
Java native types would have to be presented as ``PyObject``,
but here we are considering a Java API.

Rather than exhibit the whole declaration,
we draw attention to the one bit of behaviour
added to the approach taken by CPython.

When a frame is created to execute this code,
its initial state is drawn from a combination of
the type and content of the ``PyCode``,
properties of the function, class or module of which it is the body, and
actual arguments supplied to invoke it.
We make the ``PyCode`` responsible for this creation.
The following is sufficient API to create a ``PyFrame`` for
executing module code (as in a call to Python ``exec()``):

..  code-block:: java

    static class PyCode implements PyObject {
        // ...
        PyFrame createFrame(ThreadState tstate, PyDictionary globals,
                PyObject locals) {
            return new CPythonFrame(tstate, this, globals, locals);
        }
        // ...
    }

We are only ready to consider CPython byte code content,
so the implementation of ``createFrame`` produces that specialisation
of ``PyFrame``.
Long-term, we must cater for different types of ``PyCode``,
possibly sub-classes,
that need other specialisations,
not least one that links to JVM byte code.


Implementing ``frame``
**********************

The Python ``frame`` object holds the dynamic information
associated with (an instance of) an executing module, class definition,
function or generator.
Frames normally form a stack at any moment (sometimes a tree),
the top of which, the currently executing frame,
is referenced by the ``ThreadState`` representing the current platform thread.

The ``ThreadState`` is critical to the management of concurrency,
but at the moment our only interest in it is that is holds the frame stack.

Like its ``PyCodeObject``,
CPython's ``PyFrameObject`` is a fairly large struct
that allows access to its state outside encapsulation.
Most of this access is from the CPython byte code interpreter
(see ``ceval.c``).
The ``PyFrameObject`` also holds elements of the interpreter's state,
that are not exposed in Python,
and that the developers adjust as the interpreter changes.
It is mainly these two observations that cause us to encapsulate the
interpretation of byte code within (a subclass of) ``PyFrame``.

..  code-block:: java

    /** A {@code PyFrame} is the context for the execution of code. */
    private static abstract class PyFrame implements PyObject {

        /** ThreadState owning this frame. */
        protected final ThreadState tstate;
        /** Frames form a stack by chaining through the back pointer. */
        PyFrame back;
        /** Code this frame is to execute. */
        final PyCode code;
        /** Built-in objects */
        final PyDictionary builtins;
        /** Global context (name space) of execution. */
        final PyDictionary globals;
        /** Local context (name space) of execution. (Assign if needed.) */
        Map<PyObject, PyObject> locals = null;

        /**
         * Partial constructor, leaves {@link #locals} {@code null}.
         * Establishes the back-link to the current stack top but does not
         * make this frame the stack top. ({@link #eval()} should do that.)
         *
         * @param tstate thread state (supplies link to previous frame)
         * @param code that this frame executes
         * @param globals global name space
         */
        PyFrame(ThreadState tstate, PyCode code, PyDictionary globals) {
            this.tstate = tstate;
            this.code = code;
            this.back = tstate.frame; // NB not pushed until eval()
            this.globals = globals;
            // globals.get("__builtins__") ought to be a module with dict:
            this.builtins = new PyDictionary();
        }

        /**
         * Foundation constructor on which subclass constructors rely.
         *
         * @param tstate thread state (supplies back)
         * @param code that this frame executes
         * @param globals global name space
         * @param locals local name space (or it may be {@code globals})
         */
        protected PyFrame(ThreadState tstate, PyCode code,
                PyDictionary globals, PyObject locals) {

            // Initialise the basics.
            this(tstate, code, globals);

            // The need for a dictionary of locals depends on the code
            EnumSet<PyCode.Trait> traits = code.traits;
            if (traits.contains(PyCode.Trait.NEWLOCALS)) {
                // Ignore locals argument
                if (traits.contains(PyCode.Trait.OPTIMIZED)) {
                    // We can create it later but probably won't need to
                    this.locals = null;
                } else {
                    this.locals = new PyDictionary();
                }
            } else if (locals == null) {
                // Default to same as globals.
                this.locals = globals;
            } else {
                /*
                 * Use supplied locals. As it may not implement j.u.Map, we
                 * should arrange to wrap any Python object supporting the
                 * right methods as a Map<>, but later.
                 */
                this.locals = (Map<PyObject, PyObject>) locals;
            }
        }

        /** Execute the code in this frame. */
        abstract PyObject eval();
    }

Our ``PyFrame`` is actually just a base class for
the various types of interpreter we shall need,
but so far ``CPythonFrame`` will be enough.
It is shorter that the CPython equivalent partly for that reason,
partly because we do not need,
or are not ready to address,
the tracing and other support CPython places here.


The ``frame`` as an Interpreter
*******************************

Our ``PyFrame`` concrete subclass is called ``CPythonFrame``.
The main feature of this is that it implements ``PyFrame.eval()``.
It is worth a quick look at the member fields of ``CPythonFrame``.
These parts correspond to the members of the CPython struct
that are not exposed to Python,
but support the evaluation loop.
In particular, note that the local variables are here,
and the value stack.
More may be needed as we develop the set of supported opcodes.
In a potential ``JVMFrame``,
quite different state variables may be needed.

..  code-block:: java

    /** A {@link PyFrame} for executing CPython 3.8 byte code. */
    private static class CPythonFrame extends PyFrame {

        /** Cells for free variables (used not created in this code). */
        final Cell[] freevars;
        /** Cells for local cell variables (created in this code). */
        final Cell[] cellvars;
        /** Local simple variables (corresponds to "varnames"). */
        final PyObject[] fastlocals;
        /** Value stack. */
        final PyObject[] valuestack;
        /** Index of first empty space on the value stack. */
        int stacktop = 0;
        // ...
        CPythonFrame(ThreadState tstate, PyCode code, PyDictionary globals,
                PyObject locals) {
            super(tstate, code, globals, locals);
            this.valuestack = new PyObject[code.stacksize];
            this.fastlocals = null;
            this.freevars = null;
            this.cellvars = null;
        }
        // ...
    }

The bulk of the class definition is the ``eval()`` method.
As in CPython, this is a for-loop around a switch,
each case corresponding to a supported opcode.
For now, we support just four.
The code is reasonably faithful to the CPython version,
with the object lifecycle management taken out.

..  code-block:: java

        @Override
        PyObject eval() {
            // Push this frame to stack
            back = tstate.swap(this);
            // Evaluation stack index
            int sp = this.stacktop;
            // Cached references from code
            PyTuple names = code.names;
            PyTuple consts = code.consts;
            byte[] inst = code.code.value;
            // Get first instruction
            byte opcode = inst[0];
            int oparg = inst[1] & 0xff;
            int ip = 2;
            // Local variables used repeatedly in the loop
            PyObject name, v;

            loop : for (;;) {

                // Interpret opcode
                switch (opcode) {

                    case Opcode.RETURN_VALUE:
                        returnValue = valuestack[--sp]; // POP
                        break loop;

                    case Opcode.STORE_NAME:
                        name = names.getItem(oparg);
                        v = valuestack[--sp]; // POP
                        if (locals == null)
                            throw new PyException(
                                    "no locals found when storing '%s'",
                                    name);
                        locals.put(name, v);
                        break;

                    case Opcode.LOAD_CONST:
                        v = consts.getItem(oparg);
                        valuestack[sp++] = v; // PUSH
                        break;

                    case Opcode.LOAD_NAME:
                        name = names.getItem(oparg);

                        if (locals == null)
                            throw new PyException(
                                    "no locals found when loading '%s'",
                                    name);
                        v = locals.get(name);
                        if (v == null) {
                            v = globals.get(name);
                            if (v == null) {
                                v = builtins.get(name);
                                if (v == null)
                                    throw new PyException(NAME_ERROR_MSG,
                                            name);
                            }
                        }
                        valuestack[sp++] = v; // PUSH
                        break;

                    default:
                        throw new PyException("ip: %d, opcode: %d", ip - 2,
                                opcode);
                }

                // Pick up the next instruction
                opcode = inst[ip];
                oparg = inst[ip + 1] & 0xff;
                ip += 2;
            }

            tstate.swap(back);
            return returnValue;
        }


Program Examples
****************

We have produced a lot of "mechanism", but how can we test it?

Help from the CPython Compiler
==============================

We could approach a test by writing a Python module,
compiling it,
and reading the byte code into a Java test program.
This would involve at least un-pickling ``.pyc`` files.

Or we could try working like this from a string at the prompt:

..  code-block:: python

    >>> import dis
    >>> prog = """\
    ... a = b
    ... b = 2.0
    ... c = 'begins!'
    ... """
    >>> bprog = dis.Bytecode(compile(prog, "<test>", "exec"))
    >>> print(bprog.info())
    Name:              <module>
    Filename:          <test>
    Argument count:    0
    Positional-only arguments: 0
    Kw-only arguments: 0
    Number of locals:  0
    Stack size:        1
    Flags:             NOFREE
    Constants:
       0: 2.0
       1: 'begins!'
       2: None
    Names:
       0: b
       1: a
       2: c
    >>> print(bprog.dis())
      1           0 LOAD_NAME                0 (b)
                  2 STORE_NAME               1 (a)

      2           4 LOAD_CONST               0 (2.0)
                  6 STORE_NAME               0 (b)

      3           8 LOAD_CONST               1 ('begins!')
                 10 STORE_NAME               2 (c)
                 12 LOAD_CONST               2 (None)
                 14 RETURN_VALUE

Here we have all we need to know about the compiled program.
How about running it?
It needs an initial value for ``b``:

..  code-block:: python

    >>> glob = { "b": "World" }
    >>> loc = {}
    >>> exec(bprog.codeobj, glob, loc)
    >>> loc
    {'a': 'World', 'b': 2.0, 'c': 'begins!'}

This shows we can generate byte code and get at its attributes.
And we can then run it to generate a reference result.
But it would be nice to have a Java test program that is self-contained,
with the set-up and expected results all in the text.


Some Homemade Magic
===================

Fortunately,
but not by accident,
there are tools at ``rt2/src/test/python`` that do what we need.

..  code-block:: python

    >>> import sys, os.path
    >>> sys.path.insert(1, os.path.join("rt2", "src", "test", "python"))
    >>> from vsj2.srcgen import PyObjectEmitter
    >>> e = PyObjectEmitter()
    >>> e.emit("a = ").python(42).emit(";").flush()
    a = new PyLong(42);

As we can see,
the ``PyObjectEmitter`` has been written to emit expressions
matching the constructors in the test program ``PyByteCode1.java``.
We can emit a whole ``code`` object like so:

..  code-block:: python

    >>> e.emit("PyCode c = ").python(bprog.codeobj).emit(";").flush()
    PyCode c = new PyCode(0, 0, 0, 0, 1, 64,
            new PyBytes(new byte[] { 101, 0, 90, 1, 100, 0, 90, 0, 100,
                    1, 90, 2, 100, 2, 83, 0 }),
            new PyTuple(new PyObject[] { new PyFloat(2.0),
                    new PyUnicode("begins!"), Py.None }),
            new PyTuple(new PyObject[] { new PyUnicode("b"),
                    new PyUnicode("a"), new PyUnicode("c") }),
            new PyTuple(new PyObject[] {}),
            new PyTuple(new PyObject[] {}),
            new PyTuple(new PyObject[] {}), new PyUnicode("<test>"),
            new PyUnicode("<module>"), 1,
            new PyBytes(new byte[] { 4, 1, 4, 1 }));

Here we see the byte code, the constants and the global names,
all generated as constant expressions in Java.
This forms an initialised object we can test.

We might like to try the same program
with several different sets of initial values.
And it would be nice if something could run the program with each set
and generate the reference (that is CPython) result.

There is a helpful program at ``rt2/src/test/python/vsj2/py_byte_code1.py``,
relative to the project root.
To use it, we put our examples next to it in
``rt2/src/test/python/vsj2/py_byte_code1.ex.py``,
which looks like this:

..  code-block:: python

    # Examples for PyByteCode1.java

    # load_store_name:
    a, b = 1, 2
    # ? a, b, c
    a = b
    b = 4
    c = 6

    # load_store_name_ex:
    a, b = "Hello", "World"
    # ? a, b, c
    a = b
    b = 2.0
    c = 'begins!'

In this text, which is only superficially a Python program,
each test begins with a line like
``# name:``.
Code before the first of these is ignored.
After this comes one or more cases,
assigning initial values to variables that will become the namespace.
Each line here generates a new test case.
Then there is a statement like ``# ? a, b, c``
of what values should be examined after the code runs.
Finally,
the text up to the next test is the code fragment to compile and run.

The program can be invoked in the IDE or like this:

..  code-block:: posh

    PS very-slow-jython> $env:PYTHONPATH="rt2\src\test\python"
    PS very-slow-jython> python -m vsj2.py_byte_code1
        // Code generated by py_byte_code1.py
        ...

This spews out test cases we can paste directly into ``PyByteCode1.java``,
of which the first is:

..  code-block:: java

    /**
     * Example 'load_store_name': <pre>
     * a = b
     * b = 4
     * c = 6
     * </pre>
     */
    //@formatter:off
    static final PyCode LOAD_STORE_NAME =
    /*
     *   1           0 LOAD_NAME                0 (b)
     *               2 STORE_NAME               1 (a)
     *
     *   2           4 LOAD_CONST               0 (4)
     *               6 STORE_NAME               0 (b)
     *
     *   3           8 LOAD_CONST               1 (6)
     *              10 STORE_NAME               2 (c)
     *              12 LOAD_CONST               2 (None)
     *              14 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 1, 64,
        new PyBytes(new byte[] { 101, 0, 90, 1, 100, 0, 90, 0, 100,
                1, 90, 2, 100, 2, 83, 0 }),
        new PyTuple(new PyObject[] { new PyLong(4), new PyLong(6),
                Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("b"),
                new PyUnicode("a"), new PyUnicode("c") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyUnicode("load_store_name"), new PyUnicode("<module>"),
        1,
        new PyBytes(new byte[] { 4, 1, 4, 1 }));
    //@formatter:on

    @Test
    void test_load_store_name1() {
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(1));
        globals.put(new PyUnicode("b"), new PyLong(2));
        PyCode code = LOAD_STORE_NAME;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        // a == 2
        assertEquals("a", new PyLong(2), globals.get(
            new PyUnicode("a")));
        // b == 4
        assertEquals("b", new PyLong(4), globals.get(
            new PyUnicode("b")));
        // c == 6
        assertEquals("c", new PyLong(6), globals.get(
            new PyUnicode("c")));
    }

In the subsequent sections,
the way of creating Python objects will evolve,
and so the exact representation we need in tests will also change.
``vsj2.py_byte_code2`` reading ``py_byte_code2.ex.py`` to generate code for
``PyByteCode2.java`` may not be quite the same.
We'll note the changes in passing,
and not delve into the detail again of how the tests are generated.

