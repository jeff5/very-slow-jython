..  treepython/type+dispatch.rst


Type and Operation Dispatch
###########################

.. _operation_dispatch_cpython:

Operation Dispatch in CPython
*****************************

..  _PyTypeObject (API): https://docs.python.org/3/c-api/typeobj.html

Types are at the heart of the CPython interpreter.
Every object instance contains storage for its state and
a pointer to an instance of ``PyTypeObject``.

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

For these reasons something more than Java overloaded methods is neeeded
to implement Python semantics,
although we'll try that shortly to see where we get to.

.. _operation_dispatch_jython:

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
Suppose that a type is defined in Python,
by means of a class declaration.
In that case,
``__add__`` and ``__radd__`` are both overridden in a sub-class,
with methods that check the type's dictionary
for methods ``__add__`` and ``__radd__`` (respectively),
defined in Python.
If either is not found,
the corresponding Java (``super``) method is still called.

As well as being the base type of all objects,
``PyObject`` contains much of the runtime support for generated code.

The final assembly is quite complex, as is the corresponding CPython code,
but we may be sure it implements the Python rules well enough,
since it passes the extensive Python regression tests.
The general path through the code (for derived types defined in Python)
has the potential to be slow,
since multiple nested calls occur.
However, one cannot be categorical about this.
If the JVM JIT compiler is able to infer (or speculate on) argument types,
and if the code is used enough,
it is possible for it to in-line the code at the call site of ``_add``,
where it would collapse to one of the many type-specific cases.

Where a complex pure Java object is handled in Python,
the interpreter handles it via a proxy that is Java-derived from ``PyObject``.
Expressions that have simple Java types are converted to and from Jython built-in types
(``PyString``, ``PyFloat``, and so on)
at the boundary between Python and Java,
for example when passing arguments to a method.

.. _java_object_approach:

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
a Python ``object`` cannot be implemented by ``java.lang.Object``,
since it carries additional state.

We're going to explore this approach
in preference to reproducing the Jython approach,
partly for its potential to reduce indirection,
but mainly because the very helpful JSR-292 `cookbook`_ recommends it
(slide 34, minute 53 in the `cookbook video`_).

..  _cookbook: http://www.wiki.jvmlangsummit.com/images/9/93/2011_Forax.pdf
..  _cookbook video: http://medianetwork.oracle.com/video/player/1113248965001

The first challenge in this approach is
how to locate the operations the interpreter needs.
These correspond to the function slots in a CPython type object,
and there is a finite repertoire of them.

.. _dispatch_via_overloading:

Dispatch via overloading in Java
********************************

    Code fragments in this section are taken from
    ``vsj1/src/test/java/.../vsj1/example/treepython/TestEx2.java``
    in the project source.

Suppose we want to support the ``Add`` and ``Mult`` binary operations.
The implementation must differ according to the types of the operands.
CPython dispatches primarily on a single operand,
via the slots table,
combining two alternatives according to language rules for binary operations.

The single dispatch fits naturally with direct use of overloading in Java.
We cannot give ``java.lang.Integer`` itself any new behaviour,
and so we must map that type to a handler object that understands
the Python operations.
A registry is necessary to map the Java class to operations handler,
that for now we may initialise statically.
Java provides a class for exactly this thread-safe lookup in ``java.lang``
called ``ClassValue``.
We'll use this for runtime support like this:

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

The alert reader will notice that the classes shown in our examples
are often ``static`` nested classes.
This has no functional significance.
We simply want to hide them within the test class that demonstrates them.
Once they mature, we'll create public or package-visible classes.

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
as there is no restriction on the type of arguments and returns in Python.

Our attempt at implementing the operations for ``int`` then looks like this:

