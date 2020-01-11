..  generated-code/introduction.rst


Introduction
############

In the former set of experiments (in sub-project ``rt1``)
we explored using an interpreter based on the AST of the source.
Here we will explore using an interpreter for CPython bytecode.
This avoids any code generation (to begin with),
as we can use the CPython compiler to generate it.

We remain focused on developing a data model that has the potential to
exploit fully the dynamic language support in the JVM.
The retreat to a more conservative kind of interpreter,
one that is closer to CPython's than the one in ``rt1`` or Jython 2 today,
will allow us to concentrate on the implementation of
a much wider variety of objects than was encountered in ``rt1``.

Our initial approach will be to emulate CPython closely
in the implementation its opcodes,
prioritising the numerical operations.
This does not imply that for the long term,
we have abandoned an implementation where code is compiled to Java byte code.
But the adoption of CPython byte code saves us complexity while we explore
the richer set of types we did not reach in ``rt1``.


Concepts Banked
***************

We will rely on the following ideas from ``rt1``:

* The ``frame`` is the locus of execution,
  containing the state and mechanism of executing code.
  (Different Java sub-classes exist for different types of code.)
* The ``code`` object is the factory for ``frame`` objects.
  This factory is attached to function and other objects,
  and used by ``exec()`` and so on to create their frames.
  (Different Java sub-classes exist for different types of code,
  to create the corresponding sub-class of frame.)
* We can avoid the ``PyObject`` universal base class,
  initially by making it an interface.
  As in CPython, objects get their behaviour through a ``type`` object,
  associated with each instance.
* Python objects may be represented by native Java objects,
  their Python semantics being provided by a distinct object
  associated to the class of the Java object.

This last principle will be the source of significant extra work,
both in design and at run-time.
The expectation in the JVM's dynamic language support is that
languages will emit their operations as call sites with class-based guards.
The hope (to be tested) is that, by falling in with this expectation,
generated code will be efficiently executed by the JVM.



Concepts to be Explored
***********************

We will examine a number of ideas under ``rt2``:

* An interpreter for CPython byte code.
* ``MethodHandle``\s as type slots.
* Attribute access.
* Function definition and call.
* Classes defined in Python.
* Sub-classes defined in Python.
* Mutable and immutable types.
* Adopting a Java type as a built-in.
* Java types in Python.

We can't necessarily explore this list linearly,
however, we start with the first.
