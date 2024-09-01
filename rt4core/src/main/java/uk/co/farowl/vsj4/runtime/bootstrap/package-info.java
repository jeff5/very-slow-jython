/**
 * The {@code runtime.bootstrap} package contains parts of the Python
 * run-time system (types mainly) needed early in the creation of the
 * object system, but not needing privileged access to the
 * {@code runtime.kernel} package. Classes are placed here if their
 * static initialisation must occur in the bootstrap thread.
 * <p>
 * This package is not exported. Classes {@code public} in this package
 * are accessible across the module, but not to client programs.
 */
// XXX Should all be package-private in kernel instead?
package uk.co.farowl.vsj4.runtime.bootstrap;
