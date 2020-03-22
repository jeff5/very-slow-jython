..  generated-code/refactor-to-evo3.rst

Refactoring the Code Base
#########################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo3``
    in the project source,
    and are exercised by unit tests in ``rt2/src/test/java/.../vsj2/evo3``.


Code in the last few sections has all resided in package ``evo2``.
Reflecting on how this has developed,
several choices made along the way could be improved upon.
We don't want to invalidate the commentary that has been written so far,
so that readers can no longer find the code referred to.
The only solution is to make a copy of everything in a new package ``evo3``,
and refactor the code there.


Static Factory Methods in ``Py``
********************************

We have made extensive use of constructors to create Python objects in Java.
There are some potential advantages to using static functions:
brevity in the client code,
the potential to share commonly used values,
and the possibility of returning specialised sub-classes.
This will be done mostly for immutable value types.

It seems sensible (as in Jython 2.7) to implement these in class ``Py``.
We name the factory methods after the Python type they produce.
In place of ``new PyUnicode("a")``,
we should write ``Py.str("a")``,
in place of ``new PyTuple(...)``, ``Py.tuple(...)``,
and so on.
``Py.int(42)`` and ``Py.float(1.0)`` aren't valid Java,
so we take advantage of overloading and write
``Py.val(42)`` and ``Py.val(1.0)``.

``srcgen.py`` supports this with a pluggable code generator,
generating new tests in the new pattern,
from the same examples used previously.


Re-working ``PyType``
*********************

Untangling the Type Initialisation
==================================

In ``evo2``,
the implementation class of each Python object
created an instance of concrete class ``PyType`` by calling a *constructor*
from the static initialisation of the class.
This has been ok so far,
but it results in a bit of a recursive scramble,
with the risk that we call methods on fundamental types
before they are ready.
(The Jython 2.7 type registry is also delicate on this way.)

We should like the option to create variants of ``PyType``,
according (for example) to different mutability patterns,
and whether the type has numerical or sequence nature.
This might extend to sub-classing ``PyType`` if necessary.
Constraints on the coding of constructors
(on when ``super`` and ``this`` or instance methods may be called)
limit the possibilities for expression.

A series of static factory methods and helpers is more flexible,
but it is complicated to express different desired behaviours
even to static calls,
and undesirable that a type once created be mutated freely.
We therefore introduce a "specification" object:

..  code-block:: java

    class PyType implements PyObject {

        // ...

        /** Specification for a type. A data structure with mutators. */
        static class Spec {

            final static EnumSet<Flag> DEFAULT_FLAGS =
                    EnumSet.of(Flag.BASETYPE);
            final String name;
            final Class<? extends PyObject> implClass;
            final List<PyType> bases = new LinkedList<>();
            EnumSet<Flag> flags = EnumSet.copyOf(DEFAULT_FLAGS);

            /** Create (begin) a specification for a {@link PyType}. */
            Spec(String name, Class<? extends PyObject> implClass) {
                this.name = name;
                this.implClass = implClass;
            }

            Spec base(PyType b) { ... }
            Spec flag(Flag f) { ... }
            Spec flagNot(Flag f) { ... }
            PyType[] getBases() { ... }
            // ...
        }
    }

One may observe in this the beginnings of support for new features,
including an attempt to support Python inheritance, and
a field called ``flags`` (an ``EnumSet``),
with the same general purpose as ``tp_flags`` (a bit set) in CPython.

..  note::
    As things develop, we expect this code to evolve
    (so it may not be the same in the code base as in the text).

CPython also has a ``PyType_Spec`` structure (for a ``PyHeapTypeObject``\s),
which shares ``name`` and ``flags`` with ours,
but it otherwise runs mainly to a description of the slots,
which we seem to be doing adequately by a naming convention.
Following CPython,
we introduce a static factory method to interpret the ``PyType.Spec``:

