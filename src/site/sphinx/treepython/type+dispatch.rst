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
partly because the JSR-292 `cookbook`_
(slide 34, minute 53 in the `cookbook video`_)
on dynamic JVM languages recommends it.

..  _cookbook: http://www.wiki.jvmlangsummit.com/images/9/93/2011_Forax.pdf
..  _cookbook video: http://medianetwork.oracle.com/video/player/1113248965001

The first challenge in this approach is
how to locate the operations the interpreter needs.
These correspond to the slots in a CPython type object,
and there is a finite repertoire of them.

Dispatch via overloading in Java
********************************

    Code fragments in this section are taken from
    ``.../vsj1/example/treepython/TestEx2.java``
    in the project test source.

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

    Code fragments in this section are taken from
    ``.../vsj1/example/treepython/TestEx3.java``
    in the project test source.

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

Caching Type Decisions
**********************

In the preceding examples,
we can see that between gathering the ``left`` and ``right``
values in a ``BinOp`` node,
and any actual arithmetic,
stands some reasoning about the argument types.
This reasoning is embedded in the implementation of each type.
Something very similar is true in the C implementation of Python.

While we are not interested in
optimising the performance of *this* implementation,
because it is a toy,
we *are* interested in playing with
the techniques the JVM has for efficient implementation of dynamic languages.
That support revolves around the "dynamic call site" and
the ``invokedynamic`` instruction.
We don't actually (yet) have to generate ``invokedynamic`` opcodes:
we can study the elements by attaching a call site to each AST node,
and using it first in the ``BinOp`` node type,
by invoking its target.
The JVM will not optimise these,
as it would were they used in an ``invokedynamic`` instruction,
but that doesn't matter for now.

In a real program,
any given expression is likely to be evaluated many times,
for different values, and
in a dynamic language,
for different types.
(We seldom care about speed otherwise.)
It is commonly observed that,
even in dynamic languages,
code is executed many times in succession with the *same* types,
for example within a loop.
The type wrangling in our implementation is a costly part of the work,
so Java offers us a way to cache the result of that work
at the particular site where the operation is needed,
and re-use it as often as the same argument types recur.
We need the following elements to pull this off (for a binary operation):

*   An implementation of the binary operation,
    specialised to the argument types,
    or rather, one for each combination of types.
*   A mechanism to map from (a pair of) types to the specialised implementation.
*   A test (called a guard) that the types this time are the same as last time.
*   A structure to hold the last decision
    and the types for which the site is currently specialised
    (the call site).

It is possible to create call sites in which several specialisations
are cached for re-use,
according to any strategy the language implementer favours,
but we'll demonstrate it with a single cached specialisation.

We'll point out in passing that
we've slipped a false assumption into this argument:
namely that type alone
is the determinant of where or whether an operation is implemented.
This is an assumption with which the ``invokedynamic`` framework
is usually explained,
but it isn't wholly valid for us.
In Python,
an implementation of ``__add__`` (say) could return ``NotImplemented``
for certain *values*,
causing delegation to the ``__radd__`` function of the other type.
This hardly ever happens in practice,
the determinant usually *is* just the types involved,
and it can be accommodated in the technique we're about to demonstrate.

Mapping to a Specialised Implementation
=======================================

    Code fragments in this section are taken from
    ``.../vsj1/example/treepython/TestEx4.java``
    in the project test source.

We'll avoid an explicit ``CallSite`` object to begin with.
The first transformation we need is to separate
choosing an implementation of the binary operation,
in the ``visit_BinOp`` method of the ``Evaluator``,
from calling the chosen implementation.

