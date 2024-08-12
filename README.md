# The Very Slow Jython Project

The aim of the Very Slow Jython project is to re-think implementation choices
in the [Jython](http://www.jython.org) core,
through the gradual, narrated evolution of a toy implementation,
starting from zero code.

This project is as much a writing project as a programming one.
The narrative of the development is in reStructuredText using Sphinx.
It may be read at [The Very Slow Jython Project](https://the-very-slow-jython-project.readthedocs.io)
on ***Read the Docs***.
(Thanks for hosting it, guys.)

## Contents

Without building the project,
GitHub does a reasonable job of rendering pages but not the navigation.
The chapters are these:

[Contents](docs/src/site/sphinx/index.rst)

1. [Background to the Very Slow Jython Project](docs/src/site/sphinx/background/_background.rst)
1. [A Tree-Python Interpreter](docs/src/site/sphinx/treepython/_treepython.rst)
1. [A Generated Code Interpreter](docs/src/site/sphinx/generated-code/_generated-code.rst)
1. [A Plain Java Object Interpreter](docs/src/site/sphinx/plain-java-object/_plain-java-object.rst)
1. [Performance](docs/src/site/sphinx/performance/_performance.rst)
1. [Architecture](docs/src/site/sphinx/architecture/_architecture.rst)
1. [Writing Code](docs/src/site/sphinx/coding/_coding.rst)

It is probably best to start at [A Plain Java Object Interpreter](docs/src/site/sphinx/plain-java-object/_plain-java-object.rst).
Information before that describes ideas tried and abandoned.
It is best to read at [Read the Docs](https://the-very-slow-jython-project.readthedocs.io),
which should be up to date thanks to an integration with GitHub.


## Organisation of Sources

The [narrative](docs/src/site/sphinx) of the Very Slow Jython project
will be maintained as documentation in reStructuredText,
in the same repository as the code itself.
However the "false starts", various failed implementation ideas,
will stay around so that the narrative can make reference to them.

Currently, I organise the successive versions as sub-projects and packages,
concurrently in the project structure,
at the cost of some duplication.


## Building the Java Projects

This project builds using Gradle.

The most comprehensive work is in the sub-project `rt3`,
and a revision of that is under construction in the `rt4` sub-project.

`rt3` at the time of this note,
it just runs [JUnit tests](rt3/src/test).
These are the same tests run by a GitHub action
attached to the project.
`rt4` is just beginning and will follow the same pattern,
with the addition of an `rt4client` sub-project that depends on `rt4`,
and explores the API towards client programs and extensions.

There is no command-line interpreter or REPL in either work.

### Bytecode Interpreter

The most interesting demonstration of progress is that the interpreter
is able to run a limited subset of CPython bytecode.
[Example programs](rt3/src/test/pythonExample/vsj3/evo1)
are compiled and run by CPython during the build and the results
saved.
The Very Slow Jython interpreter then runs the same bytecode
and we compare the results.

It should be possible to run other sample programs compiled by CPython,
based on the code in [CPython38CodeTest.java](rt3/src/test/java/uk/co/farowl/vsj3/evo1/CPython38CodeTest.java).

To build and run the rt3 tests, use the command (Windows):

    .\gradlew --console=plain rt3:test

This compiles and runs the unit tests
that call the code shown in the narrative documentation.

### Performance

The project `rt3bm` measures the performance of simple
unary and binary arithmetic operations
using calls to the abstract object API as by the bytecode interpreter,
and compares it with the equivalent pure Java expression
(with known types).

A second project `dy3bm` repeats the measurement using
`invokedynamic` call sites of the sort we might generate
from a Python compiler into Java bytecode.




## Building the Narrative Documentation

Another important output is the narrative.
Read it at [Read the Docs](https://the-very-slow-jython-project.readthedocs.io).
As a last resort,
(and obviously this is what the author has to do)
it can be built on your own machine as follows.

It no longer builds under Gradle
thanks to bit-rot in the previously excellent [sphinx-gradle-plugin](https://trustin.github.io/sphinx-gradle-plugin).

To build the narrative documentation (Windows),
navigate in a PowerShell window to
`.\docs\src\site\sphinx` relative to the project root.
There create a Python virtual environment
and install the tools we need to build the documentation:

    PS sphinx> python -m venv sphinx-env
    PS sphinx> .\sphinx-env\Scripts\activate
    (sphinx-env) PS sphinx> python -m pip install -r requirements.txt

The line up to `PS sphinx> ` is the PowerShell prompt.
Yours may be different.
This is a one-time setup of course,
apart from the `activate` command
when you next need to start the environment.

The documentation builds with:

    (sphinx-env) PS sphinx> .\make html

The generated documentation will then be found at
`.\docs\build\site\index.html`.


