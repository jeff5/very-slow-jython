..  architecture/functions-in-java.rst


Functions Defined in Java
#########################

Introduction
************

This section is for discussion of
conventions around the definition and calling of functions defined in Java.
We might include in this:

* Functions in modules defined in Jave.
* Abstract API intended to be public.
* Private methods within the interpreter.
* Methods in "discovered" Java objects.  

What signatures can these have?
What conversion rules apply at the call interface?


..  note:: At the time of writing,
    we have a mixed style, but lean towards a signature in Java
    that reflects constraints documented and enforced in CPython. 


We have to consider the precedent set by CPython,
in which public API is usually in terms only of ``PyObject *``,
even when the documentation says the object must be of a particular type.
Contrary to this,
the implementation functions of type slots
sometimes declare with the actual type expected,
and those in modules defined in C,
where Argument Clinic has inserted a type-checking wrapper.

We must also look ahead to the possibility of "acceptable implementations".
This may mean that a method currently declared
to expect exactly ``PyUnicode``
will have to accept ``String`` as well.



