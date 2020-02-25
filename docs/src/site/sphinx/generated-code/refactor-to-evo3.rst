..  generated-code/refactor-to-evo3.rst

Refactoring the Code Base
#########################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo3``
    and ``rt2/src/test/java/.../vsj2/evo3/PyByteCode4.java``
    in the project source.


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


Static Factories for ``PyType``
*******************************

*   Re-work type object construction to use static factory methods.
    Identify and support necessary patterns of immutability,
    re-using ``NumberMethods.EMPTY`` etc. where possible.

Lightweight ``EmptyException``
******************************

*   ``EmptyException`` to be lightweight and static,
    following "The Exceptional Performance of Lil' Exception"
    (`Shipilev 2014`_).
    Add discussion suggesting correct balance.

..  _Shipilev 2014: https://shipilev.net/blog/2014/exceptional-performance/

Utility Methods
***************

*   Reconsider the placement of utility methods,
    such as those for exception construction.

Type Cast in the Method Handle
******************************

*   Try to wrap the cast into the slot function handle graph,
    so that "self" parameters to slot functions may be declared
    with their natural type.

Standardised Type-checking
**************************

*   Work out an approach for type-checking ``PyObject`` arguments,
    where still necessary.

