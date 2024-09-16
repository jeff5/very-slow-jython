// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * An instance of a class implementing {@code WithDictAssignment}
 * possesses a dictionary, generally exposed as a {@code __dict__}
 * attribute, that is definitely a Python {@code dict}. A {@code type}
 * object, for example, implements {@link #getDict()} to return only an
 * unmodifiable view of its dictionary.
 */
public interface WithDictAssignment extends WithDict {

    /**
     * The instance dictionary, a Python {@code dict}.
     *
     * @return instance dictionary
     */
    @Override
    // @Exposed.Get(name="__dict__")
    PyDict getDict();

    /**
     * Assign a new object to the {@code __dict__} attribute provided
     * the object meets certain criteria.
     *
     * @implNote The assignment must fail if {@code replacementDict} is
     *     not a Python {@code dict} (or subclass). The necessary check
     *     (and cast) should be made with
     *     {@link #checkDictAssignment(Object)}.
     * @param replacementDict intended new type object
     */
    // @Exposed.Set(name="__dict__")
    void setDict(Object replacementDict);

    /**
     * Called during {@code __dict__} assignment (that is, during the
     * implementation of {@link #setDict(Object)}) to check that the
     * object being assigned is acceptable. It is only acceptable if the
     * representation class of the new type is exactly that of the
     * caller. The declared type of {@code type} is {@code Object} to
     * simplify exposure to Python via {@code __setattr__}.
     *
     * @param replacementDict intended new type object
     * @return argument cast to PyDict (if no error raised)
     */
    default PyDict checkDictAssignment(Object replacementDict) {
        String msg;
        if (replacementDict == null) {
            msg = "__dict__ attribute cannot be deleted";
        } else if (replacementDict instanceof PyDict d) {
            return d;
        } else {
            msg = String.format(
                    "__dict__ must be set to a dictionary,  "
                            + "not a '%s'",
                    PyType.of(replacementDict).getName());
        }
        // FIXME make this a TypeError
        throw new InterpreterError(msg);
    }

}
