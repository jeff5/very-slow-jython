package uk.co.farowl.vsj2.evo2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Continues the test illustrating a naive emulation using
 * {@code MethodHandle} of CPython's approach to type objects. In
 * {@link PyByteCode2} we demonstrated a type system capable of
 * co-ordinating unary and binary operations. We now extend this (using
 * the same classes) to a simple loops over a sequence.
 */
class PyByteCode3 {

    // --------------------- Generated Tests -----------------------
    // Code generated by py_byte_code3_evo2.py
    // from py_byte_code3.ex.py

    /**
     * Example 'tuple_index': <pre>
     * d = (20, "hello", c)
     * b = d[1]
     * c = d[2] + d[0]
     * </pre>
     */
    //@formatter:off
    static final PyCode TUPLE_INDEX =
    /*
     *   1           0 LOAD_CONST               0 (20)
     *               2 LOAD_CONST               1 ('hello')
     *               4 LOAD_NAME                0 (c)
     *               6 BUILD_TUPLE              3
     *               8 STORE_NAME               1 (d)
     *
     *   2          10 LOAD_NAME                1 (d)
     *              12 LOAD_CONST               2 (1)
     *              14 BINARY_SUBSCR
     *              16 STORE_NAME               2 (b)
     *
     *   3          18 LOAD_NAME                1 (d)
     *              20 LOAD_CONST               3 (2)
     *              22 BINARY_SUBSCR
     *              24 LOAD_NAME                1 (d)
     *              26 LOAD_CONST               4 (0)
     *              28 BINARY_SUBSCR
     *              30 BINARY_ADD
     *              32 STORE_NAME               0 (c)
     *              34 LOAD_CONST               5 (None)
     *              36 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 3, 64,
        new PyBytes(new byte[] { 100, 0, 100, 1, 101, 0, 102, 3, 90,
                1, 101, 1, 100, 2, 25, 0, 90, 2, 101, 1, 100, 3, 25,
                0, 101, 1, 100, 4, 25, 0, 23, 0, 90, 0, 100, 5, 83,
                0 }),
        new PyTuple(new PyObject[] { new PyLong(20),
                new PyUnicode("hello"), new PyLong(1), new PyLong(2),
                new PyLong(0), Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("c"),
                new PyUnicode("d"), new PyUnicode("b") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}), new PyUnicode("tuple_index"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 10, 1, 8, 1 }));
    //@formatter:on

    @Test
    void test_tuple_index1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("c"), new PyFloat(22.0));
        PyCode code = TUPLE_INDEX;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyUnicode("hello"), globals.get(
            new PyUnicode("b")), "b == 'hello'");
        assertEquals(new PyFloat(42.0), globals.get(
            new PyUnicode("c")), "c == 42.0");
        //@formatter:on
    }

    /**
     * Example 'tuple_dot_product': <pre>
     * sum = a[0] * b[0]
     * i = 1
     * while i < n:
     *     sum = sum + a[i] * b[i]
     *     i = i + 1
     * </pre>
     */
    //@formatter:off
    static final PyCode TUPLE_DOT_PRODUCT =
    /*
     *   1           0 LOAD_NAME                0 (a)
     *               2 LOAD_CONST               0 (0)
     *               4 BINARY_SUBSCR
     *               6 LOAD_NAME                1 (b)
     *               8 LOAD_CONST               0 (0)
     *              10 BINARY_SUBSCR
     *              12 BINARY_MULTIPLY
     *              14 STORE_NAME               2 (sum)
     *
     *   2          16 LOAD_CONST               1 (1)
     *              18 STORE_NAME               3 (i)
     *
     *   3     >>   20 LOAD_NAME                3 (i)
     *              22 LOAD_NAME                4 (n)
     *              24 COMPARE_OP               0 (<)
     *              26 POP_JUMP_IF_FALSE       58
     *
     *   4          28 LOAD_NAME                2 (sum)
     *              30 LOAD_NAME                0 (a)
     *              32 LOAD_NAME                3 (i)
     *              34 BINARY_SUBSCR
     *              36 LOAD_NAME                1 (b)
     *              38 LOAD_NAME                3 (i)
     *              40 BINARY_SUBSCR
     *              42 BINARY_MULTIPLY
     *              44 BINARY_ADD
     *              46 STORE_NAME               2 (sum)
     *
     *   5          48 LOAD_NAME                3 (i)
     *              50 LOAD_CONST               1 (1)
     *              52 BINARY_ADD
     *              54 STORE_NAME               3 (i)
     *              56 JUMP_ABSOLUTE           20
     *         >>   58 LOAD_CONST               2 (None)
     *              60 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 4, 64,
        new PyBytes(new byte[] { 101, 0, 100, 0, 25, 0, 101, 1, 100,
                0, 25, 0, 20, 0, 90, 2, 100, 1, 90, 3, 101, 3, 101,
                4, 107, 0, 114, 58, 101, 2, 101, 0, 101, 3, 25, 0,
                101, 1, 101, 3, 25, 0, 20, 0, 23, 0, 90, 2, 101, 3,
                100, 1, 23, 0, 90, 3, 113, 20, 100, 2, 83, 0 }),
        new PyTuple(new PyObject[] { new PyLong(0), new PyLong(1),
                Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("a"),
                new PyUnicode("b"), new PyUnicode("sum"),
                new PyUnicode("i"), new PyUnicode("n") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyUnicode("tuple_dot_product"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 16, 1, 4, 1, 8, 1, 20, 1 }));
    //@formatter:on

    @Test
    void test_tuple_dot_product1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"),
        new PyTuple(new PyObject[] { new PyLong(2), new PyLong(3),
                new PyLong(4) }));
        globals.put(new PyUnicode("b"),
        new PyTuple(new PyObject[] { new PyLong(3), new PyLong(4),
                new PyLong(6) }));
        globals.put(new PyUnicode("n"), new PyLong(3));
        PyCode code = TUPLE_DOT_PRODUCT;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(42), globals.get(
            new PyUnicode("sum")), "sum == 42");
        //@formatter:on
    }

    @Test
    void test_tuple_dot_product2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"),
        new PyTuple(new PyObject[] { new PyFloat(1.0),
                new PyFloat(2.0), new PyFloat(3.0),
                new PyFloat(4.0) }));
        globals.put(new PyUnicode("b"),
        new PyTuple(new PyObject[] { new PyFloat(4.0),
                new PyFloat(3.0), new PyFloat(4.0),
                new PyFloat(5.0) }));
        globals.put(new PyUnicode("n"), new PyLong(4));
        PyCode code = TUPLE_DOT_PRODUCT;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyFloat(42.0), globals.get(
            new PyUnicode("sum")), "sum == 42.0");
        //@formatter:on
    }

    /**
     * Example 'list_index': <pre>
     * d = [20, "hello", c]
     * a = d[0]
     * b = d[1]
     * d[2] = a + c
     * c = d[2]
     * </pre>
     */
    //@formatter:off
    static final PyCode LIST_INDEX =
    /*
     *   1           0 LOAD_CONST               0 (20)
     *               2 LOAD_CONST               1 ('hello')
     *               4 LOAD_NAME                0 (c)
     *               6 BUILD_LIST               3
     *               8 STORE_NAME               1 (d)
     *
     *   2          10 LOAD_NAME                1 (d)
     *              12 LOAD_CONST               2 (0)
     *              14 BINARY_SUBSCR
     *              16 STORE_NAME               2 (a)
     *
     *   3          18 LOAD_NAME                1 (d)
     *              20 LOAD_CONST               3 (1)
     *              22 BINARY_SUBSCR
     *              24 STORE_NAME               3 (b)
     *
     *   4          26 LOAD_NAME                2 (a)
     *              28 LOAD_NAME                0 (c)
     *              30 BINARY_ADD
     *              32 LOAD_NAME                1 (d)
     *              34 LOAD_CONST               4 (2)
     *              36 STORE_SUBSCR
     *
     *   5          38 LOAD_NAME                1 (d)
     *              40 LOAD_CONST               4 (2)
     *              42 BINARY_SUBSCR
     *              44 STORE_NAME               0 (c)
     *              46 LOAD_CONST               5 (None)
     *              48 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 3, 64,
        new PyBytes(new byte[] { 100, 0, 100, 1, 101, 0, 103, 3, 90,
                1, 101, 1, 100, 2, 25, 0, 90, 2, 101, 1, 100, 3, 25,
                0, 90, 3, 101, 2, 101, 0, 23, 0, 101, 1, 100, 4, 60,
                0, 101, 1, 100, 4, 25, 0, 90, 0, 100, 5, 83, 0 }),
        new PyTuple(new PyObject[] { new PyLong(20),
                new PyUnicode("hello"), new PyLong(0), new PyLong(1),
                new PyLong(2), Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("c"),
                new PyUnicode("d"), new PyUnicode("a"),
                new PyUnicode("b") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}), new PyUnicode("list_index"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 10, 1, 8, 1, 8, 1, 12, 1 }));
    //@formatter:on

    @Test
    void test_list_index1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("c"), new PyFloat(22.0));
        PyCode code = LIST_INDEX;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(20), globals.get(new PyUnicode("a")),
            "a == 20");
        assertEquals(new PyUnicode("hello"), globals.get(
            new PyUnicode("b")), "b == 'hello'");
        assertEquals(new PyFloat(42.0), globals.get(
            new PyUnicode("c")), "c == 42.0");
        //@formatter:on
    }

    /**
     * Example 'boolean_arithmetic': <pre>
     * a = u + t
     * b = u * t
     * c = u * f
     * </pre>
     */
    //@formatter:off
    static final PyCode BOOLEAN_ARITHMETIC =
    /*
     *   1           0 LOAD_NAME                0 (u)
     *               2 LOAD_NAME                1 (t)
     *               4 BINARY_ADD
     *               6 STORE_NAME               2 (a)
     *
     *   2           8 LOAD_NAME                0 (u)
     *              10 LOAD_NAME                1 (t)
     *              12 BINARY_MULTIPLY
     *              14 STORE_NAME               3 (b)
     *
     *   3          16 LOAD_NAME                0 (u)
     *              18 LOAD_NAME                4 (f)
     *              20 BINARY_MULTIPLY
     *              22 STORE_NAME               5 (c)
     *              24 LOAD_CONST               0 (None)
     *              26 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 2, 64,
        new PyBytes(new byte[] { 101, 0, 101, 1, 23, 0, 90, 2, 101,
                0, 101, 1, 20, 0, 90, 3, 101, 0, 101, 4, 20, 0, 90,
                5, 100, 0, 83, 0 }),
        new PyTuple(new PyObject[] { Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("u"),
                new PyUnicode("t"), new PyUnicode("a"),
                new PyUnicode("b"), new PyUnicode("f"),
                new PyUnicode("c") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyUnicode("boolean_arithmetic"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 8, 1, 8, 1 }));
    //@formatter:on

    @Test
    void test_boolean_arithmetic1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("u"), new PyLong(42));
        globals.put(new PyUnicode("t"), PyBool.True);
        globals.put(new PyUnicode("f"), PyBool.False);
        PyCode code = BOOLEAN_ARITHMETIC;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(43), globals.get(new PyUnicode("a")),
            "a == 43");
        assertEquals(new PyLong(42), globals.get(new PyUnicode("b")),
            "b == 42");
        assertEquals(new PyLong(0), globals.get(new PyUnicode("c")),
            "c == 0");
        //@formatter:on
    }

    @Test
    void test_boolean_arithmetic2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("u"), new PyFloat(42.0));
        globals.put(new PyUnicode("t"), PyBool.True);
        globals.put(new PyUnicode("f"), PyBool.False);
        PyCode code = BOOLEAN_ARITHMETIC;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyFloat(43.0), globals.get(
            new PyUnicode("a")), "a == 43.0");
        assertEquals(new PyFloat(42.0), globals.get(
            new PyUnicode("b")), "b == 42.0");
        assertEquals(new PyFloat(0.0), globals.get(
            new PyUnicode("c")), "c == 0.0");
        //@formatter:on
    }

    /**
     * Example 'simple_if': <pre>
     * if b:
     *     r = 1
     * else:
     *     r = 0
     * </pre>
     */
    //@formatter:off
    static final PyCode SIMPLE_IF =
    /*
     *   1           0 LOAD_NAME                0 (b)
     *               2 POP_JUMP_IF_FALSE       10
     *
     *   2           4 LOAD_CONST               0 (1)
     *               6 STORE_NAME               1 (r)
     *               8 JUMP_FORWARD             4 (to 14)
     *
     *   4     >>   10 LOAD_CONST               1 (0)
     *              12 STORE_NAME               1 (r)
     *         >>   14 LOAD_CONST               2 (None)
     *              16 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 1, 64,
        new PyBytes(new byte[] { 101, 0, 114, 10, 100, 0, 90, 1, 110,
                4, 100, 1, 90, 1, 100, 2, 83, 0 }),
        new PyTuple(new PyObject[] { new PyLong(1), new PyLong(0),
                Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("b"),
                new PyUnicode("r") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}), new PyUnicode("simple_if"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 4, 1, 6, 2 }));
    //@formatter:on

    @Test
    void test_simple_if1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("b"), PyBool.True);
        PyCode code = SIMPLE_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(1), globals.get(new PyUnicode("r")),
            "r == 1");
        //@formatter:on
    }

    @Test
    void test_simple_if2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("b"), PyBool.False);
        PyCode code = SIMPLE_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(0), globals.get(new PyUnicode("r")),
            "r == 0");
        //@formatter:on
    }

    @Test
    void test_simple_if3() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("b"), new PyLong(0));
        PyCode code = SIMPLE_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(0), globals.get(new PyUnicode("r")),
            "r == 0");
        //@formatter:on
    }

    @Test
    void test_simple_if4() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("b"), new PyLong(1));
        PyCode code = SIMPLE_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(1), globals.get(new PyUnicode("r")),
            "r == 1");
        //@formatter:on
    }

    @Test
    void test_simple_if5() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("b"), new PyUnicode(""));
        PyCode code = SIMPLE_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(0), globals.get(new PyUnicode("r")),
            "r == 0");
        //@formatter:on
    }

    @Test
    void test_simple_if6() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("b"), new PyUnicode("something"));
        PyCode code = SIMPLE_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(1), globals.get(new PyUnicode("r")),
            "r == 1");
        //@formatter:on
    }

    @Test
    void test_simple_if7() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("b"), Py.None);
        PyCode code = SIMPLE_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(0), globals.get(new PyUnicode("r")),
            "r == 0");
        //@formatter:on
    }

    /**
     * Example 'multi_if': <pre>
     * if a and b:
     *     r = 2
     * elif a or b:
     *     r = 1
     * else:
     *     r = 0
     * </pre>
     */
    //@formatter:off
    static final PyCode MULTI_IF =
    /*
     *   1           0 LOAD_NAME                0 (a)
     *               2 POP_JUMP_IF_FALSE       14
     *               4 LOAD_NAME                1 (b)
     *               6 POP_JUMP_IF_FALSE       14
     *
     *   2           8 LOAD_CONST               0 (2)
     *              10 STORE_NAME               2 (r)
     *              12 JUMP_FORWARD            18 (to 32)
     *
     *   3     >>   14 LOAD_NAME                0 (a)
     *              16 POP_JUMP_IF_TRUE        22
     *              18 LOAD_NAME                1 (b)
     *              20 POP_JUMP_IF_FALSE       28
     *
     *   4     >>   22 LOAD_CONST               1 (1)
     *              24 STORE_NAME               2 (r)
     *              26 JUMP_FORWARD             4 (to 32)
     *
     *   6     >>   28 LOAD_CONST               2 (0)
     *              30 STORE_NAME               2 (r)
     *         >>   32 LOAD_CONST               3 (None)
     *              34 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 1, 64,
        new PyBytes(new byte[] { 101, 0, 114, 14, 101, 1, 114, 14,
                100, 0, 90, 2, 110, 18, 101, 0, 115, 22, 101, 1, 114,
                28, 100, 1, 90, 2, 110, 4, 100, 2, 90, 2, 100, 3, 83,
                0 }),
        new PyTuple(new PyObject[] { new PyLong(2), new PyLong(1),
                new PyLong(0), Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("a"),
                new PyUnicode("b"), new PyUnicode("r") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}), new PyUnicode("multi_if"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 8, 1, 6, 1, 8, 1, 6, 2 }));
    //@formatter:on

    @Test
    void test_multi_if1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), PyBool.False);
        globals.put(new PyUnicode("b"), PyBool.False);
        PyCode code = MULTI_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(0), globals.get(new PyUnicode("r")),
            "r == 0");
        //@formatter:on
    }

    @Test
    void test_multi_if2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), PyBool.False);
        globals.put(new PyUnicode("b"), PyBool.True);
        PyCode code = MULTI_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(1), globals.get(new PyUnicode("r")),
            "r == 1");
        //@formatter:on
    }

    @Test
    void test_multi_if3() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), PyBool.True);
        globals.put(new PyUnicode("b"), PyBool.False);
        PyCode code = MULTI_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(1), globals.get(new PyUnicode("r")),
            "r == 1");
        //@formatter:on
    }

    @Test
    void test_multi_if4() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), PyBool.True);
        globals.put(new PyUnicode("b"), PyBool.True);
        PyCode code = MULTI_IF;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(2), globals.get(new PyUnicode("r")),
            "r == 2");
        //@formatter:on
    }

    /**
     * Example 'comparison': <pre>
     * lt = a < b
     * le = a <= b
     * eq = a == b
     * ne = a != b
     * ge = a >= b
     * gt = a > b
     * </pre>
     */
    //@formatter:off
    static final PyCode COMPARISON =
    /*
     *   1           0 LOAD_NAME                0 (a)
     *               2 LOAD_NAME                1 (b)
     *               4 COMPARE_OP               0 (<)
     *               6 STORE_NAME               2 (lt)
     *
     *   2           8 LOAD_NAME                0 (a)
     *              10 LOAD_NAME                1 (b)
     *              12 COMPARE_OP               1 (<=)
     *              14 STORE_NAME               3 (le)
     *
     *   3          16 LOAD_NAME                0 (a)
     *              18 LOAD_NAME                1 (b)
     *              20 COMPARE_OP               2 (==)
     *              22 STORE_NAME               4 (eq)
     *
     *   4          24 LOAD_NAME                0 (a)
     *              26 LOAD_NAME                1 (b)
     *              28 COMPARE_OP               3 (!=)
     *              30 STORE_NAME               5 (ne)
     *
     *   5          32 LOAD_NAME                0 (a)
     *              34 LOAD_NAME                1 (b)
     *              36 COMPARE_OP               5 (>=)
     *              38 STORE_NAME               6 (ge)
     *
     *   6          40 LOAD_NAME                0 (a)
     *              42 LOAD_NAME                1 (b)
     *              44 COMPARE_OP               4 (>)
     *              46 STORE_NAME               7 (gt)
     *              48 LOAD_CONST               0 (None)
     *              50 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 2, 64,
        new PyBytes(new byte[] { 101, 0, 101, 1, 107, 0, 90, 2, 101,
                0, 101, 1, 107, 1, 90, 3, 101, 0, 101, 1, 107, 2, 90,
                4, 101, 0, 101, 1, 107, 3, 90, 5, 101, 0, 101, 1,
                107, 5, 90, 6, 101, 0, 101, 1, 107, 4, 90, 7, 100, 0,
                83, 0 }),
        new PyTuple(new PyObject[] { Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("a"),
                new PyUnicode("b"), new PyUnicode("lt"),
                new PyUnicode("le"), new PyUnicode("eq"),
                new PyUnicode("ne"), new PyUnicode("ge"),
                new PyUnicode("gt") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}), new PyUnicode("comparison"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 8, 1, 8, 1, 8, 1, 8, 1, 8, 1 }));
    //@formatter:on

    @Test
    void test_comparison1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(2));
        globals.put(new PyUnicode("b"), new PyLong(4));
        PyCode code = COMPARISON;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(PyBool.True, globals.get(new PyUnicode("lt")),
            "lt == True");
        assertEquals(PyBool.True, globals.get(new PyUnicode("le")),
            "le == True");
        assertEquals(PyBool.False, globals.get(new PyUnicode("eq")),
            "eq == False");
        assertEquals(PyBool.True, globals.get(new PyUnicode("ne")),
            "ne == True");
        assertEquals(PyBool.False, globals.get(new PyUnicode("ge")),
            "ge == False");
        assertEquals(PyBool.False, globals.get(new PyUnicode("gt")),
            "gt == False");
        //@formatter:on
    }

    @Test
    void test_comparison2() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(4));
        globals.put(new PyUnicode("b"), new PyLong(2));
        PyCode code = COMPARISON;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(PyBool.False, globals.get(new PyUnicode("lt")),
            "lt == False");
        assertEquals(PyBool.False, globals.get(new PyUnicode("le")),
            "le == False");
        assertEquals(PyBool.False, globals.get(new PyUnicode("eq")),
            "eq == False");
        assertEquals(PyBool.True, globals.get(new PyUnicode("ne")),
            "ne == True");
        assertEquals(PyBool.True, globals.get(new PyUnicode("ge")),
            "ge == True");
        assertEquals(PyBool.True, globals.get(new PyUnicode("gt")),
            "gt == True");
        //@formatter:on
    }

    @Test
    void test_comparison3() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("a"), new PyLong(2));
        globals.put(new PyUnicode("b"), new PyLong(2));
        PyCode code = COMPARISON;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(PyBool.False, globals.get(new PyUnicode("lt")),
            "lt == False");
        assertEquals(PyBool.True, globals.get(new PyUnicode("le")),
            "le == True");
        assertEquals(PyBool.True, globals.get(new PyUnicode("eq")),
            "eq == True");
        assertEquals(PyBool.False, globals.get(new PyUnicode("ne")),
            "ne == False");
        assertEquals(PyBool.True, globals.get(new PyUnicode("ge")),
            "ge == True");
        assertEquals(PyBool.False, globals.get(new PyUnicode("gt")),
            "gt == False");
        //@formatter:on
    }

    /**
     * Example 'simple_loop': <pre>
     * sum = 0
     * while n > 0:
     *     sum = sum + n
     *     n = n - 1
     * </pre>
     */
    //@formatter:off
    static final PyCode SIMPLE_LOOP =
    /*
     *   1           0 LOAD_CONST               0 (0)
     *               2 STORE_NAME               0 (sum)
     *
     *   2     >>    4 LOAD_NAME                1 (n)
     *               6 LOAD_CONST               0 (0)
     *               8 COMPARE_OP               4 (>)
     *              10 POP_JUMP_IF_FALSE       30
     *
     *   3          12 LOAD_NAME                0 (sum)
     *              14 LOAD_NAME                1 (n)
     *              16 BINARY_ADD
     *              18 STORE_NAME               0 (sum)
     *
     *   4          20 LOAD_NAME                1 (n)
     *              22 LOAD_CONST               1 (1)
     *              24 BINARY_SUBTRACT
     *              26 STORE_NAME               1 (n)
     *              28 JUMP_ABSOLUTE            4
     *         >>   30 LOAD_CONST               2 (None)
     *              32 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 2, 64,
        new PyBytes(new byte[] { 100, 0, 90, 0, 101, 1, 100, 0, 107,
                4, 114, 30, 101, 0, 101, 1, 23, 0, 90, 0, 101, 1,
                100, 1, 24, 0, 90, 1, 113, 4, 100, 2, 83, 0 }),
        new PyTuple(new PyObject[] { new PyLong(0), new PyLong(1),
                Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("sum"),
                new PyUnicode("n") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}), new PyUnicode("simple_loop"),
        new PyUnicode("<module>"), 1,
        new PyBytes(new byte[] { 4, 1, 8, 1, 8, 1 }));
    //@formatter:on

    @Test
    void test_simple_loop1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("n"), new PyLong(6));
        PyCode code = SIMPLE_LOOP;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(0), globals.get(new PyUnicode("n")),
            "n == 0");
        assertEquals(new PyLong(21), globals.get(
            new PyUnicode("sum")), "sum == 21");
        //@formatter:on
    }

    /**
     * Example 'list_dot_product': <pre>
     * a = [1.2, 3.4, 5.6, 7.8] * (3 * n)
     * b = (4 * n) * [1.2, 4.5, 7.8]
     * n = 12 * n  # lists are this long
     * i = 0
     * sum = 0.0
     * while i < n:
     *     sum = sum + a[i] * b[i]
     *     i = i + 1
     * </pre>
     */
    //@formatter:off
    static final PyCode LIST_DOT_PRODUCT =
    /*
     *   1           0 LOAD_CONST               0 (1.2)
     *               2 LOAD_CONST               1 (3.4)
     *               4 LOAD_CONST               2 (5.6)
     *               6 LOAD_CONST               3 (7.8)
     *               8 BUILD_LIST               4
     *              10 LOAD_CONST               4 (3)
     *              12 LOAD_NAME                0 (n)
     *              14 BINARY_MULTIPLY
     *              16 BINARY_MULTIPLY
     *              18 STORE_NAME               1 (a)
     *
     *   2          20 LOAD_CONST               5 (4)
     *              22 LOAD_NAME                0 (n)
     *              24 BINARY_MULTIPLY
     *              26 LOAD_CONST               0 (1.2)
     *              28 LOAD_CONST               6 (4.5)
     *              30 LOAD_CONST               3 (7.8)
     *              32 BUILD_LIST               3
     *              34 BINARY_MULTIPLY
     *              36 STORE_NAME               2 (b)
     *
     *   3          38 LOAD_CONST               7 (12)
     *              40 LOAD_NAME                0 (n)
     *              42 BINARY_MULTIPLY
     *              44 STORE_NAME               0 (n)
     *
     *   4          46 LOAD_CONST               8 (0)
     *              48 STORE_NAME               3 (i)
     *
     *   5          50 LOAD_CONST               9 (0.0)
     *              52 STORE_NAME               4 (sum)
     *
     *   6     >>   54 LOAD_NAME                3 (i)
     *              56 LOAD_NAME                0 (n)
     *              58 COMPARE_OP               0 (<)
     *              60 POP_JUMP_IF_FALSE       92
     *
     *   7          62 LOAD_NAME                4 (sum)
     *              64 LOAD_NAME                1 (a)
     *              66 LOAD_NAME                3 (i)
     *              68 BINARY_SUBSCR
     *              70 LOAD_NAME                2 (b)
     *              72 LOAD_NAME                3 (i)
     *              74 BINARY_SUBSCR
     *              76 BINARY_MULTIPLY
     *              78 BINARY_ADD
     *              80 STORE_NAME               4 (sum)
     *
     *   8          82 LOAD_NAME                3 (i)
     *              84 LOAD_CONST              10 (1)
     *              86 BINARY_ADD
     *              88 STORE_NAME               3 (i)
     *              90 JUMP_ABSOLUTE           54
     *         >>   92 LOAD_CONST              11 (None)
     *              94 RETURN_VALUE
     */
    new PyCode(0, 0, 0, 0, 4, 64,
        new PyBytes(new byte[] { 100, 0, 100, 1, 100, 2, 100, 3, 103,
                4, 100, 4, 101, 0, 20, 0, 20, 0, 90, 1, 100, 5, 101,
                0, 20, 0, 100, 0, 100, 6, 100, 3, 103, 3, 20, 0, 90,
                2, 100, 7, 101, 0, 20, 0, 90, 0, 100, 8, 90, 3, 100,
                9, 90, 4, 101, 3, 101, 0, 107, 0, 114, 92, 101, 4,
                101, 1, 101, 3, 25, 0, 101, 2, 101, 3, 25, 0, 20, 0,
                23, 0, 90, 4, 101, 3, 100, 10, 23, 0, 90, 3, 113, 54,
                100, 11, 83, 0 }),
        new PyTuple(new PyObject[] { new PyFloat(1.2),
                new PyFloat(3.4), new PyFloat(5.6), new PyFloat(7.8),
                new PyLong(3), new PyLong(4), new PyFloat(4.5),
                new PyLong(12), new PyLong(0), new PyFloat(0.0),
                new PyLong(1), Py.None }),
        new PyTuple(new PyObject[] { new PyUnicode("n"),
                new PyUnicode("a"), new PyUnicode("b"),
                new PyUnicode("i"), new PyUnicode("sum") }),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyTuple(new PyObject[] {}),
        new PyUnicode("list_dot_product"), new PyUnicode("<module>"),
        1,
        new PyBytes(new byte[] { 20, 1, 18, 1, 8, 1, 4, 1, 4, 1, 8,
                1, 20, 1 }));
    //@formatter:on

    @Test
    void test_list_dot_product1() {
        //@formatter:off
        PyDictionary globals = new PyDictionary();
        globals.put(new PyUnicode("n"), new PyLong(2));
        PyCode code = LIST_DOT_PRODUCT;
        ThreadState tstate = new ThreadState();
        PyFrame frame = code.createFrame(tstate, globals, globals);
        frame.eval();
        assertEquals(new PyLong(24), globals.get(new PyUnicode("n")),
            "n == 24");
        assertEquals(new PyFloat(486.0), globals.get(
            new PyUnicode("sum")), "sum == 486.0");
        //@formatter:on
    }

}
