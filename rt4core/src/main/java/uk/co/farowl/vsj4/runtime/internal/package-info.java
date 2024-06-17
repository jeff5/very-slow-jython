/**
 * The {@code runtime.internal} package contains internal parts of the
 * Python run-time system supporting compiled Python code during
 * execution.
 * <p>
 * This package is not exported. Classes {@code public} in this package
 * are accessible across the module, but not to client programs. Classes
 * belonging to this level should, for preference, be created here, then
 * carefully re-factored as it becomes necessary to make them API.
 * <p>
 * Classes here depend on the {@code runtime.types} API package and
 * <b>not</b> on {@code runtime} API package, which depends on this. We
 * thus avoid a dependency loop between API and implementation using the
 * <i>dependency inversion</i> pattern.
 */
package uk.co.farowl.vsj4.runtime.internal;
