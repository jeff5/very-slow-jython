// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.kernel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.core.ArgumentError;
import uk.co.farowl.vsj4.kernel.SpecialMethod.Signature;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.types.TypeSpec;

/**
 * A table of binary operations that may be indexed by (unreflected)
 * operation and operand classes.
 *
 * There is one instance in the type system, supporting all types.
 * <p>
 * Binary operations, at the same time as appearing as the {@code op}
 * and {@code rop} slots, meaning for example
 * {@link Representation#op_add} and {@link Representation#op_radd}, are
 * optionally given implementations specialised for the Java classes of
 * their operands. The {@code BinopLookup} stores a mapping to the
 * method handle that is the shortest route to invocation. We only use
 * this optimisation for built-in numeric types.
 */
public class BinopTable {

    /** Logger while building the binary operations lookup. */
    final Logger logger = LoggerFactory.getLogger(BinopTable.class);

    /** Uses as the key when looking up entries. */
    record Key(SpecialMethod op, Class<?> vc, Class<?> wc) {
        // hashCode() and equals() implicitly defined.
    }

    /** The cache. */
    private final Map<Key, MethodHandle> map;

    /**
     * 14 binary ops, (*not* their reflections), 6 comparisons, 10
     * in-place ops (maybe). Times approximately 10 classes of each
     * operand.
     */
    private static final int OP_COUNT = 14 + 6 + 10;
    /** Approximately 10 classes of each operand. */
    private static final int CLASS_COUNT = 10;

    /** Construct a mapping for a particular {@link Representation}. */
    BinopTable() {
        /*
         * We store at most one handle for each (forward) binary
         * operation type and every pair of the classes representing
         * types that take advantage of the binop table.
         */
        final int ENTRIES = OP_COUNT * CLASS_COUNT * CLASS_COUNT;
        /*
         * Explicitly control collisions. We can afford to waste space
         * as ENTRIES is not big and there is only one instance of the
         * table.
         */
        final float LOAD_FACTOR = 0.2f;
        /*
         * Concurrency is not an issue since the table is filled once
         * under type factory lock after which we only read.
         */
        final int SIZE = Math.round(ENTRIES / LOAD_FACTOR);
        logger.atInfo().setMessage(
                "initialised at size={} for {}*{}*{}={} entries")
                .addArgument(SIZE).addArgument(OP_COUNT)
                .addArgument(CLASS_COUNT).addArgument(CLASS_COUNT)
                .addArgument(ENTRIES).log();
        this.map = new HashMap<>(SIZE, LOAD_FACTOR);
    }

    /**
     * Get the method handle of an implementation
     * {@code Object op(V v, W w)} specialised to the given classes,
     * where the owning {@link Representation} is registered for the
     * class {@code V}, and {@code W} is an acceptable "other" argument
     * class. , the return will be a handle on an implementation of
     * {@code op} matching those classes. If no implementation is
     * available for those classes (which means they are not
     * representation and accepted types for the Python type) an empty
     * slot handle is returned.
     *
     * @param op of the binary operation
     */
    public MethodHandle get(SpecialMethod op, Class<?> vc,
            Class<?> wc) {
        assert op.signature == Signature.BINARY;
        return map.get(new Key(op, vc, wc));
    }

    /**
     * Add an entry for a handle for the given operation and argument
     * classes.
     *
     * @param op of the binary operation
     */
    MethodHandle put(SpecialMethod op, Class<?> vc, Class<?> wc,
            MethodHandle mh) {
        assert op.signature == Signature.BINARY;
        assert op.reflected != null;
        Key k = new Key(op, vc, wc);
        return map.put(k, mh);
    }

    /**
     * Add an entry for a handle for the given operation and argument
     * classes.
     *
     * @param op of the binary operation
     * @param mh of the offered implementation
     * @throws ArgumentError when {@code mh} is not a binary operation
     */
    MethodHandle put(SpecialMethod op, MethodHandle mh)
            throws ArgumentError {
        MethodType mt = mh.type();
        if (mt.parameterCount() == 2
                && mt.returnType() == Object.class) {
            Class<?> vc = mt.parameterType(0);
            Class<?> wc = mt.parameterType(1);
            return put(op, vc, wc, mh);
        } else {
            throw new ArgumentError(mt.parameterCount());
        }
    }

    /**
     * Add {@code MethodHandle}s from binary operations defined for the
     * given class, on behalf of the type given. This table is
     * 3-dimensional, being indexed by the slot of the method being
     * defined, which must be a binary operation, and the two of the
     * operand classes in the type. These handles are used privately by
     * the type to create call sites. Although the process of creating
     * them is similar to making wrapper descriptors, these structures
     * do not become exposed as descriptors.
     *
     * @param spec for the type being defined
     * @param type to which these descriptors apply
     * @throws InterpreterError on duplicates or unsupported types
     */
    void addFromSpec(BaseType type, TypeSpec spec) {
        Class<?> binops = spec.getBinopClass();
        Lookup lookup = spec.getLookup();
        for (Method m : binops.getDeclaredMethods()) {
            // If it is a special method, record the definition.
            String name = m.getName();
            SpecialMethod sm = SpecialMethod.forMethodName(name);
            if (sm != null) {
                // TODO add equivalent forward binops to __radd__ etc.
                /*
                 * We look binary operations up only by their forward
                 * special method (e.g. op_add, never op_radd). So we
                 * *should* generate __add__(int, Double) and save it
                 * under op_add.
                 */
                if (sm.signature == SpecialMethod.Signature.BINARY) {
                    // Convert to unreflected for (op_radd to op_add)
                    if (sm.isreflected) { sm = sm.unreflected(); }
                    binopTableAdd(sm, m, lookup, binops, type);
                }
            }
        }
    }

    /**
     * Add a method handle to the table, verifying that the method type
     * produced is compatible with the {@link #slot}.
     *
     * @param defs the method table to add to
     * @param sm being matched
     * @param m implementing method
     * @param lookup authorisation to access fields
     * @param binops class defining class-specific binary operations
     * @param type to which these belong
     */
    private void binopTableAdd(SpecialMethod sm, Method m,
            Lookup lookup, Class<?> binops, BaseType type) {
        try {
            // Convert the method to a handle
            MethodHandle mh = lookup.unreflect(m);
            logger.atTrace()
                    .setMessage(() -> String.format(
                            " - Add optimised %s.%s for %s",
                            type.getName(), sm.methodName, mh.type()))
                    .log();
            put(sm, mh);
        } catch (ArgumentError ae) {
            throw new InterpreterError("%s does not implement %s.%s", m,
                    type.getName(), sm.methodName);
        } catch (IllegalAccessException | WrongMethodTypeException e) {
            throw new InterpreterError(e,
                    "ill-formed or inaccessible binary op '%s'", m);
        }
    }
}
