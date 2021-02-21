package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import uk.co.farowl.vsj3.evo1.Exposed.Getter;
import uk.co.farowl.vsj3.evo1.Slot.EmptyException;
import uk.co.farowl.vsj3.evo1.Slot.Signature;

/**
 * The Python {@code type} object. Type objects are normally created
 * (when created from Java) by a call to {@link PyType#fromSpec(Spec)}.
 */
class PyType extends Operations implements PyObjectDict {
    /*
     * The static initialisation of PyType is a delicate business, since
     * it occurs early in the initialisation of the run-time system. The
     * objective is simple: we must bring into existence type objects
     * for both PyBaseObject ('object') and PyType ('type'), and then
     * the descriptor types that will populate the dictionaries of all
     * types including their own.
     *
     * This last fact makes it necessary to Java-initialise the classes
     * that represent these objects, and afterwards return to build
     * their dictionaries. This done, all subsequent type objects may be
     * built in the obvious sequence.
     */

    // *** The order of these initialisations is critical

    /**
     * Classes for which the type system has to prepare {@code PyType}
     * objects in two stages, deferring the filling of the dictionary of
     * the type until all classes in this set have completed their
     * static initialisation in Java and built a {@code PyType}.
     * Generally, this is because these types are necessary to create
     * entries in the dictionary of any type.
     */
    // Use an ordered list so we have full control over sequence.
    static final Map<Class<?>, BootstrapTask> bootstrapTasks =
            new LinkedHashMap<>();
    static {
        /*
         * Name the classes needing this bootstrap treatment in the
         * order they should be processed.
         */
        Class<?>[] bootstrapClasses = {
                // Really special cases
                PyBaseObject.class, PyType.class,
                // The entries are descriptors so defer those
                // PyMemberDescr.class, //
                // PyGetSetDescr.class, //
                PyWrapperDescr.class, //
                // PyMethodDescr.class, //
                // And sometimes things go wrong :(
                BaseException.class, //
                // Types with multiple implementations
                PyUnicode.class, //
                PyLong.class, //
                PyFloat.class, //
        };
        // Fill the map from the list.
        for (Class<?> c : bootstrapClasses) {
            bootstrapTasks.put(c, new BootstrapTask());
        }
    }

    /** An empty array of type objects */
    static final PyType[] EMPTY_TYPE_ARRAY = new PyType[0];
    /** Lookup object on {@code PyType}. */
    private static Lookup LOOKUP = MethodHandles.lookup();
    /** The type object of {@code type} objects. */
    static final PyType TYPE = new PyType();
    /** The type object of {@code object} objects. */
    static final PyType OBJECT_TYPE = TYPE.base;
    /** An array containing only 'object', the bases of many types. */
    private static final PyType[] ONLY_OBJECT =
            new PyType[] {OBJECT_TYPE};

    static {
        // For each bootstrap class: ensure static initialisation
        for (Class<?> c : bootstrapTasks.keySet()) {
            String name = c.getName();
            try {
                Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new InterpreterError(
                        "failed to initialise bootstrap class %s",
                        c.getSimpleName());
            }
        }
    }

    // *** End critically ordered section

    /**
     * Particular type of this {@code PyType}. Why is this not always
     * {@link #TYPE}? Because there may be subclasses of type
     * (meta-classes) and objects having those as their {@code type}.
     */
    private final PyType type;

    /** Name of the type. */
    final String name;

    /** The Java class defining operations on instances of the type. */
    final Class<?> implClass;

    /**
     * Handle arrays for in which to look up binary class-specific
     * methods when these are provided as a supplementary implementation
     * class. {@code null} such a class is not provided in the
     * specification. See {@link Spec#binops(Class)}.
     */
    final Map<Slot, MethodHandle[][]> binopTable;

    /**
     * The Java classes appearing as operands in the operations and
     * methods of the type.
     * <ol>
     * <li>[0] is the canonical implementation class</li>
     * <li>[1:implCount] are the adopted implementation classes</li>
     * <li>[:acceptedCount] are the classes acceptable as
     * {@code self}</li>
     * <li>[:] (entire array) are the classes provided for as the
     * "other" argument of binary operations</li>
     * </ol>
     */
    final Class<?>[] operandClasses;

    /**
     * The number of {@link #operandClasses} in {@link #operandClasses}
     * recognised by the run-time as implementations of the type.
     */
    final int implCount;

    /**
     * The number of Java classes in {@link #operandClasses} that are
     * acceptable as {@code self} in methods.
     */
    final int acceptedCount;

    /**
     * Characteristics of the type, to determine behaviours (such as
     * mutability) of instances or the type itself, or to provide quick
     * answers to frequent questions such as "are instances data
     * descriptors".
     */
    EnumSet<Flag> flags;

    // Support for class hierarchy
    private PyType base;
    private PyType[] bases;
    private PyType[] mro;

    /**
     * The dictionary of the type is always an ordered {@code Map}. It
     * is only accessible (outside the core) through a
     * {@code mappingproxy} that renders it read-only.
     */
    private final Map<PyUnicode, Object> dict = new LinkedHashMap<>();

    /**
     * Partially construct a {@code type} object for {@code type}, and
     * by side-effect the type object of its base {@code object}. The
     * special constructor solves the problem that each of these has to
     * exist in order properly to create the other. This constructor is
     * <b>only used once</b>, during the static initialisation of
     * {@code PyType}, after which these objects are constants.
     */
    private PyType() {
        /*
         * We are creating the PyType for "type". We need a
         * specification too, because there's nothing more bootstrappy
         * than type. :)
         */
        Spec spec = new Spec("type", LOOKUP).metaclass(this);
        /*
         * We cannot use fromSpec here, because we are already in a
         * constructor and it needs TYPE, which we haven't set.
         */
        this.type = this;
        this.name = spec.name;
        this.implClass = spec.implClass();
        this.binopTable = Collections.emptyMap();
        this.implCount = spec.adoptedCount() + 1;
        this.acceptedCount = spec.acceptedCount();
        this.operandClasses = spec.accepted();
        this.flags = spec.flags;

        /*
         * Break off to construct the type object for "object", which we
         * need as the base. Again, we need the spec.
         */
        Spec objectSpec = new Spec("object", PyBaseObject.class, LOOKUP)
                .metaclass(this).canonical(Object.class);
        /*
         * This time the constructor will work, as long as we supply the
         * metatype. For consistency, take values from objectSpec.
         */
        PyType objectType = new PyType(objectSpec);

        // The only base of type is object
        this.base = objectType;
        this.bases = new PyType[] {objectType};
        this.mro = new PyType[] {this, objectType};

        // Defer filling the dictionary for both types we made
        BootstrapTask.shelve(objectSpec, objectType);
        BootstrapTask.shelve(spec, this);
    }