..  code-block:: java

    class PyType implements PyObject {
        // ...

        /** Construct a type from the given specification. */
        static PyType fromSpec(Spec spec) {
            return new PyType(spec.name, spec.implClass, spec.getBases(),
                    spec.flags);
        }

        private PyType(String name, Class<? extends PyObject> implClass,
                PyType[] declaredBases, EnumSet<Flag> flags) {
            this.name = name;
            this.implClass = implClass;
            this.flags = flags;
            // Fix-up base and MRO from bases array
            setMROfromBases(declaredBases);
            // Fill slots from implClass or bases
            setAllSlots();
        }
        // ...
    }

As an example of its use, consider PyBool:

..  code-block:: java

    class PyBool extends PyLong {

        static final PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("bool", PyBool.class) //
                        .base(PyLong.TYPE) //
                        .flagNot(PyType.Flag.BASETYPE));
        // ...

Here we are saying that ``bool`` has ``int`` as a base (in Python)
and may not itself be a base for further derivation (in Python).


Flattening the Slot-function Table
==================================

In the section :ref:`representing-python-class`,
we noted that in the CPython ``PyTypeObject``,
some slots were directly in the type object (e.g. ``tp_repr``),
while most were arranged in sub-tables,
pointed to by fields (that may be ``NULL``) in the type object.
The motivation is surely to save space on type objects that do not need
the full set of slots.
The cost is some testing and indirection where these slots are used.
A common idiom in the CPython source is something like:

..  code-block:: C

    m = o->ob_type->tp_as_mapping;
    if (m && m->mp_subscript) {
        PyObject *item = m->mp_subscript(o, key);
        return item;
    }

We observe that types defined in Python (``PyHeapTypeObject``)
always create all the tables,
so only types defined in C benefit from this parsimony.
As there are 80 slots in total,
the benefit cannot exceed 640 bytes per type (64-bit pointers),
which is a minor saving even if there are a few hundred such types.

In ``evo3`` we have chosen an implementation in which
all the slots are fields directly in the type object.
This significantly simplifies the code to create them,
and saves an indirection or two with each operation.
With the trick of using ``EmptyException`` in place of a test,
the equivalent Java code is just:

..  code-block:: java

        PyType oType = o.getType();
        try {
            return (PyObject) oType.mp_subscript.invokeExact(o, key);
        } catch (EmptyException e) {}

The supporting fields in ``PyType`` are all ``MethodHandle``\s as before:

