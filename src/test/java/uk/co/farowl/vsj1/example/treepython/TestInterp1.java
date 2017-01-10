package uk.co.farowl.vsj1.example.treepython;

import static java.lang.invoke.MethodHandles.lookup;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import uk.co.farowl.vsj1.TreePython.arg;
import uk.co.farowl.vsj1.TreePython.arguments;
import uk.co.farowl.vsj1.TreePython.expr;
import uk.co.farowl.vsj1.TreePython.expr_context;
import uk.co.farowl.vsj1.TreePython.keyword;
import uk.co.farowl.vsj1.TreePython.mod;
import uk.co.farowl.vsj1.TreePython.operator;
import uk.co.farowl.vsj1.TreePython.stmt;
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
    public void assignNameG() {
        PyCode code = new PyCode(assignments());
        SystemState interp = new SystemState();
        ThreadState tstate = interp.registerCurrentThread();
        Map<String, Object> globals = new HashMap<>();
        PyFrame frame = new ExecutionFrame(tstate, code, globals, globals);
        frame.eval();
        // eval(module, globals, globals);
        assertThat(globals.get("bar"), equalTo(42));
    }

    @Test
    public void assignNameGL() {
        PyCode code = new PyCode(assignments());
        SystemState interp = new SystemState();
        ThreadState tstate = interp.registerCurrentThread();
        Map<String, Object> globals = new HashMap<>();
        Map<String, Object> locals = new HashMap<>();
        // Define foo as a local variable
        locals.put("foo", null);
        PyFrame frame = new ExecutionFrame(tstate, code, globals, locals);
        frame.eval();
        // eval(module, globals, globals);
        assertThat(locals.get("foo"), equalTo(6));
        assertThat(globals.get("bar"), equalTo(42));
    }

    private Node nested() {

        // @formatter:off
        // qux = 9
        // a = 1
        // def f():
        //     global a, g
        //     b = 10 + a
        //     c = 20 + b
        //     def gg():
        //         global a
        //         nonlocal b, c
        //         d = 100 + b + a
        //         c = 20
        //         a = 2
        //     e = 30 + c
        //     g = gg
        // f()
        // g()

        Node module = Module( list(
            Assign(list(Name("qux", Store)), Num(9)),
            Assign(list(Name("a", Store)), Num(1)),
            FunctionDef(
                "f",
                arguments(list(), null, list(), list(), null, list()),
                list(
                    Global(list("a", "g")),
                    Assign(
                        list(Name("b", Store)),
                        BinOp(Num(10), Add, Name("a", Load))),
                    Assign(
                        list(Name("c", Store)),
                        BinOp(Num(20), Add, Name("b", Load))),
                    FunctionDef(
                        "gg",
                        arguments(
                            list(),
                            null,
                            list(),
                            list(),
                            null,
                            list()),
                        list(
                            Global(list("a")),
                            Nonlocal(list("b", "c")),
                            Assign(
                                list(Name("d", Store)),
                                BinOp(
                                    BinOp(
                                        Num(100),
                                        Add,
                                        Name("b", Load)),
                                    Add,
                                    Name("a", Load))),
                            Assign(list(Name("c", Store)), Num(20)),
                            Assign(list(Name("a", Store)), Num(2))),
                        list(),
                        null),
                    Assign(
                        list(Name("e", Store)),
                        BinOp(Num(30), Add, Name("c", Load))),
                    Assign(list(Name("g", Store)), Name("gg", Load))),
                list(),
                null),
            Expr(Call(Name("f", Load), list(), list())),
            Expr(Call(Name("g", Load), list(), list()))));
        // @formatter:on
        return module;
    }

    @Test
    public void identifyScope() {
        Node module = nested();
        PyCode code = new PyCode(module);
//        SystemState interp = new SystemState();
//        ThreadState tstate = interp.registerCurrentThread();
//        Map<String, Object> globals = new HashMap<>();
//        Map<String, Object> locals = new HashMap<>();

        SymbolVisitor visitor = new SymbolVisitor();
        module.accept(visitor);

//        locals.put("foo", 42);
//        assertThat("qux, f are local", locals.keySet(),
//                hasItems("qux", "f"));
//        assertThat(locals.keySet(), hasItem("f"));
//        assertThat(globals.keySet(), hasItem("a"));
    }

    /** Create a frame from the AST and execute it in the provided maps. */
    private static Object eval(Node ast, Map<String, Object> globals,
            Map<String, Object> locals) {
        PyCode code = new PyCode(ast);
        SystemState interp = new SystemState();
        ThreadState tstate = interp.registerCurrentThread();
        PyFrame frame = new ExecutionFrame(tstate, code, globals, locals);
        Object result = frame.eval();
        return result;
    }

    /** Hold the AST. (More later.) */
    private static class PyCode {

        final Node ast;

        final Object[] co_consts;
        final String[] co_names;
        final String[] co_varnames;
        final String[] co_freevars;
        final String[] co_cellvars;

        PyCode(Node ast) {
            this(ast, null, null, null, null, null);
        }

        public PyCode(Node ast, Object[] consts, String[] names,
                String[] varnames, String[] freevars, String[] cellvars) {
            this.ast = ast;
            this.co_consts = asValues(consts);
            this.co_names = asNames(names);
            this.co_varnames = asNames(varnames);
            this.co_freevars = asNames(freevars);
            this.co_cellvars = asNames(cellvars);
        }

        private String[] asNames(String[] names) {
            if (names == null) {
                return EMPTY_NAMES;
            } else {
                return names;
            }
        }

        private static final String[] EMPTY_NAMES = new String[] {};

        private Object[] asValues(Object[] names) {
            if (names == null) {
                return EMPTY_VALUES;
            } else {
                return names;
            }
        }

        private static final Object[] EMPTY_VALUES = new Object[] {};

        private <T> List<T> asList(T[] names) {
            if (names == null) {
                return Collections.emptyList();
            } else {
                return Arrays.asList(names);
            }
        }

    }

    /**
     * Visitor on the AST adding compile-time information to each node
     * about the binding of names. In this version, the information is
     * added externally through a Map created in construction and filled
     * during the traverse.
     */
    private static class SymbolVisitor implements Visitor<Void> {

        static class Block {

            final Block outer;
            /** Names used in expressions ("Load" context). */
            Set<String> used = new HashSet<>();
            /** Names assigned, deleted or otherwise bound. */
            Set<String> bound = new HashSet<>();
            /** Names appearing in a global declaration. */
            Set<String> declaredGlobal = new HashSet<>();
            /** Names appearing in a nonlocal declaration. */
            Set<String> declaredNonlocal = new HashSet<>();

            Block(Block outer) {
                this.outer = outer;
            }

        }

        protected Block block;
        final Map<Node, Block> blockMap;

        /**
         * Construct a SymbolVisitor to traverse an AST and create a
         */
        SymbolVisitor() {
            blockMap = new HashMap<>();
            block = new Block(null);    // Module block
        }

        /**
         * Get the generated map from nodes to the name-binding information generated by
         * visiting the tree. Only nodes that represent blocks (Module and
         * FunctionDef) will be keys in this map.
         */
        public Map<Node, Block> getBlockMap() {
            return blockMap;
        }

        /** Visit all non-null nodes in some collection. */
        private Void visitAll(Collection<? extends Node> nodes) {
            for (Node node : nodes) {
                visitIfNotNull(node);
            }
            return null;
        }

        /** Visit node if not null. */
        private Void visitIfNotNull(Node node) {
            return node == null ? null : node.accept(this);
        }

        /**
         * The visitor is normally applied to a
         */
        @Override
        public Void visit_Module(mod.Module module) {
            // Use block allocated in constructor
            blockMap.put(module, block);
            // Process the statements in the block
            visitAll(module.body);
            // Restore context
            block = block.outer;
            return null;
        }

        @Override
        public Void visit_FunctionDef(stmt.FunctionDef functionDef) {
            // Start a nested block
            block = new Block(block);
            blockMap.put(functionDef, block);
            // Process the statements in the block
            visitAll(functionDef.body);
            // Restore context
            block = block.outer;
            block.bound.add(functionDef.name);
            return null;
        }

        @Override
        public Void visit_Delete(stmt.Delete delete) {
            return visitAll(delete.targets);
        }

        @Override
        public Void visit_Assign(stmt.Assign assign) {
            assign.value.accept(this);
            return visitAll(assign.targets);
        }

        @Override
        public Void visit_Global(stmt.Global global) {
            block.declaredGlobal.addAll(global.names);
            return null;
        }

        @Override
        public Void visit_Nonlocal(stmt.Nonlocal nonlocal) {
            block.declaredNonlocal.addAll(nonlocal.names);
            return null;
        }

        @Override
        public Void visit_Expr(stmt.Expr expr) {
            return expr.value.accept(this);
        }

        @Override
        public Void visit_BinOp(expr.BinOp binOp) {
            binOp.left.accept(this);
            binOp.right.accept(this);
            return null;
        }

        @Override
        public Void visit_UnaryOp(expr.UnaryOp unaryOp) {
            return unaryOp.operand.accept(this);
        }

        @Override
        public Void visit_Call(expr.Call call) {
            call.func.accept(this);
            return visitAll(call.args);
        }

        @Override
        public Void visit_Num(expr.Num _Num) {
            return null;    // No names here
        }

        @Override
        public Void visit_Name(expr.Name name) {
            if (name.ctx == Load) {
                block.used.add(name.id);
            } else {
                block.bound.add(name.id);
            }
            return null;
        }

        @Override
        public Void visit_arguments(arguments arguments) {
            visitAll(arguments.args);
            arg vararg = arguments.vararg;
            if (vararg != null) {
                vararg.accept(this);
            }
            visitAll(arguments.kwonlyargs);
            visitAll(arguments.kw_defaults);
            arg kwarg = arguments.kwarg;
            if (kwarg != null) {
                kwarg.accept(this);
            }
            return visitAll(arguments.defaults);
        }

        @Override
        public Void visit_arg(arg arg) {
            block.bound.add(arg.arg); // aaaargh!!!
            return null;
        }

        @Override
        public Void visit_keyword(keyword keyword) {
            block.bound.add(keyword.arg);
            return keyword.value.accept(this);
        }
    }

    /**
     * PyFrame is where the interpreter for Python actually runs. In this
     * implementation it works by walking the fragment of the AST from
     * which the frame was created.
     */
    private static abstract class PyFrame {

        /** Frames form a stack by chaining through the back pointer. */
        PyFrame f_back;
        /** Code this frame is to execute. */
        final PyCode f_code;
        /** Global context (name space) of execution. */
        Map<String, Object> f_globals;
        /** Local context (name space) of execution. */
        Map<String, Object> f_locals;

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
        abstract Object eval();

    }

    private static class ExecutionFrame extends PyFrame
            implements Visitor<Object> {

        ExecutionFrame(ThreadState tstate, PyCode code,
                Map<String, Object> globals, Map<String, Object> locals) {
            super(tstate, code, globals, locals);
        }

        /** Lookup rights of this frame. */
        Lookup lookup = lookup();

        /** Execute the code in this frame. */
        @Override
        Object eval() {
            return f_code.ast.accept(this);
        }

        @Override
        public Object visit_Module(mod.Module module) {
            List<stmt> body = module.body;
            for (stmt s : body) {
                s.accept(this);
            }
            return null;
        }

        @Override
        public Object visit_FunctionDef(stmt.FunctionDef functionDef) {
            String name = functionDef.name;

            try {
                // TODO: look up the code object
                // TODO: look up the symbol table

                return null;
            } finally {
                // // TODO: Restore current symbol table (frame stack?).
            }
        }

        @Override
        public Object visit_Assign(stmt.Assign assign) {
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

        @Override
        public Object visit_arguments(arguments _arguments) {
            // Arguments are not used in our function definitions yet
            return null;
        }

        @Override
        public Object visit_arg(arg _arg) {
            // Arguments are not used in our function definitions yet
            return null;
        }

        @Override
        public Object visit_keyword(keyword _keyword) {
            // Arguments are not used in our function definitions yet
            return null;
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

    private static stmt FunctionDef(String name, arguments args,
            List<?> body, List<?> decorator_list, expr returns) {
        return new stmt.FunctionDef(name, args, cast(body, stmt.class),
                cast(decorator_list, expr.class), returns);}
    private static stmt Delete(List<?> targets)
        { return new stmt.Delete(cast(targets, expr.class)); }
    private static stmt Assign(List<?> targets, expr value)
        { return new stmt.Assign(cast(targets, expr.class), value); }
    private static stmt Global(List<?> names)
        { return new stmt.Global(cast(names, String.class)); }
    private static stmt Nonlocal(List<?> names)
        { return new stmt.Nonlocal(cast(names, String.class)); }
    private static stmt Expr(expr value)
        { return new stmt.Expr(value); }

    private static expr BinOp(expr left, operator op, expr right)
        { return new expr.BinOp(left, op, right); }
    private static expr UnaryOp(unaryop op, expr operand)
        { return new expr.UnaryOp(op, operand); }
    private static expr Call(expr func, List<?> args, List<?> keywords){
        return new expr.Call(func, cast(args, expr.class),
                cast(keywords, keyword.class)); }
    private static expr Num(Object n)
        { return new expr.Num(n); }
    private static expr Name(String id, expr_context ctx)
        { return new expr.Name(id, ctx); }

    private static arguments arguments(List<?> args, arg vararg,
            List<?> kwonlyargs, List<?> kw_defaults, arg kwarg,
            List<?> defaults) {
        return new arguments(cast(args, arg.class), vararg,
                cast(kwonlyargs, arg.class), cast(kw_defaults, expr.class),
                kwarg, cast(defaults, expr.class));
        }
    // @formatter:on

}
