/**
 * The {@code codegen} package contains the apparatus for generating
 * Java class definitions for the ephemeral classes that define the
 * behaviour of Python code objects. These classes extend
 * {@code PyFrame} and belong to the {@code compiled} package. No code
 * outside the runtime references the classes directly although it may
 * encounter instances of them as Python {@code frame} objects.
 * <p>
 * This package is not exported.
 */
package uk.co.farowl.vsj4.codegen;
