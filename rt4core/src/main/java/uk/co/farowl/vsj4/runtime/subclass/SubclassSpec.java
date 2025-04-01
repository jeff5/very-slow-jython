package uk.co.farowl.vsj4.runtime.subclass;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.StringJoiner;

import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.WithDict;
import uk.co.farowl.vsj4.runtime.internal.NamedSpec;
import uk.co.farowl.vsj4.runtime.kernel.AbstractPyType.ConstructorAndHandle;

/**
 * A {@code SubclassSpec} is a specification for a Java class to
 * represent the instances of a type defined in Python. The
 * specification generally arises from the processing of a Python class
 * definition, or a three-argument call to
 * {@code type(name, bases, dict)}. It is not a factory for the actual
 * class object, creation of which requires more of the caller's
 * context.
 * <p>
 * The Java representation class specified is likely to be the
 * representation class of more than one Python type. At the time it is
 * specified, a matching representation may already exist. So our first
 * purpose for the {@code SubclassSpec} is to seek a match in the
 * {@code TypeFactory}, and only after that fails, to read it to create
 * the {@code Class} and {@code Representation}.
 */
public class SubclassSpec extends NamedSpec implements Cloneable {

    /** Base class the representation {@code extends}. */
    private final Class<?> base;
    /** Interfaces the representation {@code implements}. */
    private List<Class<?>> interfaces = EMPTY;
    /** Constructors on which to base those created here. */
    private List<Constructor<?>> constructors = new LinkedList<>();
    /** Whether to create a {@code __dict__} member. */
    private boolean hasDict;

    /**
     * Names of slots to be added as members or {@link #NONAMES}. Note
     * even an empty {@code __slots__} ensures {@code slots != NONAMES},
     * so we use this value as a marker.
     */
    private List<String> slots=NONAMES;

    /**
     * Create (or begin) a specification. Note that the name given here
     * is provisional, reflecting the Python class (and useful for error
     * messages). The name of the Java class created according to the
     * specification will be different.
     *
     * @param name provisional name of the representation class.
     * @param base the representation {@code extends}.
     */
    public SubclassSpec(String name, Class<?> base) {
        super(name);
        this.base = base;
        // If base implements WithDict, subclass has instance dict
        if (WithDict.class.isAssignableFrom(base)) { hasDict = true; }
        // The base class may hold a type attribute
    }

    /**
     * Create (or begin) a specification. Note that the name given here
     * is provisional, reflecting the Python class (and useful for error
     * messages). The name of the Java class created according to the
     * specification will be different.
     *
     * @param name provisional name of the representation class.
     * @param base to determine the base representation class.
     */
    public SubclassSpec(String name, PyType base) {
        this(name, base.canonicalClass());
    }

    @Override
    protected SubclassSpec freeze() {
        /*
         * If we are not frozen yet, it means we have yet to finalise
         * the interfaces and slots.
         */
        if (frozen == false) {
            // Prevent further change to the specification.
            frozen = true;

            if (slots == NONAMES) {
                // No slots => __dict__. (Not the converse.)
                hasDict = true;
                slots = List.of();
            } else {
                // May or may not have a dictionary.
                // We have slots. Sort and freeze the list.
                Collections.sort(slots);
                slots = Collections.unmodifiableList(slots);
            }
        }
        return this;
    }

    /**
     * Return the class the representation {@code extends}.
     *
     * @return class the representation {@code extends}
     */
    Class<?> getBase() { return base; }

    /**
     * Set the name of the class being created. The name is not pat of
     * the has (or test of equality) so it is not covered by
     * {@link #freeze()}.
     *
     * @param name
     * @return {@code this}
     */
    SubclassSpec setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Add one interface (guarding against repetition).
     *
     * @param iface new interface
     * @return {@code this}
     */
    SubclassSpec addInterface(Class<?> iface) {
        checkNotFrozen();
        if (!iface.isInterface()) {
            String msg = String.format("%s is not an interface",
                    iface.getTypeName());
            throw specError(msg);
        } else if (interfaces == EMPTY) {
            interfaces = new LinkedList<Class<?>>();
        } else if (interfaces.indexOf(iface) >= 0) {
            throw repeatError("addInterface", iface);
        }
        this.interfaces.add(iface);
        return this;
    }

    /**
     * Add multiple interfaces (guarding against repetition).
     *
     * @param ifaces new interfaces
     * @return {@code this}
     */
    public SubclassSpec addInterfaces(Collection<Class<?>> ifaces) {
        for (Class<?> iface : ifaces) { addInterface(iface); }
        return this;
    }

    /**
     * Add multiple interfaces (guarding against repetition).
     *
     * @param ifaces new interfaces
     * @return {@code this}
     */
    SubclassSpec addInterfaces(Class<?>... ifaces) {
        return addInterfaces(List.of(ifaces));
    }

    /**
     * Return list of interfaces the representation {@code implements}
     *
     * @return interfaces the representation {@code implements}
     */
    List<Class<?>> getInterfaces() { return interfaces; }

