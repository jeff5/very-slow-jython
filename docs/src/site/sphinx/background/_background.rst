..  background/_background.rst

Background to the Very Slow Jython Project
##########################################

The aim of the Very Slow Jython project is to re-think implementation choices
in the `Jython`_ core,
through the gradual, narrated evolution of a toy implementation.

..  _Jython: http://www.jython.org
..  _Python 1.6: https://www.python.org/download/releases/1.6.1


Background to the project
*************************

`Jython`_ is an implementation of
the Python programming language for the JVM,
such that Java objects may be used from Python code, and vice versa.
It is a mature project, that has delivered a complex piece of software,
but it is not always clear why things are done as they are,
particularly in the core interpreter.

Jython is the work of nearly 20 years and many contributors:
it began as an implementation of Python 1.5 on JDK 1.1.
Jython versions track the changing language features of Python
(so that Jython 2.7, for example, implements Python 2.7).
When a feature is implemented in Jython, the best route to a compatible behaviour
is often to reproduce the logic used in CPython.
The implementation detail of a Jython feature therefore often reflects
the C implementation that was current at the time the feature was added.
This alignment with the CPython implementation,
which will have been discussed at length in `python-dev`,
and other places, may have been sufficient design justification at the time.
It can seem insufficient to those who look at the code much later.
In other places, the specifics of a *Java* implementation win out,
or code may have been re-worked from a Java perspective,
and there may, or may not, be a justification of the Java-specific design.

A lot has changed in Python, CPython and Java since some of this code was written.
The C implementation of a given feature may have diverged,
and some significant changes have been made to the Java language.

The fundamental question of the Very Slow Jython project is,
given that the desired behaviour is that of Python 3,
and the language available is Java 8,
what would we write if we started clean today?
The answer is not wholly obvious, but we will answer it through a practical attempt.
We can expect to make some false starts.
The idea is to do this from scratch, *very slowly*,
with explanations,
and reflecting on why these false starts were insufficient.
Proceeding via a series of false starts, although informative, is likely to be *very slow*.
We will mostly avoid "speed-up tricks" that make it less clear what is going on,
so if it gets anywhere near a runnable interpreter,
it will probably run *very slowly*.

(Incidentally, there was once a plain Slow Jython Project,
but it lost its way somewhat and this is a mostly fresh start.)

The Very Slow Jython project is not a clean-room activity:
the existing C and Java implementations are important references,
and Very Slow Jython will not differ from them just for the sake of difference.
In fact, the *simplicity* of older implementations like `Python 1.6`_
is one of the things we'll try to recapture.
But we will not simply fork or copy what already exists:
the reason we are starting with a toy implementation is
to free the exploration from the layers of prior decisions and optimisation.

Organisation of Sources
***********************

The narrative of the Very Slow Jython Project (which you are reading)
will be maintained as documentation in reStructuredText (reST),
in the same repository as the code itself.
However the "false starts", various failed implementation ideas,
will stay around so that the narrative can make reference to them.

Currently, I'm not sure how I want to organise the successive versions:
probably they will exist as versions,
concurrently in the package structure,
at the cost of some duplication.
