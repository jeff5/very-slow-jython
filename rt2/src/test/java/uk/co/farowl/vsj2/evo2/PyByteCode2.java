package uk.co.farowl.vsj2.evo2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

/**
 * A test illustrating a naive emulation using {@code MethodHandle} of
 * CPython's approach to type objects.
 * <p>
 * For simplicity, we use a byte code interpreter, implemented after
 * CPython's, but for just the opcodes we need.
 */
class PyByteCode2 {
    /**
     * A test that the method handles we place in nominally empty slots, do
     * in fact raise the exception used internally to detect them.
     */
    @Test
    void testSlotsEmptyException() {
        // Zero argument call to "empty" slot.
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.EMPTY.invokeExact();
        });
        // Single PyObject argument call to "empty" slot.
        PyObject v = new PyLong(100);
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.UNARY_EMPTY.invokeExact(v);
        });
        // Two PyObject argument call to "empty" slot.
        PyObject w = new PyLong(200);
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r = (PyObject) Slot.BINARY_EMPTY.invokeExact(v, w);
        });
        // Three PyObject argument call to "empty" slot.
        PyObject u = new PyLong(1);
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r =
                    (PyObject) Slot.TERNARY_EMPTY.invokeExact(u, v, w);
        });
        // Two PyObject argument call to "empty" slot.
        Opcode.PyCmp op = Opcode.PyCmp.LT;
        assertThrows(Slot.EmptyException.class, () -> { //
            PyObject r =
                    (PyObject) Slot.RICHCMP_EMPTY.invokeExact(v, w, op);
        });
    }

    /** Test that TP slots accept only the right type of method handles. */
    @Test
    void testSlotTP() {
        // Create a type defining none of the reserved names
        final PyType basic = new PyType("0Test", PyObject.class);
        assertEquals(Slot.UNARY_EMPTY, basic.repr, "not EMPTY");

        // Make method handles to try
        MethodHandle unary = MethodHandles.empty(Slot.UNARY);
        MethodHandle binary = MethodHandles.empty(Slot.BINARY);
        MethodHandle ternary = MethodHandles.empty(Slot.TERNARY);

        // These go quietly
        Slot.TP.hash.setSlot(basic, unary);
        Slot.TP.str.setSlot(basic, unary);
        // Re-type the good method handles as unacceptable types
        final MethodHandle unary2 = unary
                .asType(MethodType.methodType(Float.class, Integer.class));
        final MethodHandle binary2 = binary.asType(MethodType
                .methodType(Float.class, String.class, Byte.class));
        final MethodHandle ternary2 =
                ternary.asType(MethodType.methodType(PyObject.class,
                        Float.class, String.class, Byte.class));

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.TP.hash.setSlot(basic, unary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.TP.hash.setSlot(basic, binary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.TP.hash.setSlot(basic, ternary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.TP.hash.setSlot(basic, null);
        });

        // And the slot should be unaffected
        assertEquals(unary, basic.hash, "slot modified");
    }

    /** Test that NB slots accept only the right type of method handles. */
    @Test
    void testSlotNB() {
        // Create an empty methods holder
        PyType.NumberMethods number = new PyType.NumberMethods();
        assertEquals(Slot.UNARY_EMPTY, number.negative,
                Slot.NB.negative.name());
        assertEquals(Slot.BINARY_EMPTY, number.add, Slot.NB.add.name());

        // Make method handles to try
        final MethodHandle unary = MethodHandles.empty(Slot.UNARY);
        final MethodHandle binary = MethodHandles.empty(Slot.BINARY);
        final MethodHandle ternary = MethodHandles.empty(Slot.TERNARY);
        // These go quietly
        Slot.NB.negative.setSlot(number, unary);
        Slot.NB.add.setSlot(number, binary);

        // Re-type the good method handles as unacceptable types
        final MethodHandle unary2 = unary
                .asType(MethodType.methodType(Float.class, Integer.class));
        final MethodHandle binary2 = binary.asType(MethodType
                .methodType(Float.class, String.class, Byte.class));
        final MethodHandle ternary2 =
                ternary.asType(MethodType.methodType(PyObject.class,
                        Float.class, String.class, Byte.class));

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.negative.setSlot(number, unary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.negative.setSlot(number, binary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.negative.setSlot(number, ternary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.negative.setSlot(number, binary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.negative.setSlot(number, null);
        });

        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.add.setSlot(number, unary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.add.setSlot(number, binary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.add.setSlot(number, ternary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.add.setSlot(number, unary);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.NB.add.setSlot(number, null);
        });

        // And the slots should have the value set earlier
        assertEquals(unary, number.negative, "slot modified");
        assertEquals(binary, number.add, "slot modified");
    }

    /** Test that SQ slots accept only the right type of method handles. */
    @Test
    void testSlotSQ() {
        // Create an empty methods holder
        final PyType.SequenceMethods sequence =
                new PyType.SequenceMethods();
        assertEquals(Slot.UNARY_EMPTY, sequence.length, "not EMPTY");

        // Make method handles to try
        MethodHandle unary = MethodHandles.empty(Slot.UNARY);
        MethodHandle binary = MethodHandles.empty(Slot.BINARY);
        MethodHandle ternary = MethodHandles.empty(Slot.TERNARY);

        // These go quietly
        Slot.SQ.length.setSlot(sequence, unary);
        // Re-type the good method handles as unacceptable types
        final MethodHandle unary2 = unary
                .asType(MethodType.methodType(Float.class, Integer.class));
        final MethodHandle binary2 = binary.asType(MethodType
                .methodType(Float.class, String.class, Byte.class));
        final MethodHandle ternary2 =
                ternary.asType(MethodType.methodType(PyObject.class,
                        Float.class, String.class, Byte.class));

        // These should be prevented
        assertThrows(InterpreterError.class, () -> { //
            Slot.SQ.length.setSlot(sequence, unary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.SQ.length.setSlot(sequence, binary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.SQ.length.setSlot(sequence, ternary2);
        });
        assertThrows(InterpreterError.class, () -> { //
            Slot.SQ.length.setSlot(sequence, null);
        });

        // And the slot should be unaffected
        assertEquals(unary, sequence.length, "slot modified");
    }

    // --------------------- Generated Tests -----------------------
    // Code generated by py_byte_code2.py

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