..  code-block:: java

    static class Evaluator implements Visitor<Object> {

        Map<String, Object> variables = new HashMap<>();
        Lookup lookup = lookup();

        @Override
        public Object visit_BinOp(expr.BinOp binOp) {
            // This must be a first visit
            Object v = binOp.left.accept(this);
            Object w = binOp.right.accept(this);
            try {
                MethodHandle mh = Runtime.findBinOp(v.getClass(), binOp.op,
                        w.getClass());
                return mh.invokeExact(v, w);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Implementation returned NotImplemented or equivalent
                throw notDefined(v, binOp.op, w);
            } catch (Throwable e) {
                // Something else went wrong
                e.printStackTrace();
                return null;
            }
        }

        // ...
    }

One delicate point is how to handle the absence of an implementation
in a consistent way.
``java.lang.invoke`` lookups throw a ``NoSuchMethodException``
when no implementation is found.
In some places it suits us to convert that condition to
a method handle that would return ``NotImplemented`` when invoked,
as a Python implementation must,
and test for this special handle, or special value.
However,
the binary operation itself must raise a Python ``NotImplementedError``,
if neither argument type knows what to do.
The strategy is to turn everything into ``NoSuchMethodException``,
within the finding and invoking section,
then convert that to an appropriate exception
here where we know all the necessary facts.

It remains to be seen how we will implement ``Runtime.findBinOp``
to return the ``MethodHandle`` we need.
Skipping to the other end of the problem,
this is how we would like to write specialised implementations:

..  code-block:: java

    static class DoubleHandler extends TypeHandler {

        DoubleHandler() { super(lookup(), Double.class); }

        private static Object add(Double v, Integer w) { return v+w; }
        private static Object add(Integer v, Double w) { return v+w; }
        private static Object add(Double v, Double w)  { return v+w; }
        private static Object sub(Double v, Integer w) { return v-w; }
        private static Object sub(Integer v, Double w) { return v-w; }
        private static Object sub(Double v, Double w)  { return v-w; }
        private static Object mul(Double v, Integer w) { return v*w; }
        private static Object mul(Integer v, Double w) { return v*w; }
        private static Object mul(Double v, Double w)  { return v*w; }
        private static Object div(Double v, Integer w) { return v/w; }
        private static Object div(Integer v, Double w) { return v/w; }
        private static Object div(Double v, Double w)  { return v/w; }
    }

Notice how clean this is relative to the previous handler code.
This is partly because implicit Java un-boxing and widening rules
happen to be just what we need:
the text of every implementation of ``add`` is the same,
but the JVM byte code is not.

In Python,
when differing types meet in a binary operation,
and each has an implementation of the operation
(in the corresponding slot, in CPython),
each is given the chance to compute the result,
first the left-hand, then the right-hand type.
Or if the right-hand type is a sub-class of the left,
first the right-hand type gets a chance, then the left.
This chance is given by actually calling the implementation,
which returns ``NotImplemented`` if it can't deal with the arguments.
For our purpose here,
we therefore invent the convention that
every type provides a method we can consult to find the implementation,
given the operation and the *type* of the other argument.
For types implemented in Java, this can work by reflection:

