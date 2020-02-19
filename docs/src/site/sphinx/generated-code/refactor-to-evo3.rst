..  generated-code/refactor-to-evo3.rst

Refactoring the Code Base
#########################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo3``
    and ``rt2/src/test/java/.../vsj2/evo3/PyByteCode4.java``
    in the project source.


Refactoring Agenda
******************

Things to improve:

*   Replace constructors with functions in ``Py``,
    so ``Py.str("a")``, not ``new PyUnicode("a")``.
    This is shorter when constructing a ``PyCode``,
    and creates an opportunity for caching and alternate implementations.
    Must re-write the generator.
*   ``EmptyException`` to be lightweight and static,
    following "The Exceptional Performance of Lil' Exception"
    (`Shipilev 2014`_).
    Add discussion suggesting correct balance.
*   Reconsider the placement of utility methods,
    such as those for exception construction.
*   Try to wrap the cast into the slot function handle graph,
    so that "self" parameters to slot functions may be declared
    with their natural type.
*   Work out an approach for type-checking ``PyObject`` arguments,
    where still necessary.
*   Re-work type object construction to use static factory methods.
    Identify and support necessary patterns of immutability,
    re-using ``NumberMethods.EMPTY`` etc. where possible.


..  _Shipilev 2014: https://shipilev.net/blog/2014/exceptional-performance/