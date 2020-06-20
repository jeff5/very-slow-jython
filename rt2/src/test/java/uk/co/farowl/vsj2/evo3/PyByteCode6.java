package uk.co.farowl.vsj2.evo3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Continues the test illustrating a naive emulation using
 * {@code MethodHandle} of CPython's approach to type objects. The
 * present tests exercise class definition and access to attributes.
 */
class PyByteCode6 {

    /**
     * This class is used in test {@link PyByteCode6#abstract_attr()} to
     * act as a Python class with a constructor and an attribute.
     */
    @SuppressWarnings("unused")
    private static class C implements PyObject {

        static final PyType TYPE =
                PyType.fromSpec(new PyType.Spec("00C", C.class));

        @Override
        public PyType getType() { return TYPE; }

        PyObject x;         // Attribute for test

        static PyObject tp_getattro(C self, PyUnicode name)
                throws Throwable {
            String n = name.toString();
            if ("x".equals(n) && self.x != null)
                return self.x;
            else
                throw Abstract.noAttributeError(self, name);
        }

        static void tp_setattro(C self, PyUnicode name, PyObject value)
                throws Throwable {
            String n = name.toString();
            if ("x".equals(n))
                self.x = value;
            else
                throw Abstract.noAttributeError(self, name);
        }
    }

    @Test
    void abstract_attr() throws Throwable {
        PyObject c = new C();
        Abstract.setAttr(c, "x", Py.val(42));
        PyObject result = Abstract.getAttr(c, "x");
        assertEquals(Py.val(42), result);
    }

    /**
     * A sub-class of {@link PyUnicode} for
     * {@link PyByteCode6#abstract_attr2()}.
     */
    private static class MyStr extends PyUnicode {

        static final PyType TYPE = new PyType("MyStr", MyStr.class);

        @Override
        public PyType getType() { return TYPE; }

        MyStr(String value) { super(value); }
    }

    /** Check that a sub-class of {@code str} is acceptable as name. */
    @Test
    void abstract_attr2() throws Throwable {
        PyObject c = new C();
        PyObject name = new MyStr("x");
        Abstract.setAttr(c, name, Py.val(42));
        PyObject result = Abstract.getAttr(c, name);
        assertEquals(Py.val(42), result);
    }

    @Test
    void intFromInt() throws Throwable {
        PyObject result = Number.asLong(Py.val(42));
        assertEquals(Py.val(42), result);
        result = Number.asLong(Py.True);
        assertEquals(Py.val(1), result);
    }

    @Test
    void intFromStr() throws Throwable {
        PyObject result = Number.asLong(Py.str("42"));
        assertEquals(Py.val(42), result);
        result = Number.asLong(new MyStr("42"));
        assertEquals(Py.val(42), result);
    }

    @Test
    void intFromStrBase() {
        PyObject result = PyLong.fromUnicode(Py.str("60"), 7);
        assertEquals(Py.val(42), result);
        result = PyLong.fromUnicode(Py.str("2c"), 15);
        assertEquals(Py.val(42), result);
    }

    @Test
    void intFrom__new__() throws Throwable {
        PyType intType = PyLong.TYPE;
        // int()
        PyObject result = Callables.call(intType);
        assertEquals(PyLong.ZERO, result);
        // int(42)
        result = Callables.call(intType, Py.tuple(Py.val(42)), null);
        assertEquals(Py.val(42), result);
        // int("2c", 15)
        PyTuple args = Py.tuple(Py.str("2c"), Py.val(15));
        result = Callables.call(intType, args, null);
        assertEquals(Py.val(42), result);
    }

    // --------------------- Generated Tests -----------------------
    // Code generated by py_byte_code6_evo3.py
    // from py_byte_code6.ex.py

