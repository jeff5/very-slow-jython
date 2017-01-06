package uk.co.farowl.vsj1.example.treepython;

import static java.lang.invoke.MethodHandles.lookup;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.co.farowl.vsj1.BigIntegerOperations;
import uk.co.farowl.vsj1.BinOpCallSite;
import uk.co.farowl.vsj1.DoubleOperations;
import uk.co.farowl.vsj1.IntegerOperations;
import uk.co.farowl.vsj1.Operations.BinOpInfo;
import uk.co.farowl.vsj1.Operations.UnaryOpInfo;
import uk.co.farowl.vsj1.TreePython.Node;
import uk.co.farowl.vsj1.TreePython.Visitor;
import uk.co.farowl.vsj1.TreePython.expr;
import uk.co.farowl.vsj1.TreePython.expr_context;
import uk.co.farowl.vsj1.TreePython.mod;
import uk.co.farowl.vsj1.TreePython.mod.Module;
import uk.co.farowl.vsj1.TreePython.operator;
import uk.co.farowl.vsj1.TreePython.stmt;
import uk.co.farowl.vsj1.TreePython.stmt.Assign;
import uk.co.farowl.vsj1.TreePython.unaryop;
import uk.co.farowl.vsj1.UnaryOpCallSite;

public class TestInterp1 {

    @BeforeClass
    public static void setUpClass() {
        // In case the preceding test didn't tear it down.
        // This is what's wrong with static data. :(
        Py.deregisterOps();
        // Built-in types
        Py.registerOps(new IntegerOperations(), Integer.class);
        Py.registerOps(new BigIntegerOperations(), BigInteger.class);
        Py.registerOps(new DoubleOperations(), Float.class, Double.class);
    }

    @AfterClass
    public static void tearDownClass() {
        Py.deregisterOps();
    }

    // Interpreter to execute the code.
    SystemState interpreter;

    @Before
    public void setUp() {
        // Create a visitor to execute the code.
        interpreter = new SystemState();
    }

    /** Initialise statistics gathering. */
    private static void resetFallbackCalls() {
        BinOpCallSite.fallbackCalls = 0;
        UnaryOpCallSite.fallbackCalls = 0;
    }

    /** Number of times we took the slow path at a unary call site. */
    private static int unaryFallbackCalls() {
        return UnaryOpCallSite.fallbackCalls;
    }

    /** Number of times we took the slow path at a binary call site. */
    private static int binaryFallbackCalls() {
        return BinOpCallSite.fallbackCalls;
    }

    private Node assignments() {
        // @formatter:off
        Node module = Module( list(
            // foo = 6
            Assign(list(Name("foo", Store)), Num(6)),
            // bar = foo * 7
            Assign(
                list(Name("bar", Store)),
                BinOp(
                    Name("foo", Load),
                    Mult,
                    Num(7)))
        ));
        // @formatter:on
        return module;
    }

    @Test
    public void assignGlobal() {
        PyCode code = new PyCode(assignments());
        SystemState interp = new SystemState();
        ThreadState tstate = interp.registerCurrentThread();
        Map<String, Object> globals = new HashMap<>();
        PyFrame frame = new PyFrame(tstate, code, globals, globals);
        frame.eval();
        // eval(module, globals, globals);
        assertThat(globals.get("bar"), is(42));
    }

    @Test
    public void assignGlobalLocal() {
        PyCode code = new PyCode(assignments());
        SystemState interp = new SystemState();
        ThreadState tstate = interp.registerCurrentThread();
        Map<String, Object> globals = new HashMap<>();
        Map<String, Object> locals = new HashMap<>();
        // Define foo as a local variable
        locals.put("foo", null);
        PyFrame frame = new PyFrame(tstate, code, globals, locals);
        frame.eval();
        // eval(module, globals, globals);
        assertThat(locals.get("foo"), is(6));
        assertThat(globals.get("bar"), is(42));
    }