..  code-block:: java

    static abstract class TypeHandler {

        /** A method implementing a binary operation has this type. */
        protected static final MethodType BINOP = Runtime.BINOP;
        /** Shorthand for <code>Object.class</code>. */
        static final Class<Object> O = Object.class;

        // ...

        /**
         * Return the method handle of the implementation of
         * <code>left op right</code>, where left is an object of this
         * handler's type.
         */
        public MethodHandle findBinOp(operator op, TypeHandler rightType) {
            String name = composeNameFor(op);
            Class<?> here = this.getClass();
            Class<?> leftClass = this.javaClass;
            Class<?> rightClass = rightType.javaClass;

            // Look for an exact match with the actual types
            MethodType mt = MethodType.methodType(O, leftClass, rightClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            // ...
            if (mh == null) {
                return Runtime.BINOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(BINOP);
            }
        }

        /**
         * Return the method handle of the (reverse) implementation of
         * <code>left op right</code>, where right is an object of this
         * handler's type.
         */
        public MethodHandle findBinOp(TypeHandler leftType, operator op)
            // Essentially the same code ...
        // ...
    }

In fact, the code above for ``TypeHandler.findBinOp`` is a simplification.
We'd like to support the possibility of implementations that
accept any type of argument and embed their own type logic,
which then operates when the binary operation is executed.
(We certainly need this for objects defined in Python.)
These will be recognisable because they have one or two ``Object`` arguments.
With the missing clauses visible, we have:

..  code-block:: java

    static abstract class TypeHandler {
        // ...

        /**
         * Return the method handle of the implementation of
         * <code>left op right</code>, where left is an object of this
         * handler's type.
         */
        public MethodHandle findBinOp(operator op, TypeHandler rightType) {
            String name = composeNameFor(op);
            Class<?> here = this.getClass();
            Class<?> leftClass = this.javaClass;
            Class<?> rightClass = rightType.javaClass;

            // Look for an exact match with the actual types
            MethodType mt = MethodType.methodType(O, leftClass, rightClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            // Look for a match with (T, Object)
            if (mh == null) {
                mt = MethodType.methodType(O, leftClass, O);
                mh = findStaticOrNull(here, name, mt);
            }

            // Look for a match with (Object, Object)
            if (mh == null) {
                mh = findStaticOrNull(here, name, BINOP);
            }

            if (mh == null) {
                return Runtime.BINOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(BINOP);
            }
        }
        // ...
    }


Now we can use this to implement the required Python delegation pattern:

..  code-block:: java

    /** Runtime support for the interpreter. */
    static class Runtime {
        //...

        static MethodHandle findBinOp(Class<?> leftClass, operator op,
                Class<?> rightClass)
                throws NoSuchMethodException, IllegalAccessException {
            TypeHandler V = Runtime.typeFor(leftClass);
            TypeHandler W = Runtime.typeFor(rightClass);
            MethodHandle mhV = V.findBinOp(op, W);
            if (W == V) {
                return mhV;
            }
            MethodHandle mhW = W.findBinOp(V, op); // reversed op
            if (mhW == BINOP_NOT_IMPLEMENTED) {
                return mhV;
            } else if (mhV == BINOP_NOT_IMPLEMENTED) {
                return mhW;
            } else if (mhW.equals(mhV)) {
                return mhV;
            } else if (W.isSubtypeOf(V)) {
                return firstImplementer(mhW, mhV);
            } else {
                return firstImplementer(mhV, mhW);
            }
        }

        //...
    }

In many cases, only one of the argument types will offer an implementation,
and a simple (direct) method handle may be returned.
The complicated case arises when both offer to do the job;
in that case,
and this is the clever bit,
we have to create an appropriate method handle blob that,
when invoked,
will try them in turn, and raise an exception when both fail:

..  code-block:: java

    /** Runtime support for the interpreter. */
    static class Runtime {
        //...

        private static MethodHandle firstImplementer(MethodHandle a,
                MethodHandle b) {
            // apply_b = λ(x,y,z): b(y,z)
            MethodHandle apply_b = MethodHandles.filterReturnValue(
                    dropArguments(b, 0, O), THROW_IF_NOT_IMPLEMENTED);
            // keep_a = λ(x,y,z): x
            MethodHandle keep_a = dropArguments(identity(O), 1, O, O);
            // x==NotImplemented ? b(y,z) : a(y,z)
            MethodHandle guarded =
                    guardWithTest(IS_NOT_IMPLEMENTED, apply_b, keep_a);
            // The functions above apply to (a(y,z), y, z) thanks to:
            return foldArguments(guarded, a);
        }
    }

This is an adapter for two method handles ``a`` and ``b``.
When its returned handle is invoked,
first ``a`` is invoked,
then if it returns ``NotImplemented``,
``b`` is invoked on the same arguments.
If ``b`` returns ``NotImplemented``,
that is converted to a thrown ``NoSuchMethodException``.
This corresponds to the way Python implements binary operations when
each operand offers a different implementation.

Bootstrapping a Call Site
=========================

    Code fragments in this section are taken from
    ``.../vsj1/example/treepython/TestEx5.java``
    in the project test source.

It remains for us to introduce a ``CallSite`` object into the AST node
and to use that in place of a call to ``Runtime.findBinOp``.
On first encountering each binary AST node,
we create the ``CallSite``,
as would an ``invokedynamic`` instruction.
The revised code looks like this:

..  code-block:: java

    static class Evaluator implements Visitor<Object> {

        Map<String, Object> variables = new HashMap<>();
        Lookup lookup = lookup();

        @Override
        public Object visit_BinOp(expr.BinOp binOp) {
            // Evaluate sub-trees
            Object v = binOp.left.accept(this);
            Object w = binOp.right.accept(this);
            // Evaluate the node
            try {
                if (binOp.site == null) {
                    // This must be a first visit
                    binOp.site = Runtime.bootstrap(lookup, binOp);
                }
                MethodHandle mh = binOp.site.dynamicInvoker();
                return mh.invokeExact(v, w);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Implementation returned NotImplemented or equivalent
                throw notDefined(v, binOp.op, w);
            } catch (Throwable e) {
                // Something else went wrong
                e.printStackTrace();
                return null;
            }
        }

       //...
    }

The constant pool supporting an ``invokedynamic`` instruction
would specify a bootstrap method, name and (static) calling signature.
Here the binary operation provides the name and (implicit) signature.

The bootstrap method just passes the ``op``
to the constructor of the ``BinOpCallSite`` class.
This class has only one interesting method, called ``fallback``.
This method computes the result of the operation
by using ``findBinOp`` to find the specialised implementation,
returned as a method handle ``resultMH``,
which it invokes.
This method (as ``fallbackMH``) is the first target of the call site.
However, when invoked it installs a new target,
constructed by ``makeGuarded``,
that re-uses the ``resultMH`` if it can,
or resorts to ``fallbackMH`` if it must.
Thus it forms a single level, in-line cache.
We're following the JSR-292 `cookbook`_ almost exactly.

..  code-block:: java

    static class BinOpCallSite extends MutableCallSite {

        final operator op;
        final Lookup lookup;
        final MethodHandle fallbackMH;

        public BinOpCallSite(Lookup lookup, operator op)
                throws NoSuchMethodException, IllegalAccessException {
            super(Runtime.BINOP);
            this.op = op;
            this.lookup = lookup;
            fallbackMH = lookup().bind(this, "fallback", Runtime.BINOP);
            setTarget(fallbackMH);
        }

        private Object fallback(Object v, Object w) throws Throwable {
            Class<?> V = v.getClass();
            Class<?> W = w.getClass();
            MethodType mt = MethodType.methodType(Object.class, V, W);
            // MH to compute the result for these classes
            MethodHandle resultMH = Runtime.findBinOp(V, op, W);
            // MH for guarded invocation (becomes new target)
            MethodHandle guarded = makeGuarded(V, W, resultMH, fallbackMH);
            setTarget(guarded);
            // Compute the result for this case
            return resultMH.invokeExact(v, w);
        }

        /**
         * Adapt two method handles, one that computes the desired result
         * specialised to the given classes, and a fall-back appropriate
         * when the arguments (when the handle is invoked) are not the
         * given types.
         */
        private MethodHandle makeGuarded(Class<?> V, Class<?> W,
                MethodHandle resultMH, MethodHandle fallbackMH) {
            MethodHandle testV, testW, guardedForW, guarded;
            testV = Runtime.HAS_CLASS.bindTo(V);
            testW = Runtime.HAS_CLASS.bindTo(W);
            testW = dropArguments(testW, 0, Object.class);
            guardedForW = guardWithTest(testW, resultMH, fallbackMH);
            guarded = guardWithTest(testV, guardedForW, fallbackMH);
            return guarded;
        }
    }

We decorate ``BinOpCallSite`` and ``fallback``
so that they count the calls to ``fallback`` (not shown above).
We may test the approach with a program such as this:

..  code-block:: java

    private Node cubic() {
        // (x*x-2) * (x+y)
        Node tree =
            BinOp(
                BinOp(
                    BinOp(Name("x", Load), Mult, Name("x", Load)),
                    Sub,
                    Num(2)),
                Mult,
                BinOp(Name("x", Load), Add, Name("y", Load)));
        return tree;
    }

    @Test
    public void testChangeType() {
        Node tree = cubic();
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42));
        int baseline = BinOpCallSite.fallbackCalls;
        evaluator.variables.put("x", 4);
        evaluator.variables.put("y", -1);
        assertThat(tree.accept(evaluator), is(42));
        assertThat(BinOpCallSite.fallbackCalls, is(baseline + 0));
        // Suddenly y is a float
        evaluator.variables.put("x", 2);
        evaluator.variables.put("y", 19.);
        assertThat(tree.accept(evaluator), is(42.));
        assertThat(BinOpCallSite.fallbackCalls, is(baseline + 2));
        // And now so is x
        baseline = BinOpCallSite.fallbackCalls;
        evaluator.variables.put("x", 6.);
        evaluator.variables.put("y", 7.);
        assertThat(tree.accept(evaluator), is(442.));
        assertThat(BinOpCallSite.fallbackCalls, is(baseline + 4));
        // And now y is an int again
        baseline = BinOpCallSite.fallbackCalls;
        evaluator.variables.put("x", 6.);
        evaluator.variables.put("y", 7);
        assertThat(tree.accept(evaluator), is(442.));
        assertThat(BinOpCallSite.fallbackCalls, is(baseline + 1));
    }

