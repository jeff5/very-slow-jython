..  architecture/_architecture.rst


Architecture
############

..  note:: This chapter
    (uniquely in the document as a whole)
    tries to capture the latest thinking rather than the journey.
    But parts are not always revised as things move on,
    so tends to be more inconsistent than the rest.
    (When settled, it should be in part of the Jython project.)

Previous chapters describe actual implementation experiments.
This chapter is for stepping back from the realised code.
Apart from analysis (of CPython etc.),
it will collect together two broad kinds of idea
about the architecture of a Java implementation of Python:

*   Ideas tested by the implementation experiments and shown to be useful.

*   Untested speculations.
    These may be tested in due course,
    in the experimental code of this project,
    quite likely resulting in some adjustment.

There will also be some issues we don't know how to address (yet).
Hopefully, these give rise to speculations
and then tested ideas that address them.

..  toctree::
    :maxdepth: 2

    arch-plain-java-object
    object-implementation
    type-slots
    arch-attribute-access
    interpreter-structure
    code-and-frame
    compiled-code
    functions-in-java
    descriptors


This chapter has diagrams generated with PlantUML
(if this diagnostic panel appears):

.. uml:: testdot.uml