..  code-block:: java

    static class IntegerHandler extends TypeHandler {

        @Override
        public Object add(Object vobj, Object wobj) {
            Class<?> cv = vobj.getClass();
            Class<?> cw = wobj.getClass();
            if (cv == Integer.class && cw == Integer.class) {
                return (Integer) vobj + (Integer) wobj;
            } else {
                return null;
            }
        }

        @Override
        public Object multiply(Object vobj, Object wobj) {
            Class<?> cv = vobj.getClass();
            Class<?> cw = wobj.getClass();
            if (cv == Integer.class && cw == Integer.class) {
                return (Integer) vobj * (Integer) wobj;
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
The type (``V``) of the left operand gets the first go at evaluation.
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

.. _dispatch_via_method_handle:

Dispatch via a Java ``MethodHandle``
************************************

    Code fragments in this section are taken from
    ``vsj1/src/test/java/.../vsj1/example/treepython/TestEx3.java``
    in the project source.

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

The operation handler for each type extends this class,
and each handler must provide ``static`` methods roughly as before,
to perform the operations.
Now we are not using overloading,
we no longer need an abstract function for each.
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

Each operation handler enforces its singleton nature,
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

.. _caching_type_decisions:

Caching Type Decisions
**********************

In the preceding examples,
we can see that between gathering the ``left`` and ``right``
values in a ``BinOp`` node,
and any actual arithmetic,
stands some reasoning about the operand types.
This reasoning is embedded in the implementation of each type.
Something very similar is true in the C implementation of Python.

While we are not interested in
optimising the performance of *this* implementation,
because it is a toy,
we *are* interested in playing with
the techniques the JVM has for efficient implementation of dynamic languages.
That support revolves around the dynamic call site concept and
the ``invokedynamic`` instruction.
We don't actually (yet) have to generate ``invokedynamic`` opcodes:
we can study the elements by attaching a call site to each AST node.
We'll use it first in the ``BinOp`` node type,
by invoking its target.
The JVM will not optimise these,
as it would were they used in an ``invokedynamic`` instruction,
but that doesn't matter:
we're learning the elements to  use in generated code.

In a real program,
any given expression is likely to be evaluated many times,
for different values, and
in a dynamic language,
for different types.
(We seldom care about speed in code run only once.)
It is commonly observed that,
even in dynamic languages,
code is executed many times in succession with the *same* types,
for example within a loop.
The type wrangling in our implementation is a costly part of the work,
so Java offers us a way to cache the result of that work
at the particular site where the operation is needed,
and re-use it as often as the same operand types recur.
We need the following elements to pull this off (for a binary operation):

*   An implementation of the binary operation,
    specialised to the operand types,
    or rather, one for each combination of types.
*   A mechanism to map from (a pair of) types to the specialised implementation.
*   A structure to hold the last decision
    and the types for which the site is currently specialised
    (the call site).
*   A test (called a guard)
    that the types this time match the current specialisation.

It is possible to create call sites in which several specialisations
are cached for re-use,
according to any strategy the language implementer favours,
but we'll demonstrate it with a cache size of one.

A false Assumption?
===================

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
This hardly ever happens in practice --
the determinant usually *is* just the types involved --
and it can be accommodated in the technique we're about to demonstrate.

Mapping to a Specialised Implementation
=======================================

    Code fragments in this section are taken from
    ``vsj1/src/test/java/.../vsj1/example/treepython/TestEx4.java``
    in the project source.

We'll avoid an explicit ``CallSite`` object to begin with.
The first transformation we need is to separate,
in the ``visit_BinOp`` method of the ``Evaluator``,
*choosing* an implementation of the binary operation,
from *calling* the chosen implementation.

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
if neither operand type knows what to do.
The strategy is to turn everything into ``NoSuchMethodException``,
within the finding and invoking section,
then convert that to an appropriate exception
here where we know all the necessary facts.

It remains to be seen how we will implement ``Runtime.findBinOp``
to return the ``MethodHandle`` we need.
To explain it, we'll skip to the other end of the problem first.
This is how we would like to write specialised implementations:

..  code-block:: java

    static class DoubleHandler extends TypeHandler {

        DoubleHandler() { super(lookup()); }

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
In CPython, this chance is offered by actually calling the implementation,
which returns ``NotImplemented`` if it can't deal with the operands.

This is where we part company with CPython,
since our purpose is *only* to obtain a method handle,
without calling it at this point.
We therefore invent the convention that
every handler must provide a method we can consult to find the implementation,
given the operation and the Java class of each operand.
For types implemented in Java, this can work by reflection:

..  code-block:: java

    static abstract class TypeHandler {

        /** A method implementing a binary operation has this type. */
        protected static final MethodType BINOP = Runtime.BINOP;
        /** Shorthand for <code>Object.class</code>. */
        static final Class<Object> O = Object.class;

        private MethodHandle findStaticOrNull(Class<?> refc, String name,
                MethodType type) {
            try {
                MethodHandle mh = lookup.findStatic(refc, name, type);
                return mh;
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return null;
            }
        }

        /**
         * Return the method handle of the implementation of
         * <code>v op w</code>, if one exists within this handler.
         *
         * @param vClass Java class of left operand
         * @param op operator to apply
         * @param wClass Java class of right operand
         * @return
         */
        public MethodHandle findBinOp(Class<?> vClass, operator op,
                Class<?> wClass) {
            String name = BinOpInfo.forOp(op).name;
            Class<?> here = this.getClass();

            // Look for an exact match with the actual types
            MethodType mt = MethodType.methodType(O, vClass, wClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            if (mh == null) {
                return Runtime.BINOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(BINOP);
            }
        }

        // ...
    }

In fact, the logic above for ``TypeHandler.findBinOp``
will not cover all cases of interest.
We'd like to support the possibility of implementations that
accept any type of operand and embed their own type logic,
which then operates when the binary operation is executed.
(We certainly need this for objects defined in Python.)
This may be accomplished by overriding ``findBinOp`` in a sub-class handler.

Now we can use this to implement the required Python delegation pattern:

..  code-block:: java

    /** Runtime support for the interpreter. */
    static class Runtime {
        //...

        static MethodHandle findBinOp(Class<?> vClass, operator op,
                Class<?> wClass)
                throws NoSuchMethodException, IllegalAccessException {
            TypeHandler V = Runtime.typeFor(vClass);
            TypeHandler W = Runtime.typeFor(wClass);
            MethodHandle mhV = V.findBinOp(vClass, op, wClass);
            if (W == V) {
                return mhV;
            }
            MethodHandle mhW = W.findBinOp(vClass, op, wClass);
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

In many cases, only one of the operand types will offer an implementation,
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
    ``vsj1/src/test/java/.../vsj1/example/treepython/TestEx5.java``
    in the project source.

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

        resetFallbackCalls();
        evaluator.variables.put("x", 4);
        evaluator.variables.put("y", -1);
        assertThat(tree.accept(evaluator), is(42));
        assertThat(BinOpCallSite.fallbackCalls, is(0));

        // Suddenly y is a float
        evaluator.variables.put("x", 2);
        evaluator.variables.put("y", 19.);
        assertThat(tree.accept(evaluator), is(42.));
        assertThat(BinOpCallSite.fallbackCalls, is(2));

        // And now so is x
        resetFallbackCalls();
        evaluator.variables.put("x", 6.);
        evaluator.variables.put("y", 7.);
        assertThat(tree.accept(evaluator), is(442.));
        assertThat(BinOpCallSite.fallbackCalls, is(4));

        // And now y is an int again
        resetFallbackCalls();
        evaluator.variables.put("x", 6.);
        evaluator.variables.put("y", 7);
        assertThat(tree.accept(evaluator), is(442.));
        assertThat(BinOpCallSite.fallbackCalls, is(1));
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
    ``vsj1/src/test/java/.../vsj1/example/treepython/TestEx6.java``
    in the project source.

The first job is to implement a little more of the Python AST for expressions,
represented here in ASDL::

    module TreePythonEx6
    {
        expr = BinOp(expr left, operator op, expr right)
             | UnaryOp(unaryop op, expr operand)
             | Constant(constant value, string? kind)
             | Name(identifier id, expr_context ctx)

        operator = Add | Sub | Mult | Div
        unaryop = UAdd | USub
        expr_context = Load | Store | Del
    }

When we generate the ``TreePythonEx6`` AST class from this,
we shall have the necessary data structures to represent unary ``+`` and ``-``.
In order that we can easily write expressions in Java,
representing expression ASTs,
we add the corresponding wrapper functions and constants:

..  code-block:: java

    public static final unaryop UAdd = unaryop.UAdd;
    public static final unaryop USub = unaryop.USub;
    public static final expr UnaryOp(unaryop op, expr operand)
        { return new expr.UnaryOp(op, operand); }

The ``UnaryOp`` node gets added to the ``Visitor`` interface:

..  code-block:: java

    public abstract class TreePythonEx6 { //...

        public interface Visitor<T> {
            default T visit_BinOp(expr.BinOp _BinOp) { return null; }
            default T visit_UnaryOp(expr.UnaryOp _UnaryOp) { return null; }
            default T visit_Constant(expr.Constant _Constant) { return null; }
            default T visit_Name(expr.Name _Name) { return null; }
        }
    }

We must add a specific visit method to our ``Evaluator``,
but it's just a simplified version of ``visit_BinOp``:

..  code-block:: java

        public Object visit_UnaryOp(expr.UnaryOp unaryOp) {
            // Evaluate sub-tree
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
and within the operation handlers for applicable types we define:

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
        static MethodHandle findUnaryOp(unaryop op, Class<?> vClass)
                throws NoSuchMethodException, IllegalAccessException {
            TypeHandler V = Runtime.typeFor(vClass);
            MethodHandle mhV = V.findUnaryOp(op, vClass);
            return mhV;
        }
        //...
    }

    static abstract class TypeHandler {
        //...
        public MethodHandle findUnaryOp(unaryop op, Class<?> vClass) {
            String name = UnaryOpInfo.forOp(op).name;
            Class<?> here = this.getClass();

            // Look for a match with the operand class
            MethodType mt = MethodType.methodType(O, vClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            if (mh == null) {
                return Runtime.UOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(UOP);
            }
        }

For unary operators, we need a new ``CallSite`` subclass.
However, there is only one class to test in the guarded method handle:

..  code-block:: java

    static class UnaryOpCallSite extends MutableCallSite {

        final unaryop op;
        final Lookup lookup;
        final MethodHandle fallbackMH;

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
            Class<?> V = v.getClass();
            MethodType mt = MethodType.methodType(Object.class, V);
            // MH to compute the result for this class
            MethodHandle resultMH = Runtime.findUnaryOp(op, V);
            // MH for guarded invocation (becomes new target)
            MethodHandle testV = Runtime.HAS_CLASS.bindTo(V);
            setTarget(guardWithTest(testV, resultMH, fallbackMH));
            // Compute the result for this case
            return resultMH.invokeExact(v);
        }
    }

And that's the pattern for unary operations.

.. _dispatch_with_multiple_implementations:

Dispatch with Multiple Implementations
**************************************

We've implemented the Python ``int`` type incorrectly!
Python integers have effectively no size limits
except the amount of memory available.
``Integer`` is limited to the range [-2\ :sup:`31`, 2\ :sup:`31`-1].

The Java type corresponding to ``int`` should be ``java.math.BigInteger``.
However, the implementation of basic operations in ``BigInteger`` is costly,
while the integers used in programs will often be small.
The JVM works naturally with 32-bit integers,
and many Java methods will return ``int``.
(We can't avoid boxing Java ``int`` to ``Integer``:
not if we want it to be an object in either language.)
Is it possible we could permit an object that is a Python ``int`` to have
either implementation,
without visible difference at the Python language level?

There are other applications for this too.
When implementing ``str``,
it is efficient to have two or three implementations
(as CPython does under the covers).
Many strings contain only ASCII, or Unicode BMP characters.
Only rarely do we need (20-bit) Unicode characters
that are likely to occupy a 32-bit word.

Let us begin by taking ``BigInteger`` as our *only* implementation of ``int``.
As before,
we implement the operations in a class with static methods like this:

..  code-block:: java

    static class BigIntegerHandler extends TypeHandler {

        BigIntegerHandler() { super(lookup()); }

        private static Object add(BigInteger v, BigInteger w)
            { return v.add(w); }
        private static Object sub(BigInteger v, BigInteger w)
            { return v.subtract(w); }
        private static Object mul(BigInteger v, BigInteger w)
            { return v.multiply(w); }
        private static Object div(BigInteger v, BigInteger w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object neg(BigInteger v) { return v.negate(); }
        private static Object pos(BigInteger v) { return v; }
    }

Now suppose that we choose to add the 32-bit ``Integer``
as an alternative implementation of ``int``.
Operations between ``Integer`` and ``Integer``
could follow the same pattern,
except that the result may sometimes overflow to a ``BigInteger``.
But what will we do when these two types meet at a binary operator?
We encountered this already where ``DoubleHandler`` accepted ``Integer``.
Continuing the same way,
we have to define every combination in at least one operation handler.
So, ``BigInteger`` has:

..  code-block:: java

        private static Object add(BigInteger v, BigInteger w) { ... }
        private static Object add(BigInteger v, Integer w) { ... }
        private static Object add(Integer v, BigInteger w) { ... }

and ``Double`` has:

..  code-block:: java

        private static Object add(Double v, Double w)  { ... }
        private static Object add(Double v, BigInteger w)  { ... }
        private static Object add(BigInteger v, Double w)  { ... }
        private static Object add(Double v, Integer w)  { ... }
        private static Object add(Integer v, Double w)  { ... }

Now, suppose further that we wish to admit other integer Java types:
``Byte``, ``Short``, ``Long``.
And suppose we wish to allow for single-precision floating-point ``Float``.
There could be an operation handler for each type,
accepting (say) all other types up to its own size.
It would work, but the number of combinations becomes uncomfortably large.

..  code-block:: java

        private static Object add(BigInteger v, BigInteger w) { ... }
        private static Object add(BigInteger v, Long w) { ... }
        private static Object add(Long v, BigInteger w) { ... }
        private static Object add(BigInteger v, Integer w) { ... }
        private static Object add(Integer v, BigInteger w) { ... }
        private static Object add(BigInteger v, Short w) { ... }
        private static Object add(Short v, BigInteger w) { ... }
        private static Object add(BigInteger v, Byte w) { ... }
        private static Object add(Byte v, BigInteger w) { ... }

In a normal Java program,
we would take advantage of the fact that
all these types extend the abstract class ``Number``.
We'd like do the same here, and write signatures like this:

..  code-block:: java

        private static Object add(BigInteger v, BigInteger w) { ... }
        private static Object add(BigInteger v, Number w) { ... }
        private static Object add(Number v, BigInteger w) { ... }

or even this:

..  code-block:: java

        private static Object add(Number v, Number w) { ... }

but this is not possible with the version of ``TypeHandler.findBinOp``
that we have been using,
since it looks for an exact match with the operand types.
However, these signatures become usable if we specialise ``TypeHandler``.

A ``TypeHandler`` for  ``Number`` operands
==========================================

    Code fragments in this section and the next are taken from
    ``vsj1/src/test/java/.../vsj1/example/treepython/TestEx7.java``
    in the project source.

Consider making ``BigIntegerHandler`` accept
a variety of integer ``Number`` types as either operand.
After checking in ``findBinOp`` for a signature that matches exactly,
we could look for one where the other operand,
left or right,
is any ``Number``.
In that method we could convert the ``Number`` to a ``BigInteger``,
before performing the arithmetic.
Or finally, we could allow both operands to be ``Number``.

In fact, that won't quite work.
We don't want to accept every ``Number``:
only the types for which conversion to ``BigInteger`` is a widening one,
so not ``Float``, for example.
For a given operation handler
we must state what kinds of ``Number`` are acceptable.

The general solution looks like this:

..  code-block:: java

    static abstract class MixedNumberHandler extends TypeHandler {

        /** Shorthand for <code>Number.class</code>. */
        static final Class<Number> N = Number.class;

        protected static final MethodType UOP_N =
                MethodType.methodType(O, N);
        protected static final MethodType BINOP_NN =
                MethodType.methodType(O, N, N);

        protected MixedNumberHandler(Lookup lookup) { super(lookup); }

        /** Test that the actual class of an operand is acceptable. */
        abstract protected boolean acceptable(Class<?> oClass);

        @Override
        public MethodHandle findUnaryOp(unaryop op, Class<?> vClass) {
            String name = UnaryOpInfo.forOp(op).name;
            Class<?> here = this.getClass();

            // Look for a match with the operand class
            MethodType mt = MethodType.methodType(O, vClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            if (mh == null && acceptable(vClass)) {
                // Look for a match with (Number)
                mh = findStaticOrNull(here, name, UOP_N);
            }

            if (mh == null) {
                return Runtime.UOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(UOP);
            }
        }

        @Override
        public MethodHandle findBinOp(Class<?> vClass, operator op,
                Class<?> wClass) {
            String name = BinOpInfo.forOp(op).name;
            Class<?> here = this.getClass();

            // Look for an exact match with the actual types
            MethodType mt = MethodType.methodType(O, vClass, wClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            if (mh == null) {
                if (acceptable(wClass)) {
                    // Look for a match with (vClass, Number)
                    mt = MethodType.methodType(O, vClass, N);
                    mh = findStaticOrNull(here, name, mt);
                    if (mh == null && acceptable(wClass)) {
                        // Look for a match with (Number, Number)
                        mh = findStaticOrNull(here, name, BINOP_NN);
                    }
                } else if (acceptable(vClass)) {
                    // Look for a match with (Number, wClass)
                    mt = MethodType.methodType(O, N, wClass);
                    mh = findStaticOrNull(here, name, mt);
                }
            }

            if (mh == null) {
                return Runtime.BINOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(BINOP);
            }
        }
    }

The finder methods for unary and binary operations look,
as before,
for an exact match with the operands,
but then go on to seek a relaxed match *if* the types are acceptable.
Let's see how we might use this.

A simple case is that of the handler for ``Double``:

..  code-block:: java

    static class DoubleHandler extends MixedNumberHandler {

        DoubleHandler() { super(lookup()); }

        private static Object add(Double v, Double w)  { return v+w; }
        private static Object sub(Double v, Double w)  { return v-w; }
        private static Object mul(Double v, Double w)  { return v*w; }
        private static Object div(Double v, Double w)  { return v/w; }

        private static Object neg(Double v) { return -v; }

        // Accept any Number types by widening to double
        private static Object add(Number v, Number w)
            { return v.doubleValue() + w.doubleValue(); }
        private static Object sub(Number v, Number w)
            { return v.doubleValue() - w.doubleValue(); }
        private static Object mul(Number v, Number w)
            { return v.doubleValue() * w.doubleValue(); }
        private static Object div(Number v, Number w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object neg(Number v) { return -v.doubleValue(); }
        private static Object pos(Number v) { return v; }

        @Override
        protected boolean acceptable(Class<?> oClass) {
            return oClass == Byte.class || oClass == Short.class
                    || oClass == Integer.class || oClass == Long.class
                    || oClass == BigInteger.class || oClass == Float.class
                    || oClass == Double.class;
        }
    }

Notice there are methods specific to ``Double``,
implementing the four rules and negation.
Here we need no conversion methods and un-boxing is automatic.
Then we offer the same operations again, applicable to ``Number``,
converting to double with a call to (virtual) ``Number.doubleValue()``.
The finder methods ensure these will only be called with acceptable
sub-classes of ``Number``.

We have not bothered to define explicit mixed methods with signature
``(Double, Number)`` and ``(Number, Double)``.
For this reason, ``Double.class`` must be amongst the acceptable types,
so that ``add(Double, Integer)`` matches ``add (Number, Number)``.
We don't actually need the ``(Double, Double)`` signatures at all,
but this way we avoid two virtual calls in the most common case.

Now, for ``BigInteger`` we have to work a little harder.
We convert ``Number`` operands to ``long`` first using ``Number.longValue()``,
and then to ``BigInteger`` to perform the arithmetic.
``BigInteger`` also implements ``Number.longValue``,
but to use it would truncate large values.
The list of acceptable ``Number`` types therefore does not include
``BigInteger`` itself,
only those types that may be widened to ``long``,
and we must implement the combinations with ``Number`` explicitly.
This argument doesn't apply to ``div`` (because it converts to ``double``)
or ``pos`` (because it is a pass-through).

..  code-block:: java

    static class BigIntegerHandler extends MixedNumberHandler {

        BigIntegerHandler() { super(lookup()); }

        private static Object add(BigInteger v, BigInteger w)
            { return v.add(w); }
        private static Object sub(BigInteger v, BigInteger w)
            { return v.subtract(w); }
        private static Object mul(BigInteger v, BigInteger w)
            { return v.multiply(w); }
        // Delegate to div(Number, Number): same for all types
        private static Object div(Number v, Number w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object neg(BigInteger v) { return v.negate(); }
        // Delegate to pos(Number) as just returning self
        private static Object pos(Number v) { return v; }

        // Accept any integer as w by widening to BigInteger
        private static Object add(BigInteger v, Number w)
            { return v.add(BigInteger.valueOf(w.longValue())); }
        private static Object sub(BigInteger v, Number w)
            { return v.subtract(BigInteger.valueOf(w.longValue())); }
        private static Object mul(BigInteger v, Number w)
            { return v.multiply(BigInteger.valueOf(w.longValue())); }

        // Accept any integer as v by widening to BigInteger
        private static Object add(Number v, BigInteger w)
            { return BigInteger.valueOf(v.longValue()).add(w); }
        private static Object sub(Number v, BigInteger w)
            { return BigInteger.valueOf(v.longValue()).subtract(w); }
        private static Object mul(Number v, BigInteger w)
            { return BigInteger.valueOf(v.longValue()).multiply(w); }

        // Accept any integers as v, w by widening to BigInteger
        private static Object add(Number v, Number w) {
            return BigInteger.valueOf(v.longValue())
                    .add(BigInteger.valueOf(w.longValue()));
        }
        private static Object sub(Number v, Number w) {
            return BigInteger.valueOf(v.longValue())
                    .subtract(BigInteger.valueOf(w.longValue()));
        }
        private static Object mul(Number v, Number w) {
            return BigInteger.valueOf(v.longValue())
                    .multiply(BigInteger.valueOf(w.longValue()));
        }

        private static Object neg(Number v) {
            return BigInteger.valueOf(v.longValue()).negate();
        }

        @Override
        protected boolean acceptable(Class<?> oClass) {
            return oClass == Byte.class || oClass == Short.class
                    || oClass == Integer.class || oClass == Long.class;
        }
    }


Specimen Optimisation for ``Integer``
=====================================

    Code fragments in this section are taken from
    ``vsj1/src/test/java/.../vsj1/example/treepython/TestEx8.java``
    in the project source.

In the implementation so far,
every integer operation promotes the type immediately to ``BigInteger``,
which gets the right result,
but isn't achieving our objective to compute with narrower types where possible.
Where the operands are ``Integer`` or narrower,
it is desirable to keep the result as an ``Integer``,
if it does not overflow.

In order to do this we (re-)create the handler for ``Integer``,
which now makes its computations in ``long``
and returns via a wrapper,
that chooses the representation according to the size of the result:

..  code-block:: java

    @SuppressWarnings(value = {"unused"})
    static class IntegerHandler extends MixedNumberHandler {

        IntegerHandler() { super(lookup()); }

        private static Object add(Integer v, Integer w)
            { return result( (long)v + (long)w); }
        private static Object sub(Integer v, Integer w)
            { return result( (long)v - (long)w); }
        private static Object mul(Integer v, Integer w)
            { return result( (long)v * (long)w); }
        private static Object div(Integer v, Integer w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object neg(Integer v) { return result(-(long)v); }
        private static Object pos(Integer v) { return v; }

        private static Object add(Integer v, Number w)
            { return result( v + w.longValue()); }
        private static Object sub(Integer v, Number w)
            { return result( v - w.longValue()); }
        private static Object mul(Integer v, Number w)
            { return result( v * w.longValue()); }
        private static Object div(Integer v, Number w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object add(Number v, Integer w)
            { return result( v.longValue() + w); }
        private static Object sub(Number v, Integer w)
            { return result( v.longValue() - w); }
        private static Object mul(Number v, Integer w)
            { return result( v.longValue() * w); }
        private static Object div(Number v, Integer w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object add(Number v, Number w)
            { return v.intValue() + w.intValue(); }
        private static Object sub(Number v, Number w)
            { return v.intValue() - w.intValue(); }
        private static Object mul(Number v, Number w)
            { return v.intValue() * w.intValue(); }
        private static Object div(Number v, Number w)
            { return v.doubleValue() / w.doubleValue(); }

        private static Object neg(Number v) { return -v.intValue(); }
        private static Object pos(Number v) { return v; }

        @Override
        protected boolean acceptable(Class<?> oClass) {
            return oClass == Byte.class || oClass == Short.class;
        }

        private static final long BIT31 = 0x8000_0000L;
        private static final long HIGHMASK = 0xFFFF_FFFF_0000_0000L;

        private static final Object result(long r) {
            // 0b0...0_0rrr_rrrr_rrrr_rrrr -> Positive Integer
            // 0b1...1_1rrr_rrrr_rrrr_rrrr -> Negative Integer
            // Anything else -> Long
            if (((r + BIT31) & HIGHMASK) == 0L) {
                return Integer.valueOf((int)r);
            } else {
                return Long.valueOf(r);
            }
        }
    }

We hope the JIT compiler will in-line the wrapper ``result``.
The methods here return ``Integer`` if they can and ``Long`` if they must.
Note that we do not try to select adaptively between all available
integer types, ``Byte``, ``Short``, etc.,
in order to avoid too frequent relinking of call sites.

Progress so far
***************
Refactoring
===========
At this point,
the classes developed in test programs
are solid enough to place in the core support library at
``vsj1/src/main/java/.../vsj1``
in the source of the project (not under ``test``).
What's left of the test program after this refactoring is at
``vsj1/src/test/java/.../vsj1/example/treepython/TestEx9.java``
in the project source.

We take this opportunity to adjust the supporting methods to simplify
writing operation handlers.
Changes include:

* ``TypeHandler`` was re-named ``Operations`` (see notes below).
* ``Runtime`` was re-named ``Py`` for brevity and clarity, and to avoid
  accidentally designating ``java.lang.Runtime``.
* There is a single ``NOT_IMPLEMENTED`` handle,
  ignoring an ``Object[]`` argument,
  and used uniformly
  (where previously ``null`` indicated a method was not found).
* ``Operations.findBinOp`` does not call ``asType``
  to ensure the returned handle can be invoked exactly as
  ``(Object,Object)Object``.
  Instead, ``Py.findBinOp`` takes care of the conversion.

With these changes, the finders within the ``Operations`` class
(previously ``TypeHandler``)
now look like:

..  code-block:: java

    public abstract class Operations {
        // ...

        public MethodHandle findUnaryOp(unaryop op, Class<?> vClass) {
            String name = UnaryOpInfo.forOp(op).name;
            // Look for a match with the operand class
            MethodType mt = MethodType.methodType(O, vClass);
            return findStatic(name, mt);
        }

        public MethodHandle findBinOp(Class<?> vClass, operator op,
                Class<?> wClass) {
            String name = BinOpInfo.forOp(op).name;
            // Look for an exact match with the actual types
            MethodType mt = MethodType.methodType(O, vClass, wClass);
            return findStatic(name, mt);
        }

        protected MethodHandle findStatic(String name, MethodType type) {
            try {
                return lookup.findStatic(this.getClass(), name, type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return NOT_IMPLEMENTED;
            }
        }
        // ...
    }

The runtime support in class ``Py``
(previously ``Runtime``)
gets a little more complicated because of the need to cast:

..  code-block:: java

    public class Py {
        // ...
        /** Handle of a method returning NotImplemented (any number args). */
        public static final MethodHandle NOT_IMPLEMENTED;
        /** A (static) method implementing a unary op has this type. */
        public static final MethodType UOP;
        /** A (static) method implementing a binary op has this type. */
        public static final MethodType BINOP;

        /** Operation throwing NoSuchMethodError, use as dummy. */
        static Object notImplemented(Object... ignored)
                throws NoSuchMethodException {
            throw new NoSuchMethodException();
        }

        /**
         * Provide (as a method handle) an appropriate implementation of the
         * given operation, on a a target Java type.
         */
        public static MethodHandle findUnaryOp(unaryop op, Class<?> vClass)
                throws NoSuchMethodException, IllegalAccessException {
            Operations V = Py.opsFor(vClass);
            MethodHandle mhV = V.findUnaryOp(op, vClass);
            return mhV.asType(UOP);
        }

        /**
         * Provide (as a method handle) an appropriate implementation of the
         * given operation, between operands of two Java types, conforming to
         * Python delegation rules.
         */
        public static MethodHandle findBinOp(Class<?> vClass, operator op,
                Class<?> wClass)
                throws NoSuchMethodException, IllegalAccessException {
            Operations V = Py.opsFor(vClass);
            Operations W = Py.opsFor(wClass);
            MethodHandle mhV = V.findBinOp(vClass, op, wClass);
            MethodHandle opV = mhV.asType(BINOP);
            if (W == V) {
                return opV;
            }
            MethodHandle mhW = W.findBinOp(vClass, op, wClass);
            if (mhW == NOT_IMPLEMENTED) {
                return opV;
            }
            MethodHandle opW = mhW.asType(BINOP);
            if (mhV == NOT_IMPLEMENTED || mhW.equals(mhV)) {
                return opW;
            } else if (W.isSubtypeOf(V)) {
                return firstImplementer(opW, opV);
            } else {
                return firstImplementer(opV, opW);
            }
        }
        // ...
    }


Reflection
==========
To note for future work:

* We have successfully implemented binary and unary operations
  using the dynamic features of Java -- call sites and method handles.
* We have used guarded invocation to create a simple cache in a textbook way.
* Something is not quite right regarding ``MethodHandles.Lookup``:

  * Each concrete sub-class of ``Operations`` yields method handles for its
    ``private static`` implementation methods, using its own look-up.
    (Good.)
  * Each call-site class uses its own look-up to access a ``fallback`` method
    it owns.
    (Good.)
  * The ultimate caller (node visitor here) gives its own look-up object to the
    call-site, as it will under ``invokedynamic``, but we don't use it.
    (Something wrong?)

* We have not yet discovered an actual Python ``type`` object.
  The class tentatively named ``TypeHandler`` (now ``Operations``) is not it,
  since we have several for one type ``int``:
  it holds the *operations* for one Java implementation of the type,
  not the Python-level *type* information.
* Speculation: we will discover a proper type object
  when we need a Python *class* attribute (like the MRO or ``__new__``.
  So far, only *instance* methods have appeared.

