package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import uk.co.farowl.vsj3.evo1.PyType.Spec;

/**
 * A test of the apparatus used to form {@code MethodHandle}s from
 * special and other methods.
 */
public class MethodHandleFormationTest {

    /**
     * A test that the method handles we place in nominally empty slots,
     * do in fact raise the exception used internally to detect them.
     */
    @SuppressWarnings("unused")
    @Test
    void testSlotsEmptyException() {

        // Call to handle that fills "empty" UNARY slot.
        Object v = 100;
        assertThrows(Slot.EmptyException.class, () -> { //
            Object r = Slot.Signature.UNARY.empty.invokeExact(v);
        });

        // Call to handle that fills "empty" LEN slot.
        assertThrows(Slot.EmptyException.class, () -> { //
            int r = (int) Slot.Signature.LEN.empty.invokeExact(v);
        });

        // Call to handle that fills "empty" PREDICATE slot.
        assertThrows(Slot.EmptyException.class, () -> { //
            boolean r = (boolean) Slot.Signature.PREDICATE.empty
                    .invokeExact(v);
        });

        // Call to handle that fills "empty" BINARY slot.
        Object w = 200;
        assertThrows(Slot.EmptyException.class, () -> { //
            Object r = Slot.Signature.BINARY.empty.invokeExact(v, w);
        });

        // Call to handle that fills "empty" SETITEM slot.
        Object u = new PyTuple(v, w);
        assertThrows(Slot.EmptyException.class, new Executable() { //

            @Override
            public void execute() throws Throwable {
                Object i = 1;
                Slot.Signature.SETITEM.empty.invokeExact(u, i, w);
            }
        });
    }

    /** Python object implementation for tests */
    static class BasicallyEmpty implements CraftedPyObject {

        static final PyType TYPE = PyType.fromSpec( //
                new Spec("BasicallyEmpty", MethodHandles.lookup()));

        @Override
        public PyType getType() { return TYPE; }
    }

    /**
     * Test that slots applicable to all objects (CPython {@code tp_}
     * slots) accept only the right type of method handles.
     */
    @Test
    void basicObjectSlots() {
        // Type defining none of the reserved names
        final PyType basic = BasicallyEmpty.TYPE;

        assertEquals(Slot.Signature.CALL.empty, basic.op_call,
                "not EMPTY");

        // Make method handles to try
        final MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.empty.type());
        final MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.empty.type());
        final MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.empty.type());
        final MethodHandle ternary = MethodHandles
                .empty(Slot.Signature.TERNARY.empty.type());

        // These go quietly
        Slot.op_hash.setSlot(basic, length);
        Slot.op_str.setSlot(basic, unary);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_str.setSlot(basic, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_hash.setSlot(basic, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_hash.setSlot(basic, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_hash.setSlot(basic, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_hash.setSlot(basic, (MethodHandle) null);
        });

        // And the slots should be unaffected
        assertEquals(length, basic.op_hash, "slot modified");
        assertEquals(unary, basic.op_str, "slot modified");
    }

    /**
     * Test that slots applicable to numbers (CPython {@code nb_} slots)
     * accept only the right type of method handles.
     */
    @Test
    void numericSlots() {
        // Type defining none of the reserved names
        final PyType number = BasicallyEmpty.TYPE;

        assertEquals(Slot.Signature.UNARY.empty, number.op_neg,
                Slot.op_neg.name());
        assertEquals(Slot.Signature.BINARY.empty, number.op_add,
                Slot.op_add.name());

        // Make method handles to try
        final MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.empty.type());
        final MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.empty.type());
        final MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.empty.type());
        final MethodHandle ternary = MethodHandles
                .empty(Slot.Signature.TERNARY.empty.type());
        // These go quietly
        Slot.op_neg.setSlot(number, unary);
        Slot.op_add.setSlot(number, binary);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_neg.setSlot(number, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_neg.setSlot(number, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_neg.setSlot(number, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_neg.setSlot(number, (MethodHandle) null);
        });

        assertThrows(InterpreterError.class, () -> { //
            Slot.op_add.setSlot(number, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_add.setSlot(number, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_add.setSlot(number, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_add.setSlot(number, (MethodHandle) null);
        });

        // And the slots should have the value set earlier
        assertEquals(unary, number.op_neg, "slot modified");
        assertEquals(binary, number.op_add, "slot modified");
    }

    /**
     * Test that slots applicable to sequences (CPython {@code sq_}
     * slots) accept only the right type of method handles.
     */
    @Test
    void sequenceSlots() {
        // Type defining none of the reserved names
        final PyType sequence = BasicallyEmpty.TYPE;

        // Make method handles to try
        final MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.empty.type());
        final MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.empty.type());
        final MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.empty.type());
        final MethodHandle ternary = MethodHandles
                .empty(Slot.Signature.TERNARY.empty.type());

        // This goes quietly
        Slot.op_len.setSlot(sequence, length);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_len.setSlot(sequence, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_len.setSlot(sequence, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_len.setSlot(sequence, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_len.setSlot(sequence, (MethodHandle) null);
        });

        // And the slot should be unaffected
        assertEquals(length, sequence.op_len, "slot modified");
    }

    /**
     * Test that slots applicable to mappings (CPython {@code mp_}
     * slots) accept only the right type of method handles.
     */
    @Test
    void mappingSlots() {
        // Create a type defining none of the reserved names
        final PyType mapping = PyType.fromSpec(new Spec( //
                "3Test", MethodHandles.lookup()));
        assertEquals(Slot.Signature.BINARY.empty, mapping.op_getitem,
                "not empty");
        assertEquals(Slot.Signature.SETITEM.empty, mapping.op_setitem,
                "not empty");

        // Make method handles to try
        MethodHandle getitem =
                MethodHandles.empty(Slot.Signature.BINARY.empty.type());
        MethodHandle setitem = MethodHandles
                .empty(Slot.Signature.SETITEM.empty.type());
        MethodHandle bad1 = MethodHandles
                .empty(Slot.Signature.SETATTR.empty.type());
        MethodHandle bad2 = MethodHandles
                .empty(Slot.Signature.GETATTR.empty.type());

        // These go quietly
        Slot.op_getitem.setSlot(mapping, getitem);
        Slot.op_setitem.setSlot(mapping, setitem);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_getitem.setSlot(mapping, bad1);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_getitem.setSlot(mapping, bad2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_setitem.setSlot(mapping, bad2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.op_setitem.setSlot(mapping, (MethodHandle) null);
        });

        // And the slots should be unaffected
        assertEquals(getitem, mapping.op_getitem, "slot modified");
        assertEquals(setitem, mapping.op_setitem, "slot modified");
    }

}
