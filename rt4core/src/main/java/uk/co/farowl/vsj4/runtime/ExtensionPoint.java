package uk.co.farowl.vsj4.runtime;

/**
 * {@code ExtensionPoint} is an interface that identifies an object as
 * eligible to be the implementation of classes defined in Python. A
 * Java class that is the implementation of Python types that allow
 * assignment to {@code __class__} must implement this interface.
 * <p>
 * Given a built-in Python class with Java implementation {@code A}, the
 * normal idiom is to define a nested subclass {@code A.Derived},
 * implementing this interface. At the Java level, all such classes
 * provide an instance dictionary and array of slot variables (named by
 * {@code __slots__} in the type). The type object determines which of
 * these (dictionary, slots or both) is actually present.
 */
public interface ExtensionPoint extends WithDict, Crafted {
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