    /**
     * Partially construct a type from a type specification. This
     * implements only the basic object creation, short of filling the
     * dictionary, for example. It is intended to be used with or as
     * part of {@link #fromSpec(Spec)}.
     *
     * @param spec specification for the type
     */
    private PyType(Spec spec) {
        this.type = spec.getMetaclass();
        this.name = spec.name;
        this.implClass = spec.implClass();
        this.operandClasses = spec.accepted();
        this.implCount = spec.adoptedCount() + 1;
        this.acceptedCount = spec.acceptedCount();
        // in case original changes
        this.flags = EnumSet.copyOf(spec.flags);
        // Sets base as well as bases
        this.setBases(spec.getBases());
        // Fix-up base and MRO from bases array
        this.setMROfromBases();
        // Create the binary operations table (if there is one)
        Class<?> binops = spec.binopClass();
        this.binopTable =binops == null ? Collections.emptyMap()
                : Exposer.binopTable(spec.lookup,
                        binops, this);
    }

    /**
     * Construct a type from the given specification. This approach is
     * preferred to the direct constructor. The type object does not
     * retain a reference to the specification, once constructed, so
     * that subsequent alterations have no effect on the {@code PyType}.
     *
     * @param spec specification
     * @return the constructed {@code PyType}
     */
    static PyType fromSpec(Spec spec) {

        // Construct a type with an empty dictionary
        PyType type;

        if (spec.getMetaclass() == TYPE) {
            type = new PyType(spec);
        } else {
            throw new InterpreterError("Metaclasses not supported.");
        }

        /*
         * The next step for this type is to populate the dictionary
         * from the information gathered in the specification. We can
         * only do this if all the bootstrap types have also reached at
         * least this stage (are no longer on the waiting list).
         */
        if (bootstrapTasks.isEmpty()) {
            // The bootstrap types have all completed. Make descriptors.
            type.fillDictionary(spec);

        } else {
            /*
             * Some bootstrap types are waiting for their dictionaries.
             * It is not safe to create descriptors in the dictionary).
             */
            BootstrapTask.shelve(spec, type);

            /*
             * However, the current type may be the last bootstrap type
             * we were waiting for.
             */
            if (BootstrapTask.allReady()) {
                /*
                 * Complete the types we had to shelve. Doing so may
                 * create new types, so we empty the waiting list into a
                 * private copy.
                 */
                List<BootstrapTask> tasks =
                        new ArrayList<>(bootstrapTasks.values());
                bootstrapTasks.clear();

                for (BootstrapTask task : tasks) {
                    task.type.fillDictionary(task.spec);
                }

                /*
                 * Bootstrapping is over: the type we return will be
                 * fully-functional as a Python type object after all.
                 */
            }
        }

        return type;
    }