    /**
     * Add multiple constructors on which to base those in the new
     * representation. This is a subset chosen by the super-type from
     * its canonical representation. The new representation will always
     * have a {@link PyType} first argument of its constructors.
     *
     * @param cons new constructors
     * @return {@code this}
     */
    public SubclassSpec addConstructors(List<Constructor<?>> cons) {
        checkNotFrozen();
        for (Constructor<?> c : cons) { constructors.add(c); }
        return this;
    }

    /**
     * Add all the published constructors of a particular Python type,
     * on which to base those in the new representation. The new
     * representation will always have a {@link PyType} first argument
     * of its constructors.
     *
     * @param baseType from which to get constructors
     * @return {@code this}
     */
    public SubclassSpec addConstructors(PyType baseType) {
        checkNotFrozen();
        for (ConstructorAndHandle ch : baseType.constructorLookup()
                .values()) {
            constructors.add(ch.constructor());
        }
        return this;
    }

    /**
     * Get the defined constructors.
     *
     * @param constructorSigs new constructors
     * @return {@code this}
     */
    List<Constructor<?>> getConstructors() { return constructors; }

    /**
     * Add one named slot (guarding against repetition). If a
     * specification is created and no slots added before it is used,
     * that is implicitly a request to add a dictionary.
     *
     * @param slotName new slot
     * @return {@code this}
     */
    SubclassSpec addSlot(String slotName) {
        checkNotFrozen();
        if (slots == NONAMES) {
            slots = new LinkedList<>();
        } else if (slots.indexOf(slotName) >= 0) {
            throw repeatError("addSlot", "slotName");
        }
        this.slots.add(slotName);
        return this;
    }

    /**
     * Add multiple named slots (guarding against repetition). Note that
     * An empty collection still counts as defining {@code __slots__}.
     *
     * @param slots new slot names
     * @return {@code this}
     */
    public SubclassSpec addSlots(Collection<String> slots) {
        // An empty collection still counts as defining __slots__
        if (slots == NONAMES) { slots = new LinkedList<>(); }
        for (String slot : slots) { addSlot(slot); }
        return this;
    }

    /**
     * Add multiple named slots (guarding against repetition).
     *
     * @param slots new slot names
     * @return {@code this}
     */
    SubclassSpec addSlots(String... slots) {
        return addSlots(List.of(slots));
    }

    /**
     * Return names of {@code __slots__} to be added as members.
     *
     * @return names of {@code __slots__} to be added
     */
    List<String> getSlots() { return slots; }

    /** Specify that there shall be a dictionary. */
    SubclassSpec addDict() {
        checkNotFrozen();
        hasDict = true;
        return this;
    }

    /**
     * Specify that there shall be a dictionary if the argument is
     * {@code true}. Otherwise the decision is unchanged.
     *
     * @param cond whether to add a request for a dictionary
     * @return {@code this}
     */
    SubclassSpec addDictIf(boolean cond) {
        if (cond && !hasDict) { addDict(); }
        return this;
    }

    /**
     * Whether to create a {@code __dict__} member.
     *
     * @return whether to create a {@code __dict__}
     */
    boolean hasDict() { return hasDict; }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(name);
        b.append(" extends ").append(base.getName());
        if (interfaces != EMPTY) {
            StringJoiner sj =
                    new StringJoiner(", ", " implements ", " ");
            for (Class<?> c : interfaces) { sj.add(c.getSimpleName()); }
            b.append(sj.toString());
        }
        if (hasDict()) { b.append(" +dict"); }
        if (slots != NONAMES) {
            StringJoiner sj = new StringJoiner(", ", " +slots(", ") ");
            for (String s : slots) { sj.add(s); }
            b.append(sj.toString());
        }
        return b.toString();
    }

    @Override
    public boolean equals(Object obj) {
        freeze();
        if (obj instanceof SubclassSpec r) {
            r.freeze();
            if (getBase() != r.getBase()) { return false; }
            if (hasDict() != r.hasDict()) { return false; }
            if (!getSlots().equals(r.getSlots())) { return false; }
            if (!getInterfaces().equals(r.getInterfaces())) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        freeze();
        int h = getBase().hashCode();
        if (hasDict()) { h *= 9; }
        // Need not hash hasSlots().
        for (String s : getSlots()) { h += s.hashCode(); }
        for (Class<?> i : getInterfaces()) { h += i.hashCode(); }
        return h;
    }

    @Override
    public SubclassSpec clone() {
        SubclassSpec spec = new SubclassSpec(name, base);
        if (interfaces != EMPTY) { spec.addInterfaces(interfaces); }
        if (slots != NONAMES) { spec.addSlots(slots); }
        spec.hasDict = hasDict;
        if (frozen) {
            /*
             * Slightly tricky, however the changes wrought by freeze()
             * should only compute missing values and not change the
             * membership of any lists.
             */
            spec.freeze();
        }
        return spec;
    }
}
