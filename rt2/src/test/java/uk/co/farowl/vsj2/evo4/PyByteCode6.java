package uk.co.farowl.vsj2.evo4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;

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

        static PyObject __getattr__(C self, PyUnicode name)
                throws Throwable {
            String n = name.toString();
            if ("x".equals(n) && self.x != null)
                return self.x;
            else
                throw Abstract.noAttributeError(self, name);
        }

        static void __setattr__(C self, PyUnicode name, PyObject value)
                throws Throwable {
            String n = name.toString();
            if ("x".equals(n))
                self.x = value;
            else
                throw Abstract.noAttributeError(self, name);
        }

        static PyObject __new__(PyType cls, PyTuple args, PyDict kwargs) {
            return new C();
        }
    }

    @Test
    void abstract_attr() throws Throwable {
        PyObject c = new C();
        Abstract.setAttr(c, Py.str("x"), Py.val(42));
        PyObject result = Abstract.getAttr(c, Py.str("x"));
        assertEquals(Py.val(42), result);
    }

    @Test
    void call_type_noargs() throws Throwable {
        PyObject c = Callables.call(C.TYPE);
        assertEquals(c.getType(), C.TYPE);
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

    /**
     * Test {@link Number#asLong(PyObject)} on {@code bool}, a sub-class of
     * {@code int}.
     */
    @Test
    void intFromInt() throws Throwable {
        PyObject result = Number.asLong(Py.val(42));
        assertEquals(Py.val(42), result);
        result = Number.asLong(Py.True);
        assertEquals(Py.val(1), result);
    }

    /** Test {@link Number#asLong(PyObject)} on {@code str}. */
    @Test
    void intFromStr() throws Throwable {
        PyObject result = Number.asLong(Py.str("42"));
        assertEquals(Py.val(42), result);
        result = Number.asLong(new MyStr("42"));
        assertEquals(Py.val(42), result);
    }

    /** Test {@link Number#fromUnicode(PyUnicode, int)}. */
    @Test
    void intFromStrBase() {
        PyObject result = PyLong.fromUnicode(Py.str("60"), 7);
        assertEquals(Py.val(42), result);
        result = PyLong.fromUnicode(Py.str("2c"), 15);
        assertEquals(Py.val(42), result);
    }

    /** Test {@code int.__new__} in a variety of forms. */
    @Test
    void int__new__() throws Throwable {
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

    /** A class whose instances have a dictionary for attributes. */
    private static class A implements PyObject {

        static final PyType TYPE =
                PyType.fromSpec(new PyType.Spec("00A", A.class));

        @Override
        public PyType getType() { return TYPE; }

        private PyDict dict = new PyDict();

        /** Construct an A and initialise a test value. */
        A(int foo) {
            dict.put("foo", Py.val(foo));
        }

        @Override
        public PyObject.Mapping getDict(boolean create) {
            return dict;
        }
    }


    /** Get attribute in a dictionary. */
    @Test
    void getattributeFromDict() throws Throwable {
        PyObject a = new A(42);
        PyObject result = Abstract.getAttr(a, Py.str("foo"));
        assertEquals(Py.val(42), result);
    }

    /** Get non-existent attribute raises {@code AttributeError}. */
    @SuppressWarnings("unused")
    @Test
    void getattributeFromDictError() throws Throwable {
        PyObject a = new A(43);
        PyUnicode bar = Py.str("bar");
        assertThrows(AttributeError.class, () -> { //
            PyObject result = Abstract.getAttr(a, bar);
        });
        // str has no dictionary
        assertThrows(AttributeError.class, () -> { //
            PyObject result = Abstract.getAttr(Py.str("x"), bar);
        });
    }

    /** Set attribute in a dictionary. */
    @Test
    void setAttrInDict() throws Throwable {
        PyObject a = new A(42);
        PyLong v = Py.val(7);
        PyUnicode foo = Py.str("foo");
        Abstract.setAttr(a, foo, v);
        assertEquals(v, a.getDict(false).get(foo));
        Abstract.setAttr(a, foo, null); // delete
        assertEquals(null, a.getDict(false).get(foo));
    }

    /** Set non-existent attribute raises {@code AttributeError}. */
    @SuppressWarnings("unused")
    @Test
    void setAttrInDictError() throws Throwable {
        PyObject a = new A(43);
        PyUnicode bar = Py.str("bar");
        assertThrows(AttributeError.class, () -> { // delete
            Abstract.setAttr(a, bar, null);
        });
        // str has no dictionary
        PyLong v = Py.val(7);
        assertThrows(AttributeError.class, () -> { //
            Abstract.setAttr(Py.str("x"), bar, v);
        });
    }


    // --------------------- Generated Tests -----------------------
    // Code generated by py_byte_code6_evo4.py
    // from py_byte_code6.ex.py

    /**
     * Example 'numeric_constructor': <pre>
     * # Exercise the constructors for int and float
     * i = int(u)
     * x = float(i)
     * y = float(u)
     * j = int(y)
     * </pre>
     */
    //@formatter:off
    static final PyCode NUMERIC_CONSTRUCTOR =
    /*
     *   2           0 LOAD_NAME                0 (int)
     *               2 LOAD_NAME                1 (u)
     *               4 CALL_FUNCTION            1
     *               6 STORE_NAME               2 (i)
     *
     *   3           8 LOAD_NAME                3 (float)
     *              10 LOAD_NAME                2 (i)
     *              12 CALL_FUNCTION            1
     *              14 STORE_NAME               4 (x)
     *
     *   4          16 LOAD_NAME                3 (float)
     *              18 LOAD_NAME                1 (u)
     *              20 CALL_FUNCTION            1
     *              22 STORE_NAME               5 (y)
     *
     *   5          24 LOAD_NAME                0 (int)
     *              26 LOAD_NAME                5 (y)
     *              28 CALL_FUNCTION            1
     *              30 STORE_NAME               6 (j)
     *              32 LOAD_CONST               0 (None)
     *              34 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 2, 64,
        Py.bytes(101, 0, 101, 1, -125, 1, 90, 2, 101, 3, 101, 2,
            -125, 1, 90, 4, 101, 3, 101, 1, -125, 1, 90, 5, 101, 0,
            101, 5, -125, 1, 90, 6, 100, 0, 83, 0),
        Py.tuple(Py.None),
        Py.tuple(Py.str("int"), Py.str("u"), Py.str("i"),
            Py.str("float"), Py.str("x"), Py.str("y"), Py.str("j")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("numeric_constructor"),
        Py.str("<module>"), 2,
        Py.bytes(8, 1, 8, 1, 8, 1));
    //@formatter:on

    @Test
    void test_numeric_constructor1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.val(7.5));
        interp.evalCode(NUMERIC_CONSTRUCTOR, globals, globals);
        assertEquals(Py.val(7), globals.get("i"), "i == 7");
        assertEquals(Py.val(7), globals.get("j"), "j == 7");
        assertEquals(Py.val(7.0), globals.get("x"), "x == 7.0");
        assertEquals(Py.val(7.5), globals.get("y"), "y == 7.5");
        //@formatter:on
    }

    @Test
    void test_numeric_constructor2() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.str("42"));
        interp.evalCode(NUMERIC_CONSTRUCTOR, globals, globals);
        assertEquals(Py.val(42), globals.get("i"), "i == 42");
        assertEquals(Py.val(42), globals.get("j"), "j == 42");
        assertEquals(Py.val(42.0), globals.get("x"), "x == 42.0");
        assertEquals(Py.val(42.0), globals.get("y"), "y == 42.0");
        //@formatter:on
    }

    @Test
    void test_numeric_constructor3() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.val(558545864083284007L));
        interp.evalCode(NUMERIC_CONSTRUCTOR, globals, globals);
        assertEquals(Py.val(558545864083284007L), globals.get("i"),
            "i == 558545864083284007");
        assertEquals(Py.val(558545864083284032L), globals.get("j"),
            "j == 558545864083284032");
        assertEquals(Py.val(5.5854586408328403e+17), globals.get(
            "x"), "x == 5.5854586408328403e+17");
        assertEquals(Py.val(5.5854586408328403e+17), globals.get(
            "y"), "y == 5.5854586408328403e+17");
        //@formatter:on
    }

    @Test
    void test_numeric_constructor4() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.val(-558545864083284007L));
        interp.evalCode(NUMERIC_CONSTRUCTOR, globals, globals);
        assertEquals(Py.val(-558545864083284007L), globals.get("i"),
            "i == -558545864083284007");
        assertEquals(Py.val(-558545864083284032L), globals.get("j"),
            "j == -558545864083284032");
        assertEquals(Py.val(-5.5854586408328403e+17), globals.get(
            "x"), "x == -5.5854586408328403e+17");
        assertEquals(Py.val(-5.5854586408328403e+17), globals.get(
            "y"), "y == -5.5854586408328403e+17");
        //@formatter:on
    }

    @Test
    void test_numeric_constructor5() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.val(9223372036854775807L));
        interp.evalCode(NUMERIC_CONSTRUCTOR, globals, globals);
        assertEquals(Py.val(9223372036854775807L), globals.get("i"),
            "i == 9223372036854775807");
        assertEquals(Py.val(new BigInteger("9223372036854775808")),
            globals.get("j"), "j == 9223372036854775808");
        assertEquals(Py.val(9.223372036854776e+18), globals.get("x"),
            "x == 9.223372036854776e+18");
        assertEquals(Py.val(9.223372036854776e+18), globals.get("y"),
            "y == 9.223372036854776e+18");
        //@formatter:on
    }

    @Test
    void test_numeric_constructor6() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u", Py.val(-9223372036854775808L));
        interp.evalCode(NUMERIC_CONSTRUCTOR, globals, globals);
        assertEquals(Py.val(-9223372036854775808L), globals.get("i"),
            "i == -9223372036854775808");
        assertEquals(Py.val(-9223372036854775808L), globals.get("j"),
            "j == -9223372036854775808");
        assertEquals(Py.val(-9.223372036854776e+18), globals.get(
            "x"), "x == -9.223372036854776e+18");
        assertEquals(Py.val(-9.223372036854776e+18), globals.get(
            "y"), "y == -9.223372036854776e+18");
        //@formatter:on
    }

    @Test
    void test_numeric_constructor7() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u",
            Py.val(new BigInteger("109418989131512359209")));
        interp.evalCode(NUMERIC_CONSTRUCTOR, globals, globals);
        assertEquals(Py.val(new BigInteger("109418989131512359209")),
            globals.get("i"), "i == 109418989131512359209");
        assertEquals(Py.val(new BigInteger("109418989131512365056")),
            globals.get("j"), "j == 109418989131512365056");
        assertEquals(Py.val(1.0941898913151237e+20), globals.get(
            "x"), "x == 1.0941898913151237e+20");
        assertEquals(Py.val(1.0941898913151237e+20), globals.get(
            "y"), "y == 1.0941898913151237e+20");
        //@formatter:on
    }

    @Test
    void test_numeric_constructor8() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("u",
            Py.val(new BigInteger("-109418989131512359209")));
        interp.evalCode(NUMERIC_CONSTRUCTOR, globals, globals);
        assertEquals(
            Py.val(new BigInteger("-109418989131512359209")),
            globals.get("i"), "i == -109418989131512359209");
        assertEquals(
            Py.val(new BigInteger("-109418989131512365056")),
            globals.get("j"), "j == -109418989131512365056");
        assertEquals(Py.val(-1.0941898913151237e+20), globals.get(
            "x"), "x == -1.0941898913151237e+20");
        assertEquals(Py.val(-1.0941898913151237e+20), globals.get(
            "y"), "y == -1.0941898913151237e+20");
        //@formatter:on
    }

    /**
     * Example 'type_enquiry': <pre>
     * # Distinguish type enquiry from type construction (both call type.__new__)
     * t = type(x)
     * </pre>
     */
    //@formatter:off
    static final PyCode TYPE_ENQUIRY =
    /*
     *   2           0 LOAD_NAME                0 (type)
     *               2 LOAD_NAME                1 (x)
     *               4 CALL_FUNCTION            1
     *               6 STORE_NAME               2 (t)
     *               8 LOAD_CONST               0 (None)
     *              10 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 2, 64,
        Py.bytes(101, 0, 101, 1, -125, 1, 90, 2, 100, 0, 83, 0),
        Py.tuple(Py.None),
        Py.tuple(Py.str("type"), Py.str("x"), Py.str("t")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("type_enquiry"), Py.str("<module>"), 2,
        Py.bytes());
    //@formatter:on

    @Test
    void test_type_enquiry1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("x", Py.val(6));
        interp.evalCode(TYPE_ENQUIRY, globals, globals);
        assertEquals(interp.getBuiltin("int"), globals.get("t"),
            "t == <class 'int'>");
        //@formatter:on
    }

    @Test
    void test_type_enquiry2() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("x", Py.val(6.9));
        interp.evalCode(TYPE_ENQUIRY, globals, globals);
        assertEquals(interp.getBuiltin("float"), globals.get("t"),
            "t == <class 'float'>");
        //@formatter:on
    }

    @Test
    void test_type_enquiry3() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("x", Py.str("42"));
        interp.evalCode(TYPE_ENQUIRY, globals, globals);
        assertEquals(interp.getBuiltin("str"), globals.get("t"),
            "t == <class 'str'>");
        //@formatter:on
    }

    /**
     * Example 'type_constructor': <pre>
     * # Create a class using the type constructor
     * C = type('C', (), {})
     * t = type(C)
     * name = t.__name__
     * </pre>
     */
    //@formatter:off
    static final PyCode TYPE_CONSTRUCTOR =
    /*
     *   2           0 LOAD_NAME                0 (type)
     *               2 LOAD_CONST               0 ('C')
     *               4 LOAD_CONST               1 (())
     *               6 BUILD_MAP                0
     *               8 CALL_FUNCTION            3
     *              10 STORE_NAME               1 (C)
     *
     *   3          12 LOAD_NAME                0 (type)
     *              14 LOAD_NAME                1 (C)
     *              16 CALL_FUNCTION            1
     *              18 STORE_NAME               2 (t)
     *
     *   4          20 LOAD_NAME                2 (t)
     *              22 LOAD_ATTR                3 (__name__)
     *              24 STORE_NAME               4 (name)
     *              26 LOAD_CONST               2 (None)
     *              28 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 4, 64,
        Py.bytes(101, 0, 100, 0, 100, 1, 105, 0, -125, 3, 90, 1, 101,
            0, 101, 1, -125, 1, 90, 2, 101, 2, 106, 3, 90, 4, 100, 2,
            83, 0),
        Py.tuple(Py.str("C"),
            Py.tuple(), Py.None),
        Py.tuple(Py.str("type"), Py.str("C"), Py.str("t"),
            Py.str("__name__"), Py.str("name")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("type_constructor"), Py.str("<module>"),
        2,
        Py.bytes(12, 1, 8, 1));
    //@formatter:on

    //@Test
    void test_type_constructor1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        interp.evalCode(TYPE_CONSTRUCTOR, globals, globals);
        assertEquals(interp.getBuiltin("type"), globals.get("t"),
            "t == <class 'type'>");
        assertEquals(Py.str("type"), globals.get("name"),
            "name == 'type'");
        //@formatter:on
    }

    /**
     * Example 'instance_attributes': <pre>
     * # An instance has a dictionary we may access from Python.
     * class C:
     *     pass
     *
     * c = C()
     * c.a = 10
     * r = c.a
     * </pre>
     */
    //@formatter:off
    static final PyCode INSTANCE_ATTRIBUTES =
    /*
     *   2           0 LOAD_BUILD_CLASS
     *               2 LOAD_CONST               0 (<code object C at 0x0000027A9B8BD9D0, file "instance_attributes", line 2>)
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
     *   6          20 LOAD_CONST               2 (10)
     *              22 LOAD_NAME                1 (c)
     *              24 STORE_ATTR               2 (a)
     *
     *   7          26 LOAD_NAME                1 (c)
     *              28 LOAD_ATTR                2 (a)
     *              30 STORE_NAME               3 (r)
     *              32 LOAD_CONST               3 (None)
     *              34 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 3, 64,
        Py.bytes(71, 0, 100, 0, 100, 1, -124, 0, 100, 1, -125, 2, 90,
            0, 101, 0, -125, 0, 90, 1, 100, 2, 101, 1, 95, 2, 101, 1,
            106, 2, 90, 3, 100, 3, 83, 0),
        Py.tuple(
            /*
             *   2           0 LOAD_NAME                0 (__name__)
             *               2 STORE_NAME               1 (__module__)
             *               4 LOAD_CONST               0 ('C')
             *               6 STORE_NAME               2 (__qualname__)
             *
             *   3           8 LOAD_CONST               1 (None)
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
                Py.tuple(), Py.str("instance_attributes"),
                Py.str("C"), 2,
                Py.bytes(8, 1)), Py.str("C"), Py.val(10), Py.None),
        Py.tuple(Py.str("C"), Py.str("c"), Py.str("a"), Py.str("r")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("instance_attributes"),
        Py.str("<module>"), 2,
        Py.bytes(14, 3, 6, 1, 6, 1));
    //@formatter:on

    //@Test
    void test_instance_attributes1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        interp.evalCode(INSTANCE_ATTRIBUTES, globals, globals);
        assertEquals(Py.val(10), globals.get("r"), "r == 10");
        //@formatter:on
    }

    /**
     * Example 'class_get_attribute': <pre>
     * # A class defined in Python has a dictionary we may read from Python.
     * class C:
     *     X = 42
     *     pass
     *
     * name = C.__name__
     * x = C.X
     * </pre>
     */
    //@formatter:off
    static final PyCode CLASS_GET_ATTRIBUTE =
    /*
     *   2           0 LOAD_BUILD_CLASS
     *               2 LOAD_CONST               0 (<code object C at 0x0000027A9B8BD9D0, file "class_get_attribute", line 2>)
     *               4 LOAD_CONST               1 ('C')
     *               6 MAKE_FUNCTION            0
     *               8 LOAD_CONST               1 ('C')
     *              10 CALL_FUNCTION            2
     *              12 STORE_NAME               0 (C)
     *
     *   6          14 LOAD_NAME                0 (C)
     *              16 LOAD_ATTR                1 (__name__)
     *              18 STORE_NAME               2 (name)
     *
     *   7          20 LOAD_NAME                0 (C)
     *              22 LOAD_ATTR                3 (X)
     *              24 STORE_NAME               4 (x)
     *              26 LOAD_CONST               2 (None)
     *              28 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 3, 64,
        Py.bytes(71, 0, 100, 0, 100, 1, -124, 0, 100, 1, -125, 2, 90,
            0, 101, 0, 106, 1, 90, 2, 101, 0, 106, 3, 90, 4, 100, 2,
            83, 0),
        Py.tuple(
            /*
             *   2           0 LOAD_NAME                0 (__name__)
             *               2 STORE_NAME               1 (__module__)
             *               4 LOAD_CONST               0 ('C')
             *               6 STORE_NAME               2 (__qualname__)
             *
             *   3           8 LOAD_CONST               1 (42)
             *              10 STORE_NAME               3 (X)
             *
             *   4          12 LOAD_CONST               2 (None)
             *              14 RETURN_VALUE
             */
            new CPythonCode(0, 0, 0, 0, 1, 64,
                Py.bytes(101, 0, 90, 1, 100, 0, 90, 2, 100, 1, 90, 3,
                    100, 2, 83, 0),
                Py.tuple(Py.str("C"), Py.val(42), Py.None),
                Py.tuple(Py.str("__name__"), Py.str("__module__"),
                    Py.str("__qualname__"), Py.str("X")),
                Py.tuple(),
                Py.tuple(),
                Py.tuple(), Py.str("class_get_attribute"),
                Py.str("C"), 2,
                Py.bytes(8, 1, 4, 1)), Py.str("C"), Py.None),
        Py.tuple(Py.str("C"), Py.str("__name__"), Py.str("name"),
            Py.str("X"), Py.str("x")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("class_get_attribute"),
        Py.str("<module>"), 2,
        Py.bytes(14, 4, 6, 1));
    //@formatter:on

    //@Test
    void test_class_get_attribute1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        interp.evalCode(CLASS_GET_ATTRIBUTE, globals, globals);
        assertEquals(Py.str("C"), globals.get("name"), "name == 'C'");
        assertEquals(Py.val(42), globals.get("x"), "x == 42");
        //@formatter:on
    }

    /**
     * Example 'class_set_attribute': <pre>
     * # A class defined in Python has a dictionary we may write from Python.
     * class C:
     *     X = "penguin"
     *     pass
     *
     * C.X = "telly"
     * C.Y = "explode"
     * x = C.X
     * y = C.Y
     * </pre>
     */
    //@formatter:off
    static final PyCode CLASS_SET_ATTRIBUTE =
    /*
     *   2           0 LOAD_BUILD_CLASS
     *               2 LOAD_CONST               0 (<code object C at 0x0000027A9B8BDEA0, file "class_set_attribute", line 2>)
     *               4 LOAD_CONST               1 ('C')
     *               6 MAKE_FUNCTION            0
     *               8 LOAD_CONST               1 ('C')
     *              10 CALL_FUNCTION            2
     *              12 STORE_NAME               0 (C)
     *
     *   6          14 LOAD_CONST               2 ('telly')
     *              16 LOAD_NAME                0 (C)
     *              18 STORE_ATTR               1 (X)
     *
     *   7          20 LOAD_CONST               3 ('explode')
     *              22 LOAD_NAME                0 (C)
     *              24 STORE_ATTR               2 (Y)
     *
     *   8          26 LOAD_NAME                0 (C)
     *              28 LOAD_ATTR                1 (X)
     *              30 STORE_NAME               3 (x)
     *
     *   9          32 LOAD_NAME                0 (C)
     *              34 LOAD_ATTR                2 (Y)
     *              36 STORE_NAME               4 (y)
     *              38 LOAD_CONST               4 (None)
     *              40 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 3, 64,
        Py.bytes(71, 0, 100, 0, 100, 1, -124, 0, 100, 1, -125, 2, 90,
            0, 100, 2, 101, 0, 95, 1, 100, 3, 101, 0, 95, 2, 101, 0,
            106, 1, 90, 3, 101, 0, 106, 2, 90, 4, 100, 4, 83, 0),
        Py.tuple(
            /*
             *   2           0 LOAD_NAME                0 (__name__)
             *               2 STORE_NAME               1 (__module__)
             *               4 LOAD_CONST               0 ('C')
             *               6 STORE_NAME               2 (__qualname__)
             *
             *   3           8 LOAD_CONST               1 ('penguin')
             *              10 STORE_NAME               3 (X)
             *
             *   4          12 LOAD_CONST               2 (None)
             *              14 RETURN_VALUE
             */
            new CPythonCode(0, 0, 0, 0, 1, 64,
                Py.bytes(101, 0, 90, 1, 100, 0, 90, 2, 100, 1, 90, 3,
                    100, 2, 83, 0),
                Py.tuple(Py.str("C"), Py.str("penguin"), Py.None),
                Py.tuple(Py.str("__name__"), Py.str("__module__"),
                    Py.str("__qualname__"), Py.str("X")),
                Py.tuple(),
                Py.tuple(),
                Py.tuple(), Py.str("class_set_attribute"),
                Py.str("C"), 2,
                Py.bytes(8, 1, 4, 1)), Py.str("C"), Py.str("telly"),
            Py.str("explode"), Py.None),
        Py.tuple(Py.str("C"), Py.str("X"), Py.str("Y"), Py.str("x"),
            Py.str("y")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("class_set_attribute"),
        Py.str("<module>"), 2,
        Py.bytes(14, 4, 6, 1, 6, 1, 6, 1));
    //@formatter:on

    //@Test
    void test_class_set_attribute1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        interp.evalCode(CLASS_SET_ATTRIBUTE, globals, globals);
        assertEquals(Py.str("telly"), globals.get("x"),
            "x == 'telly'");
        assertEquals(Py.str("explode"), globals.get("y"),
            "y == 'explode'");
        //@formatter:on
    }

    /**
     * Example 'instance_init': <pre>
     * # The instance dictionary may be set from __init__.
     * class C:
     *     def __init__(self, a, b):
     *         self.a = a
     *         self.b = b
     *
     * def f(cc, x):
     *     return (x + cc.a) * x + cc.b
     *
     * c = C(-9, 62)
     * ca = c.a
     * fx = f(c, x)
     * fy = f(c, y)
     * </pre>
     */
    //@formatter:off
    static final PyCode INSTANCE_INIT =
    /*
     *   2           0 LOAD_BUILD_CLASS
     *               2 LOAD_CONST               0 (<code object C at 0x0000027A9B8C60E0, file "instance_init", line 2>)
     *               4 LOAD_CONST               1 ('C')
     *               6 MAKE_FUNCTION            0
     *               8 LOAD_CONST               1 ('C')
     *              10 CALL_FUNCTION            2
     *              12 STORE_NAME               0 (C)
     *
     *   7          14 LOAD_CONST               2 (<code object f at 0x0000027A9B8C6190, file "instance_init", line 7>)
     *              16 LOAD_CONST               3 ('f')
     *              18 MAKE_FUNCTION            0
     *              20 STORE_NAME               1 (f)
     *
     *  10          22 LOAD_NAME                0 (C)
     *              24 LOAD_CONST               4 (-9)
     *              26 LOAD_CONST               5 (62)
     *              28 CALL_FUNCTION            2
     *              30 STORE_NAME               2 (c)
     *
     *  11          32 LOAD_NAME                2 (c)
     *              34 LOAD_ATTR                3 (a)
     *              36 STORE_NAME               4 (ca)
     *
     *  12          38 LOAD_NAME                1 (f)
     *              40 LOAD_NAME                2 (c)
     *              42 LOAD_NAME                5 (x)
     *              44 CALL_FUNCTION            2
     *              46 STORE_NAME               6 (fx)
     *
     *  13          48 LOAD_NAME                1 (f)
     *              50 LOAD_NAME                2 (c)
     *              52 LOAD_NAME                7 (y)
     *              54 CALL_FUNCTION            2
     *              56 STORE_NAME               8 (fy)
     *              58 LOAD_CONST               6 (None)
     *              60 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 3, 64,
        Py.bytes(71, 0, 100, 0, 100, 1, -124, 0, 100, 1, -125, 2, 90,
            0, 100, 2, 100, 3, -124, 0, 90, 1, 101, 0, 100, 4, 100,
            5, -125, 2, 90, 2, 101, 2, 106, 3, 90, 4, 101, 1, 101, 2,
            101, 5, -125, 2, 90, 6, 101, 1, 101, 2, 101, 7, -125, 2,
            90, 8, 100, 6, 83, 0),
        Py.tuple(
            /*
             *   2           0 LOAD_NAME                0 (__name__)
             *               2 STORE_NAME               1 (__module__)
             *               4 LOAD_CONST               0 ('C')
             *               6 STORE_NAME               2 (__qualname__)
             *
             *   3           8 LOAD_CONST               1 (<code object __init__ at 0x0000027A9B8C6030, file "instance_init", line 3>)
             *              10 LOAD_CONST               2 ('C.__init__')
             *              12 MAKE_FUNCTION            0
             *              14 STORE_NAME               3 (__init__)
             *              16 LOAD_CONST               3 (None)
             *              18 RETURN_VALUE
             */
            new CPythonCode(0, 0, 0, 0, 2, 64,
                Py.bytes(101, 0, 90, 1, 100, 0, 90, 2, 100, 1, 100,
                    2, -124, 0, 90, 3, 100, 3, 83, 0),
                Py.tuple(Py.str("C"),
                    /*
                     *   4           0 LOAD_FAST                1 (a)
                     *               2 LOAD_FAST                0 (self)
                     *               4 STORE_ATTR               0 (a)
                     *
                     *   5           6 LOAD_FAST                2 (b)
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
                        Py.tuple(), Py.str("instance_init"),
                        Py.str("__init__"), 3,
                        Py.bytes(0, 1, 6, 1)), Py.str("C.__init__"),
                    Py.None),
                Py.tuple(Py.str("__name__"), Py.str("__module__"),
                    Py.str("__qualname__"), Py.str("__init__")),
                Py.tuple(),
                Py.tuple(),
                Py.tuple(), Py.str("instance_init"), Py.str("C"), 2,
                Py.bytes(8, 1)), Py.str("C"),
            /*
             *   8           0 LOAD_FAST                1 (x)
             *               2 LOAD_FAST                0 (cc)
             *               4 LOAD_ATTR                0 (a)
             *               6 BINARY_ADD
             *               8 LOAD_FAST                1 (x)
             *              10 BINARY_MULTIPLY
             *              12 LOAD_FAST                0 (cc)
             *              14 LOAD_ATTR                1 (b)
             *              16 BINARY_ADD
             *              18 RETURN_VALUE
             */
            new CPythonCode(2, 0, 0, 2, 2, 67,
                Py.bytes(124, 1, 124, 0, 106, 0, 23, 0, 124, 1, 20,
                    0, 124, 0, 106, 1, 23, 0, 83, 0),
                Py.tuple(Py.None),
                Py.tuple(Py.str("a"), Py.str("b")),
                Py.tuple(Py.str("cc"), Py.str("x")),
                Py.tuple(),
                Py.tuple(), Py.str("instance_init"), Py.str("f"), 7,
                Py.bytes(0, 1)), Py.str("f"), Py.val(-9), Py.val(62),
            Py.None),
        Py.tuple(Py.str("C"), Py.str("f"), Py.str("c"), Py.str("a"),
            Py.str("ca"), Py.str("x"), Py.str("fx"), Py.str("y"),
            Py.str("fy")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("instance_init"), Py.str("<module>"), 2,
        Py.bytes(14, 5, 8, 3, 10, 1, 6, 1, 10, 1));
    //@formatter:on

    //@Test
    void test_instance_init1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("x", Py.val(4));
        globals.put("y", Py.val(5.0));
        interp.evalCode(INSTANCE_INIT, globals, globals);
        assertEquals(Py.val(42), globals.get("fx"), "fx == 42");
        assertEquals(Py.val(42.0), globals.get("fy"), "fy == 42.0");
        assertEquals(Py.val(-9), globals.get("ca"), "ca == -9");
        //@formatter:on
    }

    /**
     * Example 'simple_method': <pre>
     * # We may define methods and in the darkness bind them.
     * class C:
     *     def __init__(self, a, b, c):
     *         self.a = a
     *         self.b = b
     *         self.c = c
     *
     *     def f(self, x):
     *         return ((x-self.a)*x + self.b)*x + self.c
     *
     *
     * c = C(a, 11, 36)
     * cf = c.f
     * cfx = cf(x)
     * fx = c.f(x)
     * </pre>
     */
    //@formatter:off
    static final PyCode SIMPLE_METHOD =
    /*
     *   2           0 LOAD_BUILD_CLASS
     *               2 LOAD_CONST               0 (<code object C at 0x0000027A9B8C63A0, file "simple_method", line 2>)
     *               4 LOAD_CONST               1 ('C')
     *               6 MAKE_FUNCTION            0
     *               8 LOAD_CONST               1 ('C')
     *              10 CALL_FUNCTION            2
     *              12 STORE_NAME               0 (C)
     *
     *  12          14 LOAD_NAME                0 (C)
     *              16 LOAD_NAME                1 (a)
     *              18 LOAD_CONST               2 (11)
     *              20 LOAD_CONST               3 (36)
     *              22 CALL_FUNCTION            3
     *              24 STORE_NAME               2 (c)
     *
     *  13          26 LOAD_NAME                2 (c)
     *              28 LOAD_ATTR                3 (f)
     *              30 STORE_NAME               4 (cf)
     *
     *  14          32 LOAD_NAME                4 (cf)
     *              34 LOAD_NAME                5 (x)
     *              36 CALL_FUNCTION            1
     *              38 STORE_NAME               6 (cfx)
     *
     *  15          40 LOAD_NAME                2 (c)
     *              42 LOAD_METHOD              3 (f)
     *              44 LOAD_NAME                5 (x)
     *              46 CALL_METHOD              1
     *              48 STORE_NAME               7 (fx)
     *              50 LOAD_CONST               4 (None)
     *              52 RETURN_VALUE
     */
    new CPythonCode(0, 0, 0, 0, 4, 64,
        Py.bytes(71, 0, 100, 0, 100, 1, -124, 0, 100, 1, -125, 2, 90,
            0, 101, 0, 101, 1, 100, 2, 100, 3, -125, 3, 90, 2, 101,
            2, 106, 3, 90, 4, 101, 4, 101, 5, -125, 1, 90, 6, 101, 2,
            -96, 3, 101, 5, -95, 1, 90, 7, 100, 4, 83, 0),
        Py.tuple(
            /*
             *   2           0 LOAD_NAME                0 (__name__)
             *               2 STORE_NAME               1 (__module__)
             *               4 LOAD_CONST               0 ('C')
             *               6 STORE_NAME               2 (__qualname__)
             *
             *   3           8 LOAD_CONST               1 (<code object __init__ at 0x0000027A9B8C6240, file "simple_method", line 3>)
             *              10 LOAD_CONST               2 ('C.__init__')
             *              12 MAKE_FUNCTION            0
             *              14 STORE_NAME               3 (__init__)
             *
             *   8          16 LOAD_CONST               3 (<code object f at 0x0000027A9B8C60E0, file "simple_method", line 8>)
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
                     *   4           0 LOAD_FAST                1 (a)
                     *               2 LOAD_FAST                0 (self)
                     *               4 STORE_ATTR               0 (a)
                     *
                     *   5           6 LOAD_FAST                2 (b)
                     *               8 LOAD_FAST                0 (self)
                     *              10 STORE_ATTR               1 (b)
                     *
                     *   6          12 LOAD_FAST                3 (c)
                     *              14 LOAD_FAST                0 (self)
                     *              16 STORE_ATTR               2 (c)
                     *              18 LOAD_CONST               0 (None)
                     *              20 RETURN_VALUE
                     */
                    new CPythonCode(4, 0, 0, 4, 2, 67,
                        Py.bytes(124, 1, 124, 0, 95, 0, 124, 2, 124,
                            0, 95, 1, 124, 3, 124, 0, 95, 2, 100, 0,
                            83, 0),
                        Py.tuple(Py.None),
                        Py.tuple(Py.str("a"), Py.str("b"),
                            Py.str("c")),
                        Py.tuple(Py.str("self"), Py.str("a"),
                            Py.str("b"), Py.str("c")),
                        Py.tuple(),
                        Py.tuple(), Py.str("simple_method"),
                        Py.str("__init__"), 3,
                        Py.bytes(0, 1, 6, 1, 6, 1)),
                    Py.str("C.__init__"),
                    /*
                     *   9           0 LOAD_FAST                1 (x)
                     *               2 LOAD_FAST                0 (self)
                     *               4 LOAD_ATTR                0 (a)
                     *               6 BINARY_SUBTRACT
                     *               8 LOAD_FAST                1 (x)
                     *              10 BINARY_MULTIPLY
                     *              12 LOAD_FAST                0 (self)
                     *              14 LOAD_ATTR                1 (b)
                     *              16 BINARY_ADD
                     *              18 LOAD_FAST                1 (x)
                     *              20 BINARY_MULTIPLY
                     *              22 LOAD_FAST                0 (self)
                     *              24 LOAD_ATTR                2 (c)
                     *              26 BINARY_ADD
                     *              28 RETURN_VALUE
                     */
                    new CPythonCode(2, 0, 0, 2, 2, 67,
                        Py.bytes(124, 1, 124, 0, 106, 0, 24, 0, 124,
                            1, 20, 0, 124, 0, 106, 1, 23, 0, 124, 1,
                            20, 0, 124, 0, 106, 2, 23, 0, 83, 0),
                        Py.tuple(Py.None),
                        Py.tuple(Py.str("a"), Py.str("b"),
                            Py.str("c")),
                        Py.tuple(Py.str("self"), Py.str("x")),
                        Py.tuple(),
                        Py.tuple(), Py.str("simple_method"),
                        Py.str("f"), 8,
                        Py.bytes(0, 1)), Py.str("C.f"), Py.None),
                Py.tuple(Py.str("__name__"), Py.str("__module__"),
                    Py.str("__qualname__"), Py.str("__init__"),
                    Py.str("f")),
                Py.tuple(),
                Py.tuple(),
                Py.tuple(), Py.str("simple_method"), Py.str("C"), 2,
                Py.bytes(8, 1, 8, 5)), Py.str("C"), Py.val(11),
            Py.val(36), Py.None),
        Py.tuple(Py.str("C"), Py.str("a"), Py.str("c"), Py.str("f"),
            Py.str("cf"), Py.str("x"), Py.str("cfx"), Py.str("fx")),
        Py.tuple(),
        Py.tuple(),
        Py.tuple(), Py.str("simple_method"), Py.str("<module>"), 2,
        Py.bytes(14, 10, 12, 1, 6, 1, 8, 1));
    //@formatter:on

    //@Test
    void test_simple_method1() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("a", Py.val(6));
        globals.put("x", Py.val(1));
        interp.evalCode(SIMPLE_METHOD, globals, globals);
        assertEquals(Py.val(42), globals.get("cfx"), "cfx == 42");
        assertEquals(Py.val(42), globals.get("fx"), "fx == 42");
        //@formatter:on
    }

    //@Test
    void test_simple_method2() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("a", Py.val(6.0));
        globals.put("x", Py.val(2));
        interp.evalCode(SIMPLE_METHOD, globals, globals);
        assertEquals(Py.val(42.0), globals.get("cfx"), "cfx == 42.0");
        assertEquals(Py.val(42.0), globals.get("fx"), "fx == 42.0");
        //@formatter:on
    }

    //@Test
    void test_simple_method3() {
        //@formatter:off
        Interpreter interp = Py.createInterpreter();
        PyDict globals = Py.dict();
        globals.put("a", Py.val(6));
        globals.put("x", Py.val(3.0));
        interp.evalCode(SIMPLE_METHOD, globals, globals);
        assertEquals(Py.val(42.0), globals.get("cfx"), "cfx == 42.0");
        assertEquals(Py.val(42.0), globals.get("fx"), "fx == 42.0");
        //@formatter:on
    }

}
