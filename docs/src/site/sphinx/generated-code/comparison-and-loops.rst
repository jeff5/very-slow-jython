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

CPython byte code provides a cluster of opcodes for conditional,
and unconditional,
branching, which we implement in a straightforward way:

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



..  code-block:: java


..  code-block:: java

