// Copyright (c)2023 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.nio.CharBuffer;

import uk.co.farowl.vsj3.evo1.Exposed.Getter;
import uk.co.farowl.vsj3.evo1.stringlib.ByteArrayBuilder;

/**
 * A concrete implementation of the Python v3.11 {@code code} object
 * ({@code PyCodeObject} in CPython's C API).
 */
public class CPython311Code extends PyCode {

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

    /** Instruction opcodes, not {@code null}. */
    final char[] wordcode;

    /**
     * Full constructor based on CPython's
     * {@code PyCode_NewWithPosOnlyArgs}. The {@link #traits} of the
     * code are supplied here as CPython reports them: as a bit array in
     * an integer, but the constructor makes a conversion, and it is the
     * {@link #traits} which should be used at the Java level.
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
     * @param flags {@code co_flags} a bitmap of traits
     *
     * @param wordcode {@code co_code} as unsigned 16-bit words
     * @param firstlineno mapping byte code ranges to source lines
     * @param linetable mapping byte code ranges to source lines
     *
     * @param consts {@code co_consts}
     * @param names {@code co_names}
     *
     * @param layout variable names and properties, in the order
     *     {@code co_varnames + co_cellvars + co_freevars} but without
     *     repetition.
     *
     * @param argcount {@code co_argcount} the number of positional
     *     parameters (including positional-only arguments and arguments
     *     with default values)
     * @param posonlyargcount {@code co_posonlyargcount} the number of
     *     positional-only arguments (including arguments with default
     *     values)
     * @param kwonlyargcount {@code co_kwonlyargcount} the number of
     *     keyword-only arguments (including arguments with default
     *     values)
     *
     * @param stacksize {@code co_stacksize}
     * @param exceptiontable supports exception processing
     */
    public CPython311Code( //
            // Grouped as _PyCodeConstructor in pycore_code.h
            // Metadata
            String filename, String name, String qualname, //
            int flags,
            // The code
            char[] wordcode, int firstlineno, byte[] linetable,
            // Used by the code
            Object[] consts, String[] names,
            // Mapping frame offsets to information
            Variable[] layout,
            // Parameter navigation with varnames
            int argcount, int posonlyargcount, int kwonlyargcount,
            // Needed to support execution
            int stacksize, byte[] exceptiontable) {
        super(filename, name, qualname, flags, //
                firstlineno, //
                consts, names, //
                layout, //
                argcount, posonlyargcount, kwonlyargcount);

        // A few of these (just a few) are local to this class.
        this.wordcode = wordcode;
        this.linetable = linetable;
        this.stacksize = stacksize;
        this.exceptiontable = exceptiontable;

        // TODO Fix-up wordcode from layout

    }

    /**
     * Essentially equivalent to the (strongly-typed) constructor, but
     * accepting {@code Object} arguments, which are checked for type
     * here. This is primarily designed for use by the {@code marshal}
     * module.
     * <p>
     * The {@link #traits} of the code are supplied here as CPython
     * reports them: as a bitmap in an integer, but the constructor
     * makes a conversion, and it is the {@link #traits} which should be
     * used at the Java level.
     * <p>
     * Where the parameters map directly to an attribute of the code
     * object, that is the best way to explain them. Note that this
     * factory method is tuned to the needs of {@code marshal.read}
     * where the serialised form makes no secret of the version-specific
     * implementation details.
     *
     * @param filename ({@code str}) = {@code co_filename}
     * @param name ({@code str}) = {@code co_name}
     * @param qualname ({@code str}) = {@code co_qualname}
     * @param flags ({@code int}) = @code co_flags} a bitmap of traits
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
     * @param argcount ({@code int}) = {@code co_argcount}
     * @param posonlyargcount ({@code int}) = {@code co_posonlyargcount}
     * @param kwonlyargcount ({@code int}) = {@code co_kwonlyargcount}
     * @param stacksize ({@code int}) = {@code co_stacksize}
     * @param exceptiontable ({@code tuple}) supports exception
     *     processing
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
        if (argcount < posonlyargcount || posonlyargcount < 0
                || kwonlyargcount < 0) {
            throw new ValueError("code: argument counts inconsistent");
        }
        if (stacksize < 0) {
            throw new ValueError("code: bad stacksize");
        }
        if (flags < 0) {
            throw new ValueError("code: bad flags argument");
        }

        PyBytes _bytecode = castBytes(bytecode, "bytecode");
        PyTuple _consts = castTuple(consts, "consts");
        String[] _names = names(names, "names");

        // Compute a Variable[] layout array from localsplus* arrays
        Variable[] _layout = layout(totalargs(argcount, flags),
                localsplusnames, localspluskinds);

        String _name = castString(name, "name");
        String _qualname = castString(qualname, "qualname");
        String _filename = castString(filename, "filename");

        PyBytes _linetable = castBytes(linetable, "linetable");
        PyBytes _exceptiontable =
                castBytes(exceptiontable, "exceptiontable");

        // Everything is the right type and size
        return new CPython311Code(//
                _filename, _name, _qualname, flags, //
                wordcode(_bytecode), firstlineno,
                _linetable.asByteArray(), //
                _consts.toArray(), _names, //
                _layout, //
                argcount, posonlyargcount, kwonlyargcount, //
                stacksize, _exceptiontable.asByteArray());
    }

    // Attributes -----------------------------------------------------

    @Override
    @Getter
    int co_stacksize() { return stacksize; }

    @Override
    @Getter
    PyBytes co_code() {
        ByteArrayBuilder builder =
                new ByteArrayBuilder(2 * wordcode.length);
        for (char opword : wordcode) {
            // Opcode is high byte and first
            builder.append(opword >> 8).append(opword);
        }
        return new PyBytes(builder);
    }

    // Java API -------------------------------------------------------

    /**
     * Create a {@code PyFunction} that will execute this {@code PyCode}
     * (adequate for module-level code).
     *
     * @param interpreter providing the module context
     * @param globals name space to treat as global variables
     * @return the function
     */
    // Compare CPython PyFunction_NewWithQualName in funcobject.c
    // ... with the interpreter required by architecture
    @Override
    CPython311Function createFunction(Interpreter interpreter,
            PyDict globals) {
        return new CPython311Function(interpreter, this, globals);
    }

    @Override
    CPython311Function createFunction(Interpreter interpreter,
            PyDict globals, Object[] defaults, PyDict kwdefaults,
            Object annotations, PyCell[] closure) {
        return new CPython311Function(interpreter, this, globals,
                defaults, kwdefaults, annotations, closure);
    }

    // Plumbing -------------------------------------------------------

    /**
     * Convert the contents of a Python {@code bytes} to 16-bit word
     * code as expected by the eval-loop in {@link CPython311Frame}.
     *
     * @param bytecode as compiled by Python as bytes
     * @return 16-bit word code
     */
    private static char[] wordcode(PyBytes bytecode) {
        CharBuffer wordbuf = bytecode.getNIOByteBuffer().asCharBuffer();
        final int len = wordbuf.remaining();
        char[] code = new char[len];
        wordbuf.get(code, 0, len);
        return code;
    }
}
