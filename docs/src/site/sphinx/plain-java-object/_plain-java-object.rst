..  plain-java-object/_plain-java-object.rst


A Plain Java Object Interpreter
###############################

In this chapter we start afresh on a third model run-time system
in sub-project ``rt3``.

We will test a series of ideas that may make it possible
to treat any Java object as a Python object.
Jython 2 achieves this by wrapping objects
that are not instances of some sub-class of ``PyObject`` in a proxy.
We will attempt to do this so that the interpreter (or compiled code)
handles the object directly.

Concepts developed in sub-project ``rt2`` will be heavily re-used.


..  toctree::

    introduction
    operations-builtin


..  A reminder of the chapters in the rt2 chapter:
    interpreter-cpython-byte-code
    type-and-arithmetic
    sequences-and-indexing
    built-in-inheritance
    comparison-and-loops
    refactor-to-evo3
    function-definition-and-call
    refactor-to-evo4
    attribute-access
    attributes-java
    attributes-python

