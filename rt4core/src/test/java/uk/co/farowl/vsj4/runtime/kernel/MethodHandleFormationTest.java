package uk.co.farowl.vsj4.runtime.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import uk.co.farowl.vsj4.runtime.PyTuple;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.TypeSpec;
import uk.co.farowl.vsj4.runtime.WithClass;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * A test of the apparatus used to form {@code MethodHandle}s from
 * special and other methods.
 */
public class MethodHandleFormationTest {

    /**
     * A test that the method handles we place in nominally empty slots,
     * do in fact raise the exception used internally to detect them.
     */
    @SuppressWarnings({"unused", "static-method"})
    @Test
    void testSlotsEmptyException() {

        // Call to handle that fills "empty" UNARY slot.
        Object v = 100;
        assertThrows(EmptyException.class, () -> { //
            Object r =
                    SpecialMethod.Signature.UNARY.empty.invokeExact(v);
        });

        // Call to handle that fills "empty" LEN slot.
        assertThrows(EmptyException.class, () -> { //
            int r = (int)SpecialMethod.Signature.LEN.empty
                    .invokeExact(v);
        });

        // Call to handle that fills "empty" PREDICATE slot.
        assertThrows(EmptyException.class, () -> { //
            boolean r = (boolean)SpecialMethod.Signature.PREDICATE.empty
                    .invokeExact(v);
        });

        // Call to handle that fills "empty" BINARY slot.
        Object w = 200;
        assertThrows(EmptyException.class, () -> { //
            Object r = SpecialMethod.Signature.BINARY.empty
                    .invokeExact(v, w);
        });

        // Call to handle that fills "empty" SETITEM slot.
        Object u = new PyTuple(v, w);
        assertThrows(EmptyException.class, new Executable() { //

            @Override
            public void execute() throws Throwable {
                Object i = 1;
                SpecialMethod.Signature.SETITEM.empty.invokeExact(u, i,
                        w);
            }
        });
    }

    /** Python object implementation for tests */
    static class BasicallyEmpty implements WithClass {

        static final PyType TYPE = PyType.fromSpec( //
                new TypeSpec("BasicallyEmpty", MethodHandles.lookup()));

        @Override
        public PyType getType() { return TYPE; }
    }

