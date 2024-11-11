// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * An instance of a class implementing this interface allows assignment
 * to {@code __class__} within certain constraints that the object must
 * enforce.
 */
public interface WithClassAssignment extends WithClass {

    /**
     * Assign a new type to the {@code __class__} attribute provided the
     * type meets certain criteria. The assignment must fail if
     * {@code replacementType} is not a type with this class as its
     * instance representation.
     *
     * @implSpec The necessary check (and cast) should be made with
     *     {@link #checkClassAssignment(Object)}. It doesn't have to
     *     succeed if that test passes, of course: the implementation
     *     may enforce further restrictions of its own.
     * @param replacementType intended new type object
     */
    // @Exposed.Set(name="__class__")
    void setType(Object replacementType);

    /**
     * Called during {@code __class__} assignment (that is, during the
     * implementation of {@link #setType(Object)}) to check that the
     * object being assigned is acceptable. It is only acceptable if the
     * representation class of the proposed type is exactly the class of
     * this object. The declared type of {@code type} is {@code Object}
     * to simplify exposure to Python via {@code __setattr__}.
     *
     * @param replacementType intended new type object
     * @return argument cast to {@link PyType} (if no error raised)
     */
    default PyType checkClassAssignment(Object replacementType) {
        String msg;
        if (replacementType == null) {
            msg = "__class__ attribute cannot be deleted";
        } else if (replacementType instanceof PyType t) {
            if (this.getClass() == t.javaClass()) {
                // t = replacementType is an acceptable type
                return t;
            }
            // Current type may be null (e.g. in constructor).
            PyType type = this.getType();
            msg = String.format(
                    "__class__ assignment: '%s' representation differs from %s",
                    t.getName(), type == null ? "chosen implementation"
                            : "that of '" + type.getName() + "'");
        } else {
            msg = String.format(
                    "__class__ must be set to a class, not a '%s' object",
                    PyType.of(replacementType).getName());
        }
        // XXX make this a TypeError
        throw new InterpreterError(msg);
    }
}