    /** Create a frame from the AST and execute it in the provided maps. */
    private static Object eval(Node ast, Map<String, Object> globals,
            Map<String, Object> locals) {
        PyCode code = new PyCode(ast);
        SystemState interp = new SystemState();
        ThreadState tstate = interp.registerCurrentThread();
        PyFrame frame = new PyFrame(tstate, code, globals, locals);
        Object result = frame.eval();
        return result;
    }

    /** Hold the AST. (More later.) */
    private static class PyCode {

        final Node ast;

        PyCode(Node ast) {
            this.ast = ast;
        }
    }

    /**
     * PyFrame is where the interpreter for Python actually runs. In this
     * implementation it works by walking the fragment of the AST from
     * which the frame was created.
     */
    private static class PyFrame implements Visitor<Object> {

        /** Frames form a stack by chaining through the back pointer. */
        PyFrame f_back;
        /** Code this frame is to execute. */
        final PyCode f_code;
        /** Global context (name space) of execution. */
        Map<String, Object> f_globals;
        /** Local context (name space) of execution. */
        Map<String, Object> f_locals;

        /** Lookup rights of this frame. */
        Lookup lookup = lookup();

        /**
         * Create a <code>PyFrame</code> for which the back-reference is
         * the current frame of the identified thread state, but do not
         * (yet) push it onto that thread's frame stack. No argument may be
         * <code>null</code>, although the current frame (in
         * <code>tstate</code>) may be <code>null</code>.
         *
         * @param tstate thread whose stack to reference
         * @param code that this frame executes
         * @param globals global name space
         * @param locals local name space
         */
        PyFrame(ThreadState tstate, PyCode code,
                Map<String, Object> globals, Map<String, Object> locals) {
            this.f_code = code;
            f_back = tstate.frame;
            this.f_globals = globals;
            this.f_locals = locals;
        }

        /** Execute the code in this frame. */
        Object eval() {
            return f_code.ast.accept(this);
        }

        @Override
        public Object visit_Module(Module module) {
            List<stmt> body = module.body;
            for (stmt s : body) {
                s.accept(this);
            }
            return null;
        }

        @Override
        public Object visit_Assign(Assign assign) {
            List<expr> targets = assign.targets;
            if (targets.size() != 1) {
                throw notSupported("multiple assignments");
            }
            Object value = assign.value.accept(this);
            Object target = targets.get(0);
            if (target instanceof expr.Name) {
                // Assignment to simple name
                store((expr.Name)target, value);
            } else {
                throw notSupported("assignment target type");
            }
            return null;
        }

        private void store(expr.Name target, Object value) {
            String id = target.id;
            if (f_locals != f_globals && f_locals.containsKey(id)) {
                // locals has been primed with all local names
                f_locals.put(id, value);
            } else {
                f_globals.put(id, value);
            }
        }

