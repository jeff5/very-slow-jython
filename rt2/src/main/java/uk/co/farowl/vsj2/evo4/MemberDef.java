package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code MemberDef} ({@code PyMemberDef} in CPython) represents a field
 * of a Java class that is exposed to Python as a member of a
 * {@code PyObject}. The member may be marked read-only. The
 * implementation type of the member must be from a small number of
 * types supported by the {@code PyMemberDesc} access functions. (In
 * CPython, the types are defined in {@code structmember.h}, but the API
 * is exclusively used by member descriptors.)
 *
 * In CPython, {@code PyMemberDef}s occur in short tables each entry
 * specifying the type and offset of the member. A Java
 * {@code MemberDef} will be based on a {@code VarHandle}.
 */
// Compare CPython struct PyMemberDef in descrobject.h
abstract class MemberDef {

    /** Values used in {@link #flags} */
    String name;
    // CPython: int type; int offset;
    VarHandle handle;

    /**
     * Attributes controlling access. (In CPython, the RESTRICTED forms
     * cause a call to {@code sys.audit} and are here only for
     * compatibility with that eventual idea.)
     */
    EnumSet<MemberDef.Flag> flags =
            EnumSet.noneOf(MemberDef.Flag.class);

    /** Indicates that only getting is valid. */
    final boolean readonly;

    /** Documentation string for the member. */
    String doc;

    /**
     * Acceptable values in the {@link #flags}.
     */
    enum Flag { READONLY, READ_RESTRICTED, WRITE_RESTRICTED }

    /**
     * Construct a MemberDef from a client-supplied handle. This allows
     * all JVM-supported access modes, but you have to make your own
     * handle.
     *
     * @param name by which the member is known to Python
     * @param handle to the Java member
     * @param flags
     * @param doc
     */
    /*
     * When translating from CPython source, this constructor
     * approximates the static initialisation of a PyMethodDef. the
     * VarHandle corresponding but to the type and the offset expression
     * that references a struct type and a field name.
     */
    MemberDef(String name, VarHandle handle,
            EnumSet<MemberDef.Flag> flags, String doc) {
        this.name = name;
        this.handle = handle;
        this.flags = flags;
        this.readonly = flags.contains(MemberDef.Flag.READONLY);
        this.doc = doc;
    }

    /**
     * A method to get {@code o.name}. If the variable type of the
     * handle does not implement {@link PyObject} a conversion will be
     * applied to the value returned.
     */
    // Compare CPython PyMember_GetOne in structmember.c
    abstract PyObject get(PyObject o);

    /**
     * A method to set {@code o.name = v}. If the variable type of the
     * handle does not implement {@link PyObject} a conversion will be
     * applied to the value provided.
     *
     * @throws TypeError
     * @throws Throwable
     */
    // Compare CPython PyMember_SetOne in structmember.c
    abstract void set(PyObject o, PyObject v)
            throws TypeError, Throwable;

    /**
     * Create a {@code MethodDef} with behaviour specific to the class
     * of object being exposed.
     *
     * @param klass class of
     * @param name by which known externally
     * @param handle to access te field
     * @param flags supplying additional characteristics
     * @param doc documentation string
     * @return MethodDef through which a descriptor may access the field
     * @throws InterpreterError if the class is not supported
     */
    static MemberDef forClass(Class<?> klass, String name,
            VarHandle handle, EnumSet<MemberDef.Flag> flags, String doc)
            throws InterpreterError {
        if (klass == int.class)
            return new _int(name, handle, flags, doc);
        else if (klass == double.class)
            return new _double(name, handle, flags, doc);
        else if (klass == String.class)
            return new _String(name, handle, flags, doc);
        else {
            throw new InterpreterError(UNSUPPORTED_TYPE, name,
                    klass.getSimpleName());
        }
    }

    protected static final String UNSUPPORTED_TYPE =
            "@Member target %.50s has unsupported type %.50s";

    /** Make an unmodifiable set from an succession of classes. */
    protected static Set<Class<?>>
            unmodifiableSet(Class<?>... classes) {
        HashSet<Class<?>> set = new HashSet<>(Arrays.asList(classes));
        return Collections.unmodifiableSet(set);
    }

    private static class _int extends MemberDef {

        _int(String name, VarHandle handle, EnumSet<Flag> flags,
                String doc) {
            super(name, handle, flags, doc);
            // TODO Auto-generated constructor stub
        }

        @Override
        PyObject get(PyObject obj) {
            int value = (int) handle.get(obj);
            return Py.val(value);
        }

        @Override
        void set(PyObject obj, PyObject value)
                throws TypeError, Throwable {
            if (readonly)
                throw Abstract.readonlyAttributeError(obj, name);
            else {
                int v = Number.asSize(value, null);
                handle.set(obj, v);
            }
        }
    }

    private static class _double extends MemberDef {

        _double(String name, VarHandle handle, EnumSet<Flag> flags,
                String doc) {
            super(name, handle, flags, doc);
        }

        @Override
        PyObject get(PyObject obj) {
            double value = (double) handle.get(obj);
            return Py.val(value);
        }

        @Override
        void set(PyObject obj, PyObject value)
                throws TypeError, Throwable {
            if (readonly)
                throw Abstract.readonlyAttributeError(obj, name);
            else {
                double v = Number.toFloat(value).doubleValue();
                handle.set(obj, v);
            }
        }
    }

    private static class _String extends MemberDef {

        _String(String name, VarHandle handle, EnumSet<Flag> flags,
                String doc) {
            super(name, handle, flags, doc);
        }

        @Override
        PyObject get(PyObject obj) {
            String value = (String) handle.get(obj);
            return Py.str(value);
        }

        @Override
        void set(PyObject obj, PyObject value)
                throws TypeError, Throwable {
            if (readonly)
                throw Abstract.readonlyAttributeError(obj, name);
            else {
                String v = value.toString();
                handle.set(obj, v);
            }
        }
    }

}