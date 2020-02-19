..  generated-code/comparison-and-loops.rst

Comparison and Loops
####################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo2``
    and ``rt2/src/test/java/.../vsj2/evo2/PyByteCode3.java``
    in the project source.


Motivating Example
******************

Now that we can index ``tuple`` and ``list``,
the next obvious target is a simple loop.
The Python ``for-in-range`` statement is out of reach
until we tackle functions and iterators,
so our motivating example for this section uses old-school iteration:

..  code-block:: python

    a = (1,2,3,4,5,6)
    n = 6
    sum = 0
    while n > 0:
        n = n - 1
        sum = sum + a[n]

The compiled version contains mainly things we have already studied,
but the new elements are the rich-comparison opcode ``COMPARE_OP`` and
the branch instructions ``POP_JUMP_IF_FALSE`` and ``JUMP_ABSOLUTE``.
The comparison produces a ``bool``,
and the conditional branch consumes one.

..  code-block:: none

      1           0 LOAD_CONST               0 ((1, 2, 3, 4, 5, 6))
                  2 STORE_NAME               0 (a)

      2           4 LOAD_CONST               1 (6)
                  6 STORE_NAME               1 (n)

      3           8 LOAD_CONST               2 (0)
                 10 STORE_NAME               2 (sum)

      4     >>   12 LOAD_NAME                1 (n)
                 14 LOAD_CONST               2 (0)
                 16 COMPARE_OP               4 (>)
                 18 POP_JUMP_IF_FALSE       42

      5          20 LOAD_NAME                1 (n)
                 22 LOAD_CONST               3 (1)
                 24 BINARY_SUBTRACT
                 26 STORE_NAME               1 (n)

      6          28 LOAD_NAME                2 (sum)
                 30 LOAD_NAME                0 (a)
                 32 LOAD_NAME                1 (n)
                 34 BINARY_SUBSCR
                 36 BINARY_ADD
                 38 STORE_NAME               2 (sum)
                 40 JUMP_ABSOLUTE           12
            >>   42 LOAD_CONST               4 (None)
                 44 RETURN_VALUE


Branch instructions
*******************

CPython byte code provides a cluster of opcodes for conditional
and unconditional branching,
which we implement in a straightforward way:

..  code-block:: java

                    case Opcode.JUMP_FORWARD:
                        ip += oparg; // JUMPBY
                        break;

                    case Opcode.JUMP_IF_FALSE_OR_POP:
                        v = valuestack[--sp]; // POP
                        if (!Abstract.isTrue(v)) {
                            sp += 1;    // UNPOP
                            ip = oparg; // JUMPTO
                        }
                        break;

                    case Opcode.JUMP_IF_TRUE_OR_POP:
                        v = valuestack[--sp]; // POP
                        if (Abstract.isTrue(v)) {
                            sp += 1;    // UNPOP
                            ip = oparg; // JUMPTO
                        }
                        break;

                    case Opcode.JUMP_ABSOLUTE:
                        ip = oparg; // JUMPTO
                        break;

                    case Opcode.POP_JUMP_IF_FALSE:
                        v = valuestack[--sp]; // POP
                        if (!Abstract.isTrue(v))
                            ip = oparg; // JUMPTO
                        break;

                    case Opcode.POP_JUMP_IF_TRUE:
                        v = valuestack[--sp]; // POP
                        if (Abstract.isTrue(v))
                            ip = oparg; // JUMPTO
                        break;

These all depend on being able to convert an arbitrary object to ``boolean``,
using ``Abstract.isTrue``.
(In the C-API this is ``PyObject_IsTrue``.)
The rules of "truthiness" applied here are those common throughout Python,
and only occasionally surprising:

..  code-block:: java

    class Abstract {
        //..
        static boolean isTrue(PyObject v) throws Throwable {
            // Begin with common special cases
            if (v == PyBool.True)
                return true;
            else if (v == PyBool.False || v == Py.None)
                return false;
            else {
                // Ask the object type through the bool or length slots
                PyType t = v.getType();
                if (Slot.NB.bool.isDefinedFor(t))
                    return (boolean) t.number.bool.invokeExact(v);
                else if (Slot.MP.length.isDefinedFor(t))
                    return 0 != (int) t.mapping.length.invokeExact(v);
                else if (Slot.SQ.length.isDefinedFor(t))
                    return 0 != (int) t.mapping.length.invokeExact(v);
                else
                    // No bool and no length: claim everything is True.
                    return true;
            }
        }
        //..
    }

