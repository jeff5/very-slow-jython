..  generated-code/data-model.rst


The Data Model
##############

Necessary Kinds of Object
*************************


Code Generated from Expressions
*******************************


Object, Operations and Types
****************************


Reflection
==========
.. copied from VSJ 1 "Type and Operation Dispatch"

To note for future work:

* We have successfully implemented binary and unary operations
  using the dynamic features of Java -- call sites and method handles.
* We have used guarded invocation to create a simple cache in a textbook way.
* Something is not quite right regarding ``MethodHandles.Lookup``:

  * Each concrete sub-class of ``Operations`` yields method handles for its
    ``private static`` implementation methods, using its own look-up.
    (Good.)
  * Each call-site class uses its own look-up to access a ``fallback`` method
    it owns.
    (Good.)
  * The ultimate caller (node visitor here) gives its own look-up object to the
    call-site, as it will under ``invokedynamic``, but we don't use it.
    (Something wrong?)

* We have not yet discovered an actual Python ``type`` object.
  The class tentatively named ``TypeHandler`` (now ``Operations``) is not it,
  since we have several for one type ``int``:
  it holds the *operations* for one Java implementation of the type,
  not the Python-level *type* information.
* Speculation: we will discover a proper type object
  when we need a Python *class* attribute (like the MRO or ``__new__``.
  So far, only *instance* methods have appeared.

