..  coding-standard/coding-standard.rst


Coding Standard
###############

Divergence from Jython 2 Standard
*********************************

This project is closely related to Jython,
and adopts essentially the same standard as Jython 2 for Java.
This has been put into practice (in Eclipse) by
importing the same formatting configuration in use for Jython 2 developement,
then some variations were made.

Brevity
=======

The most significant changes have been in the interests of
presenting sections of the code in the narrative:

* The line length is set to 72.
* Curly brackets are not universally required for loop and other bodies.
* Bodies of loops, if-statements and methods are allowed on the line
  if they fit.

These are all aimed at making best use of space on the page.
The same standard should not necessarily apply to
code that becomes part of a Jython 3 implementation.

The line length in particular works against readability when coding.
There will remain a need to quote code in documentation,
but then we can reformat the quotation.

The brevities (fewer brackets, bodies on the line, etc.)
may include readability without much risk to correctness.
There are, however, an awful lot of settings related to this in Eclipse,
such that it is difficult to see how any other IDE
could exactly reproduce the same style.
At least, it would be time-consuming and uncertain to try.
Simple rules that every IDE can support are to be preferred.

Modernity
=========

The other source of code style divergence will be new features.
The legacy Jython 2 coding standard has nothing explicit to say about
constructs that entered the language since about 2010.

For the time-being,
the new construct are formatted according to the Eclipse defaults for them,
modified where it seemed necessary for brevity.
