package uk.co.farowl.vsj4.runtime.kernel;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.StringJoiner;

import uk.co.farowl.vsj4.runtime.NamedSpec;

/**
 * A {@code RepresentationSpec} is a specification for a Java class to
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
 * purpose for the {@code RepresentationSpec} is to seek a match in the
 * {@code TypeFactory}, and only after that fails, to read it to create
 * the {@code Class} and {@code Representation}.
 */
class RepresentationSpec extends NamedSpec {

    /** Base class the representation {@code extends}. */
    private final Class<?> base;
    /** Interfaces the representation {@code implements}. */
    private final List<Class<?>> interfaces =
            new LinkedList<Class<?>>();
    /** Names of slots to be added as members or {@code null}. */
    private List<String> slots;
    /** Whether to create a __dict__ member. */
    private boolean hasDict;

    /**
     * Create (or begin) a specification.
     *
     * @param name Simple name of the representation class.
     * @param base the representation {@code extends}.
     */
    RepresentationSpec(String name, Class<?> base) {
        super(name);
        this.base = base;
    }

    @Override
    protected RepresentationSpec freeze() {
        /*
         * If we are not frozen yet, it means we have yet to finalise
         * the interfaces and slots.
         */
        if (frozen == false) {
            // Prevent further change to the specification.
            frozen = true;

            if (slots == null) {
                // No __slots__ => __dict__. (Not the converse.)
                hasDict = true;
            } else {
                // We have slots. Freeze the list.
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
     * Return the class the representation {@code extends}, as a string
     * in the internal form used by the JVM (see JVMS section 4.2).
     *
     * @return class the representation {@code extends}
     */
    String getBaseName() { return internalName(base); }

    /**
     * Set the name of the class being created.
     *
     * @param name
     * @return {@code this}
     */
    RepresentationSpec setName(String name) {
        checkNotFrozen();
        this.name = name;
        return this;
    }

    /**
     * Add one interface (guarding against repetition).
     *
     * @param iface new interface
     * @return {@code this}
     */
    RepresentationSpec addInterface(Class<?> iface) {
        checkNotFrozen();
        if (interfaces.indexOf(iface) >= 0) {
            throw repeatError("addInterface", iface);
        } else if (!iface.isInterface()) {
            String msg = String.format("%s is not an interface",
                    iface.getTypeName());
            throw specError(msg);
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
    RepresentationSpec addInterfaces(Class<?>... ifaces) {
        for (Class<?> iface : ifaces) { addInterface(iface); }
        return this;
    }

    /**
     * Return list of interfaces the representation {@code implements}
     *
     * @return interfaces the representation {@code implements}
     */
    List<Class<?>> getInterfaces() { return interfaces; }

    /**
     * Return list of interfaces the representation {@code implements},
     * as strings in the internal form used by the JVM (see JVMS section
     * 4.2).
     *
     * @return interfaces the representation {@code implements}
     */
    String[] getInterfaceNames() {
        String[] names = new String[interfaces.size()];
        int i = 0;
        for (Class<?> iface : interfaces) {
            names[i++] = internalName(iface);
        }
        assert i == names.length;
        return names;
    }

    /**
     * Add one named slot (guarding against repetition).
     *
     * @param slotName new slot
     * @return {@code this}
     */
    RepresentationSpec addSlot(String slotName) {
        checkNotFrozen();
        if (slots == null) {
            slots = new LinkedList<>();
        } else if (slots.indexOf(slotName) >= 0) {
            throw repeatError("addSlot", "slotName");
        }
        this.slots.add(slotName);
        return this;
    }

    /**
     * Add multiple named slots (guarding against repetition).
     *
     * @param slots new slot names
     * @return {@code this}
     */
    RepresentationSpec addSlots(String... slots) {
        for (String slot : slots) { addSlot(slot); }
        return this;
    }

    /**
     * Whether to create a {@code __dict__} member.
     *
     * @return whether to create a {@code __dict__}
     */
    boolean hasSlots() { return slots!=null; }

    /**
     * Return names of {@code __slots__} to be added as members or
     * {@code null}.
     *
     * @return names of {@code __slots__} to be added or {@code null}
     */
    List<String> getSlots() { return slots; }

    /** Specify that there shall be a dictionary. */
    RepresentationSpec addDict() {
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
    RepresentationSpec addDictIf(boolean cond) {
        if (cond && !hasDict) { addDict(); }
        return this;
    }

    /**
     * Whether to create a {@code __dict__} member.
     *
     * @return whether to create a {@code __dict__}
     */
    boolean hasDict() { return hasDict; }

    /**
     * Name of class in internal form (JVMS section 4.2), which is the
     * form we need for ASM.
     *
     * @param c to interrogate
     * @return internal form of name
     */
    private static String internalName(Class<?> c) {
        return c.getName().replace('.', '/');
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(name);
        b.append(" extends ").append(base.getSimpleName());
        if (!interfaces.isEmpty()) {
            StringJoiner sj =
                    new StringJoiner(", ", " implements ", " ");
            for (Class<?> c : interfaces) { sj.add(c.getSimpleName()); }
            b.append(sj.toString());
        }
        if (hasSlots()) {
            StringJoiner sj = new StringJoiner(", ", " (", ") ");
            for (String s : slots) { sj.add(s); }
            b.append(" slots =").append(sj.toString());
        }
        if (hasDict()) { b.append(" +dict"); }
        return b.toString();
    }
}
