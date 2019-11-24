package uk.co.farowl.vsj1.example;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.lookup;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.co.farowl.vsj1.TreePython.Node;
import uk.co.farowl.vsj1.TreePython.Visitor;
import uk.co.farowl.vsj1.TreePython.expr;
import uk.co.farowl.vsj1.TreePython.expr_context;
import uk.co.farowl.vsj1.TreePython.operator;

/**
 * Demonstrate interpretation of the AST where nodes contain an embedded
 * CallSite object.
 */
public class TestEx5 {

    @BeforeClass
    public static void setUpClass() {
        // Built-in types
        Runtime.registerTypeFor(Integer.class, new IntegerHandler());
        Runtime.registerTypeFor(Double.class, new DoubleHandler());
        Runtime.registerTypeFor(A.class, new AHandler());
        Runtime.registerTypeFor(B.class, new BHandler());
        Runtime.registerTypeFor(C.class, new CHandler());
    }

    // Visitor to execute the code.
    Evaluator evaluator;

    @Before
    public void setUp() {
        // Create a visitor to execute the code.
        evaluator = new Evaluator();
    }

    private static void resetFallbackCalls() {
        BinOpCallSite.fallbackCalls = 0;
    }

    private Node cubic() {
        // Build the expression tree (unlinked)
        // @formatter:off
        // (x*x-2) * (x+y)
        Node tree =
            BinOp(
                BinOp(
                    BinOp(Name("x", Load), Mult, Name("x", Load)),
                    Sub,
                    Num(2)),
                Mult,
                BinOp(Name("x", Load), Add, Name("y", Load)));
        // @formatter:on
        return tree;
    }

    private Node simple() { // v * w
        return BinOp(Name("v", Load), Mult, Name("w", Load));
    }

    @Test
    public void simpleInt() {
        Node tree = simple();
        evaluator.variables.put("v", 6);
        evaluator.variables.put("w", 7);
        assertThat(tree.accept(evaluator), is(42));
        resetFallbackCalls();
        evaluator.variables.put("v", 3);
        evaluator.variables.put("w", 8);
        assertThat(tree.accept(evaluator), is(24));
        assertThat(BinOpCallSite.fallbackCalls, is(0));
    }

    private Node add() { // v + w
        return BinOp(Name("v", Load), Add, Name("w", Load));
    }

