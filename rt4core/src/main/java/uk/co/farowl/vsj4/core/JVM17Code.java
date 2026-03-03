// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.EnumSet;

import uk.co.farowl.vsj4.compiled.CompiledClasses;
import uk.co.farowl.vsj4.core.ArgParser.FrameWrapper;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * A Python {@code code} object for a Python function or module body
 * that has been compiled to JVM class definition (JVM byte code). This
 * compiled byte code defines a concrete sub-class of {@link JVM17Frame}
 * which will be initialised in an instance this class. The
 * corresponding frame is implement
 */
public class JVM17Code extends PyCode311 {
    /**
     * Describe the layout of the frame local variables (including
     * arguments), cell and free variables allowing implementation-level
     * access to CPython-specific features.
     */
    final JVM17Layout layout;

    /** JVM byte code (class definition). */
    final PyBytes bytecode;

    /**
     * A lookup, not {@code null} on the class that implements the
     * frames of the particular owning {@link PyCode} object. The class
     * was defined by compiling the body of a function or module and has
     * been loaded as a hidden class.
     */
    private final Lookup frameClassLookup;

    /**
     * A handle on the constructor of the specific class that implements
     * the frames of this code object. It has the same signature as
     * {@link JVM17Frame#JVM17Frame(PyFunction, JVM17Code, Object)},
     * including the return type.
     */
    private final MethodHandle constructorHandle;

    /**
     * An array of handles on the local variables (fields) in any frame
     * of the specific sub-class of {@code JVM17Frame} defined by the
     * owning code object.
     */
    private final VarHandle[] fieldHandles;

    /**
     * Table of byte code address ranges mapped to source lines,
     * presentable as defined in PEP 626.
     */
    // See CPython lnotab_notes.txt
    // TODO Doubtful we need this in JVM code object.
    final byte[] linetable;

    /**
     * Full constructor for the JVM-specific implementation of the
     * Python {@code code} object. Where the parameters map directly to
     * an attribute of the code object, that is the best way to explain
     * them.
     *
     * @param filename {@code co_filename}
     * @param name {@code co_name}
     * @param qualname {@code co_qualname}
     * @param flags {@code co_flags} a set of code flags
     *
     * @param bytecode the JVM byte code
     * @param firstlineno first source line of this code
     * @param linetable mapping byte code ranges to source lines
     *
     * @param consts {@code co_consts}
     * @param names {@code co_names}
     *
     * @param localsplusnames ({@code tuple[str]}) variable names
     * @param localspluskinds ({@code bytes}) variable kinds
     *
     * @param argcount {@code co_argcount} the number of positional
     *     parameters (including positional-only parameters and those
     *     with default values)
     * @param posonlyargcount {@code co_posonlyargcount} the number of
     *     positional-only parameters (including those with default
     *     values)
     * @param kwonlyargcount {@code co_kwonlyargcount} the number of
     *     keyword-only parameters (including those with default values)
     */
    JVM17Code(
            // Grouped as _PyCodeConstructor in pycore_code.h

            // Metadata
            String filename, String name, String qualname, //
            EnumSet<CodeFlag> flags,

            // The code (actual class is already loaded)
            PyBytes bytecode, int firstlineno, byte[] linetable,

            // Constants used by the code
            Object[] consts, String[] names, //

            // Mapping frame offsets to information
            Object localsplusnames, Object localspluskinds,

            // Parameter navigation with varnames
            int argcount, int posonlyargcount, int kwonlyargcount) {

        // Most of the arguments are applicable to any PyCode
        super(filename, name, qualname, flags, //
                firstlineno, //
                consts, names, //
                argcount, posonlyargcount, kwonlyargcount);

        // A few are JVM-specific (tentatively these).

        this.bytecode = bytecode;
        this.linetable = linetable;

        // Compute a layout from localsplus* arrays etc.
        this.layout = new JVM17Layout(localsplusnames, localspluskinds);

        // Create a class for the actual frame type
        try {
            this.frameClassLookup = CompiledClasses.classFrom(bytecode);
            this.fieldHandles = this.makeLocalVarHandles();
            this.constructorHandle = makeConstructorHandle();

            // Checks
            Class<?> cls = frameClassLookup.lookupClass();
            if (!JVM17Frame.class.isAssignableFrom(cls)) {
                throw PyErr.format(PyExc.ValueError,
                        "%s does not define a JVM17Frame", toString());
            }
        } catch (IllegalAccessException e) {
            throw new InterpreterError(
                    "Failed to create frame class specified by %s",
                    qualname);
        }
    }

