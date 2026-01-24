// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.kernel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * Used as the key when looking up entries, internally and for
     * tests.
     */
    record Key(SpecialMethod op, Class<?> vc, Class<?> wc) {

        @Override
        public String toString() {
            return String.format("%s(%s, %s)", op.methodName,
                    vc.getSimpleName(), wc.getSimpleName());
        }
        // hashCode() and equals() implicitly defined.

    }

    /** The cache. */
    private final Map<Key, MethodHandle> map;

    /*
     * For each type, there are N*(A+B)**2-B**2) = N*A*(A+2*B) entries,
     * where N as the number of binary operations specialised and A the
     * number of classes accepted as self and B is the number of classes
     * that are other operand only.
     */

    // int: 10 operations on 4 classes and no other operands.
    private static final int INT_OPS = 10;
    private static final int INT_ACCEPTED = 4;
    private static final int INT_OPERAND = 0;
    private static final int INT_ENTRIES =
            INT_OPS * INT_ACCEPTED * (INT_ACCEPTED + 2 * INT_OPERAND);
    // float: 4 operations on 2 accepted classes plus the ints.
    private static final int FLOAT_OPS = 4;
    private static final int FLOAT_ACCEPTED = 2;
    private static final int FLOAT_OPERAND = INT_ACCEPTED;
    private static final int FLOAT_ENTRIES = FLOAT_OPS * FLOAT_ACCEPTED
            * (FLOAT_ACCEPTED + 2 * FLOAT_OPERAND);
    // TODO BinopTable for complex, str, bytes, bytearray?

    /**
     * The minimum number of entries we expect to need in the table
     * where we look up binary operations with
     * {@link #get(SpecialMethod, Class, Class)}. We aim, in the
     * constructor, to allocate a table that is big enough never re-hash
     * during start-up, and is always sparse.
     */
    static final int ENTRIES = INT_ENTRIES + FLOAT_ENTRIES;

    /**
     * We explicitly control collisions by choosing a low load factor.
     * (We can afford to trade space for speed as ENTRIES is not big and
     * there is only one instance of the table.)
     */
    static final float LOAD_FACTOR = 0.2f;

    /** Construct a mapping for a particular {@link Representation}. */
    BinopTable() {
        /*
         * Concurrency is not an issue since the table is filled once
         * under type factory lock after which we only ever read.
         */
        final int SIZE = Math.round(0.5f + ENTRIES / LOAD_FACTOR);
        logger.atInfo().setMessage("Table size={} for {} entries")
                .addArgument(SIZE).addArgument(ENTRIES).log();
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
     * @param op the binary operation
     * @param vc class of left argument
     * @param wc class of right argument
     * @return the offered implementation
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
     * @param op the binary operation
     * @param vc class of left argument
     * @param wc class of right argument
     * @param mh of the offered implementation
     * @return the previous method handle (normally {@code null})
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
     * classes. We return the previous method handle. If this is not
     * {@code null}, we are replacing, which is probably an error.
     *
     * @param op the binary operation
     * @param mh of the offered implementation
     * @return the previous method handle (normally {@code null})
     * @throws WrongMethodTypeException when {@code mh} is not a binary
     *     operation
     */
    MethodHandle put(SpecialMethod op, MethodHandle mh)
            throws WrongMethodTypeException {
        // Actual classes from method signature
        MethodType mt = mh.type();
        // Cast operands from Object (static check at this point).
        mh = mh.asType(op.getType());
        Class<?> vc = mt.parameterType(0);
        Class<?> wc = mt.parameterType(1);
        return put(op, vc, wc, mh);
    }

    /**
     * Add {@code MethodHandle}s from binary operations defined for the
     * given class, on behalf of the type given. This table is
     * 3-dimensional, being indexed by the {@link SpecialMethod} being
     * defined, which must be a forward binary operation, and the two
     * operand classes. These handles are used privately by to create
     * call sites. Although the process of creating them is similar to
     * making wrapper descriptors, they are not exposed as descriptors,
     * rather provide an alternative path in selected cases.
     *
     * @param spec for the type being defined
     * @param type to which these descriptors apply
     * @throws InterpreterError on duplicates or unsupported types
     */
    void addFromSpec(BaseType type, TypeSpec spec) {
        Class<?> binops = spec.getBinopClass();
        Lookup lookup = spec.getLookup();
        final SpecialMethod.Signature BINARY =
                SpecialMethod.Signature.BINARY;
        for (Method m : binops.getDeclaredMethods()) {
            // If it is a special method, record the definition.
            String name = m.getName();
            SpecialMethod op = SpecialMethod.forMethodName(name);
            if (op != null && op.signature == BINARY) {
                /*
                 * We look binary operations up only by the forward
                 * special method (e.g. op_add, never op_radd).
                 */
                if (!op.isreflected) {
                    binopTableAdd(op, m, lookup, binops, type);
                }
            }
        }
    }

    /**
     * Add a method handle to the table, derived from the given method,
     * verifying that the method type produced is compatible with the
     * {@link #op} it claims to implement.
     *
     * @param defs the method table to add to
     * @param op being defined
     * @param method implementing method
     * @param lookup authorisation to access the method
     * @param binops class defining class-specific binary operations
     * @param type to which these belong
     */
    private void binopTableAdd(SpecialMethod op, Method method,
            Lookup lookup, Class<?> binops, BaseType type) {
        try {
            // Convert the method to a handle
            MethodHandle mh = lookup.unreflect(method);
            logger.atTrace()
                    .setMessage(() -> String.format(
                            " - Add optimised %s.%s for %s",
                            type.getName(), op.methodName, mh.type()))
                    .log();
            put(op, mh);
        } catch (IllegalAccessException | WrongMethodTypeException e) {
            throw new InterpreterError("%s does not implement %s.%s",
                    method, type.getName(), op.methodName);
        }
    }

    /**
     * Report number of entries in the lookup, that is, the cumulative
     * number of handles we have saved to this {@link BinopTable}.
     *
     * @return number of handles saved
     */
    public int size() { return map.size(); }

    /**
     * Return the entries in the lookup, that is, the mappings from
     * co-ordinates {@code (op,vc,wc)} to {@code mh} we have saved to
     * this {@link BinopTable}. We use this only in tests.
     *
     * @return view of the contents
     */
    public Set<Entry<Key, MethodHandle>> entries() {
        return Collections.unmodifiableSet(map.entrySet());
    }
}
