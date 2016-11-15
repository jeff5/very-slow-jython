..  treepython/type+dispatch.rst


Type and Operation Dispatch
###########################

Operation Dispatch in CPython
*****************************

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

Operation Dispatch in Jython 2.7.1
**********************************

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
**********************

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
********************************

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

Dispatch via a Java ``MethodHandle``
************************************

In CPython, operator dispatch uses several arrays of pointers to functions,
and these are re-written when special functions (like ``__add__``) are defined.
Our nearest equivalent in Java is the ``MethodHandle``.
Using that would give us similar capabilities.
We may modify the ``TypeHandler`` to contain a method array like so:

..  code-block:: java

    static abstract class TypeHandler {

        /**
         * A (static) method implementing a binary operation has this type.
         */
        protected static final MethodType MT_BINOP = MethodType
                .methodType(Object.class, Object.class, Object.class);
        /** Number of binary operations supported. */
        protected static final int N_BINOPS = operator.values().length;

        /**
         * Table of binary operations (equivalent of Python
         * <code>nb_</code> slots).
         */
        private MethodHandle[] binOp = new MethodHandle[N_BINOPS];

        /**
         * Look up the (handle of) the method for the given
         * <code>op</code>.
         */
        public MethodHandle getBinOp(operator op) {
            return binOp[op.ordinal()];
        }
        // ...

The handler for each type extends this class,
and each handler must provide ``static`` methods roughly as before,
to perform the operations.
Now we are not using overloading,
we no longer need an abstract function for each
(although that may still help the author).
However, if we choose conventional names for the functions,
we can centralise filling the ``binOp`` table like this:

..  code-block:: java

        // ...
        /**
         * Initialise the slots for binary operations in this
         * <code>TypeHandler</code>.
         */
        protected void fillBinOpSlots() {
            fillBinOpSlot(Add, "add");
            fillBinOpSlot(Sub, "sub");
            fillBinOpSlot(Mult, "mul");
            fillBinOpSlot(Div, "div");
        }

        /** The lookup rights object of the implementing class. */
        private final MethodHandles.Lookup lookup;

        protected TypeHandler(MethodHandles.Lookup lookup) {
            this.lookup = lookup;
        }

        /* Helper to fill one binary operation slot. */
        private void fillBinOpSlot(operator op, String name) {
            MethodHandle mh = null;
            try {
                mh = lookup.findStatic(getClass(), name, MT_BINOP);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Let it be null
            }
            binOp[op.ordinal()] = mh;
        };
    }

Each handler enforces its singleton nature,
and ensures that its dispatch table is filled.
Here is one handler for ``Double``:

..  code-block:: java

    /**
     * Singleton class defining the operations for a Java
     * <code>Double</code>, so as to make it a Python <code>float</code>.
     */
    static class DoubleHandler extends TypeHandler {

        private static DoubleHandler instance;

        private DoubleHandler() {
            super(MethodHandles.lookup());
        }

        public static synchronized DoubleHandler getInstance() {
            if (instance == null) {
                instance = new DoubleHandler();
                instance.fillBinOpSlots();
            }
            return instance;
        }

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

        private static Object add(Object vObj, Object wObj) {
            try {
                double v = convertToDouble(vObj);
                double w = convertToDouble(wObj);
                return v + w;
            } catch (IllegalArgumentException iae) {
                return null;
            }
        }
        // ...
    }

The ``MethodHandles.Lookup`` object of each handler
grants access to its implementing functions.

So far this looks no more succinct than previously.
The gain is in the implementation of ``visit_BinOp``:

..  code-block:: java

        @Override
        public Object visit_BinOp(expr.BinOp binOp) {
            Object v = binOp.left.accept(this);
            Object w = binOp.right.accept(this);
            TypeHandler V = Runtime.handler.get(v.getClass());
            TypeHandler W = Runtime.handler.get(w.getClass());
            Object r = null;
            // Omit the case W is a Python sub-type of V, for now.
            try {
                // Get the implementation for V=type(v).
                MethodHandle mh = V.getBinOp(binOp.op);
                if (mh != null) {
                    r = mh.invokeExact(v, w);
                    if (r == null) {
                        // V.op does not support a W right-hand
                        if (W != V) {
                            // Get implementation of for W=type(w).
                            mh = W.getBinOp(binOp.op);
                            // Arguments *not* reversed unlike __radd__
                            r = mh.invokeExact(v, w);
                        }
                    }
                }
            } catch (Throwable e) {
                // r == null
            }
            String msg = "Operation %s not defined between %s and %s";
            if (r == null) {
                throw new IllegalArgumentException(String.format(msg,
                        binOp.op, v.getClass().getName(),
                        w.getClass().getName()));
            }
            return r;
        }

The ``switch`` statement has gone entirely,
and there is only one copy of the delegation logic,
which begins to resemble that in CPython
(in ``abstract.c`` at ``binary_op1()``).


