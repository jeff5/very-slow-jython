package uk.co.farowl.vsj2.evo2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A test illustrating a naive emulation using {@code MethodHandle} of
 * CPython's approach to type objects.
 * <p>
 * For simplicity, we use a byte code interpreter, implemented after
 * CPython's, but for just the opcodes we need.
 */
class PyByteCode2 {

    /**
     * A test that the method handles we place in nominally empty slots,
     * do in fact raise the exception used internally to detect them.
     */
    @SuppressWarnings("unused")
    @Test
    void testSlotsEmptyException() {

        // Call to handle that fills "empty" UNARY slot.
        PyObject v = new PyLong(100);
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.Signature.UNARY.empty
                    .invokeExact(v);
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
        PyObject w = new PyLong(200);
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.Signature.BINARY.empty
                    .invokeExact(v, w);
        });

        // Call to handle that fills "empty" SQ_ASSIGN slot.
        PyObject u = new PyTuple(v, w);
        assertThrows(Slot.EmptyException.class, new Executable() { //

            @Override
            public void execute() throws Throwable {
                Slot.Signature.SQ_ASSIGN.empty.invokeExact(u, 1, w);
            }
        });

        // Call to handle that fills "empty" RICHCMP slot.
        // Two PyObject argument call to "empty" slot.
        Comparison op = Comparison.LT;
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.Signature.RICHCMP.empty
                    .invokeExact(v, w, op);
        });
    }

    /**
     * Test that TP slots accept only the right type of method handles.
     */
    @Test
    void testSlotTP() {
        // Create a type defining none of the reserved names
        final PyType basic = new PyType("0Test", PyObject.class);
        assertEquals(Slot.Signature.UNARY.empty, basic.repr,
                "not EMPTY");

        // Make method handles to try
        final MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.type);
        final MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.type);
        final MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.type);
        final MethodHandle ternary =
                MethodHandles.empty(Slot.Signature.TERNARY.type);

        // These go quietly
        Slot.TP.hash.setSlot(basic, length);
        Slot.TP.str.setSlot(basic, unary);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.TP.str.setSlot(basic, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.TP.hash.setSlot(basic, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.TP.hash.setSlot(basic, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.TP.hash.setSlot(basic, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.TP.hash.setSlot(basic, null);
        });

        // And the slots should be unaffected
        assertEquals(length, basic.hash, "slot modified");
        assertEquals(unary, basic.str, "slot modified");
    }

    /**
     * Test that NB slots accept only the right type of method handles.
     */
    @Test
    void testSlotNB() {
        // Create an empty methods holder
        PyType.NumberMethods number = new PyType.NumberMethods();
        assertEquals(Slot.Signature.UNARY.empty, number.negative,
                Slot.NB.negative.name());
        assertEquals(Slot.Signature.BINARY.empty, number.add,
                Slot.NB.add.name());

        // Make method handles to try
        final MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.type);
        final MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.type);
        final MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.type);
        final MethodHandle ternary =
                MethodHandles.empty(Slot.Signature.TERNARY.type);
        // These go quietly
        Slot.NB.negative.setSlot(number, unary);
        Slot.NB.add.setSlot(number, binary);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.negative.setSlot(number, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.negative.setSlot(number, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.negative.setSlot(number, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.negative.setSlot(number, null);
        });

        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.add.setSlot(number, length);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.add.setSlot(number, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.add.setSlot(number, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.add.setSlot(number, null);
        });

        // And the slots should have the value set earlier
        assertEquals(unary, number.negative, "slot modified");
        assertEquals(binary, number.add, "slot modified");
    }

    /**
     * Test that SQ slots accept only the right type of method handles.
     */
    @Test
    void testSlotSQ() {
        // Create an empty methods holder
        final PyType.SequenceMethods sequence =
                new PyType.SequenceMethods();
        assertEquals(Slot.Signature.LEN.empty, sequence.length,
                "not empty");

        // Make method handles to try
        final MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.type);
        final MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.type);
        final MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.type);
        final MethodHandle ternary =
                MethodHandles.empty(Slot.Signature.TERNARY.type);

        // This goes quietly
        Slot.SQ.length.setSlot(sequence, length);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.SQ.length.setSlot(sequence, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.SQ.length.setSlot(sequence, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.SQ.length.setSlot(sequence, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.SQ.length.setSlot(sequence, null);
        });

        // And the slot should be unaffected
        assertEquals(length, sequence.length, "slot modified");
    }

    /**
     * Test that MP slots accept only the right type of method handles.
     */
    @Test
    void testSlotMP() {
        // Create an empty methods holder
        final PyType.MappingMethods sequence =
                new PyType.MappingMethods();
        assertEquals(Slot.Signature.LEN.empty, sequence.length,
                "not empty");

        // Make method handles to try
        MethodHandle length =
                MethodHandles.empty(Slot.Signature.LEN.type);
        MethodHandle unary =
                MethodHandles.empty(Slot.Signature.UNARY.type);
        MethodHandle binary =
                MethodHandles.empty(Slot.Signature.BINARY.type);
        MethodHandle ternary =
                MethodHandles.empty(Slot.Signature.TERNARY.type);

        // This goes quietly
        Slot.MP.length.setSlot(sequence, length);

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.MP.length.setSlot(sequence, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.MP.length.setSlot(sequence, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.MP.length.setSlot(sequence, ternary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.MP.length.setSlot(sequence, null);
        });

        // And the slot should be unaffected
        assertEquals(length, sequence.length, "slot modified");
    }

    // --------------------- Generated Tests -----------------------
    // Code generated by py_byte_code2_evo2.py
    // from py_byte_code2.ex.py

    /**
     * Example 'load_store_name': <pre>
     * a = b
     * b = 4
     * c = 6
     * </pre>
     */
    //@formatter:off
    static final PyCode LOAD_STORE_NAME =
    /*
     *   1           0 LOAD_NAME                0 (b)
     *               2 STORE_NAME               1 (a)
     *
     *   2           4 LOAD_CONST               0 (4)
     *               6 STORE_NAME               0 (b)
     *
     *   3           8 LOAD_CONST               1 (6)
     *              10 STORE_NAME               2 (c)
     *              12 LOAD_CONST               2 (None)
     *              14 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 1, 64,
        new PyBytes(new byte[] { 101, 0, 90, 1, 100, 0, 90, 0, 100,
                1, 90, 2, 100, 2, 83, 0 }),
        new PyTuple(new PyObject[] { new PyLong(4), new PyLong(6),
                Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("b"),
                new PyUnicode("a"), new PyUnicode("c") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyUnicode("load_store_name"), new PyUnicode("<module>"),
        1,
        new PyBytes(new byte[] { 4, 1, 4, 1 }));
    //@formatter:on

    @Test
    void test_load_store_name1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(1));
        globals.put(new PyUnicode("b"), new PyLong(2));
        PyCode code = LOAD_STORE_NAME;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(2), globals.get(new PyUnicode("a")),
            "a == 2");
        assertEquals(new PyLong(4), globals.get(new PyUnicode("b")),
            "b == 4");
        assertEquals(new PyLong(6), globals.get(new PyUnicode("c")),
            "c == 6");
        //@formatter:on
    }

    /**
     * Example 'negate': <pre>
     * a, b = -a, -b
     * </pre>
     */
    //@formatter:off
    static final PyCode NEGATE =
    /*
     *   1           0 LOAD_NAME                0 (a)
     *               2 UNARY_NEGATIVE
     *               4 LOAD_NAME                1 (b)
     *               6 UNARY_NEGATIVE
     *               8 ROT_TWO
     *              10 STORE_NAME               0 (a)
     *              12 STORE_NAME               1 (b)
     *              14 LOAD_CONST               0 (None)
     *              16 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 2, 64,
        new PyBytes(new byte[] { 101, 0, 11, 0, 101, 1, 11, 0, 2, 0,
                90, 0, 90, 1, 100, 0, 83, 0 }),
        new PyTuple(new PyObject[] { Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("a"),
                new PyUnicode("b") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}), new PyUnicode("negate"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] {}));
    //@formatter:on

    @Test
    void test_negate1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(6));
        globals.put(new PyUnicode("b"), new PyLong(-7));
        PyCode code = NEGATE;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(-6), globals.get(new PyUnicode("a")),
            "a == -6");
        assertEquals(new PyLong(7), globals.get(new PyUnicode("b")),
            "b == 7");
        //@formatter:on
    }

    @Test
    void test_negate2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyFloat(6.0));
        globals.put(new PyUnicode("b"), new PyFloat(-7.0));
        PyCode code = NEGATE;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyFloat(-6.0), globals.get(
            new PyUnicode("a")), "a == -6.0");
        assertEquals(new PyFloat(7.0), globals.get(
            new PyUnicode("b")), "b == 7.0");
        //@formatter:on
    }

    /**
     * Example 'binary': <pre>
     * sum = a + b
     * diff = a - b
     * prod = a * b
     * </pre>
     */
    //@formatter:off
    static final PyCode BINARY =
    /*
     *   1           0 LOAD_NAME                0 (a)
     *               2 LOAD_NAME                1 (b)
     *               4 BINARY_ADD
     *               6 STORE_NAME               2 (sum)
     *
     *   2           8 LOAD_NAME                0 (a)
     *              10 LOAD_NAME                1 (b)
     *              12 BINARY_SUBTRACT
     *              14 STORE_NAME               3 (diff)
     *
     *   3          16 LOAD_NAME                0 (a)
     *              18 LOAD_NAME                1 (b)
     *              20 BINARY_MULTIPLY
     *              22 STORE_NAME               4 (prod)
     *              24 LOAD_CONST               0 (None)
     *              26 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 2, 64,
        new PyBytes(new byte[] { 101, 0, 101, 1, 23, 0, 90, 2, 101,
                0, 101, 1, 24, 0, 90, 3, 101, 0, 101, 1, 20, 0, 90,
                4, 100, 0, 83, 0 }),
        new PyTuple(new PyObject[] { Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("a"),
                new PyUnicode("b"), new PyUnicode("sum"),
                new PyUnicode("diff"), new PyUnicode("prod") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}), new PyUnicode("binary"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 8, 1, 8, 1 }));
    //@formatter:on

    @Test
    void test_binary1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(7));
        globals.put(new PyUnicode("b"), new PyLong(6));
        PyCode code = BINARY;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(13), globals.get(
            new PyUnicode("sum")), "sum == 13");
        assertEquals(new PyLong(1), globals.get(
            new PyUnicode("diff")), "diff == 1");
        assertEquals(new PyLong(42), globals.get(
            new PyUnicode("prod")), "prod == 42");
        //@formatter:on
    }

    @Test
    void test_binary2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyFloat(7.0));
        globals.put(new PyUnicode("b"), new PyFloat(6.0));
        PyCode code = BINARY;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyFloat(13.0), globals.get(
            new PyUnicode("sum")), "sum == 13.0");
        assertEquals(new PyFloat(1.0), globals.get(
            new PyUnicode("diff")), "diff == 1.0");
        assertEquals(new PyFloat(42.0), globals.get(
            new PyUnicode("prod")), "prod == 42.0");
        //@formatter:on
    }

    @Test
    void test_binary3() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyFloat(7.0));
        globals.put(new PyUnicode("b"), new PyLong(6));
        PyCode code = BINARY;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyFloat(13.0), globals.get(
            new PyUnicode("sum")), "sum == 13.0");
        assertEquals(new PyFloat(1.0), globals.get(
            new PyUnicode("diff")), "diff == 1.0");
        assertEquals(new PyFloat(42.0), globals.get(
            new PyUnicode("prod")), "prod == 42.0");
        //@formatter:on
    }

    @Test
    void test_binary4() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(7));
        globals.put(new PyUnicode("b"), new PyFloat(6.0));
        PyCode code = BINARY;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyFloat(13.0), globals.get(
            new PyUnicode("sum")), "sum == 13.0");
        assertEquals(new PyFloat(1.0), globals.get(
            new PyUnicode("diff")), "diff == 1.0");
        assertEquals(new PyFloat(42.0), globals.get(
            new PyUnicode("prod")), "prod == 42.0");
        //@formatter:on
    }

}
