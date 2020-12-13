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
1. [Performance](docs/src/site/sphinx/performance/_performance.rst)
1. [Architecture](docs/src/site/sphinx/architecture/_architecture.rst)
1. [Writing Code](docs/src/site/sphinx/coding/_coding.rst)

However, it is better read at [Read the Docs](https://the-very-slow-jython-project.readthedocs.io),
which should be up to date thanks to an integration with GitHub.
Alternatively, it can be built on your own machine.


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

