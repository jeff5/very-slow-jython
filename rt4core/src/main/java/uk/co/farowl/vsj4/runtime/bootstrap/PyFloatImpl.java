// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.bootstrap;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import uk.co.farowl.vsj4.runtime.Crafted;
import uk.co.farowl.vsj4.runtime.ExtensionPoint;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.TypeSpec;

/** Implementation of Python {@code float}. */
public class PyFloatImpl implements Crafted {
    /** The type object {@code float}. */
    static final PyType TYPE = PyType
            .fromSpec(new TypeSpec("float", MethodHandles.lookup())
                    .adopt(Double.class).extendAt(Derived.class));

    /** Value of this Python {@code float} as a Java primitive. */
    final double value;

    /**
     * Construct from primitive.
     *
     * @param value of the {@code float}
     */
    PyFloatImpl(double value) { this.value = value; }

    @Override
    public PyType getType() { return TYPE; }

    /** Implementation of Python subclasses of {@code float}. */
    static class Derived extends PyFloatImpl implements ExtensionPoint {

        /** Actual type. */
        private PyType type;

        /**
         * Instance of Python {@code float} subclass.
         *
         * @param type actual type.
         * @param value of this {@code float}.
         */
        Derived(PyType type, double value) {
            super(value);
            this.type = type;
        }

        @Override
        public Map<Object, Object> getDict() {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public PyType getType() { return type; }

        @Override
        public Object getSlot(int i) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public void setSlot(int i, Object value) {
            // TODO Auto-generated method stub
        }
    }
}
