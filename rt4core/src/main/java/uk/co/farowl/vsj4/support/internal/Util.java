// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.support.internal;

import java.lang.reflect.Array;

/**
 * Convenient constants etc. for use across the implementation and not
 * needing the type system to be working.
 */
public class Util {
    /** An empty array of objects. */
    public static final Object[] EMPTY_ARRAY = new Object[0];
    /** An empty array of String. */
    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    /** Single re-used instance of {@code EmptyException}. */
    public static final EmptyException EMPTY_EXCEPTION =
            new EmptyException();

    /**
     * Return a new array in which a given element is placed first,
     * followed by the elements of an existing array.
     *
     * @param <T> Element type of the arrays
     * @param a0 first element of new array
     * @param a1plus rest of elements in new array
     * @return new copy array with first element inserted
     */
    public static <T> T[] prepend(T a0, T[] a1plus) {
        int n = a1plus.length;
        Class<?> elemType = a1plus.getClass().getComponentType();
        @SuppressWarnings("unchecked")
        T[] a = elemType == Object.class ? (T[])new Object[1 + n]
                : (T[])Array.newInstance(elemType, 1 + n);
        a[0] = a0;
        System.arraycopy(a1plus, 0, a, 1, n);
        return a;
    }
}
