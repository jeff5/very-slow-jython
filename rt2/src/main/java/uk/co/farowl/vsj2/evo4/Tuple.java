package uk.co.farowl.vsj2.evo4;

import java.util.List;

/**
 * The Python {@code tuple} object with parametric type, and
 * implementing an immutable {@code List<>} for that type. The slot
 * functions are implemented as static methods of this interface.
 * <p>
 * In the run-time, we frequently find that a Python {@code tuple}, in a
 * particular role, contains only elements of a single type. Examples
 * are the closure ({@link PyFunction#closure}) attached to a function,
 * which contains only cell objects, and the several arrays of names
 * attached to a code object ({@link PyCode#names},
 * {@link PyCode#varnames}, etc.). In these cases, it is helpful for
 * correctness, and may avoid a checked cast, to offer a guarantee about
 * the element type in the API. However, in many cases, such as the
 * interface with actual Python code, all we know is that a given object
 * is a {@link PyTuple} of {@link PyObject}.
 * <p>
 * We therefore define {@link PyTuple} in three stages: an interface
 * with typed elements, an implementation {@link TypedTuple} with typed
 * elements, and a concrete type with the simple name {@link PyTuple}.
 */
interface Tuple<E extends PyObject> extends List<E>, PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = new PyType("tuple", PyTuple.class);

}
