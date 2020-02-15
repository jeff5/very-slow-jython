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
The Python ``for ... in range`` statement is out of reach
until we tackle functions and iterators,
so our motivating example for this section uses old-school iteration:

..  code-block:: python

    n = 6
    sum = 0
    while n > 0:
        sum = sum + n
        n = n - 1

The compiled version contains mainly things we have already studied,
but the new elements are the rich-comparison opcode ``COMPARE_OP`` and
the branch instructions ``POP_JUMP_IF_FALSE`` and ``JUMP_ABSOLUTE``.
The comparison produces a ``bool``,
and the conditional branch consumes one.

..  code-block:: none

      1           0 LOAD_CONST               0 (6)
                  2 STORE_NAME               0 (n)

      2           4 LOAD_CONST               1 (0)
                  6 STORE_NAME               1 (sum)

      3     >>    8 LOAD_NAME                0 (n)
                 10 LOAD_CONST               1 (0)
                 12 COMPARE_OP               4 (>)
                 14 POP_JUMP_IF_FALSE       34

      4          16 LOAD_NAME                1 (sum)
                 18 LOAD_NAME                0 (n)
                 20 BINARY_ADD
                 22 STORE_NAME               1 (sum)

      5          24 LOAD_NAME                0 (n)
                 26 LOAD_CONST               2 (1)
                 28 BINARY_SUBTRACT
                 30 STORE_NAME               0 (n)
                 32 JUMP_ABSOLUTE            8
            >>   34 LOAD_CONST               3 (None)
                 36 RETURN_VALUE


Rich Comparison
***************


..  code-block:: java


..  code-block:: java



Branch instructions
*******************


..  code-block:: java


..  code-block:: java

