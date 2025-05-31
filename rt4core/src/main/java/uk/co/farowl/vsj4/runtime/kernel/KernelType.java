package uk.co.farowl.vsj4.runtime.kernel;

import java.util.EnumSet;
import java.util.List;

import uk.co.farowl.vsj4.runtime.PyType;

public abstract sealed class KernelType extends Representation
        permits PyType {

    /**
     * Kernel feature flags collecting various traits of this type that
     * are private to the implementation, such as defining a certain
     * special method.
     */
    // Compare CPython tp_flags in object.h
    protected final EnumSet<KernelTypeFlag> kernelFeatures =
            EnumSet.noneOf(KernelTypeFlag.class);

    protected KernelType(Class<?> javaClass) { super(javaClass); }


    /**
     * Test for possession of a specified kernel feature. Kernel
     * features are not public API.
     *
     * @param feature to check for
     * @return whether present
     */
    // protected ?
    public final boolean hasFeature(KernelTypeFlag feature) {
        return kernelFeatures.contains(feature);
    }

    @Override
    public boolean hasFeature(Object x, KernelTypeFlag feature) {
        return kernelFeatures.contains(feature);
    }

    /**
     * An immutable list of the {@link Representation}s of this type.
     * These are the representations of the primary or adopted classes
     * in the specification of this type, in order.
     *
     * @return the representations of {@code self}
     */
    public abstract List<Representation> representations();
}