    /**
     * Essentially equivalent to the (strongly-typed) constructor, but
     * accepting {@code Object} arguments, that are checked for type
     * here. This factory method is tuned to the needs of
     * {@code marshal.read} where the serialised form makes no secret of
     * the version-specific implementation details.
     * <p>
     * The {@link PyCode#flags} of the code are supplied here as CPython
     * reports them: as a bitmap in an integer, but this method makes a
     * conversion, and it is the {@code EnumSet} {@link PyCode#flags}
     * that should be used at the Java level.
     * <p>
     * Where the parameters map directly to an attribute of the code
     * object, that is the best way to explain them.
     *
     * @param filename ({@code str}) = {@code co_filename}
     * @param name ({@code str}) = {@code co_name}
     * @param qualname ({@code str}) = {@code co_qualname}
     * @param flags ({@code int}) = @code co_flags} a bitmap
     *
     * @param bytecode ({@code bytes}) = {@code co_code}
     * @param firstlineno ({@code int}) = {@code co_firstlineno}
     * @param linetable ({@code bytes}) = {@code co_linetable}
     *
     * @param consts ({@code tuple}) = {@code co_consts}
     * @param names ({@code tuple[str]}) = {@code co_names}
     *
     * @param localsplusnames ({@code tuple[str]}) variable names
     * @param localspluskinds ({@code bytes}) variable kinds
     *
     * @param argcount ({@code int}) = {@code co_argcount}
     * @param posonlyargcount ({@code int}) = {@code co_posonlyargcount}
     * @param kwonlyargcount ({@code int}) = {@code co_kwonlyargcount}
     * @return a new code object
     */
    // Compare CPython _PyCode_New in codeobject.c
    public static JVM17Code create( //
            // Grouped as _PyCodeConstructor in pycore_code.h
            // Metadata
            Object filename, Object name, Object qualname, int flags,
            // The code
            Object bytecode, int firstlineno, Object linetable,
            // Used by the code
            Object consts, Object names,
            // Mapping frame offsets to information
            Object localsplusnames, Object localspluskinds,
            // For navigation within localsplus
            int argcount, int posonlyargcount, int kwonlyargcount) {

        String _filename = castString(filename, "filename");
        String _name = castString(name, "name");
        String _qualname = castString(qualname, "qualname");
        EnumSet<CodeFlag> _flags = CodeFlag.setFromBits(flags);

        PyBytes _bytecode = castBytes(bytecode, "bytecode");
        PyTuple _consts = castTuple(consts, "consts");
        String[] _names = names(names, "names");

        PyBytes _linetable = castBytes(linetable, "linetable");

        // Everything is the right type and size
        return new JVM17Code( //
                _filename, _name, _qualname, _flags, //
                _bytecode, //
                firstlineno, _linetable.asByteArray(), //
                _consts.toArray(), _names, //
                localsplusnames, localspluskinds, //
                argcount, posonlyargcount, kwonlyargcount);
    }

    // Attributes -----------------------------------------------------

    @Override
    int co_stacksize() { return 0; }

    @Override
    PyBytes co_code() { return bytecode; }

    // Java API -------------------------------------------------------

