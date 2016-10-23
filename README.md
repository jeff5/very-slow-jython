# The Very Slow Jython Project

The aim of the Very Slow Jython project is to re-think implementation choices in the [Jython](http://www.jython.org) core, through the gradual, narrated evolution of a toy implementation,
starting from zero code.

This project is as much a writing project as a programming one.
The narrative of the development is in reStructuredText using Sphinx.

## Contents

Without building the project, GitHub does a reasonable job of rendering it, except for the [contents](src/site/sphinx/index.rst).
The main sections are these:

1. [Background to the Very Slow Jython Project](src/site/sphinx/background/background.rst)
1. [A Tree-Python Interpreter](src/site/sphinx/treepython/treepython.rst)


## Building the Project

This project builds with Apache Maven.
As it stands, the primary output is the narrative.
It uses the excellent [sphinx-maven-plugin](https://trustin.github.io/sphinx-maven-plugin), which delivers HTML output to the directory ``target/site/sphinx``, when you type:
```
mvn site
```

## Organisation of Sources

The [narrative](src/site/sphinx) of the Very Slow Jython project will be maintained as documentation in reStructuredText, in the same repository as the code itself.
However the "false starts", various failed implementation ideas, will stay around so that the narrative can make reference to them.

Currently, I'm not sure how I want to organise the successive versions: probably they will exist as versions, concurrently in the package structure, at the cost of some duplication.