    /**
     * Cause the creation (if necessary) of a PyType for a given Java
     * class, which must be a canonical implementation, or a "found"
     * type. This is support for the {@link Operations} object. By side
     * effect, one or more Operations objects will be registered,
     * including one for the given class.
     *
     * @param klass canonical implementation
     * @return
     */
    static PyType fromClass(Class<?> klass) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * A record used to defer the completion of a particular type
     * object. When so deferred, {@link PyType#fromSpec(Spec)} will
     * return a type object without filling the dictionary of the type.
     * The type and the implementation class can be available to Java,
     * but will not yet function properly as a Python object.
     * <p>
     * This only happens while starting up the run-time. The purpose is
     * to allow Java class initialisation to complete for all of the
     * types needed to populate type dictionaries ("bootstrap types").
     * Other classes that request a type object during this time will be
     * caught up temporarily in the same process.
     * <p>
     * A {@code BootstrapTask} stores the {@link PyType} object and the
     * {@link Spec} for a given type. All the waiting types are
     * completed as soon as the last of them becomes available.
     */
    private static class BootstrapTask {

        Spec spec;
        PyType type;

        /**
         * Place a partially-completed {@code type} on the
         * {@link PyType#bootstrapTasks} list.
         *
         * @param spec specification for the type
         * @param type corresponding (partial) type object
         */
        static void shelve(Spec spec, PyType type) {
            Class<?> key = spec.implClass();
            BootstrapTask t = bootstrapTasks.get(key);
            if (t == null)
                // Not present: add an entry.
                bootstrapTasks.put(key, t = new BootstrapTask());
            else if (t.spec != null)
                throw new InterpreterError(REPEAT_CLASS, key);
            // Fill the entry as partially initialised.
            t.spec = spec;
            t.type = type;
        }

        /**
         * Check to see if all {@link PyType#bootstrapTasks} have
         * reached partially complete (are awaiting a dictionary).
         *
         * @return true iff all are ready
         */
        static boolean allReady() {
            for (BootstrapTask t : bootstrapTasks.values()) {
                if (t.spec == null) { return false; }
            }
            return true;
        }

        @Override
        public String toString() {
            return String.format("BootstrapTask[%s]", spec);
        }

        private static final String REPEAT_CLASS =
                "PyType bootstrapping: class %s encountered twice";
    }

    /**
     * Load the dictionary of this type with attributes discovered
     * through the specification.
     *
     * @param spec to apply
     */
    private void fillDictionary(Spec spec) {

        // XXX How is inheritance respected?

        // Fill slots from implClass or bases
        // addMembers(spec);
        // addGetSets(spec);
        // addMethods(spec);
        addWrappers(spec);
        // XXX Possibly belong elsewhere
        // setAllSlots();
        defineOperations();
        deduceFlags();
    }

    /**
     * Define the Operations objects for this type, posting them to the
     * registry.
     */
    private void defineOperations() {
        setAllSlots();
        Operations.registry.set(operandClasses[0], this);
        for (int i = 1; i < operandClasses.length; i++) {
            // Creating the operations object sets the slots in it
            Operations.Accepted ops = new Operations.Accepted(this, i);
            Operations.registry.set(operandClasses[i], ops);
        }
    }

// /**
// * Add members to this type discovered through the specification.
// *
// * @param spec to apply
// */
// private void addMembers(Spec spec) {
//
// Map<String, PyMemberDescr> members =
// Exposer.memberDescrs(spec.lookup, implClass, this);
//
// for (Map.Entry<String, PyMemberDescr> e : members.entrySet()) {
// PyUnicode k = new PyUnicode(e.getKey());
// Object v = e.getValue();
// dict.put(k, v);
// }
//
// }
//
// /**
// * Add get-set attributes to this type discovered through the
// * specification.
// *
// * @param spec to apply
// */
// private void addGetSets(Spec spec) {
//
// Map<String, PyGetSetDescr> getsets =
// Exposer.getsetDescrs(spec.lookup, implClass, this);
//
// for (Entry<String, PyGetSetDescr> e : getsets.entrySet()) {
// PyUnicode k = new PyUnicode(e.getKey());
// PyGetSetDescr v = e.getValue();
// dict.put(k, v);
// }
// }

    /**
     * Add slot wrapper attributes to this type discovered through the
     * specification.
     *
     * @param spec to apply
     */
    private void addWrappers(Spec spec) {

        Map<String, PyWrapperDescr> wrappers = Exposer.wrapperDescrs(
                spec.lookup, spec.implClass, spec.methodClass, this);

        for (Map.Entry<String, PyWrapperDescr> e : wrappers
                .entrySet()) {
            PyUnicode k = new PyUnicode(e.getKey());
            Object v = e.getValue();
            dict.put(k, v);
        }
    }

// /**
// * Add method attributes to this type discovered through the
// * specification.
// *
// * @param spec to apply
// */
// private void addMethods(Spec spec) {
//
// Map<String, PyMethodDescr> methods =
// Exposer.methodDescrs(spec.lookup, implClass, this);
//
// for (Map.Entry<String, PyMethodDescr> e : methods.entrySet()) {
// PyUnicode k = new PyUnicode(e.getKey());
// Object v = e.getValue();
// dict.put(k, v);
// }
// }

    /**
     * The {@link #flags} field caches many characteristics of the type
     * that we need to consult: we deduce them here.
     */
    private void deduceFlags() {
        if (Slot.op_get.isDefinedFor(this)) {
            // It's a descriptor
            flags.add(Flag.IS_DESCR);
            if (Slot.op_set.isDefinedFor(this)
                    || Slot.op_delete.isDefinedFor(this))
                flags.add(Flag.IS_DATA_DESCR);
        }
    }

    /**
     * Get the Python type of the given object {@code obj}. The Java
     * class of {@code obj} will normally have been initialised, since
     * an instance exists.
     *
     * @param obj
     * @return the Python type
     */
    public static PyType of(Object obj) {
        return Operations.forClass(obj.getClass()).type(obj);
    }

    @Override
    public PyType getType() { return type; }

    @Override
    PyType type(Object x) {
        return this;
    }

    @Override
    Class<?> getJavaClass() { return operandClasses[0]; }

    /**
     * Set {@link #bases} and deduce {@link #base}.
     *
     * @param bases to set
     */
    private void setBases(PyType bases[]) {
        this.bases = bases;
        this.base = bestBase(bases);
    }

    /** Set the MRO, but at present only single base. */
    // XXX note may retain a reference todeclaredBases
    private void setMROfromBases() {

        int n = bases.length;

        if (n == 0) {
            // Special case of 'object'
            this.mro = new PyType[] {this};

        } else if (n == 1) {
            // Just one base: short-cut: mro = (this,) + this.base.mro
            PyType[] baseMRO = base.getMRO();
            int m = baseMRO.length;
            PyType[] mro = new PyType[m + 1];
            mro[0] = this;
            System.arraycopy(baseMRO, 0, mro, 1, m);
            this.mro = mro;

        } else { // n >= 2
            // Need the proper C3 algorithm to set MRO
            String fmt =
                    "multiple inheritance not supported yet (type `%s`)";
            throw new InterpreterError(fmt, name);
        }
    }

    /**
     * Set all the slots ({@code op_*}) from the entries in the
     * dictionaries of this type and its bases.
     */
    private void setAllSlots() {
        for (Slot s : Slot.values()) {
            Object def = lookup(ID.intern(s.methodName));
            s.setSlot(this, def);
        }
    }

    /** name has the form __A__ where A is one or more characters. */
    private static boolean isDunderName(PyUnicode name) {
        String n = name.value;
        final int L = n.length();
        return L > 4 && n.charAt(1) == '_' && n.charAt(0) == '_'
                && n.charAt(L - 2) == '_' && n.charAt(L - 1) == '_';
    }

    /**
     * Called from {@link #__setattr__(PyUnicode, Object)} after an
     * attribute has been set or deleted. This gives the type the
     * opportunity to recompute slots and perform any other actions.
     *
     * @param name of the attribute modified
     */
    protected void updateAfterSetAttr(PyUnicode name) {

    }

    private void setSlot(Slot slot, MethodHandle mh) {
        if (isMutable())
            slot.setSlot(this, mh);
        else
            throw new TypeError("cannot update slots of %s", name);
    }

    @Override
    public String toString() {
        return "<class '" + name + "'>";
    }

    /**
     * The name of this type.
     *
     * @return name of this type
     */
    public String getName() { return name; }

    /**
     * Find the index of the given class in the accepted classes
     * for this type. There is a match if the found class is a
     * assignable from the given class. In the case that more than one
     * matches, the highest qualifying index is returned. The "accepted"
     * implementations consist of:
     * <ol>
     * <li>the canonical class (index zero)</li>
     * <li>the adopted implementations (like {@code Double} for
     * {@code float})</li>
     * <li>the accepted implementations of any sub-classes that are not
     * assignable to the canonical or adopted</li>
     * </ol>
     *
     * @param c a class matching one of the accepted classes
     * @return its index or -1
     */
    int indexAccepted(Class<?> c) {
        for (int i = operandClasses.length; --i >= 0;) {
            if (operandClasses[i].isAssignableFrom(c)) { return i; }
        }
        return -1;
    }

    /**
     * Find the index of the given class in the known operand classes
     * for this type. There is a match if the found class is a
     * assignable from the given class. In the case that more than one
     * matches, the highest qualifying index is returned.
     *
     * @param c a class matching one of the operand classes
     * @return its index or -1
     */
    int indexOperand(Class<?> c) {
        for (int i = operandClasses.length; --i >= 0;) {
            if (operandClasses[i].isAssignableFrom(c)) { return i; }
        }
        return -1;
    }

    /**
     * {@code true} iff the type of {@code o} is a Python sub-type of
     * {@code this} (including exactly {@code this} type). This is
     * likely to be used in the form:<pre>
     * if(!PyUnicode.TYPE.check(oName)) throw ...
     *</pre>
     *
     * @param o object to test
     * @return {@code true} iff {@code o} is of a sub-type of this type
     */
    boolean check(Object o) {
        PyType t = PyType.of(o);
        return t == this || t.isSubTypeOf(this);
    }

    /**
     * {@code true} iff the Python type of {@code o} is exactly
     * {@code this}, not a Python sub-type of {@code this}, nor just any
     * Java sub-class of {@code PyType}. This is likely to be used in
     * the form:<pre>
     * if(!PyUnicode.TYPE.checkExact(oName)) throw ...
     *</pre>
     *
     * @param o object to test
     * @return {@code true} iff {@code o} is exactly of this type
     */
    boolean checkExact(Object o) {
        return PyType.of(o) == this;
    }

    /**
     * True iff this type is a Python sub-type {@code b} (if {@code b}
     * is on the MRO of this type).
     *
     * @param b to test
     * @return {@code true} if {@code this} is a sub-type of {@code b}
     */
    // Compare CPython PyType_IsSubtype in typeobject.c
    boolean isSubTypeOf(PyType b) {
        if (mro != null) {
            /*
             * Deal with multiple inheritance without recursion by
             * walking the MRO tuple
             */
            for (PyType base : mro) {
                if (base == b)
                    return true;
            }
            return false;
        } else
            // a is not completely initialized yet; follow base
            return type_is_subtype_base_chain(b);
    }

    // Compare CPython type_is_subtype_base_chain in typeobject.c
    private boolean type_is_subtype_base_chain(PyType b) {
        // Only crudely supported. Later, search the MRO of this for b.
        // Awaits PyType.forClass() factory method.
        PyType t = this;
        while (t != b) {
            t = t.base;
            if (t == null) { return b == OBJECT_TYPE; }
        }
        return true;
    }

    /**
     * Return whether special methods in this type may be assigned new
     * meanings after type creation (or may be safely cached).
     *
     * @return whether a data descriptor
     */
    final boolean isMutable() {
        return flags.contains(Flag.MUTABLE);
    }

    /**
     * Return whether an instance of this type is a data descriptor
     * (defines {@code __get__} and at least one of {@code __set__} or
     * {@code __delete__}.
     *
     * @return whether a data descriptor
     */
    @Override
    final boolean isDataDescr() {
        return flags.contains(Flag.IS_DATA_DESCR);
    }

    /**
     * Return whether an instance of this type is a descriptor (defines
     * {@code __get__}).
     *
     * @return whether a descriptor
     */
    final boolean isDescr() { return flags.contains(Flag.IS_DESCR); }

    /** @return the base (core use only). */
    PyType getBase() {
        return base;
    }

    /** @return the bases as an array (core use only). */
    PyType[] getBases() {
        return bases;
    }

    /** @return the MRO as an array (core use only). */
    PyType[] getMRO() {
        return mro;
    }

    /**
     * The dictionary of a {@code type} in a read-only view.
     */
    @Getter("__dict__")
    @Override
    public final Map<Object, Object> getDict() {
        return Collections.unmodifiableMap(dict);
    }

    /**
     * Look for a name, returning the entry directly from the first
     * dictionary along the MRO containing key {@code name}. This may be
     * a descriptor, but no {@code __get__} takes place on it: the
     * descriptor itself will be returned. This method does not throw an
     * exception if the name is not found, but returns {@code null} like
     * a {@code Map.get}
     *
     * @param name to look up, must be exactly a {@code str}
     * @return dictionary entry or null
     */
    // Compare CPython _PyType_Lookup in typeobject.c
    // and find_name_in_mro in typeobject.c
    Object lookup(PyUnicode name) {

        /*
         * CPython wraps this in a cache keyed by (type, name) and
         * sensitive to the "version" of this type. (Version changes
         * when any change occurs, even in a super-class, that would
         * alter the result of a look-up.) We do not reproduce that at
         * present.
         */

        // Look in dictionaries of types in MRO
        PyType[] mro = getMRO();

        // CPython checks here to see in this type is "ready".
        // Could we be "not ready" in some loop of types?

        for (PyType base : mro) {
            Object res;
            if ((res = base.dict.get(name)) != null)
                return res;
        }
        return null;
    }

    /**
     * Enumeration of the characteristics of a type. These are the
     * members that appear appear in the {@link PyType#flags} to
     * determine behaviours or provide quick answers to frequent
     * questions such as "are you a data descriptor".
     */
    enum Flag {
        /**
         * Special methods may be assigned new meanings in the
         * {@code type}, after creation.
         */
        MUTABLE,
        /**
         * An object of this type can change to another type (within
         * "layout" constraints).
         */
        VARIABLE,
        /**
         * This type the type allows sub-classing (is acceptable as a
         * base).
         */
        BASETYPE,
        /**
         * An object of this type is a descriptor (defines
         * {@code __get__}).
         */
        IS_DESCR,
        /**
         * An object of this type is a data descriptor (defines
         * {@code __get__} and at least one of {@code __set__} or
         * {@code __delete__}).
         */
        IS_DATA_DESCR
    }

    /**
     * A specification for a Python type. A Java class intended as the
     * implementation of a Python object creates one of these data
     * structures during static initialisation, and configures it using
     * the mutators. A fluent interface makes this configuration
     * readable as a single, long statement.
     */
    static class Spec {

        /** Name of the class being specified. */
        final String name;

        /** Delegated authorisation to resolve names. */
        final Lookup lookup;

        /** The implementation class in which the spec was created. */
        final Class<?> implClass;

        /**
         * Additional class in which to look up method names or
         * {@code null}. See {@link #methods(Class)}
         */
        private Class<?> methodClass;

        /**
         * Additional class in which to look up binary class-specific
         * methods or {@code null}. See {@link #binops(Class)}.
         */
        private Class<?> binopClass;

        /**
         * The canonical and adopted implementations of the Python type,
         * classes acceptable as {@code self}, and other known operand
         * classes will be collected here.
         */
        private ArrayList<Class<?>> operandClasses = new ArrayList<>(1);

        /**
         * The number of adopted implementations of the Python type, in
         * addition to the canonical one. Increment for each adopted
         * class added.
         */
        private int adoptedCount;

        /**
         * The number of classes, including the canonical and adopted
         * classes, accepted implementations of the Python type.
         * Increment for each adopted or accepted class added.
         */
        private int acceptedCount;

        /**
         * The Python type being specified may be represented by a
         * Python sub-class of {@code type}, i.e. something other than
         * {@link PyType#TYPE}. This will be represented by a sub-class
         * of {@link PyType}.
         */
        private PyType metaclass;

        /** Python types that are bases of the type being specified. */
        // Must allow null element, needed when defining 'object'
        private final List<PyType> bases = new LinkedList<>();

        /** Characteristics of the type being specified. */
        EnumSet<Flag> flags = Spec.getDefaultFlags();

        /**
         * Create (begin) a specification for a {@link PyType} based on
         * a specified implementation class.
         * <p>
         * {@link PyType#fromSpec(Spec)} will interrogate the
         * implementation class reflectively to discover attributes the
         * type should have, and will form type dictionary entries with
         * {@link MethodHandle}s or {@link VarHandle}s on qualifying
         * members. The caller supplies a {@link Lookup} object to make
         * this possible. An implementation class may declare methods
         * and fields as {@code private}, and annotate them to be
         * exposed to Python, as long as the lookup object provided to
         * the {@code Spec} confers the right to access them.
         * <p>
         * A {@code Spec} given private or package access to members
         * should not be passed to untrusted code. {@code PyType} does
         * not hold onto the {@code Spec} after completing the type
         * object.
         * <p>
         * Additional classes may be given containing the implementation
         * and the lookup classes (see {code Lookup.lookupClass()}) to
         * be different from the caller. Usually they are the same.
         *
         * @param name of the type
         * @param implClass in which operations are defined
         * @param lookup authorisation to access {@code implClass}
         *
         * @deprecated Use {@link #PyType(String, Lookup)} istead
         */
        @Deprecated
        Spec(String name, Class<?> implClass, Lookup lookup) {
            this.name = name;
            this.implClass = implClass;
            this.methodClass = this.binopClass = null;
            this.operandClasses.add(implClass);
            this.lookup = lookup;
        }

        /**
         * Create (begin) a specification for a {@link PyType} based on
         * the caller as the implementation class. This is the beginning
         * normally made by built-in classes in their static
         * initialisation.
         * <p>
         * The caller supplies a {@link Lookup} object which must have
         * been created by the implementation class.
         * {@link PyType#fromSpec(Spec)} will interrogate the
         * implementation class reflectively to discover attributes the
         * type should have, and will form type dictionary entries with
         * {@link MethodHandle}s or {@link VarHandle}s on qualifying
         * members. An implementation class may declare methods and
         * fields as {@code private}, and annotate them to be exposed to
         * Python, as long as the lookup object provided to the
         * {@code Spec} confers the right to access them.
         * <p>
         * A {@code Spec} given private or package access to members
         * should not be passed to untrusted code. {@code PyType} does
         * not hold onto the {@code Spec} after completing the type
         * object.
         * <p>
         * Additional classes may be given containing the implementation
         * and the lookup classes (see {code Lookup.lookupClass()}) to
         * be different from the caller. Usually they are the same.
         *
         * @param name of the type
         * @param lookup authorisation to access {@code implClass}
         */
        Spec(String name, Lookup lookup) {
            this.name = name;
            this.lookup = lookup;
            this.implClass = lookup.lookupClass();
            this.methodClass = this.binopClass = null;
            this.operandClasses.add(implClass);
        }

        /**
         * Create (begin) a specification for a {@link PyType} based on
         * a specific implementation class. The class given also
         * specifies the canonical implementation class, unless
         * subsequently overridden with {@link #canonical(Class)}. This
         * is the beginning normally made by built-in classes.
         *
         * @param name of the type
         * @param implClass in which operations are defined
         */
        Spec(String name, Class<?> implClass) {
            this(name, implClass,
                    /*
                     * The method is package-accessible, so the lookup
                     * here should have no more than package access, in
                     * order to avoid granting the caller access to
                     * details of the run-time. PRIVATE implicitly drops
                     * PROTECTED. (Read the Javadoc carefully.)
                     */
                    MethodHandles.lookup()
                            .dropLookupMode(Lookup.PRIVATE));
        }

        /**
         * Specify additional acceptable implementation classes for the
         * type.
         *
         * @param impl classes to append to the list
         * @return {@code this}
         */
        Spec ops(Class<?>... impl) {
            operandClasses.addAll(Arrays.asList(impl));
            return this;
        }

        /**
         * Specify the canonical implementation class for the type. By
         * default, if {@link #canonical(Class)} is not called, the
         * canonical implementation is the class given in the
         * constructor. This method makes it possible to define
         * operations in different class from the implementation.
         * <p>
         * This is the case for the Python {@code object}, for which the
         * canonical implementation is Java {@code java.lang.Object}
         * while operations are defined elsewhere.
         *
         * @param impl replacement canonical implementation class
         * @return {@code this}
         */
        Spec canonical(Class<?> impl) {
            operandClasses.set(0, impl);
            return this;
        }

        /**
         * Specify adopted implementation classes for the type. The
         * adopted implementations are those that will be identified by
         * the run-time as having the Python type of this {@code Spec}.
         * Successive calls are cumulative.
         * <p>
         * For every instance method {@code m} (including special
         * methods) on a Python object, and for for every adopted or
         * accepted class {@code C}, there must be an implementation
         * {@code m(D self, ...)} where the "self" (first) argument type
         * {@code D} is assignable from {@code C}.
         *
         * @param impl classes to treat as adopted implementations
         * @return {@code this}
         */
        Spec adopt(Class<?>... impl) {
            for (Class<?> c : impl) {
                // Add at the end of the adopted classes
                operandClasses.add(adoptedCount + 1, c);
                adoptedCount++;
                acceptedCount++;
            }
            return this;
        }

        /**
         * Specify Java classes to be accepted as "self" arguments for
         * the type, in addition to the adopted implementations. The use
         * of this is to ensure that the canonical and adopted
         * implementation classes of Python sub-types of the type being
         * specified are acceptable as "self". Successive calls are
         * cumulative. Classes assignable to existing accepted classes
         * are ignored.
         * <p>
         * For every instance method {@code m} (including special
         * methods) on a Python object, and for for every adopted or
         * accepted class {@code C}, there must be an implementation
         * {@code m(D self, ...)} where the "self" (first) argument type
         * {@code D} is assignable from {@code C}.
         *
         * @param classes to append to the list
         * @return {@code this}
         */
        Spec accept(Class<?>... classes) {
            for (Class<?> c : classes) {
                if (indexOf(c) < 0) {
                    // Add at the end of the accepted classes
                    operandClasses.add(acceptedCount, c);
                    acceptedCount++;
                }
            }
            return this;
        }

        /**
         * Specify Java classes accepted as the second operand in binary
         * operations. Successive calls are cumulative. Classes
         * assignable to existing accepted classes are ignored.
         *
         * @param classes to append to the list
         * @return {@code this}
         */
        Spec operand(Class<?>... classes) {
            for (Class<?> c : classes) {
                if (indexOf(c) < 0) {
                    // Add at the very end
                    operandClasses.add(c);
                }
            }
            return this;
        }

        /**
         * The number of classes specified as adopted implementations of
         * the Python type being specified, not counting the canonical
         * implementation.
         *
         * @return number of adopted classes
         */
        int adoptedCount() {
            return adoptedCount;
        }

        /**
         * The number of classes specified as canonical, adopted or
         * accepted for the Python type being specified.
         *
         * @return number of accepted classes
         */
        int acceptedCount() {
            return acceptedCount;
        }

        /**
         * The number of classes specified as canonical, adopted,
         * accepted or operands for the Python type being specified.
         *
         * @return number of all classes to be treated as operands
         */
        int operandCount() {
            return operandClasses.size();
        }

        /**
         * Find c in the known classes.
         *
         * @param c class to find
         * @return index of {@code c} in accepted
         */
        private int indexOf(Class<?> c) {
            for (int i = operandClasses.size(); --i >= 0;) {
                if (operandClasses.get(i).isAssignableFrom(c)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * Specify a base for the type. Successive bases given are
         * cumulative and ordered.
         *
         * @param base to append to the bases
         * @return {@code this}
         */
        Spec base(PyType base) {
            bases.add(base);
            return this;
        }

        /**
         * A new set of flags with the default values for a type defined
         * in Java.
         *
         * @return new default flags
         */
        static EnumSet<Flag> getDefaultFlags() {
            return EnumSet.of(Flag.BASETYPE);
        }

        /**
         * Specify a characteristic (type flag) to be added.
         *
         * @param f to add to the current flags
         * @return {@code this}
         */
        /*
         * XXX Better encapsulation to have methods for things we want
         * to set/unset. Most PyType.flags members should not be
         * manipulated through the Spec and are derived in construction,
         * or as a side effect of setting something else.
         */
        Spec flag(Flag f) {
            flags.add(f);
            return this;
        }

        /**
         * Specify a characteristic (type flag) to be removed.
         *
         * @param f to remove from the current flags
         * @return {@code this}
         */
        Spec flagNot(Flag f) {
            flags.remove(f);
            return this;
        }

        /**
         * Specify that the Python type being specified will be
         * represented by a an instance of this Python sub-class of
         * {@code type}, i.e. something other than {@link PyType#TYPE}.
         *
         * @param metaclass to specify (or null for {@code type}
         * @return {@code this}
         */
        Spec metaclass(PyType metaclass) {
            this.metaclass = metaclass;
            return this;
        }

        /** Get the canonical implementation class for the type. */
        Class<?> implClass() {
            return implClass;
        }

        /**
         * Set the class additionally defining methods for the type.
         * This class will be consulted when filling the dictionary of
         * the type. A separate class is useful when the method
         * definitions are generated by a script, as for types that
         * admit multiple realisations in Java.
         *
         * @param methodClass class with additional methods
         * @return {@code this}
         */
        Spec methods(Class<?> methodClass) {
            this.methodClass = methodClass;
            return this;
        }

        /**
         * Get the class additionally defining methods for the type. See
         * {@link #methods(Class)}.
         *
         * @return class additionally defining methods for the type
         */
        Class<?> methodClass() {
            return methodClass;
        }

        /**
         * Set the class in which to look up binary class-specific
         * operations, for example {@code __rsub__(MyObject, Integer)}.
         * Such signatures are used in call sites.
         * <p>
         * Types may ignore this technique if the designer is content
         * with a {@code __rsub__(MyObject, Object)} that coerces its
         * right-hand argument on each call. (This method has to exist
         * to satisfy the Python data model.) The method may be defined
         * in the {@link #implClass()}, or {@link #methodClass()}
         * <p>
         * A separate class is necessary since the method definition for
         * {@code __rsub__(MyObject, Object)} must sometimes return
         * {@link Py#NotImplemented}, and we should like to avoid
         * checking for that in the call site. Rather, the absence of a
         * definition should indicate that he operation is not defined
         * for a given pair of types Certain built-ins use the technique
         * to speed up call sites in JVM byte code compiled from Python.
         * (The class may be generated by a script.)
         *
         * @param binopClass class with binary class-specific methods
         * @return {@code this}
         */
        Spec binops(Class<?> binopClass) {
            this.binopClass = binopClass;
            return this;
        }

        /**
         * Get the class defining binary class-specific operations for
         * the type. See {@link #binops(Class)}. {@code null} if there
         * isn't one.
         *
         * @return class defining binary class-specific operations (or
         *     {@code null})
         */
        Class<?> binopClass() {
            return binopClass;
        }

        /**
         * Get all the implementation classes for the type, in order,
         * the canonical at index 0 and additinal accepted ones
         * following.
         */
        Class<?>[] accepted() {
            return operandClasses
                    .toArray(new Class<?>[operandClasses.size()]);
        }

        /**
         * Return the accumulated list of bases. If no bases were added,
         * the result is just {@code [object]}, except when we do this
         * for object itself, for which it is a zero-length array.
         *
         * @return array of the bases of this type
         */
        PyType[] getBases() {
            if (bases.isEmpty()) {
                /*
                 * No bases specified: that means 'object' is the
                 * implicit base, unless that's us.
                 */
                if (implClass() != PyBaseObject.class)
                    return ONLY_OBJECT;         // Normally
                else
                    return EMPTY_TYPE_ARRAY;    // For 'object'
            } else
                return bases.toArray(new PyType[bases.size()]);
        }

        /**
         * Return the meta-class of the type being created. If none was
         * set, it is {@link PyType#TYPE}..
         *
         * @return the proper meta-class
         */
        PyType getMetaclass() {
            return metaclass != null ? metaclass : TYPE;
        }

        // Something helpful in debugging
        @Override
        public String toString() {
            String fmt = "'%s' %s, flags=%s impl=%s";
            return String.format(fmt, name, bases, flags,
                    implClass().getSimpleName());
        }
    }

    // Special methods -----------------------------------------------

    protected Object __repr__() throws Throwable {
        return PyUnicode.fromFormat("<class '%s'>", name);
    }

    /**
     * Handle calls to a type object, which will normally be for
     * construction of an object of that type, except for the special
     * case {@code type(obj)}, which enquires the Python type of the
     * object.
     *
     * @param args argument tuple (length 1 in a type enquiry).
     * @param kwargs keyword arguments (empty or {@code null} in a type
     *     enquiry).
     * @return new object (or a type if an enquiry).
     * @throws TypeError when cannot create instances
     * @throws Throwable from implementation slot functions
     */
    protected Object __call__(PyTuple args, PyDict kwargs)
            throws TypeError, Throwable {
        try {
            // Create the instance with given arguments.
            Object o = op_new.invokeExact(this, args, kwargs);
            // Check for special case type enquiry: yes afterwards!
            // (PyType.__new__ performs both functions.)
            if (isTypeEnquiry(this, args, kwargs)) { return o; }
            // As __new__ may be user-defined, check type as expected.
            Operations ops = Operations.of(o);
            if (ops.type(o).isSubTypeOf(this)) {
                // Initialise the object just returned (if necessary).
                if (Slot.op_init.isDefinedFor(ops))
                    ops.op_init.invokeExact(o, args, kwargs);
            }
            return o;
        } catch (EmptyException e) {
            // this.op_new is empty (not TYPE.op_new)
            throw new TypeError("cannot create '%.100s' instances",
                    name);
        }
    }

    /**
     * Create a new Python {@code type} or execute the built-in
     * {@code type()}, depending on the number of arguments in
     * {@code args}. Because {@code type} is a type, calling it for type
     * enquiry looks initially like a constructor call, except for the
     * number of arguments. A handle to {@code __new__} populates
     * {@link Slot#op_new}. It has signature {@link Signature#NEW}.
     *
     * @param metatype the subclass of type to be created
     * @param args supplied positionally to the call in Python
     * @param kwds supplied as keywords to the call in Python
     * @return the created type
     * @throws TypeError if the wrong number of arguments is given or
     *     there are keywords
     * @throws Throwable for other errors
     */
    static Object __new__(PyType metatype, PyTuple args, PyDict kwds)
            throws TypeError, Throwable {

        // Special case: type(x) should return type(x)
        if (isTypeEnquiry(metatype, args, kwds)) {
            return PyType.of(args.get(0));
        }

        // Type creation call
        Object oBases, oName, oNamespace;

        if (args.size() != 3) {
            throw new TypeError("type() takes 1 or 3 arguments");
        } else if (!PyUnicode.TYPE.check(oName = args.get(0))) {
            throw new TypeError(NEW_ARG_MUST_BE, 0, PyUnicode.TYPE,
                    PyType.of(oName));
        } else if (!PyTuple.TYPE.check(oBases = args.get(1))) {
            throw new TypeError(NEW_ARG_MUST_BE, 1, PyTuple.TYPE.name,
                    PyType.of(oBases));
        } else if (!PyDict.TYPE.check(oNamespace = args.get(2))) {
            throw new TypeError(NEW_ARG_MUST_BE, 2, PyDict.TYPE.name,
                    PyType.of(oNamespace));
        }

        // XXX This is still rather crude

        // Construct a type with an empty dictionary

        String name = oName.toString();

        // XXX How do I decide the base (and find the implClass)?
        // Should depend on base and be .Derived: how does that work?

        Class<?> implClass = PyBaseObject.class;

        // XXX Why is this the right lookup? Why need one anyway?

        Spec spec =
                new Spec(name, implClass, LOOKUP).flag(Flag.MUTABLE);

        PyTuple bases = ((PyTuple) oBases);
        if (bases.size() == 0)
            spec.base(OBJECT_TYPE);
        else {
            for (PyType b : types(bases, "bases must be types")) {
                spec.base(b);
            }
        }

        PyType type = PyType.fromSpec(spec);

        // Populate the dictionary from the name space

        PyDict namespace = (PyDict) oNamespace;
        for (Map.Entry<Object, Object> e : namespace.entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            if (PyUnicode.TYPE.check(k))
                type.dict.put((PyUnicode) k, v);
        }

        return type;
    }

    /**
     * {@link Slot#op_getattribute} has signature
     * {@link Signature#GETATTR} and provides attribute read access on
     * this type object and its metatype. This is very like
     * {@code object.__getattribute__}
     * ({@link PyBaseObject#__getattribute__(Object, PyUnicode)}), but
     * the instance is replaced by a type object, and that object's type
     * is a meta-type (which is also a {@code type}).
     * <p>
     * The behavioural difference is that in looking for attributes on a
     * type:
     * <ul>
     * <li>we use {@link #lookup(PyUnicode)} to search along along the
     * MRO, and</li>
     * <li>if we find a descriptor, we use it.
     * ({@code object.__getattribute__} does not check for descriptors
     * on the instance.)</li>
     * </ul>
     * <p>
     * The following order of precedence applies when looking for the
     * value of an attribute:
     * <ol>
     * <li>a data descriptor from the dictionary of the meta-type</li>
     * <li>a descriptor or value in the dictionary of {@code type}</li>
     * <li>a non-data descriptor or value from dictionary of the meta
     * type</li>
     * </ol>
     *
     * @param name of the attribute
     * @return attribute value
     * @throws AttributeError if no such attribute
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython type_getattro in typeobject.c
    protected Object __getattribute__(PyUnicode name)
            throws AttributeError, Throwable {

        PyType metatype = getType();
        MethodHandle descrGet = null;

        // Look up the name in the type (null if not found).
        Object metaAttr = metatype.lookup(name);
        if (metaAttr != null) {
            // Found in the metatype, it might be a descriptor
            Operations metaAttrOps = Operations.of(metaAttr);
            descrGet = metaAttrOps.op_get;
            if (metaAttrOps.isDataDescr()) {
                // metaAttr is a data descriptor so call its __get__.
                try {
                    // Note the cast of 'this', to match op_get
                    return descrGet.invokeExact(metaAttr, (Object) this,
                            metatype);
                } catch (Slot.EmptyException e) {
                    /*
                     * We do not catch AttributeError: it's definitive.
                     * The slot shouldn't be empty if the type is marked
                     * as a descriptor (of any kind).
                     */
                    throw new InterpreterError(
                            Abstract.DESCR_NOT_DEFINING, "data",
                            "__get__");
                }
            }
        }

        /*
         * At this stage: metaAttr is the value from the meta-type, or a
         * non-data descriptor, or null if the attribute was not found.
         * It's time to give the type's instance dictionary a chance.
         */
        Object attr = lookup(name);
        if (attr != null) {
            // Found in this type. Try it as a descriptor.
            try {
                /*
                 * Note the args are (null, this): we respect
                 * descriptors in this step, but have not forgotten we
                 * are dereferencing a type.
                 */
                return Operations.of(attr).op_get.invokeExact(attr,
                        (Object) null, this);
            } catch (Slot.EmptyException e) {
                // We do not catch AttributeError: it's definitive.
                // Not a descriptor: the attribute itself.
                return attr;
            }
        }

        /*
         * The name wasn't in the type dictionary. We are now left with
         * the results of look-up on the meta-type.
         */
        if (descrGet != null) {
            // metaAttr may be a non-data descriptor: call __get__.
            try {
                return descrGet.invokeExact(metaAttr, (Object) this,
                        metatype);
            } catch (Slot.EmptyException e) {}
        }

        if (metaAttr != null) {
            /*
             * The attribute obtained from the meta-type, and that
             * turned out not to be a descriptor, is the return value.
             */
            return metaAttr;
        }

        // All the look-ups and descriptors came to nothing :(
        throw Abstract.noAttributeError(this, name);
    }

