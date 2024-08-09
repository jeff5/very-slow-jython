/**
 * The {@code support.internal} package contains classes that support
 * the interpreter without requiring it to be initialised.
 * (Specifically, they may be used before the Python type system is in
 * working order, and without causing it to initialise.)
 * <p>
 * This package is not exported. Classes {@code public} in this package
 * are accessible across the module, but not to client programs. Classes
 * belonging to this level should, for preference, be created here, then
 * carefully re-factored as it becomes necessary to make them API.
 */
package uk.co.farowl.vsj4.support.internal;
