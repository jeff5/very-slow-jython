package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Map;

import uk.co.farowl.vsj4.runtime.ExtensionPoint;
import uk.co.farowl.vsj4.runtime.PyBaseObject;
import uk.co.farowl.vsj4.runtime.PyType;

/**
 * The Python {@code object} type and its subclasses are implemented by
 * {@link PyBaseObject}, which extends this class. This class provides
 * members used internally in the run-time system, but that we do not
 * intend to expose as API from {@link PyBaseObject} itself.
 */
// Compare CPython PyBaseObject_Type in typeobject.c
public abstract class AbstractPyBaseObject implements ExtensionPoint {

    // TODO expose as __class__
    private PyType type;

    /**
     * Construct an instance of a proper subclass of {@code object}.
     * This is the constructor that creates an instance of a Python
     * class when {@code object} is the built-in base.
     *
     * @param type actual Python type of this object (a proper subclass
     *     of {@code object})
     */
    protected AbstractPyBaseObject(PyType type) {
        super();
        this.type = type;
    }

    @Override
    public Map<Object, Object> getDict() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public PyType getType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object getSlot(int i) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setSlot(int i, Object value) {
        // TODO Auto-generated method stub
    }

    /** Lookup object with package visibility. */
    static Lookup LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);
}