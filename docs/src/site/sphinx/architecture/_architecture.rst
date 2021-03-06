..  architecture/_architecture.rst


Architecture
############

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

    interpreter-structure
    code-and-frame
    object-implementation
    compiled-code
    functions-in-java
    descriptors
    arch-plain-java-object


This chapter has diagrams generated with PlantUML
(if this diagnostic panel appears):

.. uml:: testdot.uml