        @Override
        public Object visit_BinOp(expr.BinOp binOp) {
            // Evaluate sub-trees
            Object v = binOp.left.accept(this);
            Object w = binOp.right.accept(this);
            // Evaluate the node
            try {
                if (binOp.site == null) {
                    // This must be a first visit
                    binOp.site = Py.bootstrap(lookup, binOp);
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
        public Object visit_UnaryOp(expr.UnaryOp unaryOp) {
            // Evaluate sub-tree
            Object v = unaryOp.operand.accept(this);
            // Evaluate the node
            try {
                if (unaryOp.site == null) {
                    // This must be a first visit
                    unaryOp.site = Py.bootstrap(lookup, unaryOp);
                }
                MethodHandle mh = unaryOp.site.dynamicInvoker();
                return mh.invokeExact(v);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Implementation returned NotImplemented or equivalent
                throw notDefined(v, unaryOp.op);
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
            String id = name.id;
            Object value = f_locals.get(id);
            if (value == null && f_globals != f_locals) {
                value = f_globals.get(id);
            }
            return value;
        }

        /** Binary operation: create an IllegalArgumentException. */
        private static IllegalArgumentException notDefined(Object v,
                operator op, Object w) {
            String msg =
                    "unsupported operand type(s) for %s : '%s' and '%s'";
            String s = BinOpInfo.forOp(op).symbol;
            String V = v.getClass().getSimpleName();
            String W = w.getClass().getSimpleName();
            return new IllegalArgumentException(
                    String.format(msg, s, V, W));
        }

        /** Unary operation: create an IllegalArgumentException. */
        private static IllegalArgumentException notDefined(Object v,
                unaryop op) {
            String msg = "bad operand type for unary %s : '%s'";
            String s = UnaryOpInfo.forOp(op).symbol;
            String V = v.getClass().getSimpleName();
            return new IllegalArgumentException(String.format(msg, s, V));
        }

        /** Unsupported operation: create an IllegalArgumentException. */
        private static IllegalArgumentException notSupported(String s) {
            String msg = "%s not supported";
            return new IllegalArgumentException(String.format(msg, s));
        }

    }

    /** Per-thread state. */
    private static class ThreadState {

        /** Interpreter to which this <code>ThreadState</code> belongs. */
        final SystemState interp;
        /** Top of execution frame stack. */
        PyFrame frame;

        /**
         * Construct a ThreadState in the context of an owning interpreter
         * and the current Java <code>Thread</code>.
         */
        private ThreadState(SystemState interp) {
            this.interp = interp;
            this.frame = null;
            interp.threads.add(this);
        }
    }

    /** State of an interpreter, shared amongst (identified) threads. */
    private static class SystemState {

        final Set<ThreadState> threads;

        /** Create an instance of the interpreter. */
        SystemState() {
            threads = new HashSet<>();
        }

        public ThreadState registerCurrentThread() {
            ThreadState tstate = new ThreadState(this);
            threads.add(tstate);
            return tstate;
        }

    }

    /** Extensions to the runtime (placeholder) */
    static class Py extends uk.co.farowl.vsj1.Py {}

    // ------------------------------------------------------------------
    // Literal AST: helper methods
    // ------------------------------------------------------------------

    /**
     * Helper method for the literal AST: create a list from a sequence of
     * arguments.
     *
     * @see #cast(List, Class)
     * @param values array of any object
     * @return list of the same
     */
    private static List<?> list(Object... values) {
        return Arrays.asList(values);
    }

    /**
     * Safely convert a list of objects to a list of a particular element
     * class. This is a helper function for building the AST from literal
     * functions. Wherever a list is provided in the the AST, it will have
     * compile-time type of only <code>List&lt;?&gt;</code>. However, the
     * grammar guarantees it a list of a particular type, according to
     * context. This method builds a list of the required type.
     *
     * @param values the elements, all instances of elementClass.
     * @param elementClass the actual type of the values.
     * @return a copy of the list with given element-type, statically.
     */
    private static <T> List<T> cast(List<?> values,
            Class<T> elementClass) {
        List<T> list = new ArrayList<>(values.size());
        for (Object v : values) {
            list.add(elementClass.cast(v));
        }
        return list;
    }

    // @formatter:off

    private static final operator Add = operator.Add;
    private static final operator Sub = operator.Sub;
    private static final operator Mult = operator.Mult;
    private static final operator Div = operator.Div;
    private static final unaryop UAdd = unaryop.UAdd;
    private static final unaryop USub = unaryop.USub;
    private static final expr_context Load = expr_context.Load;
    private static final expr_context Store = expr_context.Store;

    private static mod Module(List<?> body)
        { return new mod.Module(cast(body, stmt.class)); }

    private static stmt Delete(List<?> targets)
        { return new stmt.Delete(cast(targets, expr.class)); }
    private static stmt Assign(List<?> targets, expr value)
        { return new stmt.Assign(cast(targets, expr.class), value); }
    private static stmt Global(List<?> names)
        { return new stmt.Global(cast(names, String.class)); }
    private static stmt Nonlocal(List<?> names)
        { return new stmt.Nonlocal(cast(names, String.class)); }

    private static expr BinOp(expr left, operator op, expr right)
        { return new expr.BinOp(left, op, right); }
    private static expr UnaryOp(unaryop op, expr operand)
        { return new expr.UnaryOp(op, operand); }
    private static expr Num(Object n)
        { return new expr.Num(n); }
    private static expr Name(String id, expr_context ctx)
        { return new expr.Name(id, ctx); }

    // @formatter:on

}
