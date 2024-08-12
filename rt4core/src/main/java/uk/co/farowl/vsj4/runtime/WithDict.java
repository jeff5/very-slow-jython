package uk.co.farowl.vsj4.runtime;

import java.util.Map;

/**
 * A Python object where objects have an explicit dictionary (that is
 * not necessarily a Python {@code dict} or directly writable). A
 * {@code type} object, for example, implements {@link #getDict()} to
 * return only an unmodifiable view of its dictionary.
 */
public interface WithDict {
    /**
     * The instance dictionary. This is not necessarily a Python
     * {@code dict}, and may not be directly writable. Some implementing
     * types override the signature to specify the return is a
     * fully-fledged Python {@code dict}.
     *
     * @return instance dictionary
     */
    Map<Object, Object> getDict();
}
