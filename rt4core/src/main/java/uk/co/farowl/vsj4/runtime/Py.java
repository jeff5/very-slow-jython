package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.runtime.kernel.TypeRegistry;

/** Operations on Python objects. */
public class Py {

    /** The (static singleton) registry created by PyType. */
    private static final TypeRegistry registry = PyType.registry;

    /** Instances are not allowed. */
    private Py() {};
}
