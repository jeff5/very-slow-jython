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

Without building the project, GitHub does a reasonable job of rendering it,
except for the [contents](src/site/sphinx/index.rst).
The main sections are these:

1. [Background to the Very Slow Jython Project](docs/src/site/sphinx/background/background.rst)
1. [A Tree-Python Interpreter](docs/src/site/sphinx/treepython/treepython.rst)
   1. [Some Help from the Reference Interpreter](docs/src/site/sphinx/treepython/ref_interp_help.rst)
   2. [An Interpreter in Java for the Python AST](docs/src/site/sphinx/treepython/ast_java.rst)
   3. [Type and Operation Dispatch](docs/src/site/sphinx/treepython/type+dispatch.rst)
   4. [Simple Statements and Assignments](docs/src/site/sphinx/treepython/simple_statements.rst) 
1. [A Generated Code Interpreter](docs/src/site/sphinx/generated-code/generated-code.rst)
   1. [Introduction](docs/src/site/sphinx/generated-code/introduction.rst)
   2. [An Interpreter for CPython Byte Code](docs/src/site/sphinx/generated-code/interpreter-cpython-byte-code.rst)
   3. [Type and Arithmetic Operations](docs/src/site/sphinx/generated-code/type-and-arithmetic.rst)
   4. [Sequences and Indexing](docs/src/site/sphinx/generated-code/sequences-and-indexing.rst)
   5. [Built-in Inheritance](docs/src/site/sphinx/generated-code/built-in-inheritance.rst)
   6. [Comparison and Loops](docs/src/site/sphinx/generated-code/comparison-and-loops.rst)
   7. [Refactoring the Code Base](docs/src/site/sphinx/generated-code/refactor-to-evo3.rst)


1. [Architecture](docs/src/site/sphinx/architecture/architecture.rst)
   1. [Interpreter Structure](docs/src/site/sphinx/architecture/interpreter-structure.rst)

## Building the Project

This project builds using Gradle.
As it stands, the primary output is the narrative.
It uses the excellent [sphinx-gradle-plugin](https://trustin.github.io/sphinx-gradle-plugin),
which delivers HTML output to the directory ``docs/build/site``.

### Windows
To build the narrative documentation type:

    .\gradlew --console=plain site

The conventional main task:

    .\gradlew --console=plain test

compiles and runs the unit tests that call the code shown in the text.


## Organisation of Sources

The [narrative](docs/src/site/sphinx) of the Very Slow Jython project
will be maintained as documentation in reStructuredText,
in the same repository as the code itself.
However the "false starts", various failed implementation ideas,
will stay around so that the narrative can make reference to them.

Currently, I organise the successive versions as sub-projects and packages,
concurrently in the project structure,
at the cost of some duplication.

