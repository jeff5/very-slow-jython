package uk.co.farowl.vsj1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import uk.co.farowl.vsj1.TreePython.operator;
import uk.co.farowl.vsj1.TreePython.unaryop;

/**
 * <code>Operations</code> is the base class of the classes that collect
 * together the operations on each Python type.
 */
public abstract class Operations {

    /** Mapping from unary operation to function names. */
    public enum UnaryOpInfo {

        POS(unaryop.UAdd, "+", "pos"),

        NEG(unaryop.USub, "-", "neg");

        /** Unary operation represented e.g. USub for NEG. */
        public final unaryop op;
        /** Symbol for the operation e.g. "-" for NEG. */
        public final String symbol;
        /** Function name implementing the operation e.g. "neg". */
        public final String name;

        private UnaryOpInfo(unaryop op, String symbol, String name) {
            this.op = op;
            this.symbol = symbol;
            this.name = name;
            // Must have same ordinal value to make forOp() work.
            assert this.ordinal() == op.ordinal();
        }

        /** Get the information for the given {@link unaryop}. */
        public static final UnaryOpInfo forOp(unaryop op) {
            return UnaryOpInfo.values()[op.ordinal()];
        }
    }

    /** Mapping from binary operation to function names. */
    public enum BinOpInfo {

        ADD(operator.Add, "+", "add"),

        SUB(operator.Sub, "-", "sub"),

        MUL(operator.Mult, "*", "mul"),

        DIV(operator.Div, "/", "div");

        /** Binary operation represented e.g. MULT for MUL. */
        public final operator op;
        /** Symbol for the operation e.g. "*" for MUL. */
        public final String symbol;
        /** Function name implementing the operation e.g. "mul". */
        public final String name;

        private BinOpInfo(operator op, String symbol, String name) {
            this.op = op;
            this.symbol = symbol;
            this.name = name;
            // Must have same ordinal value to make forOp() work.
            assert this.ordinal() == op.ordinal();
        }

        /** Get the information for the given {@link operator}. */
        public static final BinOpInfo forOp(operator op) {
            return BinOpInfo.values()[op.ordinal()];
        }
    }

    /**
     * A method implementing a unary operation has this type when returned
     * by {@link #findUnaryOp(unaryop, Class)}.
     */
    protected static final MethodType UOP = Py.UOP;
    /**
     * A method implementing a binary operation has this type when returned
     * by {@link #findUnaryOp(unaryop, Class)}.
     */
    protected static final MethodType BINOP = Py.BINOP;
    /** Shorthand for <code>Object.class</code>. */
    protected static final Class<Object> O = Object.class;

    /** Handle of a method returning NotImplemented (any args). */
    protected static final MethodHandle NOT_IMPLEMENTED =
            Py.NOT_IMPLEMENTED;

    /**
     * Return a method handle for a static method in this class (the class
     * of the object instance at run-time) matching the given name and
     * type. Return {@link NOT_IMPLEMENTED} a match cannot be found. Note
     * that <code>NOT_IMPLEMENTED</code> will not generally have the type
     * required.
     *
     * @param name method name
     * @param type method type
     * @return handle to the method or NOT_IMPLEMENTED
     */
    protected MethodHandle findStatic(String name, MethodType type) {
        try {
            return lookup.findStatic(this.getClass(), name, type);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            return NOT_IMPLEMENTED;
        }
    }

    /**
     * Return the method handle for the implementation of
     * <code>op v</code>, where <code>v</code>, or {@link NOT_IMPLEMENTED}
     * if a match cannot be found within this operation handler. The
     * returned handle is capable of being cast to
     * <code>(Object)Object</code> with
     * {@link MethodHandle#asType(MethodType)}.
     *
     * @param op the binary operation to find
     * @param vClass Java class of operand
     * @return method handle for operation or NOT_IMPLEMENTED
     */
    public MethodHandle findUnaryOp(unaryop op, Class<?> vClass) {
        String name = UnaryOpInfo.forOp(op).name;
        // Look for a match with the operand class
        MethodType mt = MethodType.methodType(O, vClass);
        return findStatic(name, mt);
    }

    /**
     * Return the method handle of the implementation of
     * <code>v op w</code>, or {@link NOT_IMPLEMENTED} if a match cannot be
     * found within this operation handler. The returned handle is capable
     * of being cast to <code>(Object,Object)Object</code> with
     * {@link MethodHandle#asType(MethodType)}.
     *
     * @param vClass Java class of left operand
     * @param op operator to apply
     * @param wClass Java class of right operand
     * @return method handle for operation or NOT_IMPLEMENTED
     */
    public MethodHandle findBinOp(Class<?> vClass, operator op,
            Class<?> wClass) {
        String name = BinOpInfo.forOp(op).name;
        // Look for an exact match with the actual types
        MethodType mt = MethodType.methodType(O, vClass, wClass);
        return findStatic(name, mt);
    }

    /** The lookup rights object of the implementing class. */
    private final Lookup lookup;

    /**
     * Constructor accepting the lookup object of the concrete class
     * implementing the operations, permitting look-up of operations within
     * it. Supported operations should be static methods with the names
     * defined in {@link BinOpInfo} etc..
     *
     * @param lookup of the concrete class implementing the operations.
     */
    protected Operations(Lookup lookup) {
        this.lookup = lookup;
    }

    /**
     * Whether this type is a sub-type of the other for the purposes of
     * method resolution.
     */
    public boolean isSubtypeOf(Operations other) {
        return false;
    }
}