    /**
     * Example 'empty_class': <pre>
     * class C:
     *     pass
     *
     * name = C.__name__
     * c = C()
     * c.a = 10
     * n = c.a
     * </pre>
     */
    //@formatter:off
    static final PyCode EMPTY_CLASS =
    /*
     *   1           0 LOAD_BUILD_CLASS
     *               2 LOAD_CONST               0 (<code object C at 0x000001FB99320240, file "empty_class", line 1>)
     *               4 LOAD_CONST               1 ('C')
     *               6 MAKE_FUNCTION            0
     *               8 LOAD_CONST               1 ('C')
     *              10 CALL_FUNCTION            2
     *              12 STORE_NAME               0 (C)
     *
     *   4          14 LOAD_NAME                0 (C)
     *              16 LOAD_ATTR                1 (__name__)
     *              18 STORE_NAME               2 (name)
     *
     *   5          20 LOAD_NAME                0 (C)
     *              22 CALL_FUNCTION            0
     *              24 STORE_NAME               3 (c)
     *
     *   6          26 LOAD_CONST               2 (10)
     *              28 LOAD_NAME                3 (c)
     *              30 STORE_ATTR               4 (a)
     *
     *   7          32 LOAD_NAME                3 (c)
     *              34 LOAD_ATTR                4 (a)
     *              36 STORE_NAME               5 (n)
     *              38 LOAD_CONST               3 (None)
     *              40 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 3, 64,
        Py.bytes(71, 0, 100, 0, 100, 1, -124, 0, 100, 1, -125, 2, 90,
            0, 101, 0, 106, 1, 90, 2, 101, 0, -125, 0, 90, 3, 100, 2,
            101, 3, 95, 4, 101, 3, 106, 4, 90, 5, 100, 3, 83, 0),
        Py.tuple(
            /*
             *   1           0 LOAD_NAME                0 (__name__)
             *               2 STORE_NAME               1 (__module__)
             *               4 LOAD_CONST               0 ('C')
             *               6 STORE_NAME               2 (__qualname__)
             *
             *   2           8 LOAD_CONST               1 (None)
             *              10 RETURN_VALUE
             */
            new CPythonCode(0, 0, 0, 0, 1, 64,
                Py.bytes(101, 0, 90, 1, 100, 0, 90, 2, 100, 1, 83,
                    0),
                Py.tuple(Py.str("C"), Py.None),
                Py.tuple(Py.str("__name__"), Py.str("__module__"),
                    Py.str("__qualname__")),
                Py.tuple(),
                Py.tuple(),
                Py.tuple(), Py.str("empty_class"), Py.str("C"), 1,
                Py.bytes(8, 1)), Py.str("C"), Py.val(10), Py.None),
        Py.tuple(Py.str("C"), Py.str("__name__"), Py.str("name"),
            Py.str("c"), Py.str("a"), Py.str("n")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("empty_class"), Py.str("<module>"), 1,
        Py.bytes(14, 3, 6, 1, 6, 1, 6, 1));
    //@formatter:on

    // @Test
    void test_empty_class1() {
        //@formatter:off
        PyDict globals = Py.dict();
        Interpreter interp = Py.createInterpreter();
        interp.evalCode(EMPTY_CLASS, globals, globals);
        assertEquals(Py.str("C"), globals.get("name"), "name == 'C'");
        assertEquals(Py.val(10), globals.get("n"), "n == 10");
        //@formatter:on
    }

    /**
     * Example 'simple_method': <pre>
     * class C:
     *     def f(self, x):
     *         return ((x-6)*x + 11)*x + 36
     *
     * c = C()
     * fx = c.f(x)
     * fy = c.f(y)
     * fz = c.f(z)
     * </pre>
     */
    //@formatter:off
    static final PyCode SIMPLE_METHOD =
    /*
     *   1           0 LOAD_BUILD_CLASS
     *               2 LOAD_CONST               0 (<code object C at 0x000001FB993205B0, file "simple_method", line 1>)
     *               4 LOAD_CONST               1 ('C')
     *               6 MAKE_FUNCTION            0
     *               8 LOAD_CONST               1 ('C')
     *              10 CALL_FUNCTION            2
     *              12 STORE_NAME               0 (C)
     *
     *   5          14 LOAD_NAME                0 (C)
     *              16 CALL_FUNCTION            0
     *              18 STORE_NAME               1 (c)
     *
     *   6          20 LOAD_NAME                1 (c)
     *              22 LOAD_METHOD              2 (f)
     *              24 LOAD_NAME                3 (x)
     *              26 CALL_METHOD              1
     *              28 STORE_NAME               4 (fx)
     *
     *   7          30 LOAD_NAME                1 (c)
     *              32 LOAD_METHOD              2 (f)
     *              34 LOAD_NAME                5 (y)
     *              36 CALL_METHOD              1
     *              38 STORE_NAME               6 (fy)
     *
     *   8          40 LOAD_NAME                1 (c)
     *              42 LOAD_METHOD              2 (f)
     *              44 LOAD_NAME                7 (z)
     *              46 CALL_METHOD              1
     *              48 STORE_NAME               8 (fz)
     *              50 LOAD_CONST               2 (None)
     *              52 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 3, 64,
        Py.bytes(71, 0, 100, 0, 100, 1, -124, 0, 100, 1, -125, 2, 90,
            0, 101, 0, -125, 0, 90, 1, 101, 1, -96, 2, 101, 3, -95,
            1, 90, 4, 101, 1, -96, 2, 101, 5, -95, 1, 90, 6, 101, 1,
            -96, 2, 101, 7, -95, 1, 90, 8, 100, 2, 83, 0),
        Py.tuple(
            /*
             *   1           0 LOAD_NAME                0 (__name__)
             *               2 STORE_NAME               1 (__module__)
             *               4 LOAD_CONST               0 ('C')
             *               6 STORE_NAME               2 (__qualname__)
             *
             *   2           8 LOAD_CONST               1 (<code object f at 0x000001FB99320500, file "simple_method", line 2>)
             *              10 LOAD_CONST               2 ('C.f')
             *              12 MAKE_FUNCTION            0
             *              14 STORE_NAME               3 (f)
             *              16 LOAD_CONST               3 (None)
             *              18 RETURN_VALUE
             */
            new CPythonCode(0, 0, 0, 0, 2, 64,
                Py.bytes(101, 0, 90, 1, 100, 0, 90, 2, 100, 1, 100,
                    2, -124, 0, 90, 3, 100, 3, 83, 0),
                Py.tuple(Py.str("C"),
                    /*
                     *   3           0 LOAD_FAST                1 (x)
                     *               2 LOAD_CONST               1 (6)
                     *               4 BINARY_SUBTRACT
                     *               6 LOAD_FAST                1 (x)
                     *               8 BINARY_MULTIPLY
                     *              10 LOAD_CONST               2 (11)
                     *              12 BINARY_ADD
                     *              14 LOAD_FAST                1 (x)
                     *              16 BINARY_MULTIPLY
                     *              18 LOAD_CONST               3 (36)
                     *              20 BINARY_ADD
                     *              22 RETURN_VALUE
                     */
                    new CPythonCode(2, 0, 0, 2, 2, 67,
                        Py.bytes(124, 1, 100, 1, 24, 0, 124, 1, 20,
                            0, 100, 2, 23, 0, 124, 1, 20, 0, 100, 3,
                            23, 0, 83, 0),
                        Py.tuple(Py.None, Py.val(6), Py.val(11),
                            Py.val(36)),
                        Py.tuple(),
                        Py.tuple(Py.str("self"), Py.str("x")),
                        Py.tuple(),
                        Py.tuple(), Py.str("simple_method"),
                        Py.str("f"), 2,
                        Py.bytes(0, 1)), Py.str("C.f"), Py.None),
                Py.tuple(Py.str("__name__"), Py.str("__module__"),
                    Py.str("__qualname__"), Py.str("f")),
                Py.tuple(),
                Py.tuple(),
                Py.tuple(), Py.str("simple_method"), Py.str("C"), 1,
                Py.bytes(8, 1)), Py.str("C"), Py.None),
        Py.tuple(Py.str("C"), Py.str("c"), Py.str("f"), Py.str("x"),
            Py.str("fx"), Py.str("y"), Py.str("fy"), Py.str("z"),
            Py.str("fz")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("simple_method"), Py.str("<module>"), 1,
        Py.bytes(14, 4, 6, 1, 10, 1, 10, 1));
    //@formatter:on

    // @Test
    void test_simple_method1() {
        //@formatter:off
        PyDict globals = Py.dict();
        globals.put("x", Py.val(1));
        globals.put("y", Py.val(2));
        globals.put("z", Py.val(3.0));
        Interpreter interp = Py.createInterpreter();
        interp.evalCode(SIMPLE_METHOD, globals, globals);
        assertEquals(Py.val(42), globals.get("fx"), "fx == 42");
        assertEquals(Py.val(42), globals.get("fy"), "fy == 42");
        assertEquals(Py.val(42.0), globals.get("fz"), "fz == 42.0");
        //@formatter:on
    }

    /**
     * Example 'attributes': <pre>
     * class C:
     *     def __init__(self, a, b):
     *         self.a = a
     *         self.b = b
     *
     *     def f(self, x):
     *         return (x + self.a) * x + self.b
     *
     * c = C(-9, 62)
     * fx = c.f(x)
     * fy = c.f(y)
     * </pre>
     */
    //@formatter:off
    static final PyCode ATTRIBUTES =
    /*
     *   1           0 LOAD_BUILD_CLASS
     *               2 LOAD_CONST               0 (<code object C at 0x000001FB99320920, file "attributes", line 1>)
     *               4 LOAD_CONST               1 ('C')
     *               6 MAKE_FUNCTION            0
     *               8 LOAD_CONST               1 ('C')
     *              10 CALL_FUNCTION            2
     *              12 STORE_NAME               0 (C)
     *
     *   9          14 LOAD_NAME                0 (C)
     *              16 LOAD_CONST               2 (-9)
     *              18 LOAD_CONST               3 (62)
     *              20 CALL_FUNCTION            2
     *              22 STORE_NAME               1 (c)
     *
     *  10          24 LOAD_NAME                1 (c)
     *              26 LOAD_METHOD              2 (f)
     *              28 LOAD_NAME                3 (x)
     *              30 CALL_METHOD              1
     *              32 STORE_NAME               4 (fx)
     *
     *  11          34 LOAD_NAME                1 (c)
     *              36 LOAD_METHOD              2 (f)
     *              38 LOAD_NAME                5 (y)
     *              40 CALL_METHOD              1
     *              42 STORE_NAME               6 (fy)
     *              44 LOAD_CONST               4 (None)
     *              46 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 3, 64,
        Py.bytes(71, 0, 100, 0, 100, 1, -124, 0, 100, 1, -125, 2, 90,
            0, 101, 0, 100, 2, 100, 3, -125, 2, 90, 1, 101, 1, -96,
            2, 101, 3, -95, 1, 90, 4, 101, 1, -96, 2, 101, 5, -95, 1,
            90, 6, 100, 4, 83, 0),
        Py.tuple(
            /*
             *   1           0 LOAD_NAME                0 (__name__)
             *               2 STORE_NAME               1 (__module__)
             *               4 LOAD_CONST               0 ('C')
             *               6 STORE_NAME               2 (__qualname__)
             *
             *   2           8 LOAD_CONST               1 (<code object __init__ at 0x000001FB993207C0, file "attributes", line 2>)
             *              10 LOAD_CONST               2 ('C.__init__')
             *              12 MAKE_FUNCTION            0
             *              14 STORE_NAME               3 (__init__)
             *
             *   6          16 LOAD_CONST               3 (<code object f at 0x000001FB99320870, file "attributes", line 6>)
             *              18 LOAD_CONST               4 ('C.f')
             *              20 MAKE_FUNCTION            0
             *              22 STORE_NAME               4 (f)
             *              24 LOAD_CONST               5 (None)
             *              26 RETURN_VALUE
             */
            new CPythonCode(0, 0, 0, 0, 2, 64,
                Py.bytes(101, 0, 90, 1, 100, 0, 90, 2, 100, 1, 100,
                    2, -124, 0, 90, 3, 100, 3, 100, 4, -124, 0, 90,
                    4, 100, 5, 83, 0),
                Py.tuple(Py.str("C"),
                    /*
                     *   3           0 LOAD_FAST                1 (a)
                     *               2 LOAD_FAST                0 (self)
                     *               4 STORE_ATTR               0 (a)
                     *
                     *   4           6 LOAD_FAST                2 (b)
                     *               8 LOAD_FAST                0 (self)
                     *              10 STORE_ATTR               1 (b)
                     *              12 LOAD_CONST               0 (None)
                     *              14 RETURN_VALUE
                     */
                    new CPythonCode(3, 0, 0, 3, 2, 67,
                        Py.bytes(124, 1, 124, 0, 95, 0, 124, 2, 124,
                            0, 95, 1, 100, 0, 83, 0),
                        Py.tuple(Py.None),
                        Py.tuple(Py.str("a"), Py.str("b")),
                        Py.tuple(Py.str("self"), Py.str("a"),
                            Py.str("b")),
                        Py.tuple(),
                        Py.tuple(), Py.str("attributes"),
                        Py.str("__init__"), 2,
                        Py.bytes(0, 1, 6, 1)), Py.str("C.__init__"),
                    /*
                     *   7           0 LOAD_FAST                1 (x)
                     *               2 LOAD_FAST                0 (self)
                     *               4 LOAD_ATTR                0 (a)
                     *               6 BINARY_ADD
                     *               8 LOAD_FAST                1 (x)
                     *              10 BINARY_MULTIPLY
                     *              12 LOAD_FAST                0 (self)
                     *              14 LOAD_ATTR                1 (b)
                     *              16 BINARY_ADD
                     *              18 RETURN_VALUE
                     */
                    new CPythonCode(2, 0, 0, 2, 2, 67,
                        Py.bytes(124, 1, 124, 0, 106, 0, 23, 0, 124,
                            1, 20, 0, 124, 0, 106, 1, 23, 0, 83, 0),
                        Py.tuple(Py.None),
                        Py.tuple(Py.str("a"), Py.str("b")),
                        Py.tuple(Py.str("self"), Py.str("x")),
                        Py.tuple(),
                        Py.tuple(), Py.str("attributes"),
                        Py.str("f"), 6,
                        Py.bytes(0, 1)), Py.str("C.f"), Py.None),
                Py.tuple(Py.str("__name__"), Py.str("__module__"),
                    Py.str("__qualname__"), Py.str("__init__"),
                    Py.str("f")),
                Py.tuple(),
                Py.tuple(),
                Py.tuple(), Py.str("attributes"), Py.str("C"), 1,
                Py.bytes(8, 1, 8, 4)), Py.str("C"), Py.val(-9),
            Py.val(62), Py.None),
        Py.tuple(Py.str("C"), Py.str("c"), Py.str("f"), Py.str("x"),
            Py.str("fx"), Py.str("y"), Py.str("fy")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("attributes"), Py.str("<module>"), 1,
        Py.bytes(14, 8, 10, 1, 10, 1));
    //@formatter:on

    // @Test
    void test_attributes1() {
        //@formatter:off
        PyDict globals = Py.dict();
        globals.put("x", Py.val(4));
        globals.put("y", Py.val(5.0));
        Interpreter interp = Py.createInterpreter();
        interp.evalCode(ATTRIBUTES, globals, globals);
        assertEquals(Py.val(42), globals.get("fx"), "fx == 42");
        assertEquals(Py.val(42.0), globals.get("fy"), "fy == 42.0");
        //@formatter:on
    }

}