The passing test demonstrates that the fall-back is not called again
when the tree is evaluated a second time
with new integer values for the variables,
and is called only once per affected operator when the types change
after that.

Extension to Unary Operators
****************************

In the discussion above, we've implemented only binary operations.
It was important to tackle binary operations early
because they present certain difficulties (of multiple dispatch).
We need to study other kinds of slot function,
that will present new difficulties,
but one type that ought to be easier is the unary operation.
We'll go there next.

    Code fragments in this section are taken from
    ``.../vsj1/example/treepython/TestEx6.java``
    in the project test source.

The first job is to implement a little more of the Python AST for expressions,
represented here in ASDL::

    module TreePython
    {
        expr = BinOp(expr left, operator op, expr right)
             | UnaryOp(unaryop op, expr operand)
             | Num(object n)
             | Name(identifier id, expr_context ctx)

        operator = Add | Sub | Mult | Div
        unaryop = UAdd | USub
        expr_context = Load | Store | Del
    }

When we regenerate the ``TreePython`` AST class from this,
we shall have the necessary data structures to represent unary ``+`` and ``-``.
In order that we can easily write expressions in Java,
representing expression ASTs,
we add the corresponding wrapper functions and constants:

..  code-block:: java

    public static final unaryop UAdd = unaryop.UAdd;
    public static final unaryop USub = unaryop.USub;
    public static final expr UnaryOp(unaryop op, expr operand)
        { return new expr.UnaryOp(op, operand); }