    @Override
    JVM17Frame createFrame(PyFunction func, Object locals) {
        // Construct an instance of the code-specific frame class
        try {
            return (JVM17Frame)constructorHandle.invoke(func, this,
                    locals);
        } catch (Throwable e) {
            // This ought not to happen
            throw new InterpreterError(e,
                    "Failed to create frame in call of %s", name);
        }
    }

    @Override
    JVM17Layout layout() { return layout; }

    /**
     * A {@link Layout} based on a representation used internally by
     * Python that appears in the stream {@code marshal} writes, e.g. in
     * a {@code .pyc} file, but adapted for code compiled for the JVM.
     */
    static class JVM17Layout extends Layout311 {
        /**
         * Construct a {@code Layout} adapted for code compiled for the
         * JVM. The compiled code is a class definition file extending
         * {@link JVM17Frame}, and presented as a {@code Lookup} object.
         *
         * @param localsplusnames tuple of all the names
         * @param localspluskinds bytes of kinds of variables
         */
        JVM17Layout(Object localsplusnames, Object localspluskinds) {
            super(localsplusnames, localspluskinds);
        }
    }

    /**
     * A wrapper on the frame that is able to get or set parameter
     * fields in a frame specific to the enclosing {@code code} object.
     */
    class Wrapper extends FrameWrapper {
        private final JVM17Frame frame;

        /**
         * Create wrapper specific to {@code JVM17Frame}.
         *
         * @param frame to be wrapped for access
         */
        Wrapper(JVM17Frame frame) {
            frame.func.getArgParser().super();
            // this.fieldHandles = JVM17Code.this.fieldHandles;
            this.frame = frame;
        }

        @Override
        Object getLocal(int i) {
            VarHandle vh = fieldHandles[i];
            return vh.get(frame);
        }

        @Override
        void setLocal(int i, Object v) {
            VarHandle vh = fieldHandles[i];
            vh.set(frame, v);
        }
    }

    /**
     * Create an array of {@code VarHandle}s on the fields of the frame
     * class corresponding to the local variables in order. The type of
     * each variable, expected by the handle, may be {@link PyCell}, if
     * it is a cell variable, otherwise it is a plain {@code Object}.
     *
     * @return an array of {@code VarHandle}s on the fields
     */
    private VarHandle[] makeLocalVarHandles() {
        int nfast = layout.size();
        if (nfast > 0) {
            VarHandle[] handles = new VarHandle[nfast];
            for (int i = 0; i < nfast; i++) {
                handles[i] = fieldhandle(frameClassLookup, i);
            }
            return handles;
        } else {
            return EMPTY_VARHANDLE_ARRAY;
        }
    }

    /**
     * Create a handle on the local variable at a given index in the
     * layout. The type of the variable, expected by the handle, may be
     * {@link PyCell}, if it is a cell variable, otherwise it is a plain
     * {@code Object}.
     *
     * @param lookup with private access to the frame class
     * @param index in the layout
     * @return the handle
     */
    private VarHandle fieldhandle(Lookup lookup, int index) {
        try {
            if (layout.isCellOrFree(index)) {
                return lookup.findVarHandle(JVM17Frame.class,
                        layout.localnames[index], PyCell.class);
            } else {
                return lookup.findVarHandle(JVM17Frame.class,
                        layout.localnames[index], Object.class);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new InterpreterError(e,
                    "Unable to access local variable field %s in %s",
                    layout.localnames[index], lookup.lookupClass());
        }
    }

    /**
     *
     * @return
     */
    private MethodHandle makeConstructorHandle() {
        Class<?> cls = frameClassLookup.lookupClass();
        try {
            return frameClassLookup.findConstructor(cls,
                    MethodType.methodType(void.class, PyFunction.class,
                            JVM17Code.class, Object.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new InterpreterError(e,
                    "Unable to create constructor handle in %s",
                    frameClassLookup.lookupClass());
        }
    }

    private static final VarHandle[] EMPTY_VARHANDLE_ARRAY =
            new VarHandle[0];
}
