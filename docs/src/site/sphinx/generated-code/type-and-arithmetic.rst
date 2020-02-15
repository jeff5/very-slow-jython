..  generated-code/type-and-arithmetic.rst

Type and Arithmetic Operations
##############################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo2``
    and ``rt2/src/test/java/.../vsj2/evo2/PyByteCode2.java``
    in the project source.

In this section we will extend the interpreter to cover
a selection of unary and binary operations.
Central to the way this is implemented in CPython is that
every Python object contains a pointer to a ``PyTypeObject``,
and this determines its class, that is,
how the operations on it are implemented.

We shall follow this design closely to obtain the same semantics,
in particular,
using Java ``MethodHandle``\s as an equivalent to C pointer to function.


Representing a Python Class
***************************

In CPython, objects are C ``struct``\s and
all operations on them are supplied by a ``PyTypeObject``.
Every Python object points to the ``PyTypeObject`` of its Python ``type``.
The ``PyTypeObject`` contains pointers to functions supporting
the operations that the Python interpreter might perform on an object.
In many cases,
the supporting function essentially implements a particular opcode,
apart from the stack effects.

In order to emulate this,
we must be able to get,
from any Python object in our implementation,
a ``type`` in which we find a definition of each supporting function
(or from which we find the function doesn't apply to that type of object).
To this end,
we define a class ``PyType``,
and require every ``PyObject`` be able to produce a reference to it.

..  code-block:: java

    interface PyObject {
        PyType getType();
    }

..  code-block:: java

    class PyType implements PyObject {
        // ...
        final String name;
        // ...
        @Override
        public String toString() { return "<class '" + name + "'>"; }
    }

In CPython,
a ``PyTypeObject`` includes a table of pointers (called "slots"),
one for each supporting function.
A slot may contain ``NULL``,
meaning that that function is not defined for the type.
The table is multi-level,
in that some of the fields in a ``PyTypeObject``
contain pointers to sub-tables of more slots,
with the possibility that that the sub-table pointer is ``NULL``,
meaning none of the slots in that table apply.
For example,
there is a sub-table for numerical operations,
and another for operations applicable to sequences,
and the pointers to these are non-null only if the type is numerical,
or a sequence, respectively.

Let's take a look at how this is used.


A Unary Operation ``UNARY_NEGATIVE``
************************************

We start with a unary operation,
as the logic is more complicated for binary ones.

When the CPython eval-loop encounters a ``UNARY_NEGATIVE`` opcode,
it calls ``PyNumber_Negative`` on the value from the top of the stack.
The definition is found in ``abstract.c`` and looks like this:

..  code-block:: C

    PyObject *
    PyNumber_Negative(PyObject *o)
    {
        PyNumberMethods *m;

        if (o == NULL) {
            return null_error();
        }

        m = o->ob_type->tp_as_number;
        if (m && m->nb_negative)
            return (*m->nb_negative)(o);

        return type_error("bad operand type for unary -: '%.200s'", o);
    }

Here, CPython picks up the object type (``o->ob_type``)
and heads for the numeric sub-table ``tp_as_number``.
If the table pointer is not null and the slot is not null,
it invokes the function it finds there.
``null_error()`` raises a Python ``SystemError``,
since we should never encounter a null object pointer here,
and ``type_error()`` raises the ``TypeError``
that tells you you've asked for an impossible operation.
Both return ``NULL``.
(Raising an exception in the C API occurs by returning ``NULL``,
and leaving values set in the CPython thread state,
that the eval-loop then picks up.
Our design will aim to make use of Java exceptions directly.)

We might ask whether we could not in-line ``PyNumber_Negative`` in ``eval()``,
but a survey of the code base shows that it is also used elsewhere,
wherever a negative is needed that adapts to the specific object type.
The companion functions ``PyNumber_Invert``, ``PyNumber_Add``,
``PyNumber_Multiply``, and so on,
are even more widely re-used,
so we take the hint and create a utility class ``Number`` to hold them.

The syntax of method handle invocation is not quite as neat as in C,
but in other ways our Java version can be more succinct:

..  code-block:: java

    class Number extends Abstract {

        /** Python {@code -v} */
        static PyObject negative(PyObject v) throws Throwable {
            try {
                MethodHandle mh = v.getType().number.negative;
                return (PyObject) mh.invokeExact(v);
            } catch (Slot.EmptyException e) {
                throw operandError("-", v);
            }
        }

        /** Create a {@code TypeError} for the named unary op. */
        static PyException operandError(String op, PyObject v) {
            return new TypeError("bad operand type for unary %s: '%.200s'",
                    op, v.getType().getName());
        }
        // ...
    }

The key part to understand is ``v.getType().number.negative``.
Here we go to the ``PyType`` object of ``v`` and
navigate to its definition of the ``negative`` slot,
which would be called ``nb_negative`` in CPython.

There are no tests for ``null``
because we do not use ``null`` to signify an empty slot,
but a special method handle ``UNARY_EMPTY``.
(As the name gives away,
there must be a special handle per slot type to match the signature.)
This handle leads to a method that throws ``EmptyException``.
Likewise, the reference ``number``
(which would be ``tp_as_number`` in CPython)
is never ``null``,
but points to a table where every slot throws that exception.

We do not mind that throwing exceptions may be a little slow,
since it mostly only happens under error conditions.
If nothing is thrown, ``try ... catch`` is essentially free.
Other exceptions (or arbitrary ``Throwable``\s),
we let propagate to the caller,
as this method does not know how to handle them.
Instead, we catch them in the ``eval()`` loop of our ``CPythonFrame``.

The handle for ``negative`` in the type of ``v``,
if it is not ``UNARY_EMPTY``,
points to a method in the implementation class.
Consider the case where ``v`` is a ``float``.
The implementation class is ``PyFloat``,
and the method will be this one:

..  code-block:: java

    class PyFloat implements PyObject {
        // ...
        static PyObject neg(PyObject v) {
            try {
                double a = ((PyFloat) v).value;
                return new PyFloat(-a);
            } catch (ClassCastException cce) {
                return Py.NotImplemented;
            }
        }
    }

There is an interesting difference from the CPython version,
which has the signature ``float_neg(PyFloatObject *v)``.
The way we choose a ``PyType`` object
guarantees that ``v`` will be a Python ``float``,
but the way we fill slots does not allow us (as CPython is allowed)
arbitrarily to cast the function signature.
Instead, we make the cast here and catch the exception.
Since it never happens
(unless there is a bug in the implementation)
perhaps we should raise an internal error,
or simply let the NPE propagate (with no ``try...catch`` at all).

.. _binary_operation:

A Binary Operation ``BINARY_ADD``
*********************************

For this specimen binary operation,
the wrapper is also like that in CPython:

..  code-block:: java

    class Number {
        // ...
        /** Python {@code v+w} */
        static PyObject add(PyObject v, PyObject w) throws Throwable {
            try {
                PyObject r = binary_op1(v, w, Slot.NB.add);
                if (r != Py.NotImplemented)
                    return r;
            } catch (Slot.EmptyException e) {}
            throw operandError("+", v, w);
        }
        // ...
    }

We do not (yet) deal with the addition of sequences,
meaning concatenation.

Note the function ``binary_op1``,
named identically to its Python counterpart,
contains the special logic that Python applies to binary operations.
Like CPython's,
it may return ``Py.NotImplemented`` if neither object knows how to implement
the operation.
Unlike CPython's,
it may also throw ``EmptyException`` if it invokes an "empty" slot.
These ``Py.NotImplemented`` may be the actual return value of an
implementation in Python of ``__add__`` or ``__radd__``.
These have exactly the same significance here,
and in either case,
we drop through to call ``typeError()``.

Our equivalent of CPython ``binop_op1`` in ``abstract.c``
is made somewhat simpler by this strategy and an absence of ``null`` tests:

..  code-block:: java

    class Number {
        // ...
        private static PyObject binary_op1(PyObject v, PyObject w,
                Slot.NB binop) throws Slot.EmptyException, Throwable {
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

        private static final MethodHandle BINARY_EMPTY =
                Slot.Signature.BINARY.empty;
        // ...
    }

In cases where we may have to let both objects answer,
we check the first slot to see if it is empty,
rather than letting it throw and having to catch it to try the other slot.
(Note the occurrence here of ``BINARY_EMPTY``.)
In other places, however,
we do not test for an empty slot,
since throwing the ``EmptyException`` is a satisfactory ending.

We do not at present implement Python sub-classing,
but the test is there (returning ``false``)
so we can exhibit the logic.

The argument ``Slot.NB binop`` may be puzzling.
It is actually a specially-crafted Java ``enum``
that is able to look up a method handle in a ``PyType``.
More on this next.


How we Fill the Slots
*********************

This is quite complicated.

CPython's ``typeobject.c`` is around eight thousand lines long,
and this compactness (!) is obtained by extensive use of C macros,
to generate both tabular data and entire function definitions.
We get something similar using inheritance and the Java ``enum``.

Our equivalent of the CPython ``PyNumberMethods`` is within ``PyType``:

..  code-block:: java

    class PyType {
        // ...
        /** Tabulates the number methods (slots) of a particular type. */
        static class NumberMethods {
            MethodHandle negative = Slot.NB.negative.empty;
            MethodHandle add = Slot.NB.add.empty;
            MethodHandle subtract = Slot.NB.subtract.empty;
            MethodHandle multiply = Slot.NB.multiply.empty;
            //...
        }
    }

The members are the slots
and construction sets them all "empty" (throwing ``EmptyException``).
There will be such a class for each ``tp_as_*`` sub-table
in a CPython ``PyTypeObject``.
The field names are identical to CPython's without the prefix ``nb_``,
but if we follow CPython,
the method names to which they map are (sometimes) not the same.

We need succinct ways to refer to the slots,
to define their signatures,
and to specify the methods that they call.
We do this through some specially-crafted Java ``enum``\s,
with appropriate behaviour.
We have already seen ``Slot.NB`` in action,
but the way we create the whole family interesting.

We begin by defining the allowable signatures for methods that fill slots.
(Compare these with the ``typedef``\s in CPython ``Include/object.h``:
``UNARY`` is ``unaryfunc``, ``SQ_ASSIGN`` is ``ssizeobjargproc``, etc..
The constructor arguments are the same as in a call to Java
``MethodType.methodType()``,
and from them we create both a ``MethodType`` ``type``,
and a ``MethodHandle`` ``empty`` conforming to that type,
that throws ``EmptyException``.

..  code-block:: java

    class Slot {

        private static final MethodHandles.Lookup LOOKUP =
                MethodHandles.lookup();

        static class EmptyException extends Exception {}

        private static final Class<PyObject> O = PyObject.class;
        private static final Class<?> I = int.class;
        private static final Class<?> B = boolean.class;
        private static final Class<?> V = void.class;
        private static final Class<Opcode.PyCmp> CMP = Opcode.PyCmp.class;
        // ...

        /**
         * An enumeration of the acceptable signatures for slots in
         * {@code PyType.*Methods} tables.
         */
        enum Signature {
            UNARY(O, O),
            BINARY(O, O, O),
            TERNARY(O, O, O, O),
            PREDICATE(B, O),
            LEN(I, O),
            RICHCMP(O, O, O, CMP),
            SQ_INDEX(O, O, I),
            SQ_ASSIGN(V, O, I, O),
            MP_ASSIGN(V, O, O, O);

            final MethodType type;
            final MethodHandle empty;

            Signature(Class<?> returnType, Class<?>... ptype) { /* ... */ }
        }
        // ...
    }

The next stage is to create an ``enum`` for each sub-table of slots,
and the slots in the ``PyType`` itself,
using these ``Signature`` constants to type the slots:

..  code-block:: java

    class Slot {
        // ...
        enum NB implements Any {

            negative(Signature.UNARY, "neg"),
            add(Signature.BINARY),
            subtract(Signature.BINARY, "sub"),
            multiply(Signature.BINARY, "mul"),

            final String methodName;
            final MethodType type;
            final MethodHandle empty;
            final VarHandle slotHandle;

            NB(Signature signature, String methodName) {
                this.methodName = methodName == null ? name() : methodName;
                this.type = signature.type;
                this.empty = signature.empty;
                this.slotHandle = EnumUtil.slotHandle(this);
            }

            NB(Signature signature) { this(signature, null); }

            // ...

            @Override
            public boolean isDefinedFor(PyType t) {
                return (MethodHandle) slotHandle.get(t.number) != empty;
            }

            @Override
            public MethodHandle findInClass(Class<?> c) {
                return EnumUtil.findInClass(this, c);
            }
        }

        interface Any {
            Group group();
            String name();
            String getMethodName();
            MethodType getType();
            MethodHandle getEmpty();
            boolean isDefinedFor(PyType t);
            MethodHandle findInClass(Class<?> c);
            MethodHandle getSlot(PyType t);
            void setSlot(PyType t, MethodHandle mh);
        }

        private static class EnumUtil {

            static VarHandle slotHandle(Any slot) {
                Class<?> methodsClass = slot.group().methodsClass;
                try {
                    // The field *Methods has the same name as the enum
                    return LOOKUP.findVarHandle(methodsClass, slot.name(),
                            MethodHandle.class);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    // ...
                }
            }

            static MethodHandle findInClass(Any slot, Class<?> c) {
                try {
                    // The method has the same name in every implementation
                    return LOOKUP.findStatic(c, slot.getMethodName(),
                            slot.getType());
                } catch (NoSuchMethodException | IllegalAccessException e) {
                    return slot.getEmpty();
                }
            }
        }
        // ...
    }

Notice that ``enum NB`` implements an interface ``Any``
that specifies its behaviour.
It also makes use of a helper class to supply behaviour
common between the enumerations ``NB``, ``SQ``, etc..

The ``enum NB`` does much more than designate a slot as it would in C.
The object is a getter/setter for slots in types,
and this works whichever sub-table the slot is in,
thanks to the use of ``java.lang.invoke.VarHandle slotHandle``.
The field (slot) a particular ``enum`` member gets and sets
has the same name as the enumeration constant itself.
So where ``t`` is the ``PyType`` target,
``Slot.NB.negative.setSlot`` sets ``t.number.negative``,
while ``Slot.TP.str.setSlot`` sets ``t.str``.

In the member declarations we get to specify the name of the method
(in some class implementing the type)
that will be placed in the slot,
and the signature that method should have.
``findInClass`` is the method that will go looking for it,
supported by ``EnumUtil.findInClass``.
The ``Signature``
implies both the ``MethodType`` of the required implementation
and the particular "empty" handle that should fill the slot otherwise.

Since the ``enum NB`` knows what "empty" looks like for this slot,
we can ask it to test for that in ``isDefinedFor(PyType t)``.
We can test a retrieved ``MethodHandle`` directly,
as in ``binary_op1`` above,
but the constant we use has to be the right kind of "empty" for the slot.

Finally,
since the several ``Slot.XX`` enumerations all implement ``Any``,
it is practicable to use one,
or work through lists of them,
without caring which sub-table any particular slot is in.
When necessary, the ``group`` method will reveal that.

We can use this apparatus in the construction of a ``PyType`` like so:

..  code-block:: java

    class PyType implements PyObject {

        static final PyType TYPE = new PyType("type", PyType.class);

        @Override
        public PyType getType() { return TYPE; }
        final String name;
        private final Class<? extends PyObject> implClass;

        // Method suites for standard abstract types.
        final NumberMethods number;
        final SequenceMethods sequence;
        final MappingMethods mapping;

        // Methods to implement standard operations.
        MethodHandle hash;
        MethodHandle repr;
        MethodHandle str;

        PyType(String name, Class<? extends PyObject> implClass) {
            this.name = name;
            this.implClass = implClass;

            // Initialise slots to implement standard operations.
            hash = Slot.TP.hash.findInClass(implClass);
            repr = Slot.TP.repr.findInClass(implClass);
            str = Slot.TP.str.findInClass(implClass);

            // If immutable, could use NumberMethods.EMPTY, etc.
            (number = new NumberMethods()).fillFromClass(implClass);
            (sequence = new SequenceMethods()).fillFromClass(implClass);
            (mapping = new MappingMethods()).fillFromClass(implClass);
         }
        // ...
    }

The method ``PyType.NumberMethods.fillFromClass``
(``SequenceMethods`` and ``MappingMethods`` are essentially the same):

..  code-block:: java

    class PyType implements PyObject {
        // ...
        static class NumberMethods {

            MethodHandle negative = Slot.NB.negative.empty;
            MethodHandle add = Slot.NB.add.empty;
            MethodHandle subtract = Slot.NB.subtract.empty;
            MethodHandle multiply = Slot.NB.multiply.empty;
            // ...

            void fillFromClass(Class<? extends PyObject> c) {
                for (Slot.NB s : Slot.NB.values()) {
                    MethodHandle mh = s.findInClass(c);
                    if (mh != s.empty) { s.setSlot(this, mh); }
                }
            }
        }
    }

This sets all the slots by reflection on the implementation class ``c``.

There are opportunities for optimisation,
spotting when a type does not define any slots in a particular sub-table,
and using a shared constant.
Some care will be required over whether and when
a type actually allows slots to be redefined.
CPython makes this distinction between built-in types and "heap types",
but where a type is allocated is not really the issue.
Appropriate visibility of mutators and validity checks will be needed.
For now, all our types admit modification of their slots.