The ``UnaryOp`` node has to be added to the ``Visitor`` interface:

..  code-block:: java

    public abstract class TreePython { //...

        public interface Visitor<T> {
            default T visit_BinOp(expr.BinOp _BinOp){ return null; }
            default T visit_UnaryOp(expr.UnaryOp _UnaryOp){ return null; }
            default T visit_Num(expr.Num _Num){ return null; }
            default T visit_Name(expr.Name _Name){ return null; }
        }
    }

Use of the ``default`` keyword allows our old examples to work,
even though their version of the visitor ``Evaluator`` was
written before we added this node type.
We must add a specific visit method to our ``Evaluator``,
but it's just a simplified version of ``visit_BinOp``:

..  code-block:: java

        public Object visit_UnaryOp(expr.UnaryOp unaryOp) {
            // Evaluate sub-trees
            Object v = unaryOp.operand.accept(this);
            // Evaluate the node
            try {
                if (unaryOp.site == null) {
                    // This must be a first visit
                    unaryOp.site = Runtime.bootstrap(lookup, unaryOp);
                }
                MethodHandle mh = unaryOp.site.dynamicInvoker();
                return mh.invokeExact(v);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Implementation returned NotImplemented or equivalent
                throw notDefined(v, unaryOp.op);
            } catch (Throwable e) {
                // Something else went wrong
                e.printStackTrace();
                return null;
            }
        }


