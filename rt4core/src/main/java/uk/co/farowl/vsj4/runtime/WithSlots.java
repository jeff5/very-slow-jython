// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

/**
 * {@code WithSlots} is an interface that identifies an object as
 * eligible to be the implementation of classes defined in Python
 * when they have a __slots__ specification.
 * <p>
 * Given a built-in Python class with Java implementation {@code A},
 * capable of being extended
* the run time system will provide sub-classes of {@code A},
*  according to need,
 * implementing this interface. At the Java level, all such classes
 * an array of slot variables (named and sized by
 * {@code __slots__} in the type). The type object determines the size of
 * array actually present.
 */
public interface WithSlots extends WithClass {

    // TODO: reconsider indexed access: do we need it?
    /**
     * Get a slot attribute by index.
     *
     * @param i index of the attribute
     * @return value of the attribute (or {@code null} if deleted).
     */
    Object getSlot(int i);

    /**
     * Set a slot attribute by index.
     *
     * @param i index of the attribute
     * @param value of the attribute (or {@code null} to delete).
     */
    void setSlot(int i, Object value);

    /**
     * Delete a slot attribute by index (set to {@code null}).
     *
     * @param i index of the attribute
     */
    default void deleteSlot(int i) { setSlot(i, null); }
}