    /**
     * Test that caches for methods applicable to all objects (CPython
     * {@code tp_*} slots) accept only the right type of method handles.
     */
    @SuppressWarnings("static-method")
    @Test
    void basicObjectSlots() {
        // Type defining none of the reserved names
        final PyType basic = BasicallyEmpty.TYPE;

        assertEquals(SpecialMethod.Signature.CALL.empty, basic.op_call,
                " not empty");

        // Make method handles of the shape corresponding to caches
        final MethodHandle length = MethodHandles
                .empty(SpecialMethod.Signature.LEN.empty.type());
        final MethodHandle unary = MethodHandles
                .empty(SpecialMethod.Signature.UNARY.empty.type());
        final MethodHandle binary = MethodHandles
                .empty(SpecialMethod.Signature.BINARY.empty.type());
        final MethodHandle ternary = MethodHandles
                .empty(SpecialMethod.Signature.TERNARY.empty.type());

        // These are allowed
        SpecialMethod.op_hash.setCache(basic, length);
        SpecialMethod.op_str.setCache(basic, unary);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_str.setCache(basic, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_hash.setCache(basic, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_hash.setCache(basic, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_hash.setCache(basic, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_hash.setCache(basic, (MethodHandle)null);
        });

        // And the slots should be unaffected
        assertEquals(length, basic.op_hash, "slot modified");
        assertEquals(unary, basic.op_str, "slot modified");
    }

    /**
     * Test that caches for methods applicable to numbers (CPython
     * {@code nb_*} slots) accept only the right type of method handles.
     */
    @SuppressWarnings("static-method")
    @Test
    void numericSlots() {
        // Type defining none of the reserved names
        final PyType number = BasicallyEmpty.TYPE;

        assertEquals(SpecialMethod.Signature.UNARY.empty, number.op_neg,
                " not empty");
        assertEquals(SpecialMethod.Signature.BINARY.empty,
                number.op_add, " not empty");

        // Make method handles of the shape corresponding to caches
        final MethodHandle length = MethodHandles
                .empty(SpecialMethod.Signature.LEN.empty.type());
        final MethodHandle unary = MethodHandles
                .empty(SpecialMethod.Signature.UNARY.empty.type());
        final MethodHandle binary = MethodHandles
                .empty(SpecialMethod.Signature.BINARY.empty.type());
        final MethodHandle ternary = MethodHandles
                .empty(SpecialMethod.Signature.TERNARY.empty.type());

        // These are allowed
        SpecialMethod.op_neg.setCache(number, unary);
        SpecialMethod.op_add.setCache(number, binary);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_neg.setCache(number, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_neg.setCache(number, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_neg.setCache(number, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_neg.setCache(number, (MethodHandle)null);
        });

        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_add.setCache(number, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_add.setCache(number, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_add.setCache(number, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_add.setCache(number, (MethodHandle)null);
        });

        // And the slots should have the value set earlier
        assertEquals(unary, number.op_neg, "slot modified");
        assertEquals(binary, number.op_add, "slot modified");
    }

    /**
     * Test that caches for methods applicable to sequences (CPython
     * {@code sq_*} slots) accept only the right type of method handles.
     */
    @SuppressWarnings("static-method")
    @Test
    void sequenceSlots() {
        // Type defining none of the reserved names
        final PyType sequence = BasicallyEmpty.TYPE;

        // Make method handles of the shape corresponding to caches
        final MethodHandle length = MethodHandles
                .empty(SpecialMethod.Signature.LEN.empty.type());
        final MethodHandle unary = MethodHandles
                .empty(SpecialMethod.Signature.UNARY.empty.type());
        final MethodHandle binary = MethodHandles
                .empty(SpecialMethod.Signature.BINARY.empty.type());
        final MethodHandle ternary = MethodHandles
                .empty(SpecialMethod.Signature.TERNARY.empty.type());

        // This is allowed
        SpecialMethod.op_len.setCache(sequence, length);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_len.setCache(sequence, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_len.setCache(sequence, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_len.setCache(sequence, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_len.setCache(sequence, (MethodHandle)null);
        });

        // And the slot should be unaffected
        assertEquals(length, sequence.op_len, "slot modified");
    }

    /**
     * Test that caches for methods applicable to mappings (CPython
     * {@code mp_*} slots) accept only the right type of method handles.
     */
    @SuppressWarnings("static-method")
    @Test
    void mappingSlots() {
        // Type defining none of the reserved names
        final PyType mapping = BasicallyEmpty.TYPE;

        assertEquals(SpecialMethod.Signature.BINARY.empty,
                mapping.op_getitem, "not empty");
        assertEquals(SpecialMethod.Signature.SETITEM.empty,
                mapping.op_setitem, "not empty");

        // Make method handles of the shape corresponding to caches
        MethodHandle getitem = MethodHandles
                .empty(SpecialMethod.Signature.BINARY.empty.type());
        MethodHandle setitem = MethodHandles
                .empty(SpecialMethod.Signature.SETITEM.empty.type());
        MethodHandle bad1 = MethodHandles
                .empty(SpecialMethod.Signature.SETATTR.empty.type());
        MethodHandle bad2 = MethodHandles
                .empty(SpecialMethod.Signature.GETATTR.empty.type());

        // These are allowed
        SpecialMethod.op_getitem.setCache(mapping, getitem);
        SpecialMethod.op_setitem.setCache(mapping, setitem);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_getitem.setCache(mapping, bad1);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_getitem.setCache(mapping, bad2);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_setitem.setCache(mapping, bad2);
        });
        assertThrows(InterpreterError.class, () -> { //
            SpecialMethod.op_setitem.setCache(mapping,
                    (MethodHandle)null);
        });

        // And the slots should be unaffected
        assertEquals(getitem, mapping.op_getitem, "slot modified");
        assertEquals(setitem, mapping.op_setitem, "slot modified");
    }
}
