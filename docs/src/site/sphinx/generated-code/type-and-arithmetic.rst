..  generated-code/type-and-arithmetic.rst

Type and Arithmetic Operations
##############################

    Code fragments in this section are taken from
    ``rt2/src/test/java/.../vsj2/PyByteCode2.java``
    in the project source.
    Class and other declarations shown are nested in class ``PyByteCode2``.

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
all operations on them are supplied by a ``PyTypeObject``
to which every Python object implementation of a given Python class points.
The ``PyTypeObject`` contains pointers to functions supporting
the operations that the Python interpreter might perform on an object.
In many cases,
the supporting function essentially implements the opcode,
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

    static class PyType implements PyObject {
        // ...
        final String name;
        // ...
        @Override
        public String toString() { return "<class '" + name + "')"; }
    }

In CPython,
a ``PyTypeObject`` includes a large-ish table of pointers
(called "slots"),
one for each supporting function.
The slot may contain ``NULL``,
meaning that function is not defined for the type.
The table is multi-level,
in that some of the fields in a ``PyTypeObject``
contain pointers to sub-tables of more slots,
with the possibility that that the pointer is ``NULL``,
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
since it should never happen,
and ``type_error()`` a ``TypeError`` based on the string you see.
(Raising an exception in the C API occurs by returning ``NULL``,
and leaving values set in the CPython thread state.
Our design is to make use of Java exceptions directly.)

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

   static class Number {

        /** Python {@code -v} */
        static PyObject negative(PyObject v) throws Throwable {
            try {
                MethodHandle mh = v.getType().number.negative;
                return (PyObject) mh.invokeExact(v);
            } catch (Slot.EmptyException e) {
                throw typeError("-", v);
            }
        }

        /** Create a {@code TypeError} for the named unary op. */
        static PyException typeError(String op, PyObject v) {
            return new TypeError(
                    "bad operand type for unary %s: '%.200s'", op,
                    v.getType().getName());
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
(The special value still has to be a unary function in a unary slot.)
This handle leads to a method that throws ``EmptyException``.
Likewise, the reference ``number``
(which would be ``tp_as_number`` in CPython)
is never ``null``,
but points to a table where every slot is such a handle.

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

        static PyObject neg(PyObject v) {
            try {
                double a = ((PyFloat) v).value;
                return new PyFloat(-a);
            } catch (ClassCastException cce) {
                return Py.NotImplemented;
            }
        }

There is an interesting difference from the CPython version,
which has the signature ``float_neg(PyFloatObject *v)``.
It is guaranteed that ``v`` will be a Python ``float``,
but in the way we fill slots
we cannot (as CPython does) arbitrarily cast the function signature,
so we make the cast and catch the exception.
Since it never happens
(unless there is a bug in the implementation)
perhaps we should raise an internal error,
or simply let the NPE propagate (with no ``try...catch`` at all).


A Binary Operation ``BINARY_ADD``
*********************************

The wrapper is like that in CPython:

..  code-block:: java

   static class Number {
        // ...
        /** Python {@code v+w} */
        static PyObject add(PyObject v, PyObject w) throws Throwable {
            try {
                PyObject r = binary_op1(v, w, NBSlot.add);
                if (r != Py.NotImplemented)
                    return r;
            } catch (Slot.EmptyException e) {}
            throw typeError("+", v, w);
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
These have exactly the same significance here,
and in either case,
we drop through to call ``typeError()``.

Our equivalent of CPython ``binop_op1`` in ``abstract.c``
is made somewhat simpler by this strategy and an absence of ``null`` tests:

..  code-block:: java

   static class Number {
        // ...
        private static PyObject binary_op1(PyObject v, PyObject w,
                NBSlot binop) throws Slot.EmptyException, Throwable {
            PyType vtype = v.getType();
            PyType wtype = w.getType();

            MethodHandle slotv = binop.getSlot(vtype);
            MethodHandle slotw;

            if (wtype == vtype
                    || (slotw = binop.getSlot(wtype)) == slotv)
                // Both types give the same result
                return (PyObject) slotv.invokeExact(v, w);

            else if (!wtype.isSubTypeOf(vtype)) {
                // Ask left (if not empty) then right.
                if (slotv != Slot.BINARY_EMPTY) {
                    PyObject r = (PyObject) slotv.invokeExact(v, w);
                    if (r != Py.NotImplemented)
                        return r;
                }
                return (PyObject) slotw.invokeExact(v, w);

            } else {
                // Right is sub-class: ask first (if not empty).
                if (slotw != Slot.BINARY_EMPTY) {
                    PyObject r = (PyObject) slotw.invokeExact(v, w);
                    if (r != Py.NotImplemented)
                        return r;
                }
                return (PyObject) slotv.invokeExact(v, w);
            }
        }
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

The argument ``NBSlot binop`` may be puzzling.
It is actually a specially-crafted Java ``enum``
that is able to look up a method handle in a ``PyType``.
More on this next.


How we Fill the Slots
*********************

This is quite complicated.


..  code-block:: java


..  code-block:: java


..  code-block:: java