..  code-block:: java

    class PyType implements PyObject {
        //...
        // Standard type slots table see CPython PyTypeObject
        MethodHandle tp_hash;
        MethodHandle tp_repr;
        //...

        // Number slots table see CPython PyNumberMethods
        MethodHandle nb_negative;
        MethodHandle nb_add;
        //...

        // Sequence slots table see CPython PySequenceMethods
        MethodHandle sq_length;
        MethodHandle sq_repeat;
        MethodHandle sq_item;
        //...

        // Mapping slots table see CPython PyMappingMethods
        MethodHandle mp_length;
        MethodHandle mp_subscript;
        //...

Previously we gave these short names within their sub-table structures,
but in the larger scope
it seems wise to flag them as slots by using the same names as CPython.
This will simplify translation of code from CPython to Jython, and
it avoids clashes,
e.g. between ``sq_length`` and ``mp_length``,
or between Java ``int`` and ``nb_int``.
We shall not name all the ``PyType`` fields with the ``tp_`` prefix:
fields like ``name``, ``bases`` and ``mro`` are not slots in this sense.

A revised version of ``Slot.java`` (about half the length it was)
now defines an enum with constants for each slot:

..  code-block:: java

    enum Slot {

        tp_hash(Signature.LEN), //
        tp_repr(Signature.UNARY), //
        //...

        nb_negative(Signature.UNARY, "-", "neg"), //
        nb_add(Signature.BINARY, "+", "add"), //
        //...

        sq_length(Signature.LEN, null, "length"), //
        sq_repeat(Signature.SQ_INDEX), //
        sq_item(Signature.SQ_INDEX), //
        sq_ass_item(Signature.SQ_ASSIGN), //

        mp_length(Signature.LEN, null, "length"), //
        mp_subscript(Signature.BINARY), //
        mp_ass_subscript(Signature.MP_ASSIGN);

        final MethodType type;
        final String methodName;
        final String opName;
        final MethodHandle empty;
        final VarHandle slotHandle;

        Slot(Signature signature, String opName, String methodName) {
            this.opName = opName == null ? name() : opName;
            this.methodName = methodName == null ? name() : methodName;
            this.type = signature.type;
            this.empty = signature.empty;
            this.slotHandle = Util.slotHandle(this);
        }

        Slot(Signature signature) { this(signature, null, null); }

        Slot(Signature signature, String opName) {
            this(signature, opName, null);
        }
        // ...
    }

As with the previous ``Slot.TP``, ``Slot.NB``, and so on,
the ``enum`` encapsulates a lot of behaviour (not shown),
supporting its use.
The design is the same one outlined in :ref:`how-we-fill-slots`,
but we no longer have to repeat the logic for ``Slot.NB``, ``Slot.SQ``, etc..

The name of the slot is the same as that of the ``enum`` constant, and
unless otherwise given,
so is the name of the method in the implementation class.
Consequently,
this change has us changing the names of some of these methods,
to match the distinctive slot name, e.g.:

..  code-block:: java

    class PyTuple implements PyObject {
        //...
        static int length(PyObject s) {
           //...
        }
        static PyObject sq_item(PyObject s, int i) {
           //...
        }
        static PyObject mp_subscript(PyObject s, PyObject item)
                throws Throwable {
           //...
        }
    }

Note that in the definition of ``enum Slot``,
we defined the implementation method name of ``sq_length`` and ``mp_length``,
to be ``"length"`` in both cases.
This reproduces the behaviour we had before,
but it is not necessarily right.
In all cases in the CPython core where both are defined,
one method serves both slots,
but they are not always both defined.

The initialisation of the ``PyType`` now uses a single loop
to initialise all the slots,
as may be seen in :ref:`inheritance-of-slot-functions`.


Manipulation of Slots and ``PyType.flags``
==========================================

We have made the slots package-accessible
so that we may use them directly in the implementation of
methods in the abstract object API (in ``Abstract.java``, for example).
It is not intended that they be accessible to user-written Java,
or be updated directly, even by the runtime.
Rather, we must allow the type to police updates:
in some cases, it is necessary for the ``PyType``
to co-ordinate additional changes.

Certain Python types allow setting the slots in a controlled way.
In others they are immutable.
In CPython this is controlled by a bit in an ``int tp_flags`` field,
and in our implementation by an element of ``EnumSet flags``.

In CPython, the question of mutability is conflated with
whether the type is a "heap type" (that is, allocated dynamically).
This second issue concerns where the type object is allocated.
Built-in types like ``int`` are not heap types,
and are not mutable,
while user-defined classes are heap types and are mutable.
All objects in Java are allocated by its runtime,
so we do not need the second, literal sense idea of "heap type".
When reading CPython source to emulate it,
we must be alert to which sense of ``Py_TPFLAGS_HEAPTYPE`` is being used.

We will use ``PyType.Flag.MUTABLE`` to signify that slots may be written,
or conversely, may be depended upon never to change.

.. _inheritance-of-slot-functions:

Inheritance of Slot Functions
=============================

We noted in :ref:`bool-implementation` that
``bool`` inherited the slot functions of ``int``,
because the look-up of (say) ``add`` on ``PyBool`` found ``PyLong.add``.
This made the test pass,
but resulted in taking the slow path in ``Number.binary_op1``.
We actually need to copy the ``add`` slot from the type of ``PyLong``
to the type of ``PyBool``,
and not to create a new ``MethodHandle`` for the same method.

Secondly,
we should ensure that when ``PyBool`` gives a different meaning to a slot,
this is the one that applies to a Python ``bool``.
An example of this is the boolean binary operations ``&``, ``|`` and ``^``,
which are bit-wise operations in ``int``,
but when both operands are ``bool``, yield ``True`` or ``False``.

We address this in the refactoring by copying the slots in a new type
from the base.
In fact we allow for multiple bases,
as we shall have to eventually.
The first one to supply the slot wins:

..  code-block:: java

    class PyType implements PyObject {
        // ...
        private void setAllSlots() {
            for (Slot s : Slot.values()) {
                final MethodHandle empty = s.getEmpty();
                MethodHandle mh = s.findInClass(implClass);
                for (int i = 0; mh == empty && i < bases.length; i++) {
                    mh = s.getSlot(bases[i]);
                }
                s.setSlot(this, mh);
            }
        }
        // ...

The equivalent code in CPython is ``typeobject.c::inherit_slots()``.
The logic there is more complex
as it has to deal with the sub-table structure.

..  note:: At the time of writing,
    the author has not worked out why in CPython the "base of the base"
    is involved in the ``SLOTDEFINED``,
    so that seems only to copy slots originating in the immediate base.

Eventually, we aim to:

*   Compute an MRO by Python rules (or approximation).

*   Choose the unique ``__base__`` by Python rules.

These can wait until we introduce class definitions in Python,
through generated tests.


Lightweight ``EmptyException``
******************************

In the section :ref:`unary-operation`,
we introduced the idea that empty slots would not be ``null``,
as in CPython,
but would throw an ``EmptyException`` when invoked.
Code in the run-time would catch this exception,
and this would save us a test for ``null``.

It is normally advised that, in Java,
the run-time cost of exceptions precludes
using them for normal transfers of control.
As a result,
the logic for binary operations (see :ref:`binary_operation`)
reserves this technique for use only where
an empty slot would lead to an exception anyway (often a ``TypeError``).
At the point where finding an empty slot is a normal occurrence,
the attempt is preceded by a test for ``BINARY_EMPTY``:

..  code-block:: java
    :emphasize-lines: 17,26

    class Number extends Abstract {
        // ...
        private static PyObject binary_op1(PyObject v, PyObject w,
                Slot binop) throws Slot.EmptyException, Throwable {
            PyType vtype = v.getType();
            PyType wtype = w.getType();

            MethodHandle slotv = binop.getSlot(vtype);
            MethodHandle slotw;

            if (wtype == vtype || (slotw = binop.getSlot(wtype)) == slotv)
                // Both types give the same result
                return (PyObject) slotv.invokeExact(v, w);

            else if (!wtype.isSubTypeOf(vtype)) {
                // Ask left (if not empty) then right.
                if (slotv != BINARY_EMPTY) {
                    PyObject r = (PyObject) slotv.invokeExact(v, w);
                    if (r != Py.NotImplemented)
                        return r;
                }
                return (PyObject) slotw.invokeExact(v, w);

            } else {
                // Right is sub-class: ask first (if not empty).
                if (slotw != BINARY_EMPTY) {
                    PyObject r = (PyObject) slotw.invokeExact(v, w);
                    if (r != Py.NotImplemented)
                        return r;
                }
                return (PyObject) slotv.invokeExact(v, w);
            }
        }

In ``Abstract.getItem`` and ``Abstract.setItem``
(see :ref:`tuple-creation-indexing` and :ref:`list-creation-indexing`)
we try the mapping slot first and do not guard it with a test,
choosing instead to catch the exception.
In almost all built-in types,
``mp_subscript`` is defined if ``sq_item`` is,
so the exception is rare.

The alternative is a call to ``Slot.isDefinedFor()``.
The cost of the test is paid every time,
while the cost of the exception only arises if it is thrown.
This may be a good trade if exceptions are cheap enough,
but how cheap do they have to be?

It has been shown
(see "The Exceptional Performance of Lil' Exception" [`Shipilev 2014`_])
that the cost of throwing an exception is very high, and consists of:

* the creation of a stack-trace, and

* the subsequent unwinding to the catch point.

The first cost may be eliminated by creating one instance statically and
throwing it every time.
Obviously the trace is then misleading, but we don't ever use it,
and we could suppress it altogether.
We can make that change in a refactoring of ``Slot``:

..  code-block:: java

    enum Slot {

        tp_hash(Signature.LEN), //
        tp_repr(Signature.UNARY), //
        // ...

        enum Signature implements ClassShorthand {
            UNARY(O, O), // NB.negative, NB.invert
            // ...

            Signature(Class<?> returnType, Class<?>... ptype) {
                // em = λ : throw Util.EMPTY
                // (with nominally-correct return type)
                MethodHandle em = MethodHandles.throwException(returnType,
                        EmptyException.class).bindTo(Util.EMPTY);
                // empty = λ u v ... : throw Util.EMPTY
                this.empty = MethodHandles.dropArguments(em, 0, ptype);
                // All handles in the slot must have the same type as empty
                this.type = this.empty.type(); // = (ptype...)returnType
            }
        }

        static class Util {
            static final EmptyException EMPTY = new EmptyException();
            // ...
        }
    }

The unwinding cost will be greatly reduced,
to no more than the cost of a jump,
if the compiler has in-lined the intervening calls.
Shipilev suggests that
if the exception is thrown more than one time in 10\ :sup:`4`,
a test is to be preferred,
although for a compiler that in-lines successfully,
his figures suggest the break-even is more like one time in 100.

Shipilev warns against relying on the in-lining,
but in our case (Java 11 and 6 years on) it may now be reliable.
The modern compiler is said to in-line method handle graphs well.

It is difficult to decide the issue without performance tests
that would deflect from our architectural investigation.
The benefit of the static exception trick
is to make it matter less which we choose in any given piece of code.

..  note:: Exceptions in Python *are* recommended
    as a normal form of flow control.
    It may be necessary to revisit ours with this technique in mind,
    if our ``PyException``\s are to have adequate performance.

..  _Shipilev 2014: https://shipilev.net/blog/2014/exceptional-performance/


..  _type-cast-in-method-handle:

Type Cast in the Method Handle
******************************

The implementation of the ``neg`` method of ``PyLong``
provides a simple example of this change.
The slot ``nb_negative`` may be invoked on any object.
As far as ``MethodHandle.invoke()`` is concerned
it must have the signature ``(PyObject)PyObject``,
but this leads to ugly implementations of ``neg`` and many other methods.

Previously,
the implementation method ``neg`` looked like this:

..  code-block:: java

    /** The Python {@code int} object. */
    class PyLong implements PyObject {
        // ...
        static PyObject neg(PyObject v) {
            BigInteger a = valueOrError(v);
            return new PyLong(a.negate());
        }
        // ...
        private static BigInteger valueOrError(PyObject v)
                throws InterpreterError {
            try {
                return ((PyLong) v).value;
            } catch (ClassCastException cce) {
                throw PyObjectUtil.typeMismatch(v, TYPE);
            }
        }
    }

We had to cast ``v`` to the correct type before working on it.
The ``ClassCastException`` should never be thrown in practice,
since only operations on a ``PyLong`` instance
should ever lead the interpreter to the ``PyType`` for ``int``,
and in no other type (except sub-classes) is a slot bound to this method.

Of course, it will happen that sometimes we end up here incorrectly,
but that would signify a bug in the interpreter.
We should perhaps let the cast fail,
catch it outside the interpreter loop,
and let the Java stack trace lead us to the problem.

In ``evo3`` we introduce a new type ``Slot.Self``,
exclusively for use as a placeholder in method signatures.
When looking for an implementation method with ``Slot.findInClass()``,
``Self`` gets replaced with with the class being searched.
Therefore the new implementation will be found that looks like this:

..  code-block:: java

        static PyObject neg(PyLong v) {
            return new PyLong(v.value.negate());
        }

This is much clearer to read,
here and in many other types and methods,
than what preceded it.
The same effect appears in CPython,
but because C is not type-safe,
it is just a matter of casting the implementation function pointer
to the required type for the slot.
``Objects/longobject.c`` provides an example:

..  code-block:: c

    static PyObject *
    long_neg(PyLongObject *v)
    {
        PyLongObject *z;
        if (Py_ABS(Py_SIZE(v)) <= 1)
            return PyLong_FromLong(-MEDIUM_VALUE(v));
        z = (PyLongObject *)_PyLong_Copy(v);
        if (z != NULL)
            Py_SIZE(z) = -(Py_SIZE(v));
        return (PyObject *)z;
    }

    static PyNumberMethods long_as_number = {
        /* ... */
        (unaryfunc)long_neg,        /*nb_negative*/

The support for this is to revise the signatures,
and to re-work ``Util.findInClass()`` as follows:

..  code-block:: java
    :emphasize-lines: 4,8,13,28,45,48

    enum Slot {
        // ...

        interface Self extends PyObject {}

        private interface ClassShorthand {
            static final Class<PyObject> O = PyObject.class;
            static final Class<?> S = Self.class;
            // ...
        }

        enum Signature implements ClassShorthand {
            UNARY(O, S), // nb_negative, nb_invert
            BINARY(O, O, O), // +, -, u[v]
            TERNARY(O, O, O, O), // **
            PREDICATE(B, O), // nb_bool
            LEN(I, S), // sq_length
            RICHCMP(O, O, O, CMP), // (richcmpfunc) tp_richcompare only
            SQ_INDEX(O, O, I), // (ssizeargfunc) sq_item, sq_repeat only
            SQ_ASSIGN(V, O, I, O), // (ssizeobjargproc) sq_ass_item only
            MP_ASSIGN(V, O, O, O); // (objobjargproc) mp_ass_subscript only

            // ...
            Signature(Class<?> returnType, Class<?>... ptypes) {
                // The signature is recorded exactly as given
                this.type = MethodType.methodType(returnType, ptypes);
                // In the type of this.empty, replace Self with PyObject.
                MethodType slotType = Util.replaceSelf(this.type, O);
                // em = λ : throw Util.EMPTY
                MethodHandle em = MethodHandles
                        .throwException(returnType, EmptyException.class)
                        .bindTo(Util.EMPTY);
                // empty = λ u v ... : throw Util.EMPTY
                this.empty = MethodHandles.dropArguments(em, 0,
                        slotType.parameterArray());
            }
        }
        // ...
        private static class Util {
            static MethodHandle findInClass(Slot slot, Class<?> c) {
                try {
                    // The method has the same name in every implementation
                    String name = slot.getMethodName();
                    // The implementation has c where slot.type has Self
                    MethodType mtype = replaceSelf(slot.type, c);
                    MethodHandle impl = LOOKUP.findStatic(c, name, mtype);
                    // The invocation type remains that of slot.empty
                    return impl.asType(slot.empty.type());
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    return slot.empty;
                }
            }

            static MethodType replaceSelf(MethodType type, Class<?> c) {
                int n = type.parameterCount();
                for (int i = 0; i < n; i++) {
                    if (type.parameterType(i) == Self.class) {
                        type = type.changeParameterType(i, c);
                    }
                }
                return type;
            }
        }
    }

The cast that cluttered the implementation code is not avoided at runtime,
but is bound into the ``MethodHandle`` we create for the slot,
by the call to ``MethodHandle.asType()``.
If we ever need the invocation ``MethodType`` of a slot ``s``,
it is simply ``s.empty.type()``.

We make a final observation concerning inheritance.
``bool`` should inherit ``int``'\s definition of ``nb_negative``.
``PyBool`` is an acceptable argument to ``PyLong.neg``
because ``PyBool`` extends ``PyLong``.
The function definition is no longer a match in ``findInClass``,
because it does not match ``(PyBool)PyObject``,
but inheritance occurs as we require it
by slot copy in ``PyType.setAllSlots()``.


Standardised Type-checking
**************************

*   Work out an approach for type-checking ``PyObject`` arguments,
    where still necessary.


Utility Methods
***************

*   Reconsider the placement of utility methods,
    such as those for exception construction.

