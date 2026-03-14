// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import java.nio.ShortBuffer;
import java.util.EnumSet;

import uk.co.farowl.vsj4.stringlib.ByteArrayBuilder;

/**
 * A concrete implementation of the Python v3.11 {@code code} object
 * ({@code PyCodeObject} in CPython's C API).
 */
public class CPython311Code extends PyCode {

    /**
     * Describe the layout of the frame local variables (including
     * arguments), cell and free variables allowing implementation-level
     * access to CPython-specific features.
     */
    final Layout311 layout;

    /**
     * Instruction opcodes, not {@code null}. Treat these as unsigned
     * 16-bit patterns in which the low 8 bits is the argument and the
     * upper 8 bits is the opcode itself.
     */
    final short[] wordcode;

    /**
     * Table of byte code address ranges mapped to source lines,
     * presentable as defined in PEP 626.
     */
    // See CPython lnotab_notes.txt
    final byte[] linetable;

    /** Number of entries needed for evaluation stack. */
    final int stacksize;

    /**
     * Table of byte code address ranges mapped to handler addresses in
     * a compact byte encoding (defined by CPython and appearing in the
     * serialised form of a {@code code} object).
     */
    final byte[] exceptiontable;

    /**
     * Full constructor based on CPython's
     * {@code PyCode_NewWithPosOnlyArgs}.
     * <p>
     * Where the parameters map directly to an attribute of the code
     * object, that is the best way to explain them. Note that this
     * factory method is tuned to the needs of {@code marshal.read}
     * where the serialised form makes no secret of the version-specific
     * implementation details.
     *
     * @param filename {@code co_filename}
     * @param name {@code co_name}
     * @param qualname {@code co_qualname}
     * @param flags {@code co_flags} a set of flags identifying various
     *     (boolean) traits of the code object
     *
     * @param wordcode {@code co_code} as unsigned 16-bit words
     * @param firstlineno first source line of this code
     * @param linetable mapping byte code ranges to source lines
     *
     * @param consts {@code co_consts}
     * @param names {@code co_names}
     *
     * @param layout variable names and properties, in the order
     *     {@code co_varnames + co_cellvars + co_freevars} but without
     *     repetition.
     *
     *
     * @param stacksize {@code co_stacksize}
     * @param exceptiontable supports exception processing
     */
    private CPython311Code( //
            // Grouped as _PyCodeConstructor in pycore_code.h
            // Metadata
            String filename, String name, String qualname, //
            EnumSet<CodeFlag> flags,
            // The code
            short[] wordcode, int firstlineno, byte[] linetable,
            // Used by the code
            Object[] consts, String[] names,
            // Mapping frame offsets to information
            Layout311 layout,
            // Needed to support execution
            int stacksize, byte[] exceptiontable) {

        // Most of the arguments are applicable to any PyCode
        super(filename, name, qualname, flags, //
                firstlineno, //
                consts, names);

        // A few are CPython-specific (tentatively these).
        this.layout = layout;
        this.wordcode = wordcode;
        this.linetable = linetable;
        this.stacksize = stacksize;
        this.exceptiontable = exceptiontable;
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
     * @param stacksize ({@code int}) = {@code co_stacksize}
     * @param exceptiontable ({@code tuple}) supports exception
     *     processing
     *
     * @return a new code object
     */
    // Compare CPython _PyCode_New in codeobject.c
    public static CPython311Code create( //
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
            int argcount, int posonlyargcount, int kwonlyargcount,
            // Needed to support execution
            int stacksize, Object exceptiontable) {

        // Order of checks and casts based on _PyCode_Validate FWIW
        if (stacksize < 0) {
            throw PyErr.format(PyExc.ValueError, "code: bad stacksize");
        }

        String _filename = castString(filename, "filename");
        String _name = castString(name, "name");
        String _qualname = castString(qualname, "qualname");
        EnumSet<CodeFlag> _flags = CodeFlag.setFromBits(flags);

        PyBytes _bytecode = castBytes(bytecode, "bytecode");
        PyTuple _consts = castTuple(consts, "consts");
        String[] _names = names(names, "names");

        // Compute a layout from localsplus* arrays
        Layout311 _layout = new Layout311(localsplusnames,
                localspluskinds, argcount, posonlyargcount,
                kwonlyargcount, _flags);

        PyBytes _linetable = castBytes(linetable, "linetable");
        PyBytes _exceptiontable =
                castBytes(exceptiontable, "exceptiontable");

        // Everything is the right type and size
        return new CPython311Code(//
                _filename, _name, _qualname, _flags, //
                wordcode(_bytecode), firstlineno,
                _linetable.asByteArray(), //
                _consts.toArray(), _names, //
                _layout, //
                stacksize, _exceptiontable.asByteArray());
    }

    // Attributes -----------------------------------------------------

    @Override
    int co_stacksize() { return stacksize; }

    @Override
    PyBytes co_code() {
        ByteArrayBuilder builder =
                new ByteArrayBuilder(2 * wordcode.length);
        for (short opword : wordcode) {
            // Opcode is high byte and goes first in byte code
            builder.append(opword >> 8).append(opword);
        }
        return new PyBytes(builder);
    }

    // Java API -------------------------------------------------------

    @Override
    CPython311Frame createFrame(PyFunction func, Object locals) {
        return new CPython311Frame(func, this, locals);
    }

    @Override
    Layout311 layout() { return layout; }

    // Plumbing -------------------------------------------------------

    /**
     * Convert the contents of a Python {@code bytes} to 16-bit word
     * code as expected by the eval-loop in {@link CPython311Frame}.
     *
     * @param bytecode as compiled by Python as bytes
     * @return 16-bit word code
     */
    private static short[] wordcode(PyBytes bytecode) {
        ShortBuffer wordbuf =
                bytecode.getNIOByteBuffer().asShortBuffer();
        final int len = wordbuf.remaining();
        short[] code = new short[len];
        wordbuf.get(code, 0, len);
        return code;
    }
}
