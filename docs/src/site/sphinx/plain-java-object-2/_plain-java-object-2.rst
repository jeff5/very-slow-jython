..  plain-java-object/_plain-java-object.rst


The Plain Java Object Model Extended
####################################

This chapter marks yet another fresh start,
although we are far from abandoning what was gained last chapter.
The Plain Object model is considered established,
we retain much of the apparatus of method exposure and calling,
and the Python byte code interpreter frame is little changed.

In the fourth model run-time system ``rt4``,
we aim initially for these new things:

#. Properly address Java classes as Python types.
#. Handle special methods as methods first:
   type slots are just optimisation.
#. Control visibility of the Jython API using Java modules.

We elaborate on these aims in the :doc:`introduction`.

..  toctree::

    introduction
    basic-patterns
    object-and-pytype