    @Test
    public void addAA() {
        Node tree = add();
        evaluator.variables.put("v", new A());
        evaluator.variables.put("w", new A());
        assertThat(tree.accept(evaluator), is(instanceOf(A.class)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void addAB() {
        evaluator.variables.put("v", new A());
        evaluator.variables.put("w", new B());
        add().accept(evaluator);
    }

    @Test(expected = IllegalArgumentException.class)
    public void addBA() {
        evaluator.variables.put("v", new B());
        evaluator.variables.put("w", new A());
        add().accept(evaluator);
    }

    @Test
    public void addAC() {
        evaluator.variables.put("v", new A());
        evaluator.variables.put("w", new C());
        assertThat(add().accept(evaluator), is(instanceOf(C.class)));
    }

    @Test
    public void addCA() {
        evaluator.variables.put("v", new C());
        evaluator.variables.put("w", new A());
        assertThat(add().accept(evaluator), is(instanceOf(C.class)));
    }

    @Test
    public void testInt() {
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3);
        assertThat(cubic().accept(evaluator), is(42));
    }

    @Test
    public void testFloatInt() {
        // (x*x-2) exercises sub(Double, Integer)
        evaluator.variables.put("x", 3.);
        evaluator.variables.put("y", 3);
        assertThat(cubic().accept(evaluator), is(42.));
    }

    @Test
    public void testIntFloat() {
        // (x+y) exercises add(Integer, Double) (float.__radd__)
        // (x*x-2)*(x+y) exercises mul(Integer, Double) (float.__rmul__)
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3.);
        assertThat(cubic().accept(evaluator), is(42.));
    }

    @Test
    public void testIntRepeat() {
        Node tree = cubic();
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42));
        resetFallbackCalls();
        evaluator.variables.put("x", 4);
        evaluator.variables.put("y", -1);
        assertThat(tree.accept(evaluator), is(42));
        evaluator.variables.put("x", 2);
        evaluator.variables.put("y", 19);
        assertThat(tree.accept(evaluator), is(42));
        evaluator.variables.put("x", 6);
        evaluator.variables.put("y", 7);
        assertThat(tree.accept(evaluator), is(442));
        assertThat(BinOpCallSite.fallbackCalls, is(0));
    }

    @Test
    public void testFloatRepeat() {
        Node tree = cubic();
        evaluator.variables.put("x", 3.);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42.));
        resetFallbackCalls();
        evaluator.variables.put("x", 4.);
        evaluator.variables.put("y", -1);
        assertThat(tree.accept(evaluator), is(42.));
        evaluator.variables.put("x", 2.);
        evaluator.variables.put("y", 19);
        assertThat(tree.accept(evaluator), is(42.));
        evaluator.variables.put("x", 6.);
        evaluator.variables.put("y", 7);
        assertThat(tree.accept(evaluator), is(442.));
        assertThat(BinOpCallSite.fallbackCalls, is(0));
    }

    @Test
    public void testChangeType() {
        Node tree = cubic();
        evaluator.variables.put("x", 3);
        evaluator.variables.put("y", 3);
        assertThat(tree.accept(evaluator), is(42));

        resetFallbackCalls();
        evaluator.variables.put("x", 4);
        evaluator.variables.put("y", -1);
        assertThat(tree.accept(evaluator), is(42));
        assertThat(BinOpCallSite.fallbackCalls, is(0));

        // Suddenly y is a float
        evaluator.variables.put("x", 2);
        evaluator.variables.put("y", 19.);
        assertThat(tree.accept(evaluator), is(42.));
        assertThat(BinOpCallSite.fallbackCalls, is(2));

        // And now so is x
        resetFallbackCalls();
        evaluator.variables.put("x", 6.);
        evaluator.variables.put("y", 7.);
        assertThat(tree.accept(evaluator), is(442.));
        assertThat(BinOpCallSite.fallbackCalls, is(4));

        // And now y is an int again
        resetFallbackCalls();
        evaluator.variables.put("x", 6.);
        evaluator.variables.put("y", 7);
        assertThat(tree.accept(evaluator), is(442.));
        assertThat(BinOpCallSite.fallbackCalls, is(1));
    }

    /**
     * An interpreter for Python that works by walking the AST, and uses
     * look-up to find the type object corresponding to the Java native
     * type.
     */
    static class Evaluator implements Visitor<Object> {

        Map<String, Object> variables = new HashMap<>();
        Lookup lookup = lookup();

        @Override
        public Object visit_BinOp(expr.BinOp binOp) {
            // Evaluate sub-trees
            Object v = binOp.left.accept(this);
            Object w = binOp.right.accept(this);
            // Evaluate the node
            try {
                if (binOp.site == null) {
                    // This must be a first visit
                    binOp.site = Runtime.bootstrap(lookup, binOp);
                }
                MethodHandle mh = binOp.site.dynamicInvoker();
                return mh.invokeExact(v, w);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Implementation returned NotImplemented or equivalent
                throw notDefined(v, binOp.op, w);
            } catch (Throwable e) {
                // Something else went wrong
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public Object visit_Num(expr.Num num) {
            return num.n;
        }

        @Override
        public Object visit_Name(expr.Name name) {
            return variables.get(name.id);
        }

        /** Convenience method to create an IllegalArgumentException. */
        private static IllegalArgumentException notDefined(Object v,
                operator op, Object w) {
            String msg = "Operation %s not defined between %s and %s";
            String V = v.getClass().getSimpleName();
            String W = w.getClass().getSimpleName();
            return new IllegalArgumentException(
                    String.format(msg, op, V, W));
        }
    }

    static class BinOpCallSite extends MutableCallSite {

        final operator op;
        final Lookup lookup;
        final MethodHandle fallbackMH;

        static int fallbackCalls = 0;

        public BinOpCallSite(Lookup lookup, operator op)
                throws NoSuchMethodException, IllegalAccessException {
            super(Runtime.BINOP);
            this.op = op;
            this.lookup = lookup;
            fallbackMH = lookup().bind(this, "fallback", Runtime.BINOP);
            setTarget(fallbackMH);
        }

        @SuppressWarnings("unused")
        private Object fallback(Object v, Object w) throws Throwable {
            fallbackCalls += 1;
            Class<?> V = v.getClass();
            Class<?> W = w.getClass();
            MethodType mt = MethodType.methodType(Object.class, V, W);
            // MH to compute the result for these classes
            MethodHandle resultMH = Runtime.findBinOp(V, op, W);
            // MH for guarded invocation (becomes new target)
            MethodHandle guarded = makeGuarded(V, W, resultMH, fallbackMH);
            setTarget(guarded);
            // Compute the result for this case
            return resultMH.invokeExact(v, w);
        }

        /**
         * Adapt two method handles, one that computes the desired result
         * specialised to the given classes, and a fall-back appropriate
         * when the arguments (when the handle is invoked) are not the
         * given types.
         *
         * @param V Java class of left argument of operation
         * @param W Java class of right argument of operation
         * @param resultMH computes v op w, where v&in;V and w&in;W.
         * @param fallbackMH computes the result otherwise
         * @return method handle computing the result either way
         */
        private MethodHandle makeGuarded(Class<?> V, Class<?> W,
                MethodHandle resultMH, MethodHandle fallbackMH) {
            MethodHandle testV, testW, guardedForW, guarded;
            testV = Runtime.HAS_CLASS.bindTo(V);
            testW = Runtime.HAS_CLASS.bindTo(W);
            testW = dropArguments(testW, 0, Object.class);
            guardedForW = guardWithTest(testW, resultMH, fallbackMH);
            guarded = guardWithTest(testV, guardedForW, fallbackMH);
            return guarded;
        }
    }

    /** Runtime support for the interpreter. */
    static class Runtime {

        /** Support class mapping from Java classes to handlers. */
        private static final Map<Class<?>, TypeHandler> typeRegistry =
                new Hashtable<>();

        /** Look up <code>TypeHandler</code> for Java class. */
        public static final ClassValue<TypeHandler> handlers =
                new ClassValue<TypeHandler>() {

                    @Override
                    protected synchronized TypeHandler
                            computeValue(Class<?> c) {
                        return typeRegistry.get(c);
                    }
                };

        /** Look up <code>TypeHandler</code> for Java class. */
        public static TypeHandler typeFor(Class<?> c) {
            return handlers.get(c);
        }

        /** Assign <code>TypeHandler</code> for Java class (once only). */
        public static void registerTypeFor(Class<?> c, TypeHandler type) {
            if (typeRegistry.putIfAbsent(c, type) != null) {
                throw new IllegalArgumentException(
                        "Attempt to redefine type handling class " + c);
            }
        }

        /** A (static) method implementing a unary op has this type. */
        protected static final MethodType UOP;
        /** A (static) method implementing a binary op has this type. */
        protected static final MethodType BINOP;
        /** Handle of a method returning NotImplemented. */
        static final MethodHandle BINOP_NOT_IMPLEMENTED;
        /** Handle of a method testing result == NotImplemented. */
        static final MethodHandle IS_NOT_IMPLEMENTED;
        /** Handle of a method throwing if result == NotImplemented. */
        static final MethodHandle THROW_IF_NOT_IMPLEMENTED;
        /** Handle of Object.getClass. */
        static final MethodHandle GET_CLASS;
        /** Handle of a method for testing class equality. */
        static final MethodHandle CLASS_EQUALS;
        /** Handle of testing that an object has a particular class. */
        static final MethodHandle HAS_CLASS;
        /** Shorthand for <code>Object.class</code>. */
        static final Class<Object> O = Object.class;
        /** Shorthand for <code>Class.class</code>. */
        static final Class<?> C = Class.class;

        private static final Lookup lookup;

        static {
            lookup = lookup();
            UOP = MethodType.methodType(O, O);
            BINOP = MethodType.methodType(O, O, O);
            BINOP_NOT_IMPLEMENTED =
                    findStatic(Runtime.class, "notImplemented", BINOP);
            IS_NOT_IMPLEMENTED =
                    findStatic(Runtime.class, "isNotImplemented",
                            MethodType.methodType(boolean.class, O));
            THROW_IF_NOT_IMPLEMENTED = findStatic(Runtime.class,
                    "throwIfNotImplemented", UOP);
            GET_CLASS =
                    findVirtual(O, "getClass", MethodType.methodType(C));
            CLASS_EQUALS = findVirtual(C, "equals",
                    MethodType.methodType(boolean.class, O));
            // HAS_CLASS = filterArguments(CLASS_EQUALS, 0, GET_CLASS);
            HAS_CLASS = findStatic(Runtime.class, "classEquals",
                    MethodType.methodType(boolean.class, C, O));
        }

        /** Singleton object for return by unimplemented operations. */
        static final Object NotImplemented = new Object();

        /** True iff argument is NotImplemented. */
        static boolean isNotImplemented(Object u) {
            return NotImplemented.equals(u);
        }

        /**
         * Binary operation throwing NoSuchMethodError, use as dummy.
         *
         * @throws NoSuchMethodException
         */
        static Object notImplemented(Object v, Object w)
                throws NoSuchMethodException {
            throw new NoSuchMethodException();
        }

        /**
         * Throw if argument is NotImplemented, else return argument.
         *
         * @throws NoSuchMethodException if passed NotImplemented
         */
        static Object throwIfNotImplemented(Object u)
                throws NoSuchMethodException {
            if (NotImplemented.equals(u)) {
                throw new NoSuchMethodException();
            } else {
                return u;
            }
        }

        @SuppressWarnings("unused") // referenced as HAS_CLASS
        private static boolean classEquals(Class<?> clazz, Object obj) {
            return clazz == obj.getClass();
        }

        /**
         * Convenience function wrapping
         * {@link Lookup#findStatic(Class, String, MethodType)}, throwing
         * {@code RuntimeException} if the method cannot be found, an
         * unchecked exception, wrapping the real cause.
         *
         * @param refc class in which to find the method
         * @param name method name
         * @param type method type
         * @return handle to the method
         * @throws RuntimeException if the method was not found
         */
        static MethodHandle findStatic(Class<?> refc, String name,
                MethodType type) throws RuntimeException {
            try {
                return lookup.findStatic(refc, name, type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw lookupRTE(refc, name, type, false, e);
            }
        }

        /**
         * Convenience function wrapping
         * {@link Lookup#findVirtual(Class, String, MethodType)}, throwing
         * {@code RuntimeException} if the method cannot be found, an
         * unchecked exception, wrapping the real cause.
         *
         * @param refc class in which to find the method
         * @param name method name
         * @param type method type
         * @return handle to the method
         * @throws RuntimeException if the method was not found
         */
        static MethodHandle findVirtual(Class<?> refc, String name,
                MethodType type) throws RuntimeException {
            try {
                return lookup.findVirtual(refc, name, type);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw lookupRTE(refc, name, type, true, e);
            }
        }

        /** Convenience method to create a lookup RuntimeException. */
        private static RuntimeException lookupRTE(Class<?> refc,
                String name, MethodType type, boolean isVirtual,
                Throwable t) {
            final String modeString = isVirtual ? "virtual" : "static";
            String fmt = "In runtime looking up %s %s#%s with type %s";
            String msg = String.format(fmt, modeString,
                    refc.getSimpleName(), name, type);
            return new RuntimeException(msg, t);
        }

        /**
         * Bootstrap a simulated invokedynamic call site.
         *
         * @throws IllegalAccessException
         * @throws NoSuchMethodException
         */
        static CallSite bootstrap(Lookup lookup, expr.BinOp binOp)
                throws NoSuchMethodException, IllegalAccessException {
            return new BinOpCallSite(lookup, binOp.op);
        }

        /**
         * Provide (as a method handle) an appropriate implementation of
         * the given operation, between operands of two Java types,
         * conforming to Python delegation rules.
         *
         * @param vClass Java class of left operand
         * @param op operator to apply
         * @param wClass Java class of right operand
         * @return MH representing the operation
         * @throws NoSuchMethodException
         * @throws IllegalAccessException
         */
        static MethodHandle findBinOp(Class<?> vClass, operator op,
                Class<?> wClass)
                throws NoSuchMethodException, IllegalAccessException {
            TypeHandler V = Runtime.typeFor(vClass);
            TypeHandler W = Runtime.typeFor(wClass);
            MethodHandle mhV = V.findBinOp(vClass, op, wClass);
            if (W == V) {
                return mhV;
            }
            MethodHandle mhW = W.findBinOp(vClass, op, wClass);
            if (mhW == BINOP_NOT_IMPLEMENTED) {
                return mhV;
            } else if (mhV == BINOP_NOT_IMPLEMENTED) {
                return mhW;
            } else if (mhW.equals(mhV)) {
                return mhV;
            } else if (W.isSubtypeOf(V)) {
                return firstImplementer(mhW, mhV);
            } else {
                return firstImplementer(mhV, mhW);
            }
        }

        /**
         * An adapter for two method handles <code>a</code> and
         * <code>b</code> such that when invoked, first <code>a</code> is
         * invoked, then if it returns <code>NotImplemented</code>,
         * <code>b</code> is invoked on the same arguments. If it also
         * returns <code>NotImplemented</code> then
         * <code>NoSuchMethodException</code> will be thrown. This
         * corresponds to the way Python implements binary operations when
         * each operand offers a different implementation.
         */
        private static MethodHandle firstImplementer(MethodHandle a,
                MethodHandle b) {
            // apply_b = λ(x,y,z): b(y,z)
            MethodHandle apply_b = MethodHandles.filterReturnValue(
                    dropArguments(b, 0, O), THROW_IF_NOT_IMPLEMENTED);
            // keep_a = λ(x,y,z): x
            MethodHandle keep_a = dropArguments(identity(O), 1, O, O);
            // x==NotImplemented ? b(y,z) : a(y,z)
            MethodHandle guarded =
                    guardWithTest(IS_NOT_IMPLEMENTED, apply_b, keep_a);
            // The functions above apply to (a(y,z), y, z) thanks to:
            return foldArguments(guarded, a);
        }
    }

    /** Mapping from binary operation to function names. */
    enum BinOpInfo {

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
            // Must have same ordinal value.
            assert this.ordinal() == op.ordinal();
        }

        /** Get the information for the given {@link operator}. */
        static final BinOpInfo forOp(operator op) {
            return BinOpInfo.values()[op.ordinal()];
        }
    };

    /**
     * <code>TypeHandler</code> is the base class of the classes that
     * collect together the operations on each Python type. These
     * subclasses should be singletons.
     */
    static abstract class TypeHandler {

        /** A method implementing a binary operation has this type. */
        protected static final MethodType BINOP = Runtime.BINOP;
        /** Shorthand for <code>Object.class</code>. */
        static final Class<Object> O = Object.class;

        /**
         * Convenience function wrapping
         * {@link Lookup#findStatic(Class, String, MethodType)}, and typing
         * as a binary operation, returning null if the method cannot be
         * found.
         *
         * @param refc class in which to find the method
         * @param name method name
         * @param type method type
         * @return handle to the method or null
         */
        private MethodHandle findStaticOrNull(Class<?> refc, String name,
                MethodType type) {
            try {
                MethodHandle mh = lookup.findStatic(refc, name, type);
                return mh;
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return null;
            }
        }

        /**
         * Return the method handle of the implementation of
         * <code>v op w</code>, if one exists within this handler.
         *
         * @param vClass Java class of left operand
         * @param op operator to apply
         * @param wClass Java class of right operand
         * @return
         */
        public MethodHandle findBinOp(Class<?> vClass, operator op,
                Class<?> wClass) {
            String name = BinOpInfo.forOp(op).name;
            Class<?> here = this.getClass();

            // Look for an exact match with the actual types
            MethodType mt = MethodType.methodType(O, vClass, wClass);
            MethodHandle mh = findStaticOrNull(here, name, mt);

            if (mh == null) {
                return Runtime.BINOP_NOT_IMPLEMENTED;
            } else {
                return mh.asType(BINOP);
            }
        }

        /** The lookup rights object of the implementing class. */
        private final Lookup lookup;

        protected TypeHandler(Lookup lookup) {
            this.lookup = lookup;
        }

        /**
         * Whether this type is a sub-type of the other for the purposes of
         * method resolution.
         */
        public boolean isSubtypeOf(TypeHandler other) {
            return false;
        }
    }

    /**
     * Class defining the operations for a Java <code>Integer</code>, so as
     * to make it a Python <code>int</code>.
     */
    @SuppressWarnings(value = {"unused"})
    static class IntegerHandler extends TypeHandler {

        // @formatter:off
        IntegerHandler() { super(lookup()); }

        private static Object add(Integer v, Integer w) { return v+w; }
        private static Object sub(Integer v, Integer w) { return v-w; }
        private static Object mul(Integer v, Integer w) { return v*w; }
        private static Object div(Integer v, Integer w)
            { return v.doubleValue() / w.doubleValue(); }
        // @formatter:on
    }

    /**
     * Class defining the operations for a Java <code>Double</code>, so as
     * to make it a Python <code>float</code>.
     */
    @SuppressWarnings(value = {"unused"})
    static class DoubleHandler extends TypeHandler {

        // @formatter:off
        DoubleHandler() { super(lookup()); }

        private static Object add(Double v, Integer w) { return v+w; }
        private static Object add(Integer v, Double w) { return v+w; }
        private static Object add(Double v, Double w)  { return v+w; }
        private static Object sub(Double v, Integer w) { return v-w; }
        private static Object sub(Integer v, Double w) { return v-w; }
        private static Object sub(Double v, Double w)  { return v-w; }
        private static Object mul(Double v, Integer w) { return v*w; }
        private static Object mul(Integer v, Double w) { return v*w; }
        private static Object mul(Double v, Double w)  { return v*w; }
        private static Object div(Double v, Integer w) { return v/w; }
        private static Object div(Integer v, Double w) { return v/w; }
        private static Object div(Double v, Double w)  { return v/w; }
        // @formatter:on
    }

    // @formatter:off
    static class A {}
    static class AHandler extends TypeHandler {
        AHandler() { super(lookup()); }
        @SuppressWarnings(value = {"unused"})
        private static Object add(A v, A w) { return new A(); }
    }

    static class B {}
    static class BHandler extends TypeHandler {
        BHandler() { super(lookup()); }
        @SuppressWarnings(value = {"unused"})
        private static Object add(B v, B w) { return new B(); }
    }

    static class C {}
    @SuppressWarnings(value = {"unused"})
    static class CHandler extends TypeHandler {
        private CHandler() { super(lookup()); }
        @Override
        public boolean isSubtypeOf(TypeHandler other)
            { return other.getClass() == AHandler.class; }
        private static Object add(C v, A w) { return new C(); }
        private static Object add(A v, C w) { return new C(); }
        private static Object add(C v, C w) { return new C(); }
    }
    // @formatter:on

    // @formatter:off
    public static final operator Add = operator.Add;
    public static final operator Sub = operator.Sub;
    public static final operator Mult = operator.Mult;
    public static final operator Div = operator.Div;
    public static final expr_context Load = expr_context.Load;
    public static final expr Name(String id, expr_context ctx)
        {return new expr.Name(id, ctx); }
    public static final expr Num(Object n) {return new expr.Num(n); }
    public static final expr BinOp(expr left, operator op, expr right)
        {return new expr.BinOp(left, op, right); }
    // @formatter:on

}