CPython in-lines the first part of this in its implementation of the opcodes,
to avoid the call when the object is an actual ``PyBool``,
but we've gone for simplicity (and let the JVM do so if it wants).

Of note is the use of the ``Slot.NB.bool`` and ``PyType.number.bool``.
The techniques should be familiar by now.
We choose the "test before use" paradigm,
rather than "let it throw",
as none of the paths is an error.


Rich Comparison
***************

The CPython comparison opcode ``COMPARE_OP`` acts on two stacked objects.
It takes the type of comparison required from its argument,
interpreted as one of 11 possible types.
The implementation of the opcode is:

..  code-block:: java

                    case Opcode.COMPARE_OP:
                        w = valuestack[--sp]; // POP
                        v = valuestack[sp - 1]; // TOP
                        Comparison cmpOp = Comparison.from(oparg);
                        switch (cmpOp) {
                            case IS_NOT:
                                res = v != w ? PyBool.True
                                        : PyBool.False;
                                break;
                            case IS:
                                res = v == w ? PyBool.True
                                        : PyBool.False;
                                break;
                            case NOT_IN:
                            case IN:
                            case EXC_MATCH:
                                throw cmpError(cmpOp);
                            default:
                                res = Abstract.richCompare(v, w, cmpOp);
                        }
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

The function call ``Comparison.from(oparg)``
converts the opcode argument to an ``enum``.
We define the ``enum Comparison`` so that we may conveniently express
alternate paths.

The cases not handled explicitly
(``LT``, ``LE``, ``EQ``, ``NE``, ``GT`` and ``GE``)
are handed off to ``Abstract.richCompare``.
This wraps the slot function ``richcmp``,
in a way similar to that in which binary operations are invoked,
allowing left then right arguments to answer,
or right then left if the right-hand one is a sub-type of the left.

..  code-block:: java

    class Abstract {
        // ...
        static PyObject do_richcompare(PyObject v, PyObject w,
                Comparison op) throws Throwable {
            PyType vType = v.getType();
            PyType wType = w.getType();

            boolean checkedReverse = false;
            MethodHandle f;

            if (vType != wType && wType.isSubTypeOf(vType)
                    && (f = wType.richcompare) != RICH_EMPTY) {
                checkedReverse = true;
                PyObject r = (PyObject) f.invokeExact(w, v, op.swapped());
                if (r != Py.NotImplemented) { return r; }
            }

            if ((f = vType.richcompare) != RICH_EMPTY) {
                PyObject r = (PyObject) f.invokeExact(v, w, op);
                if (r != Py.NotImplemented) { return r; }
            }

            if (!checkedReverse && (f = wType.richcompare) != RICH_EMPTY) {
                PyObject r = (PyObject) f.invokeExact(w, v, op.swapped());
                if (r != Py.NotImplemented) { return r; }
            }

            /// Neither object implements op: base == and != on identity.
            switch (op) {
                case EQ:
                    return (v == w) ? PyBool.True : PyBool.False;
                case NE:
                    return (v != w) ? PyBool.True : PyBool.False;
                default:
                    throw comparisonTypeError(v, w, op);
            }
        }
        // ...
    }

The implementation of that slot for ``PyLong`` is:

..  code-block:: java

    class PyLong implements PyObject {
        // ...
        static PyObject richcompare(PyObject v, PyObject w, Comparison op) {
            if (v instanceof PyLong && w instanceof PyLong) {
                int u = ((PyLong) v).value.compareTo(((PyLong) w).value);
                return PyObjectUtil.richCompareHelper(u, op);
            } else {
                return Py.NotImplemented;
            }
        }
    }


The helper method converts the result of ``a.compareTo(b)``
to a ``bool``, respecting the operation type:

..  code-block:: java

    class PyObjectUtil {
        // ...
    static PyObject richCompareHelper(int u, Comparison op) {
        boolean r = false;
        switch (op) {
            case LE: r = u <= 0; break;
            case LT: r = u < 0; break;
            case EQ: r = u == 0; break;
            case NE: r = u != 0; break;
            case GE: r = u >= 0; break;
            case GT: r = u > 0; break;
            default: // pass
        }
        return r ? PyBool.True : PyBool.False;
    }

We'll be content for now with simple comparison of ``int``\s.
The logic for other types is complex,
without exposing any new issues of interpreter design.

It is perhaps interesting to observe that throughout this logic,
we continually branch on the type of operation,
and that for any given occurrence of the ``COMPARISON_OP`` opcode,
this is a constant.
It would presumably be beneficial to unravel this to a single switch
at opcode level (as we have partly done),
or to have defined 11 opcodes.