We choose ``neg`` and ``pos`` as the standard names,
corresponding to the Python special functions ``__neg__`` and ``__pos__``,
and within the handlers for applicable types we define:

..  code-block:: java

    static class IntegerHandler extends TypeHandler {
        // ...
        private static Object neg(Integer v) { return -v; }
        private static Object pos(Integer v) { return v; }
    }

    static class DoubleHandler extends TypeHandler {
        // ...
        private static Object neg(Double v) { return -v; }
        private static Object pos(Double v) { return v; }
    }

Finding the implementation of a unary operation within the ``TypeHandler``
is not so complex as it was for binary operations.

..  code-block:: java

    static class Runtime {
        //...
        static MethodHandle findUnaryOp(Class<?> operandClass, unaryop op)
                throws NoSuchMethodException, IllegalAccessException {
            TypeHandler V = Runtime.typeFor(operandClass);
            MethodHandle mhV = V.findUnaryOp(op);
            return mhV;
        }
        //...
    }

    static abstract class TypeHandler {
        //...
        public MethodHandle findUnaryOp(unaryop op) {
            String name = UnaryOpInfo.forOp(op).name;
            Class<?> here = this.getClass();
            Class<?> targetClass = this.javaClass;

            // Look for an exact match with the actual types
            MethodType mt = MethodType.methodType(O, targetClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            // Look for a match with (T)
            if (mh == null) {
                mt = MethodType.methodType(O, targetClass, O);
                mh = findStaticOrNull(here, name, mt);
            }

            // Look for a match with (Object)
            if (mh == null) {
                mh = findStaticOrNull(here, name, UOP);
            }

            if (mh == null) {
                return Runtime.UOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(UOP);
            }
        }
        //...
    }

For unary operators, we need a new ``CallSite`` subclass.
However, there is only one class to test in the guarded method handle:

..  code-block:: java

    static class UnaryOpCallSite extends MutableCallSite {

        final unaryop op;
        final Lookup lookup;
        final MethodHandle fallbackMH;

        static int fallbackCalls = 0;

        public UnaryOpCallSite(Lookup lookup, unaryop op)
                throws NoSuchMethodException, IllegalAccessException {
            super(Runtime.UOP);
            this.op = op;
            this.lookup = lookup;
            fallbackMH = lookup().bind(this, "fallback", Runtime.UOP);
            setTarget(fallbackMH);
        }

        @SuppressWarnings("unused")
        private Object fallback(Object v) throws Throwable {
            fallbackCalls += 1;
            Class<?> V = v.getClass();
            MethodType mt = MethodType.methodType(Object.class, V);
            // MH to compute the result for this class
            MethodHandle resultMH = Runtime.findUnaryOp(V, op);
            // MH for guarded invocation (becomes new target)
            MethodHandle testV = Runtime.HAS_CLASS.bindTo(V);
            setTarget(guardWithTest(testV, resultMH, fallbackMH));
            // Compute the result for this case
            return resultMH.invokeExact(v);
        }
    }

And that's the pattern for unary operations.