    /**
     * {@link Slot#op_setattr} has signature {@link Signature#SETATTR}
     * and provides attribute write access on this type object. The
     * behaviour is very like the default {@code object.__setattr__}
     * except that it has write access to the type dictionary that is
     * denied through {@link #getDict(boolean)}.
     *
     * @param name of the attribute
     * @param value to give the attribute
     * @throws AttributeError if no such attribute or it is read-only
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython type_setattro in typeobject.c
    protected void __setattr__(PyUnicode name, Object value)
            throws AttributeError, Throwable {

        // Accommodate CPython idiom that set null means delete.
        if (value == null) {
            // Do this to help porting. Really this is an error.
            __delattr__(name);
            return;
        }

        // Trap immutable types
        if (!flags.contains(Flag.MUTABLE))
            throw Abstract.cantSetAttributeError(this);

        // Force name to actual str , not just a sub-class
        if (name.getClass() != PyUnicode.class) {
            name = Py.str(name.toString());
        }

        // Check to see if this is a special name
        boolean special = isDunderName(name);

        // Look up the name in the meta-type (null if not found).
        Object metaAttr = getType().lookup(name);
        if (metaAttr != null) {
            // Found in the meta-type, it might be a descriptor.
            Operations metaAttrOps = Operations.of(metaAttr);
            if (metaAttrOps.isDataDescr()) {
                // Try descriptor __set__
                try {
                    metaAttrOps.op_set.invokeExact(metaAttr,
                            (Object) this, value);
                    if (special) { updateAfterSetAttr(name); }
                    return;
                } catch (Slot.EmptyException e) {
                    // We do not catch AttributeError: it's definitive.
                    // Descriptor but no __set__: do not fall through.
                    throw Abstract.readonlyAttributeError(this, name);
                }
            }
        }

        /*
         * There was no data descriptor, so we will place the value in
         * the object instance dictionary directly.
         */
        // Use the privileged put
        dict.put(name, value);
        if (special) { updateAfterSetAttr(name); }
    }

    /**
     * {@link Slot#op_delattr} has signature {@link Signature#DELATTR}
     * and provides attribute deletion on this type object. The
     * behaviour is very like the default {@code object.__delattr__}
     * except that it has write access to the type dictionary that is
     * denied through {@link #getDict(boolean)}.
     *
     * @param name of the attribute
     * @throws AttributeError if no such attribute or it is read-only
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython type_setattro in typeobject.c
    protected void __delattr__(PyUnicode name)
            throws AttributeError, Throwable {

        // Trap immutable types
        if (!flags.contains(Flag.MUTABLE))
            throw Abstract.cantSetAttributeError(this);

        // Force name to actual str , not just a sub-class
        if (name.getClass() != PyUnicode.class) {
            name = Py.str(name.toString());
        }

        // Check to see if this is a special name
        boolean special = isDunderName(name);

        // Look up the name in the meta-type (null if not found).
        Object metaAttr = getType().lookup(name);
        if (metaAttr != null) {
            // Found in the meta-type, it might be a descriptor.
            Operations metaAttrOps = Operations.of(metaAttr);
            if (metaAttrOps.isDataDescr()) {
                // Try descriptor __delete__
                try {
                    metaAttrOps.op_delete.invokeExact(metaAttr,
                            (Object) this);
                    if (special) { updateAfterSetAttr(name); }
                    return;
                } catch (Slot.EmptyException e) {
                    // We do not catch AttributeError: it's definitive.
                    // Data descriptor but no __delete__.
                    throw Abstract.mandatoryAttributeError(this, name);
                }
            }
        }

        /*
         * There was no data descriptor, so it's time to give the type
         * instance dictionary a chance to receive. A type always has a
         * dictionary so this.dict can't be null.
         */
        // Use the privileged remove
        Object previous = dict.remove(name);
        if (previous == null) {
            // A null return implies it didn't exist
            throw Abstract.noAttributeError(this, name);
        }

        if (special) { updateAfterSetAttr(name); }
        return;
    }

    // plumbing --------------------------------------------------

    private static final String NEW_ARG_MUST_BE =
            "type.__new__() argument %d must be %s, not %s";

    /**
     * Given the bases of a new class, choose the {@code type} on which
     * a sub-class should be implemented.
     * <p>
     * When a sub-class is defined in Python, it may have several bases,
     * each with their own Java implementation. What Java class should
     * implement the new sub-class? This chosen Java class must be
     * acceptable as {@code self} to a method (slot functions,
     * descriptors) inherited from any base. The methods of
     * {@link PyBaseObject} accept any {@link Object}, but all other
     * implementation classes require an instance of their own type to
     * be presented.
     * <p>
     * A method will accept any Java sub-type of the type of its
     * declared parameter. We ensure compatibility by choosing that the
     * implementation Java class of the new sub-type is a Java sub-class
     * of the implementation types of all the bases (excluding those
     * implemented on {@link PyBaseObject}).
     * <p>
     * This imposes a constraint on the bases, except for those
     * implemented by PyBaseObject, that their implementations have a
     * common Java descendant. (The equivalent constraint in CPython is
     * that the layout of the {@code struct} that represents an instance
     * of every base should match a truncation of the one chosen.)
     *
     * @param bases sub-classed by the new type
     * @return the acceptable base
     */
    // Compare CPython best_base in typeobject.c
    private static PyType bestBase(PyType[] bases) {
        // XXX This is a stop-gap answer: revisit in due course.
        /*
         * Follow the logic of CPython typeobject.c, but adapted to a
         * Java context.
         */
        if (bases.length == 0)
            return OBJECT_TYPE;
        else {
            return bases[0];
        }
    }

    /**
     * Helper for {@link #__call__} and {@link #__new__}. This is a type
     * enquiry if {@code type} is {@link PyType#TYPE} and there is just
     * one argument.
     */
    private static boolean isTypeEnquiry(PyType type, PyTuple args,
            PyDict kwargs) {
        return type == TYPE && args.size() == 1
                && (kwargs == null || kwargs.isEmpty());
    }

    /**
     * Check that all the objects in the tuple are {@code type}, and
     * return them as an array.
     */
    private static PyType[] types(PyTuple tuple, String msg) {
        PyType[] t = new PyType[tuple.size()];
        int i = 0;
        for (Object name : tuple) {
            if (name instanceof PyType)
                t[i++] = (PyType) name;
            else
                throw new TypeError(msg);
        }
        return t;
    }

    // Compare CPython _PyType_GetDocFromInternalDoc
    // in typeobject.c
    static Object getDocFromInternalDoc(String name, String doc) {
        // TODO Auto-generated method stub
        return null;
    }

    // Compare CPython: PyType_GetTextSignatureFromInternalDoc
    // in typeobject.c
    static Object getTextSignatureFromInternalDoc(String name,
            String doc) {
        // TODO Auto-generated method stub
        return null;
    }
}
