package uk.co.farowl.vsj1.example;

import static java.lang.invoke.MethodHandles.arrayElementGetter;
import static java.lang.invoke.MethodHandles.arrayElementSetter;
import static java.lang.invoke.MethodHandles.collectArguments;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.co.farowl.vsj1.BigIntegerOperations;
import uk.co.farowl.vsj1.BinOpCallSite;
import uk.co.farowl.vsj1.DoubleOperations;
import uk.co.farowl.vsj1.IntegerOperations;
import uk.co.farowl.vsj1.LongOperations;
import uk.co.farowl.vsj1.Operations.BinOpInfo;
import uk.co.farowl.vsj1.Operations.UnaryOpInfo;
import uk.co.farowl.vsj1.TreePython;
import uk.co.farowl.vsj1.TreePython.AbstractVisitor;
import uk.co.farowl.vsj1.TreePython.Node;
import uk.co.farowl.vsj1.TreePython.Visitor;
import uk.co.farowl.vsj1.TreePython.arg;
import uk.co.farowl.vsj1.TreePython.arguments;
import uk.co.farowl.vsj1.TreePython.expr;
import uk.co.farowl.vsj1.TreePython.expr.Call;
import uk.co.farowl.vsj1.TreePython.expr_context;
import uk.co.farowl.vsj1.TreePython.keyword;
import uk.co.farowl.vsj1.TreePython.mod;
import uk.co.farowl.vsj1.TreePython.operator;
import uk.co.farowl.vsj1.TreePython.stmt;
import uk.co.farowl.vsj1.TreePython.stmt.Assign;
import uk.co.farowl.vsj1.TreePython.stmt.Expr;
import uk.co.farowl.vsj1.TreePython.stmt.FunctionDef;
import uk.co.farowl.vsj1.TreePython.stmt.Return;
import uk.co.farowl.vsj1.TreePython.unaryop;
import uk.co.farowl.vsj1.UnaryOpCallSite;

/**
 * This test program experiments with some changes to simple function
 * calling, in an AST-based interpreter.
 */
public class TestInterp5 {

    @BeforeClass
    public static void setUpClass() {
        // Built-in types
        Py.registerOps(new IntegerOperations(), Byte.class, Short.class,
                Integer.class);
        Py.registerOps(new LongOperations(), Long.class);
        Py.registerOps(new BigIntegerOperations(), BigInteger.class);
        Py.registerOps(new DoubleOperations(), Float.class, Double.class);
    }

    @AfterClass
    public static void tearDownClass() {
        Py.deregisterOps();
    }

    // Visitor to execute the code.
    // Evaluator evaluator;

    @Before
    public void setUp() {
        // Create a visitor to execute the code.
        // evaluator = new Evaluator();
    }

    private static void resetFallbackCalls() {
        BinOpCallSite.fallbackCalls = 0;
        UnaryOpCallSite.fallbackCalls = 0;
    }

    private static int unaryFallbackCalls() {
        return UnaryOpCallSite.fallbackCalls;
    }

    private static int binaryFallbackCalls() {
        return BinOpCallSite.fallbackCalls;
    }

    /** Holder for objects appearing in the closure of a function. */
    private static class Cell {

        /** The object reference. */
        Object obj;

        /** Construct cell from object reference. */
        Cell(Object obj) {
            this.obj = obj;
        }

        @Override
        public String toString() {
            return String.format("<cell [%.80s]>", obj);
        }

        static final Cell[] EMPTY_ARRAY = new Cell[0];
    }

    /** Interface presented by callable objects. */
    interface PyCallable {

        Object call(Frame back, Object[] args);
    }

    /** Interface presented by call-optimised objects. */
    interface PyGetFrame {

        ExecutionFrame getFrame(Frame back);
    }

    /**
     * Function object as created by a function definition and subsequently
     * called.
     */
    private static class Function implements PyGetFrame {

        final String name;
        final Code code;
        final Map<String, Object> globals;
        final Cell[] closure;

        /**
         * Create a function from code and the globals in context, with a
         * closure.
         */
        Function(Code code, Map<String, Object> globals, String name,
                Cell[] closure) {
            this.code = code;
            this.globals = globals;
            this.name = name;
            this.closure = (closure != null && closure.length > 0)
                    ? closure : Cell.EMPTY_ARRAY;
        }

        /**
         * Create the frame for executing this function, without arguments
         * filled in, but do not begin execution.
         */
        @Override
        public ExecutionFrame getFrame(Frame back) {
            // Execution occurs in a new frame
            return new ExecutionFrame(back, code, globals, closure);
        }

        @Override
        public String toString() {
            return String.format("<function %s>", name);
        }

    }

    /**
     * An older implementation of the Function object, without the
     * {@link Function#getFrame(Frame)} optimisation.
     */
    private static class OtherCallable implements PyCallable {

        final String name;
        final Code code;
        final Map<String, Object> globals;
        final Cell[] closure;

        /**
         * Create a function-like object from code and the globals in
         * context, with a closure.
         */
        OtherCallable(Code code, Map<String, Object> globals, String name,
                Cell[] closure) {
            this.code = code;
            this.globals = globals;
            this.name = name;
            this.closure = closure;
        }

        @Override
        public Object call(Frame back, Object[] args) {
            // Execution occurs in a new frame
            ExecutionFrame frame =
                    new ExecutionFrame(back, code, globals, closure);
            // In which we initialise the parameters from arguments
            frame.setArguments(args);
            return frame.eval();
        }

        @Override
        public String toString() {
            return String.format("<callable %s>", name);
        }
    }

    /** Extensions to the runtime */
    static class Py extends uk.co.farowl.vsj1.Py {

        static final Object NONE = new Object() {

            @Override
            public String toString() {
                return "None";
            }
        };

        /**
         * Whatever set-up is necessary for the environment.
         */
        public static void initialise() {}

    }

    /**
     * A <code>PyFrame</code> is the context for the execution of code.
     */
    private static abstract class Frame {

        /** Frames form a stack by chaining through the back pointer. */
        final Frame f_back;
        /** Code this frame is to execute. */
        final Code f_code;
        /** Built-in objects */
        final Map<String, Object> f_builtins;
        /** Global context (name space) of execution. */
        final Map<String, Object> f_globals;
        /** Local context (name space) of execution. (Assign if needed.) */
        Map<String, Object> f_locals = null;

        /**
         * Partial constructor, leaves {@link #f_locals},
         * {@link #fastlocals} and {@link #cellvars} <code>null</code>.
         *
         * @param back calling frame or <code>null</code> if first frame
         * @param code that this frame executes
         * @param globals global name space
         */
        Frame(Frame back, Code code, Map<String, Object> globals) {
            f_code = code;
            f_back = back;
            f_globals = globals;
            // globals.get("__builtins__") ought to be a module with dict:
            f_builtins = new HashMap<>();
        }

        /**
         * Foundation constructor on which subclass constructors rely.
         *
         * <ul>
         * <li>The local variables will be the specified dictionary.</li>
         * <li>The {@link #fastlocals} will be allocated if the code has
         * the traits {@link Code.Trait#NEWLOCALS} and
         * {@link Code.Trait#OPTIMIZED}.</li>
         * <li>A new {@link #f_locals} will be allocated if the code has
         * the trait {@link Code.Trait#NEWLOCALS} but not
         * {@link Code.Trait#OPTIMIZED}.</li>
         * <li>Otherwise, {@link #f_locals} is as specified by the
         * <code>locals</code> or <code>globals</code> arguments.</li>
         * </ul>
         *
         * @param back calling frame or <code>null</code> if first frame
         * @param code that this frame executes
         * @param globals global name space
         * @param locals local name space (maybe <code>==globals</code>)
         */
        protected Frame(Frame back, Code code, Map<String, Object> globals,
                Map<String, Object> locals) {

            // Initialise the basics.
            this(back, code, globals);

            // The need for a dictionary of locals depends on the code
            EnumSet<Code.Trait> traits = code.traits;
            if (traits.contains(Code.Trait.NEWLOCALS)) {
                // Ignore locals argument
                if (traits.contains(Code.Trait.OPTIMIZED)) {
                    // We can create it later but probably won't need to
                    f_locals = null;
                } else {
                    f_locals = new HashMap<>();
                }
            } else {
                // Use supplied locals or default to same as globals
                f_locals = (locals != null) ? locals : globals;
            }
        }

        /** Execute the code in this frame. */
        abstract Object eval();
    }

    /**
     * A <code>ExecutionFrame</code> is where the interpreter for Python
     * actually runs. In this implementation it works by walking the
     * fragment of the AST from which the frame was created.
     */
    private static class ExecutionFrame extends Frame
            implements Visitor<Object> {

        /** Cells for free variables (used not created in this code). */
        final Cell[] freevars;
        /** Cells for local cell variables (created in this code). */
        final Cell[] cellvars;
        /** Local simple variables (corresponds to "varnames"). */
        final Object[] fastlocals;

        /** Assigned eventually by return statement (or stays None). */
        Object returnValue = Py.NONE;
        /** Access rights of this class. */
        Lookup lookup = lookup();

        /**
         * Constructor for a frame suitable to run module-level code.
         *
         * In code compiled for the module-level:
         * <ul>
         * <li>The local variables will be the same dictionary as the
         * globals.</li>
         * <li>There can be no free variables, therefore no closure
         * given.</li>
         * <li>Cell variables are not normally created.</li>
         * </ul>
         *
         * @param back calling frame or <code>null</code> if first frame
         * @param code that this frame executes
         * @param globals global name space
         */
        ExecutionFrame(Frame back, Code code,
                Map<String, Object> globals) {
            this(back, code, globals, (Map<String, Object>)null);
        }

        /**
         * Create a <code>ExecutionFrame</code>, which is a
         * <code>Frame</code> with the storage and mechanism to execute
         * code. The constructor specifies the back-reference to the
         * current frame or <code>null</code> when this frame is to be the
         * first in the stack. No other argument may be <code>null</code>.
         *
         * The caller specifies the local variables dictionary explicitly:
         * it may be the same as the <code>globals</code>.
         *
         * @param back current stack top or <code>null</code> if none
         * @param code that this frame executes
         * @param globals global name space
         * @param locals local name space
         */
        ExecutionFrame(Frame back, Code code, Map<String, Object> globals,
                Map<String, Object> locals) {
            super(back, code, globals, locals);
            fastlocals = null;
            freevars = null;
            cellvars = null;
        }

        /**
         * Constructor suitable to run the code of a function (optionally
         * with a closure).
         *
         * <ul>
         * <li>The local variables will be the specified dictionary.</li>
         * <li>The {@link #fastlocals} will be allocated if the code has
         * the traits {@link Code.Trait#NEWLOCALS} and
         * {@link Code.Trait#OPTIMIZED}.</li>
         * <li>A new {@link #f_locals} will be allocated if the code has
         * the trait {@link Code.Trait#NEWLOCALS} but not
         * {@link Code.Trait#OPTIMIZED}.</li>
         * <li>Otherwise, {@link #f_locals} is as specified by the
         * <code>locals</code> or <code>globals</code> arguments.</li>
         * <li>If the code specifies free variables, the closure must
         * supply them.</li>
         * <li>Cell variables will be created according to the code.</li>
         * </ul>
         *
         * @param back calling frame
         * @param code that this frame executes
         * @param globals global name space
         * @param locals local name space (maybe <code>==globals</code>)
         * @param closure variables free here but bound in an enclosing
         *            block or <code>null</code> if no closure
         */
        ExecutionFrame(Frame back, Code code, Map<String, Object> globals,
                Cell[] closure) {
            super(back, code, globals, null);

            /*
             * We only need the fast locals array if the code uses
             * optimised load and store mechanisms.
             */
            // XXX How do we store/load local variables when there is both
            // a dictionary realisation of them and a fastlocals and/or
            // cellvars array?
            if (code.traits.contains(Code.Trait.OPTIMIZED)) {
                fastlocals = new Object[code.nlocals];
            } else {
                // null since never accessed?
                fastlocals = null;
            }

            // Names free in this code form the function closure.
            this.freevars = closure;

            // Create cell variables locally for nested blocks to access.
            int ncells = code.co_cellvars.length;
            if (ncells > 0) {
                // Initialise the cells that have to be created here
                cellvars = new Cell[ncells];
                for (int i = 0; i < ncells; i++) {
                    cellvars[i] = new Cell(null);
                }
            } else {
                cellvars = Cell.EMPTY_ARRAY;
            }
        }

        /**
         * Execute the code in this frame. In the AST-based implementation,
         * we execute a list of {@link stmt} ASTs and return the final
         * value of {@link #returnValue}.
         */
        @Override
        Object eval() {
            /*
             * All arguments are placed in plain local variables, but
             * sometimes they have to be cell variables instead. The
             * information is in the symbol table. Imagine this to be
             * generated code.
             */
            SymbolTable table = f_code.ast.symbolTable;
            for (int i = 0; i < f_code.argcount; i++) {
                String name = f_code.co_varnames[i];
                SymbolTable.Symbol symbol = table.lookup(name);
                if (symbol.scope == SymbolTable.ScopeType.CELL) {
                    cellvars[symbol.cellIndex].obj = fastlocals[i];
                }
            }

            // Execute the body of statements
            for (stmt s : f_code.ast.body) {
                s.accept(this);
            }
            return returnValue;
        }

        @Override
        public Object visit_Name(expr.Name name) {
            if (name.site == null) {
                // This must be a first visit
                try {
                    name.site = new ConstantCallSite(loadMH(name.id));
                } catch (ReflectiveOperationException e) {
                    throw linkageFailure(name.id, name, e);
                }
            }

            MethodHandle mh = name.site.dynamicInvoker();

            try {
                return mh.invokeExact(this);
            } catch (Throwable e) {
                throw invocationFailure("=" + name.id, name, e);
            }
        }

        /** Search locals, globals and built-ins, in that order. */
        @SuppressWarnings("unused")
        private Object loadNameLGB(String id) {
            Object v = f_locals.get(id);
            if (v == null) {
                v = f_globals.get(id);
            }
            if (v == null) {
                v = f_builtins.get(id);
            }
            return v;
        }

        /** Search globals and built-ins, in that order. */
        @SuppressWarnings("unused")
        private Object loadNameGB(String id) {
            Object v = f_globals.get(id);
            if (v == null) {
                v = f_builtins.get(id);
            }
            return v;
        }

        /**
         * Method handle to bootstrap a simulated
         * <code>invokedynamic</code> call site for an identifier in
         * context of this frame's code object.
         *
         * @param id identifier to resolve
         * @return method handle to load the value
         * @throws ReflectiveOperationException
         * @throws IllegalAccessException
         */
        private MethodHandle loadMH(String id)
                throws ReflectiveOperationException,
                IllegalAccessException {

            // How is the id used?
            SymbolTable.Symbol symbol = f_code.ast.symbolTable.lookup(id);

            // Mechanism depends on scope & OPTIMIZED trait
            switch (symbol.scope) {
                case LOCAL:
                    if (f_code.traits.contains(Code.Trait.OPTIMIZED)) {
                        return loadFastMH(symbol.index);
                    } else if (f_locals == f_globals) {
                        return loadNameMH(id, "loadNameGB");
                    } else {
                        return loadNameMH(id, "loadNameLGB");
                    }
                case CELL:
                    return loadCellMH(symbol.cellIndex, "cellvars");
                case FREE:
                    return loadCellMH(symbol.cellIndex, "freevars");
                default: // GLOBAL_*
                    return loadNameMH(id, "loadNameGB");
            }
        }

        /**
         * A method handle that may be invoked with an ExecutionFrame to
         * return a particular local variable from
         * {@link Frame#fastlocals}.
         *
         * @param index of local variable
         * @return method handle to load the value
         * @throws ReflectiveOperationException
         * @throws IllegalAccessException
         */
        MethodHandle loadFastMH(int index)
                throws ReflectiveOperationException,
                IllegalAccessException {

            Class<Object[]> OA = Object[].class;
            Class<ExecutionFrame> EF = ExecutionFrame.class;

            // fast = λ(f) : f.fastlocals
            MethodHandle fast = lookup.findGetter(EF, "fastlocals", OA);
            // get = λ(a,i) : a[i]
            MethodHandle get = arrayElementGetter(OA);
            // atIndex = λ(a) : a[index]
            MethodHandle atIndex = insertArguments(get, 1, index);
            // λ(f) : f.fastlocals[index]
            return collectArguments(atIndex, 0, fast);
        }

        /**
         * A method handle that may be invoked with an ExecutionFrame to
         * return a particular cell variable {@link Frame#cellvars}.
         *
         * @param index of cell variable
         * @param arrayName either "cellvars" or "freevars"
         * @return method handle to load the value
         * @throws ReflectiveOperationException
         * @throws IllegalAccessException
         */
        MethodHandle loadCellMH(int index, String arrayName)
                throws ReflectiveOperationException,
                IllegalAccessException {

            Class<Object> O = Object.class;
            Class<Cell[]> CA = Cell[].class;
            Class<ExecutionFrame> EF = ExecutionFrame.class;

            // fast = λ(f) : f.(arrayName)
            MethodHandle cells = lookup.findGetter(EF, arrayName, CA);
            // get = λ(a,i) : a[i]
            MethodHandle get = arrayElementGetter(CA);
            // atIndex = λ(a) : a[index]
            MethodHandle atIndex = insertArguments(get, 1, index);
            // cell = λ(f) : f.(arrayName)[index]
            MethodHandle cell = collectArguments(atIndex, 0, cells);

            // obj = λ(c) : c.obj
            MethodHandle obj = lookup.findGetter(Cell.class, "obj", O);
            // λ(f) : f.(arrayName)[index].obj
            return collectArguments(obj, 0, cell);
        }

        /**
         * A method handle that may be invoked with an
         * <code>ExecutionFrame</code> to return a particular variable by
         * name from (optionally) {@link Frame#f_locals},
         * {@link Frame#f_globals} or {@link Frame#f_builtins}. Whether or
         * not {@link Frame#f_locals} is in the search list is determined
         * by the method name: "loadNameLGB" to include the locals.
         *
         * @param name of variable to look up
         * @param mapName either "loadNameLGB" or "loadNameGB"
         * @return method handle to look up the name
         *
         * @throws ReflectiveOperationException
         * @throws IllegalAccessException
         */
        MethodHandle loadNameMH(String name, String method)
                throws ReflectiveOperationException,
                IllegalAccessException {

            Class<Object> O = Object.class;
            Class<ExecutionFrame> EF = ExecutionFrame.class;
            MethodType LOAD = MethodType.methodType(O, String.class);

            // λ(f) : f.loadNameGB(k)
            MethodHandle load = lookup.findVirtual(EF, method, LOAD);
            // λ(f) : f.loadNameGB(name)
            return insertArguments(load, 1, name);
        }

        @Override
        public Object visit_Assign(Assign assign) {

            Object value = assign.value.accept(this);

            if (assign.site == null) {
                // This must be a first visit
                if (assign.targets.size() != 1) {
                    throw notSupported("unpacking", assign);
                }
                expr target = assign.targets.get(0);
                String id = ((expr.Name)target).id;
                if (!(target instanceof expr.Name)) {
                    throw notSupported("assignment to complex lvalue",
                            assign);
                }
                try {
                    assign.site = new ConstantCallSite(storeMH(id));
                } catch (ReflectiveOperationException e) {
                    throw linkageFailure(id, assign, e);
                }
            }

            MethodHandle mh = assign.site.dynamicInvoker();

            try {
                mh.invokeExact(this, value);
                return null;
            } catch (Throwable e) {
                expr target = assign.targets.get(0);
                String id = ((expr.Name)target).id;
                throw invocationFailure(id + "=", assign, e);
            }
        }

        /**
         * Method handle to bootstrap a simulated
         * <code>invokedynamic</code> call site for assignment to an
         * identifier in context of this frame's code object.
         *
         * @param id identifier to resolve
         * @return method handle to assign the value
         * @throws ReflectiveOperationException
         * @throws IllegalAccessException
         */
        private MethodHandle storeMH(String id)
                throws ReflectiveOperationException,
                IllegalAccessException {

            // How is the id used?
            SymbolTable.Symbol symbol = f_code.ast.symbolTable.lookup(id);

            // Mechanism depends on scope & OPTIMIZED trait
            switch (symbol.scope) {
                case LOCAL:
                    if (f_code.traits.contains(Code.Trait.OPTIMIZED)) {
                        return storeFastMH(symbol.index);
                    } else {
                        return storeNameMH(id, "f_locals");
                    }
                case CELL:
                    return storeCellMH(symbol.cellIndex, "cellvars");
                case FREE:
                    return storeCellMH(symbol.cellIndex, "freevars");
                default: // GLOBAL_*
                    return storeNameMH(id, "f_globals");
            }
        }

        /**
         * A method handle that may be invoked with an ExecutionFrame and a
         * value to assign a particular local variable from
         * {@link Frame#fastlocals}.
         *
         * @param index of local variable
         * @return method handle to store the value
         * @throws ReflectiveOperationException
         * @throws IllegalAccessException
         */
        MethodHandle storeFastMH(int index)
                throws ReflectiveOperationException,
                IllegalAccessException {

            Class<Object[]> OA = Object[].class;
            Class<ExecutionFrame> EF = ExecutionFrame.class;

            // fast = λ(f) : f.fastlocals
            MethodHandle fast = lookup.findGetter(EF, "fastlocals", OA);
            // store = λ(a,k,v) : a[k] = v
            MethodHandle store = arrayElementSetter(OA);
            // storeFast = λ(f,k,v) : (f.fastlocals[k] = v)
            MethodHandle storeFast = collectArguments(store, 0, fast);
            // mh = λ(f,v) : (f.fastlocals[index] = v)
            return insertArguments(storeFast, 1, index);
        }

        /**
         * A method handle that may be invoked with an ExecutionFrame to
         * assign a particular cell variable {@link Frame#cellvars}.
         *
         * @param index of cell variable
         * @param arrayName either "cellvars" or "freevars"
         * @return method handle to assign the value
         * @throws ReflectiveOperationException
         * @throws IllegalAccessException
         */
        MethodHandle storeCellMH(int index, String arrayName)
                throws ReflectiveOperationException,
                IllegalAccessException {

            Class<Object> O = Object.class;
            Class<Cell[]> CA = Cell[].class;
            Class<ExecutionFrame> EF = ExecutionFrame.class;

            // get = λ(a,i) : a[i]
            MethodHandle get = arrayElementGetter(CA);
            // cells = λ(f) : f.(arrayName)
            MethodHandle cells = lookup.findGetter(EF, arrayName, CA);
            // getCell = λ(f,i) : f.cellvars[i]
            MethodHandle getCell = collectArguments(get, 0, cells);

            // setObj = λ(c,v) : (c.obj = v)
            MethodHandle setObj = lookup.findSetter(Cell.class, "obj", O);
            // setCellObj = λ(f,i,v) : (f.(arrayName)[i] = v)
            MethodHandle putCell = collectArguments(setObj, 0, getCell);
            // λ(f,v) : (f.(arrayName)[index].obj = v)
            return insertArguments(putCell, 1, index);
        }

        /**
         * A method handle that may be invoked with an ExecutionFrame to
         * assign a particular variable from a field that is a
         * <code>Map</code>, either {@link Frame#f_locals} or
         * {@link Frame#f_globals}.
         *
         * @param name of variable to look up
         * @param mapName either "f_locals" or "f_globals"
         * @return method handle to assign the value
         * @throws ReflectiveOperationException
         * @throws IllegalAccessException
         */
        MethodHandle storeNameMH(String name, String mapName)
                throws ReflectiveOperationException,
                IllegalAccessException {

            Class<Object> O = Object.class;
            @SuppressWarnings("rawtypes")
            Class<Map> MAP = Map.class;
            Class<ExecutionFrame> EF = ExecutionFrame.class;
            MethodType BINOP = MethodType.methodType(O, O, O);

            // put = λ(m,k,v) : m.put(k,v)
            MethodHandle put = lookup.findVirtual(MAP, "put", BINOP);

            // map = λ(f) : f.(mapName)
            MethodHandle map = lookup.findGetter(getClass(), mapName, MAP);
            // putMap = λ(f,k,v) : f.(mapName).put(k,v)
            MethodHandle putMap = collectArguments(put, 0, map);
            // λ(f,v) : f.(mapName).put(name,v)
            return insertArguments(putMap, 1, name)
                    // Discard the return from Map.put
                    .asType(MethodType.methodType(void.class, EF, O));
        }

        @Override
        public Object visit_Expr(Expr expr) {
            return expr.value.accept(this);
        }

        @Override
        public Object visit_FunctionDef(FunctionDef def) {
            // Code of function defined present as constant
            Code targetCode = f_code.ast.codeMap.get(def);
            Cell[] closure = closure(targetCode);
            Function func = new Function(targetCode, f_globals,
                    targetCode.co_name, closure);
            // OtherCallable func = new OtherCallable( //
            // targetCode, f_globals, targetCode.co_name, closure);

            if (def.site == null) {
                // This must be a first visit
                try {
                    def.site = new ConstantCallSite(storeMH(def.name));
                } catch (ReflectiveOperationException e) {
                    throw linkageFailure(def.name, def, e);
                }
            }

            MethodHandle mh = def.site.dynamicInvoker();

            try {
                mh.invokeExact(this, (Object)func);
                return null;
            } catch (Throwable e) {
                throw invocationFailure("def " + def.name, def, e);
            }
        }

        /**
         * Obtain the cells that should be wrapped into a function
         * definition, from .
         */
        private Cell[] closure(Code targetCode) {
            int nfrees = targetCode.co_freevars.length;
            if (nfrees == 0) {
                // No closure necessary
                return Cell.EMPTY_ARRAY;
            } else {
                SymbolTable localSymbols = f_code.ast.symbolTable;
                Cell[] closure = new Cell[nfrees];
                for (int i = 0; i < nfrees; i++) {
                    String name = targetCode.co_freevars[i];
                    SymbolTable.Symbol symbol = localSymbols.lookup(name);
                    boolean isFree =
                            symbol.scope == SymbolTable.ScopeType.FREE;
                    int n = symbol.cellIndex;
                    closure[i] = (isFree ? freevars : cellvars)[n];
                }
                return closure;
            }
        }

        @Override
        public Object visit_Call(Call call) {
            // Evaluating the expression should return a callable object
            Object funcObj = call.func.accept(this);

            if (funcObj instanceof PyGetFrame) {
                return functionCall((PyGetFrame)funcObj, call.args);
            } else if (funcObj instanceof PyCallable) {
                return generalCall((PyCallable)funcObj, call.args);
            } else {
                throw notSupported("target not callable", call);
            }
        }

        /** Call to {@link PyGetFrame} style of function. */
        private Object functionCall(PyGetFrame func, List<expr> args) {
            // Create the destination frame
            ExecutionFrame targetFrame = func.getFrame(this);
            // Only fixed number of positional arguments supported
            int n = args.size();
            assert n == targetFrame.f_code.argcount;
            // Visit the values of positional args
            for (int i = 0; i < n; i++) {
                targetFrame.fastlocals[i] = args.get(i).accept(this);
            }
            // Execute with the prepared frame
            return targetFrame.eval();
        }

        /** Call to {@link PyCallable} style of function. */
        private Object generalCall(PyCallable callable, List<expr> args) {
            // Only fixed number of positional arguments supported so far
            int n = args.size();
            Object[] argValues = new Object[n];
            // Visit the values of positional args
            for (int i = 0; i < n; i++) {
                argValues[i] = args.get(i).accept(this);
            }
            return callable.call(this, argValues);
        }

        /**
         * Post the arguments into their correct positions in the frame.
         *
         * @param args positional arguments
         */
        void setArguments(Object[] args) {
            // Only fixed number of positional arguments supported so far
            for (int i = 0; i < f_code.argcount; i++) {
                fastlocals[i] = args[i];
            }
        }

        @Override
        public Object visit_Return(Return returnStmt) {
            returnValue = returnStmt.value.accept(this);
            return null;
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
        public Object visit_Num(expr.Num num) {
            return num.n;
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

        /** Create an UnsupportedOperationException. */
        private static UnsupportedOperationException
                notSupported(String op, Node node) {
            String msg = "%s in '%s' node";
            String nodeType = node.getClass().getSimpleName();
            return new UnsupportedOperationException(
                    String.format(msg, op, nodeType));
        }

        /** Create a RuntimeException reflecting indy failure. */
        private static RuntimeException linkageFailure(String name,
                Node node, Throwable t) {
            String fmt = "linking '%s' in '%s' node";
            String msg = String.format(fmt, name, node == null ? "null"
                    : node.getClass().getSimpleName());
            return new RuntimeException(msg, t);
        }

        /** Create a RuntimeException reflecting indy failure. */
        private static RuntimeException invocationFailure(String name,
                Node node, Throwable t) {
            String fmt = "invoking '%s' in '%s' node";
            String msg = String.format(fmt, name, node == null ? "null"
                    : node.getClass().getSimpleName());
            return new RuntimeException(msg, t);
        }

    }

    /** Our equivalent to the Python code object. */
    private static class Code {

        /**
         * This is our equivalent to byte code that holds a sequence of
         * statements comprising the body of a function or module. Since
         * these are nodes in the AST, references to variables are
         * symbolic, and the symbol table must be present to map them into
         * frame locations. Literal constants are present in the AST nodes
         * that need them. Constants generated by compilation, in
         * particular, Code objects have to be associated through a map.
         */
        static class ASTCode {

            /** Suite comprising the body of a function or module. */
            final List<stmt> body;
            /** Map names used in this scope to their meanings. */
            final SymbolTable symbolTable;
            /** Associate a node to code it requires. */
            final Map<Node, Code> codeMap;

            ASTCode(List<stmt> body, SymbolTable symbolTable,
                    Map<Node, Code> codeMap) {
                this.body = body;
                this.symbolTable = symbolTable;
                this.codeMap = codeMap;
            }
        }

        /** Characteristics of a Code (as CPython co_flags). */
        enum Trait {
            OPTIMIZED, NEWLOCALS, VARARGS, VARKEYWORDS
        }

        final EnumSet<Trait> traits;

        /** Suite and symbols that are to us the executable code. */
        final ASTCode ast;

        /** Number of positional arguments (not counting varargs). */
        final int argcount;
        /** Number of keyword-only arguments (not counting varkeywords). */
        final int kwonlyargcount;
        /** Number of local variables. */
        final int nlocals;

        final Object[] co_consts;   // constant objects needed by the code

        // XXX: Not needed (?) but implement for test against CPython
        final String[] co_names;    // names referenced in the code
        final String[] co_varnames; // args and non-cell locals
        final String[] co_freevars; // names ref'd but not defined here
        final String[] co_cellvars; // names def'd here & ref'd elsewhere

        final String co_name; // name of function etc.

        /** Construct from result of walking the AST. */
        public Code( //
                int argcount, // co_argcount
                int kwonlyargcount, // co_kwonlyargcount
                int nlocals, // co_nlocals

                Set<Trait> traits, // co_flags

                ASTCode code, // co_code
                List<Object> consts, // co_consts

                Collection<String> names, // names ref'd in code
                Collection<String> varnames, // args and non-cell locals
                Collection<String> freevars, // ref'd here, def'd outer
                Collection<String> cellvars, // def'd here, ref'd nested
                String name // of function etc.
        ) {
            this.argcount = argcount;
            this.kwonlyargcount = kwonlyargcount;
            this.nlocals = nlocals;

            this.traits = EnumSet.copyOf(traits);
            this.ast = code;

            this.co_consts = consts.toArray();

            this.co_names = names(names);
            this.co_varnames = names(varnames);
            this.co_freevars = names(freevars);
            this.co_cellvars = names(cellvars);

            this.co_name = name;
        }

        private static String[] names(Collection<String> c) {
            return c.toArray(new String[c.size()]);
        }

        @Override
        public String toString() {
            return String.format("<code %s %s>", co_name, traits);
        }

    }

    /**
     * Patterned after Python <code>symtable.SymbolTable</code>, this class
     * holds symbol (name) information for one scope and its lexically
     * contained scopes. This is also effectively a
     * <code>PySTEntryObject</code> from CPython <code>symtable.h</code>.
     * We have no need for a separately compiled <code>_symtable</code>
     * module and raw <code>_table</code> member.
     */
    private static abstract class SymbolTable {

        /** Scopes have a name (the name of the function, class, etc.. */
        final String name;
        /** The symbol table itself. */
        final Map<String, Symbol> symbols;
        /** Enclosing scope or null if and only if this is a module. */
        final SymbolTable parent;
        /** Enclosed scopes. */
        final List<SymbolTable> children;

        /**
         * Construct a scope with the given name and parent. This does not
         * enter the name in the parent symbol table: the caller should do
         * that.
         */
        SymbolTable(String name, SymbolTable parent) {
            this.symbols = new HashMap<>();
            this.parent = parent;
            this.name = name;
            this.children = new ArrayList<>();
        }

        /**
         * Expresses the final decision how the variable is accessed. Note
         * that being a function parameter is not a <code>ScopeType</code>:
         * parameters may be <code>LOCAL</code> or <code>CELL</code>.
         */
        enum ScopeType {
            /** A local name that is bound in this scope and not a cell. */
            LOCAL,
            /** A global name declared with <code>global</code>. */
            GLOBAL_EXPLICIT,
            /** A global name not declared with <code>global</code>. */
            GLOBAL_IMPLICIT,
            /** A non-local name not bound in this scope. */
            FREE,
            /** A non-local name bound in this scope. */
            CELL
        }

        /**
         * Make an entry for a name in the symbol table with the given
         * attribute flags, or add the flags to the existing entry.
         */
        Symbol add(String name, int flags) {
            Symbol s = symbols.get(name);
            if (s == null) {
                s = new Symbol(name, flags);
                symbols.put(name, s);
            } else {
                s.flags |= flags;
            }
            return s;
        }

        /**
         * Enter all of these names (useful for global and nonlocal
         * declarations).
         */
        void addAll(Collection<String> names, int flags) {
            for (String name : names) {
                add(name, flags);
            }
        }

        /**
         * Make an entry for a child name space and add it to the symbol
         * table by name. (If an entry exists by this name already, augment
         * that entry with the name space.)
         */
        Symbol addChild(SymbolTable other) {
            Symbol s = symbols.get(other.name);
            if (s == null) {
                s = new Symbol(other);
                symbols.put(other.name, s);
            } else {
                s.addSpace(other);
                s.flags |= Symbol.ASSIGNED;
            }
            children.add(other);
            return s;
        }

        /**
         * Retrieve the named symbol from this table (or return null if not
         * found).
         */
        Symbol lookup(String name) {
            return symbols.get(name);
        }

        /**
         * The scope within which this is ultimately nested (or itself if a
         * module).
         */
        abstract ModuleSymbolTable getTop();

        /** <code>true</code> if scope is nested (not a module scope). */
        abstract boolean isNested();

        @Override
        public String toString() {
            String type = getClass().getSimpleName();
            return String.format("%s '%s'", type, name);
        }

        /** Return a list of names of symbols in this table. */
        Set<String> getIdentifiers() {
            return symbols.keySet();
        }

        /**
         * Given a name that is free in a scope <b>interior</b> to this
         * one, look in this scope's symbols for a matching name, and in
         * <b>enclosing</b> scopes until either a binding for the name has
         * been found, or it is proved it cannot be resolved. This free
         * name may have been declared explicitly <code>nonlocal</code> in
         * the original scope or it may just be used but not bound in the
         * original scope.
         *
         * The method returns true if and only if, in the scope where it is
         * found, the name is {@link ScopeType#LOCAL} or
         * {@link ScopeType#CELL}. A side effect of a successful search is
         * to convert the name to {@link ScopeType#CELL} in the scope where
         * it is found, and in all intervening scopes to
         * {@link ScopeType#FREE}. (In practice, the search will stop as
         * soon as the name is discovered <code>CELL</code> or
         * <code>FREE</code>, implying that a previous call has already
         * done the rest of our work.)
         */
        abstract boolean fixupFree(String name);

        /**
         * Resolve the symbols in this scope to their proper
         * {@link ScopeType}, also fixing-up parent scopes for those found
         * free in this scope. This method implements the "second pass"
         * over symbols in the compiler.
         */
        void resolveAllSymbols() {
            for (SymbolTable.Symbol s : symbols.values()) {
                // The use in this scope may resolve itself immediately
                if (!s.resolveScope()) {
                    // Not resolved: used free or is explicitly nonlocal
                    if (isNested() && parent.fixupFree(s.name)) {
                        // Appropriate referent exists in outer scopes
                        s.setScope(ScopeType.FREE);
                    } else if ((s.flags & Symbol.NONLOCAL) != 0) {
                        // No cell variable found: but declared non-local
                        throw new IllegalArgumentException(
                                "undefined non-local " + s.name);
                    } else {
                        // No cell variable found: assume global
                        s.setScope(ScopeType.GLOBAL_IMPLICIT);
                    }
                }
            }
        }

        /**
         * Apply {@link #resolveAllSymbols()} to the current scope and then
         * to child scopes recursively. Applied to a module, this completes
         * free variable fix-up for symbols used throughout the program.
         */
        protected void resolveSymbolsRecursively() {
            resolveAllSymbols();
            for (SymbolTable st : children) {
                st.resolveSymbolsRecursively();
            }
        }

        /**
         * Patterned after Python <code>symtable.Symbol</code>, this class
         * holds usage information for one symbol (name) in the scope
         * described by the symbol table where it is an entry.
         */
        static class Symbol {

            /** Declared global */
            private static final int GLOBAL = 1;
            /** Assigned in block scope */
            private static final int ASSIGNED = 2;
            /** Appears as formal parameter (function) */
            private static final int PARAMETER = 4;
            /** Declared non-local */
            private static final int NONLOCAL = 8;
            /** Used (referenced) in block scope */
            private static final int REFERENCED = 0x10;

            // Convenience for testing several kinds of "bound"
            private static final int BOUND = ASSIGNED | PARAMETER;

            /** Symbol name */
            final String name;
            /** Properties collected by scanning the AST for uses. */
            int flags;
            /** The final decision how the variable is accessed. */
            ScopeType scope = null;
            /** Index within local variable array in executing frame. */
            int index = -1;
            /** Index within cell variable array in executing frame. */
            int cellIndex = -1;

            /**
             * When the symbol represents a function or class, list the
             * name spaces of those scopes here. Note it is possible to
             * have several, and for the symbol to represent other types in
             * the same scope. null if and only if there are no such
             * associated namespaces.
             */
            private List<SymbolTable> namespaces;

            /**
             * Construct a symbol with given initial flags.
             */
            private Symbol(String name, int flags) {
                this.name = name;
                this.flags = flags;
                this.namespaces = Collections.emptyList();
            }

            /**
             * Construct a symbol representing one name space initially.
             */
            Symbol(SymbolTable other) {
                this.name = other.name;
                this.flags = ASSIGNED;
                this.namespaces = Collections.singletonList(other);
            }

            @Override
            public String toString() {
                return "<symbol " + name + ">";
            }

            void addSpace(SymbolTable other) {
                assert name == other.name;
                if (namespaces.isEmpty()) {
                    // First name space
                    namespaces = Collections.singletonList(other);
                } else {
                    if (namespaces.size() == 1) {
                        // Replace singleton list with a mutable one
                        SymbolTable existing = namespaces.get(0);
                        namespaces = new LinkedList<>();
                        namespaces.add(existing);
                    }
                    namespaces.add(other);
                }
            }

            void setScope(ScopeType scope) {
                this.scope = scope;
            }

            /**
             * If possible using local information only, resolve the scope
             * of the name, returning <code>true</code> if the result was
             * definitive. The result is not definitive, and the return is
             * <code>false</code>, when the name is free in this scope, or
             * is explicitly declared <code>nonlocal</code>. In either
             * case, the caller must search enclosing scopes for this name,
             * possibly adding the name free in them or converting the name
             * to {@link ScopeType#CELL} scope in the scope that binds the
             * name.
             *
             * @return false iff we must search enclosing scopes
             */
            boolean resolveScope() {
                if ((flags & GLOBAL) != 0) {
                    scope = ScopeType.GLOBAL_EXPLICIT;
                } else if ((flags & NONLOCAL) != 0) {
                    scope = ScopeType.LOCAL;
                    return false;
                } else if ((flags & BOUND) != 0) {
                    scope = ScopeType.LOCAL; // or CELL ultimately
                }
                return scope != null;
            }

            /**
             * Return <code>true</code> if the symbol is used in its scope.
             */
            boolean is_referenced() {
                return (flags & REFERENCED) != 0;
            }

            /**
             * Return <code>true</code> if the symbol is created from an
             * import statement.
             */
            public boolean is_imported() {
                // Imports not supported at the moment
                return false;
            }

            /** Return <code>true</code> if the symbol is a parameter. */
            boolean is_parameter() {
                return (flags & PARAMETER) != 0;
            }

            /** Return <code>true</code> if the symbol is global. */
            boolean is_global() {
                return scope == ScopeType.GLOBAL_IMPLICIT
                        || scope == ScopeType.GLOBAL_EXPLICIT;
            }

            /**
             * Return <code>true</code> if the symbol is declared global
             * with a global statement.
             */
            boolean is_declared_global() {
                return scope == ScopeType.GLOBAL_EXPLICIT;
            }

            /**
             * Return <code>true</code> if the symbol is local to its
             * scope.
             */
            boolean is_local() {
                // XXX This is what CPython defines. Bug?
                return (flags & BOUND) != 0;
                // Why not:
                // return scope == ScopeType.LOCAL;
            }

            /**
             * Return <code>true</code> if the symbol is referenced in its
             * scope, but not assigned to.
             */
            boolean is_free() {
                return scope == ScopeType.FREE;
            }

            /**
             * Return <code>true</code> if the symbol is assigned to in its
             * scope.
             */
            boolean is_assigned() {
                // Flag ASSIGNED is called DEF_LOCAL in CPython
                return (flags & ASSIGNED) != 0;
            }

            /**
             * Returns true if name binding introduces new namespace.
             *
             * If the name is used as the target of a function or class
             * statement, this will be true.
             *
             * Note that a single name can be bound to multiple objects. If
             * boolean isNamespace() is true, the name may also be bound to
             * other objects, like an int or list, that does not introduce
             * a new namespace.
             */
            boolean is_namespace() {
                return !namespaces.isEmpty();
            }

            /** Return a list of namespaces bound to this name */
            List<SymbolTable> getNamespaces() {
                return namespaces;
            }

            /**
             * Returns the single namespace bound to this name.
             *
             * Raises ValueError if the name is bound to multiple
             * namespaces or none.
             */
            SymbolTable getNamespace() {
                if (namespaces.isEmpty()) {
                    throw new IllegalArgumentException(
                            "name is not bound a namespace");
                } else if (namespaces.size() == 1) {
                    return namespaces.get(0);
                } else {
                    throw new IllegalArgumentException(
                            "name is bound to multiple namespaces");
                }
            }
        }
    }

    /**
     * Symbol table representing the scope of a module, that is, the top
     * level of the scope tree.
     */
    private static class ModuleSymbolTable extends SymbolTable {

        private String filename;

        ModuleSymbolTable(mod module, String filename) {
            super("top", null);
            this.filename = filename;
        }

        @Override
        ModuleSymbolTable getTop() {
            return this;
        }

        @Override
        boolean isNested() {
            return false;
        }

        @Override
        boolean fixupFree(String name) {
            // The top cannot be the referent of a non-local name.
            return false;
        }
    }

    /**
     * Symbol table representing the scope of a function body, which cannot
     * therefore be the top level of the scope tree.
     */
    private static class FunctionSymbolTable extends SymbolTable {

        private final ModuleSymbolTable top;
        /**
         * The parameters in order, with <code>*args</code> and
         * <code>**kwargs</code> last, filled when visiting the definition.
         */
        final List<Symbol> parameters;
        /** Function has a <code>*args</code> positional receiver */
        final boolean varargs;
        /** Function has a <code>*kwrgs</code> dictionary receiver */
        final boolean varkeywords;

        /**
         * Create nested symbol table from a function declaration in a
         * scope that becomes a parent of this table.
         *
         * @param def the function definition
         * @param parent enclosing scope must be non-null
         */
        FunctionSymbolTable(stmt.FunctionDef def, SymbolTable parent) {
            super(def.name, parent);
            this.varargs = def.args.vararg != null;
            this.varkeywords = def.args.kwarg != null;
            this.top = parent.getTop();
            this.parameters = new LinkedList<>();
        }

        @Override
        Symbol add(String name, int flags) {
            Symbol s = super.add(name, flags);
            if ((flags & SymbolTable.Symbol.PARAMETER) != 0) {
                // s is being declared as a parameter
                parameters.add(s);
            }
            return s;
        }

        @Override
        ModuleSymbolTable getTop() {
            return top;
        }

        @Override
        boolean isNested() {
            return true;
        }

        /** Collect the symbols for which the predicate is true. */
        protected List<Symbol> symbolsMatching(Predicate<Symbol> test) {
            return symbols.values().stream().filter(test)
                    .collect(Collectors.toList());
        }

        /**
         * Return a list containing names of parameters to this function.
         */
        List<Symbol> getParameters() {
            return parameters;
        }

        /** Return a list containing names of locals in this function. */
        List<Symbol> getLocals() {
            return symbolsMatching(Symbol::is_local);
        }

        /** Return a list containing names of globals in this function. */
        List<Symbol> getGlobals() {
            return symbolsMatching(Symbol::is_global);
        }

        /**
         * Return a list containing names of free variables in this
         * function.
         */
        List<Symbol> getFrees() {
            return symbolsMatching(Symbol::is_free);
        }

        @Override
        boolean fixupFree(String name) {
            // Look up in this scope
            SymbolTable.Symbol s = symbols.get(name);
            if (s != null) {
                /*
                 * Found name in this scope: but only CELL, FREE or LOCAL
                 * are allowable.
                 */
                switch (s.scope) {
                    case CELL:
                    case FREE:
                        // Name is CELL here or in an enclosing scope
                        return true;
                    case LOCAL:
                        // Bound here, make it CELL in this scope
                        s.setScope(ScopeType.CELL);
                        return true;
                    default:
                        /*
                         * Any other scope value is not compatible with the
                         * alleged non-local nature of this name in the
                         * original scope.
                         */
                        return false;
                }
            } else {
                /*
                 * The name is not present in this scope. If it can be
                 * found in some enclosing scope then we will add it FREE
                 * here.
                 */
                if (parent.fixupFree(name)) {
                    s = add(name, 0);
                    s.setScope(ScopeType.FREE);
                    return true;
                } else {
                    return false;
                }
            }
        }
    }

    /**
     * Visitor on the AST adding compile-time information to each node
     * about the binding of names. In this version, the information is
     * added externally through a Map created in construction and filled
     * during the traverse.
     */
    private static class SymbolVisitor extends AbstractVisitor<Void> {

        final String filename;

        /** Description of the current scope (symbol table). */
        protected SymbolTable current;
        /** Map from nodes that are block scopes to their symbols. */
        final Map<Node, SymbolTable> scopeMap;
        /** Parameter names from the function definition (heading). */
        private ArrayList<String> parameterNames = new ArrayList<>();

        /**
         * Construct a SymbolVisitor to traverse an AST and fill in
         * information about each block scope.
         */
        SymbolVisitor(mod module, String filename) {
            this.filename = filename;
            this.scopeMap = new HashMap<>();
        }

        /**
         * Get the generated map from nodes to the name-binding information
         * generated by visiting the tree. Only nodes that represent block
         * scopes (Module and FunctionDef) will be keys in this map.
         */
        public Map<Node, SymbolTable> getBlockMap() {
            return scopeMap;
        }

        /**
         * The visitor is normally applied to a module.
         */
        @Override
        public Void visit_Module(mod.Module module) {
            // Create a symbol table for the scope
            current = new ModuleSymbolTable(module, filename);
            scopeMap.put(module, current);
            try {
                // Process the statements in the block
                return super.visit_Module(module);
            } finally {
                // Restore context (should be null!)
                current = current.parent;
            }
        }

        @Override
        public Void visit_FunctionDef(stmt.FunctionDef functionDef) {
            // Visit the argument list in the current scope
            parameterNames.clear();
            functionDef.args.accept(this);
            visitAll(functionDef.decorator_list);
            visitIfNotNull(functionDef.returns);

            // Now start a nested scope as a child of the current one
            FunctionSymbolTable child =
                    new FunctionSymbolTable(functionDef, current);
            current.addChild(child);

            // Add a correspondence between this node and the child scope
            scopeMap.put(functionDef, child);

            // Make the child scope current
            current = child;

            // Add the parameter names as the first symbols
            current.addAll(parameterNames, SymbolTable.Symbol.PARAMETER);

            // Visit the body of the function in the new scope
            visitAll(functionDef.body);

            // Restore context
            current = current.parent;
            return null;
        }

        @Override
        public Void visit_Global(stmt.Global global) {
            current.addAll(global.names, SymbolTable.Symbol.GLOBAL);
            // Explicit global declaration counts for the top level too.
            if (current.isNested()) {
                current.getTop().addAll(global.names,
                        SymbolTable.Symbol.GLOBAL);
            }
            return null;
        }

        @Override
        public Void visit_Nonlocal(stmt.Nonlocal nonlocal) {
            current.addAll(nonlocal.names, SymbolTable.Symbol.NONLOCAL);
            return null;
        }

        @Override
        public Void visit_Name(expr.Name name) {
            if (name.ctx == Load) {
                current.add(name.id, SymbolTable.Symbol.REFERENCED);
            } else {
                current.add(name.id, SymbolTable.Symbol.ASSIGNED);
            }
            return null;
        }

        @Override
        public Void visit_arguments(arguments _arguments) {
            // Constructor order is:
            visitAll(_arguments.args);
            visitIfNotNull(_arguments.vararg);
            visitAll(_arguments.kwonlyargs);
            visitIfNotNull(_arguments.kwarg);
            visitAll(_arguments.defaults);
            visitAll(_arguments.kw_defaults);
            return null;
        }

        @Override
        public Void visit_arg(arg arg) {// aaaargh!!!
            // Save the name (it belongs to the future nested scope)
            parameterNames.add(arg.arg);
            return null;
        }

        @Override
        public Void visit_keyword(keyword keyword) {
            // Save the name (it belongs to the future nested scope)
            parameterNames.add(keyword.arg);
            return keyword.value.accept(this);
        }
    }

    /**
     * Visitor on the AST adding code objects using the symbol data
     * attached to each block scope in a previous pass. Each instance of
     * <code>CodeGenerator</code> produces one <code>Code</code> object,
     * representing the starting node (which must be a module), and makes
     * an entry for it in the map passed in at construction time.
     * <p>
     * Each time a <code>CodeGenerator</code> encounters a function
     * definition, it launches another <code>CodeGenerator</code> to
     * analyse it and add it to the same map, and so on recursively down
     * the program structure. The <code>Code</code> it generates must be
     * accessed by calling {@link #getCode()}, and all the
     * <code>Code</code> objects generated by nested invocation appear
     * within this structure as constants.
     */
    private static class CodeGenerator extends AbstractVisitor<Void> {

        /**
         * Holds a mapping from a function definition or module node to the
         * symbol table of its block.
         */
        private final Map<Node, SymbolTable> scopeMap;

        /** Suite comprising the body of a function or module. */
        List<stmt> body;

        /** Map names used in this scope to their meanings. */
        SymbolTable symbolTable;

        /** Name of the function generating the code. */
        private String name;
        /**
         * Holds a mapping from a function definition or module node to the
         * code object representing its body.
         */
        private final Map<Node, Code> codeMap = new HashMap<>();

        /** Characteristics of the code block (CPython co_flags). */
        Set<Code.Trait> traits = EnumSet.noneOf(Code.Trait.class);

        /** Count arguments and locals as we add them. */
        private int localIndex = 0;
        /** Count frees as we add them. */
        private int freeIndex = 0;
        /** Count cells as we add them. */
        private int cellIndex = 0;
        /** Count names of globals etc. as we add them. */
        private int nameIndex = 0;

        /** Number of positional arguments. */
        int argcount;
        /** Number of keyword-only arguments. */
        int kwonlyargcount;

        /** Values appearing as constants in the code. */
        List<Object> consts = new LinkedList<>();

        /** Names used to reference global and built-in objects. */
        List<String> names = new LinkedList<>();
        /** Parameter names followed by non-cell local variables. */
        List<String> varnames = new LinkedList<>();
        /** Names free in this scope. */
        List<String> freevars = new LinkedList<>();
        /** Names bound in this scope but referenced free elsewhere. */
        List<String> cellvars = new LinkedList<>();

        /**
         * Construct a CodeGenerator to traverse an AST and provide each
         * body with a code object.
         *
         * @param scopeMap from nodes to their symbol tables
         */
        CodeGenerator(Map<Node, SymbolTable> scopeMap) {
            this.scopeMap = scopeMap;
        }

        /**
         * Create a <code>Code</code> from the information gathered by this
         * visitor.
         */
        private Code getCode() {
            Code.ASTCode raw =
                    new Code.ASTCode(body, symbolTable, codeMap);
            Code code = new Code( //
                    argcount, kwonlyargcount, localIndex, // sizes
                    traits, // co_flags
                    raw, // co_code
                    consts, // co_consts
                    names, varnames, freevars, cellvars, // co_* names
                    name // co_name
            );
            return code;
        }

        /**
         * Assign a location in the frame to each symbol. Parameters (if
         * this is a function) have already been assigned in visiting
         * function definition.
         *
         * @param table for the current block
         */
        private void finishLayout() {
            // Iterate symbols, assigning their offsets.
            for (SymbolTable.Symbol s : symbolTable.symbols.values()) {
                switch (s.scope) {
                    case CELL:
                        s.cellIndex = cellIndex++;
                        cellvars.add(s.name);
                        break;
                    case FREE:
                        s.cellIndex = freeIndex++;
                        freevars.add(s.name);
                        break;
                    case GLOBAL_EXPLICIT:
                    case GLOBAL_IMPLICIT:
                        if (s.is_assigned() || s.is_referenced()) {
                            addName(s);
                        }
                        break;
                    case LOCAL:
                        // Parameters were already added in the walk
                        if (!s.is_parameter()) {
                            if (symbolTable.isNested()) {
                                addLocal(s);
                            } else {
                                addName(s);
                            }
                        }
                        break;
                }
            }
        }

        /** Allocate a symbol in the local variables the code object. */
        private void addLocal(SymbolTable.Symbol s) {
            s.index = localIndex++;
            varnames.add(s.name);
        }

        /** Allocate a symbol in the names section the code object. */
        private void addName(SymbolTable.Symbol s) {
            s.index = nameIndex++;
            names.add(s.name);
        }

        /**
         * Add an object to the constants section of the code object.
         *
         * @param value to add
         * @return index of this constant in this.consts
         */
        private int addConst(Object value) {
            int pos = consts.size();
            consts.add(value);
            return pos;
        }

        @Override
        public Void visit_Module(mod.Module module) {
            // Process the associated block scope from the symbol table
            symbolTable = scopeMap.get(module);
            body = module.body;
            name = "<module>";

            // Walk the child nodes: some define functions
            super.visit_Module(module);

            // Fill the rest of the frame layout from the symbol table
            finishLayout();

            // The code currently generated is the code for this node
            codeMap.put(module, getCode());
            return null;
        }

        @Override
        public Void visit_FunctionDef(stmt.FunctionDef functionDef) {
            // This has to have two behaviours
            if (symbolTable != null) {
                /*
                 * We arrived here while walking the body of some block.
                 * Start a nested code generator for the function being
                 * defined.
                 */
                CodeGenerator codeGenerator = new CodeGenerator(scopeMap);
                functionDef.accept(codeGenerator);
                // The code object generated is the code for this node
                Code code = codeGenerator.getCode();
                codeMap.put(functionDef, code);
                addConst(code);

            } else {
                /*
                 * We are a nested code generator that just began this
                 * node. The work we do is in the nested scope.
                 */
                symbolTable = scopeMap.get(functionDef);
                body = functionDef.body;
                name = functionDef.name;

                // Local variables will be in arrays not a map
                traits.add(Code.Trait.OPTIMIZED);
                // And the caller won't supply a local variable map
                traits.add(Code.Trait.NEWLOCALS);

                // Visit the parameters, assigning frame locations
                functionDef.args.accept(this);

                /*
                 * Walk the child nodes assigning frame locations to names.
                 * Some statements will define further functions
                 */
                visitAll(functionDef.body);

                // Fill the rest of the frame layout from the symbol table
                finishLayout();
            }
            return null;
        }

        @Override
        public Void visit_arguments(arguments parameters) {
            // Order of visiting determines storage layout
            // args, kwarg, vararg, kwonlyargs
            visitAll(parameters.args);
            argcount = localIndex;
            visitAll(parameters.kwonlyargs);
            kwonlyargcount = localIndex - argcount;
            // Optionally, there's a *args
            if (parameters.vararg != null) {
                // Function accepts arguments as a tuple
                traits.add(Code.Trait.VARARGS);
                parameters.vararg.accept(this);
            }
            // Optionally, there's a **kwargs
            if (parameters.kwarg != null) {
                // Function accepts arguments as a keyword dictionary
                traits.add(Code.Trait.VARKEYWORDS);
                parameters.kwarg.accept(this);
            }
            // No code to generate for these?
            // visitAll(parameters.defaults);
            // visitAll(parameters.kw_defaults);
            return null;
        }

        @Override
        public Void visit_arg(arg arg) {
            // Allocate this parameter a space in the frame
            addLocal(symbolTable.lookup(arg.arg));
            return null;
        }

    }

    /**
     * Compile the module AST and return its code object. No actual
     * compilation is performed (since we aim to interpret the AST), but we
     * return a <code>Code</code> object. The returned module (code object)
     * will contain in its constants the code objects of functions declared
     * at module level, and so on recursively down the structure. In our
     * present implementation, the statements (AST nodes, not byte code) to
     * which the code corresponds are to be interpreted here. Therefore,
     * code objects contain a list of {@link TreePython.stmt} and the
     * symbol table generated from the AST.
     */
    private static Code compileAST(mod module, String filename) {

        // Build symbol table from the module AST
        SymbolVisitor visitor = new SymbolVisitor(module, filename);
        module.accept(visitor);
        final Map<Node, SymbolTable> scopeMap = visitor.getBlockMap();

        // Complete scope deductions for AST in each SymbolTable
        scopeMap.get(module).resolveSymbolsRecursively();

        // Walk the AST to create code objects at appropriate nodes
        CodeGenerator codeGenerator = new CodeGenerator(scopeMap);
        module.accept(codeGenerator);
        return codeGenerator.getCode();
    }

    private static void executeTest(mod module,
            Map<String, Object> state) {

        // Compile the test AST
        Code code = compileAST(module, "<module>");

        // Set up globals to hold result
        Map<String, Object> globals = new HashMap<>();

        // Equivalent to: PyEval_EvalCode(co, globals, locals)
        // Compare pythonrun.c:run_mod()
        Frame frame = new ExecutionFrame(null, code, globals, globals);
        frame.eval();

        // Check the results
        for (String name : state.keySet()) {
            Object expectedValue = state.get(name);
            Object actualValue = globals.get(name);
            assertThat(actualValue, is(expectedValue));
        }
    }

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
    private static stmt Return(expr value)
        { return new stmt.Return(value); }
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
    private static expr Str(String s)
        { return new expr.Str(s); }
    private static expr Name(String id, expr_context ctx)
        { return new expr.Name(id, ctx); }

    private static arguments arguments(List<?> args, arg vararg,
            List<?> kwonlyargs, List<?> kw_defaults, arg kwarg,
            List<?> defaults) {
        return new arguments(cast(args, arg.class), vararg,
                cast(kwonlyargs, arg.class), cast(kw_defaults, expr.class),
                kwarg, cast(defaults, expr.class));
        }

    private static arg arg(String arg, expr annotation)
        { return new arg(arg, annotation); }

    private static keyword keyword(String arg, expr value)
        { return new keyword(arg, value); }

    // @formatter:on

    // ======= Generated examples ==========
    // See variable_access_testgen.py

// globprog1

    @Test
    public void globprog1() {
        // @formatter:off
        // # Test allocation of "names" (globals)
        // # global d is *not* ref'd at module level
        // b = 1
        // a = 6
        // result = 0
        //
        // def p():
        //     global result
        //     def q():
        //         global d # not ref'd at module level
        //         d = a + b
        //     q()
        //     result = a * d
        //
        // p()
        mod module = Module(
    list(
        Assign(list(Name("b", Store)), Num(1)),
        Assign(list(Name("a", Store)), Num(6)),
        Assign(list(Name("result", Store)), Num(0)),
        FunctionDef(
            "p",
            arguments(list(), null, list(), list(), null, list()),
            list(
                Global(list("result")),
                FunctionDef(
                    "q",
                    arguments(list(), null, list(), list(), null, list()),
                    list(
                        Global(list("d")),
                        Assign(list(Name("d", Store)), BinOp(Name("a", Load), Add, Name("b", Load)))),
                    list(),
                    null),
                Expr(Call(Name("q", Load), list(), list())),
                Assign(list(Name("result", Store)), BinOp(Name("a", Load), Mult, Name("d", Load)))),
            list(),
            null),
        Expr(Call(Name("p", Load), list(), list()))))
        ;
        // @formatter:on
        Map<String, Object> state = new HashMap<>();
        state.put("result", 42);
        state.put("b", 1);
        state.put("d", 7);
        state.put("a", 6);
        executeTest(module, state); // globprog1
    }

// globprog2

    @Test
    public void globprog2() {
        // @formatter:off
        // # Test allocation of "names" (globals)
        // # global d is *assigned* at module level
        // b = 1
        // a = 6
        // result = 0
        //
        // def p():
        //     global result
        //     def q():
        //         global d
        //         d = a + b
        //     q()
        //     result = a * d
        //
        // d = 41
        // p()
        mod module = Module(
    list(
        Assign(list(Name("b", Store)), Num(1)),
        Assign(list(Name("a", Store)), Num(6)),
        Assign(list(Name("result", Store)), Num(0)),
        FunctionDef(
            "p",
            arguments(list(), null, list(), list(), null, list()),
            list(
                Global(list("result")),
                FunctionDef(
                    "q",
                    arguments(list(), null, list(), list(), null, list()),
                    list(
                        Global(list("d")),
                        Assign(list(Name("d", Store)), BinOp(Name("a", Load), Add, Name("b", Load)))),
                    list(),
                    null),
                Expr(Call(Name("q", Load), list(), list())),
                Assign(list(Name("result", Store)), BinOp(Name("a", Load), Mult, Name("d", Load)))),
            list(),
            null),
        Assign(list(Name("d", Store)), Num(41)),
        Expr(Call(Name("p", Load), list(), list()))))
        ;
        // @formatter:on
        Map<String, Object> state = new HashMap<>();
        state.put("result", 42);
        state.put("b", 1);
        state.put("d", 7);
        state.put("a", 6);
        executeTest(module, state); // globprog2
    }

// globprog3

    @Test
    public void globprog3() {
        // @formatter:off
        // # Test allocation of "names" (globals)
        // # global d *decalred* but not used at module level
        // global a, b, d
        // b = 1
        // a = 6
        // result = 0
        //
        // def p():
        //     global result
        //     def q():
        //         global d
        //         d = a + b
        //     q()
        //     result = a * d
        //
        // p()
        mod module = Module(
    list(
        Global(list("a", "b", "d")),
        Assign(list(Name("b", Store)), Num(1)),
        Assign(list(Name("a", Store)), Num(6)),
        Assign(list(Name("result", Store)), Num(0)),
        FunctionDef(
            "p",
            arguments(list(), null, list(), list(), null, list()),
            list(
                Global(list("result")),
                FunctionDef(
                    "q",
                    arguments(list(), null, list(), list(), null, list()),
                    list(
                        Global(list("d")),
                        Assign(list(Name("d", Store)), BinOp(Name("a", Load), Add, Name("b", Load)))),
                    list(),
                    null),
                Expr(Call(Name("q", Load), list(), list())),
                Assign(list(Name("result", Store)), BinOp(Name("a", Load), Mult, Name("d", Load)))),
            list(),
            null),
        Expr(Call(Name("p", Load), list(), list()))))
        ;
        // @formatter:on
        Map<String, Object> state = new HashMap<>();
        state.put("result", 42);
        state.put("b", 1);
        state.put("d", 7);
        state.put("a", 6);
        executeTest(module, state); // globprog3
    }

// argprog1

    @Test
    public void argprog1() {
        // @formatter:off
        // # Test allocation of argument lists and locals
        // def p(eins, zwei):
        //     def sum(un, deux, trois):
        //         return un + deux + trois
        //     def diff(tolv, fem):
        //         return tolv - fem
        //     def prod(sex, sju):
        //         return sex * sju
        //     drei = 3
        //     six = sum(eins, zwei, drei)
        //     seven = diff(2*six, drei+zwei)
        //     return prod(six, seven)
        //
        // result = p(1, 2)
        mod module = Module(
    list(
        FunctionDef(
            "p",
            arguments(list(arg("eins", null), arg("zwei", null)), null, list(), list(), null, list()),
            list(
                FunctionDef(
                    "sum",
                    arguments(
                        list(arg("un", null), arg("deux", null), arg("trois", null)),
                        null,
                        list(),
                        list(),
                        null,
                        list()),
                    list(Return(BinOp(BinOp(Name("un", Load), Add, Name("deux", Load)), Add, Name("trois", Load)))),
                    list(),
                    null),
                FunctionDef(
                    "diff",
                    arguments(list(arg("tolv", null), arg("fem", null)), null, list(), list(), null, list()),
                    list(Return(BinOp(Name("tolv", Load), Sub, Name("fem", Load)))),
                    list(),
                    null),
                FunctionDef(
                    "prod",
                    arguments(list(arg("sex", null), arg("sju", null)), null, list(), list(), null, list()),
                    list(Return(BinOp(Name("sex", Load), Mult, Name("sju", Load)))),
                    list(),
                    null),
                Assign(list(Name("drei", Store)), Num(3)),
                Assign(
                    list(Name("six", Store)),
                    Call(
                        Name("sum", Load),
                        list(Name("eins", Load), Name("zwei", Load), Name("drei", Load)),
                        list())),
                Assign(
                    list(Name("seven", Store)),
                    Call(
                        Name("diff", Load),
                        list(
                            BinOp(Num(2), Mult, Name("six", Load)),
                            BinOp(Name("drei", Load), Add, Name("zwei", Load))),
                        list())),
                Return(Call(Name("prod", Load), list(Name("six", Load), Name("seven", Load)), list()))),
            list(),
            null),
        Assign(list(Name("result", Store)), Call(Name("p", Load), list(Num(1), Num(2)), list()))))
        ;
        // @formatter:on
        Map<String, Object> state = new HashMap<>();
        state.put("result", 42);
        executeTest(module, state); // argprog1
    }

// closprog1

    @Test
    public void closprog1() {
        // @formatter:off
        // # Program requiring closures made of local variables
        // def p(a, b):
        //     x = a + 1 # =2
        //     def q(c):
        //         y = x + c # =4
        //         def r(d):
        //             z = y + d # =6
        //             def s(e):
        //                 return (e + x + y - 1) * z # =42
        //             return s(d)
        //         return r(c)
        //     return q(b)
        //
        // result = p(1, 2)
        mod module = Module(
    list(
        FunctionDef(
            "p",
            arguments(list(arg("a", null), arg("b", null)), null, list(), list(), null, list()),
            list(
                Assign(list(Name("x", Store)), BinOp(Name("a", Load), Add, Num(1))),
                FunctionDef(
                    "q",
                    arguments(list(arg("c", null)), null, list(), list(), null, list()),
                    list(
                        Assign(list(Name("y", Store)), BinOp(Name("x", Load), Add, Name("c", Load))),
                        FunctionDef(
                            "r",
                            arguments(list(arg("d", null)), null, list(), list(), null, list()),
                            list(
                                Assign(list(Name("z", Store)), BinOp(Name("y", Load), Add, Name("d", Load))),
                                FunctionDef(
                                    "s",
                                    arguments(list(arg("e", null)), null, list(), list(), null, list()),
                                    list(
                                        Return(
                                            BinOp(
                                                BinOp(
                                                    BinOp(
                                                        BinOp(Name("e", Load), Add, Name("x", Load)),
                                                        Add,
                                                        Name("y", Load)),
                                                    Sub,
                                                    Num(1)),
                                                Mult,
                                                Name("z", Load)))),
                                    list(),
                                    null),
                                Return(Call(Name("s", Load), list(Name("d", Load)), list()))),
                            list(),
                            null),
                        Return(Call(Name("r", Load), list(Name("c", Load)), list()))),
                    list(),
                    null),
                Return(Call(Name("q", Load), list(Name("b", Load)), list()))),
            list(),
            null),
        Assign(list(Name("result", Store)), Call(Name("p", Load), list(Num(1), Num(2)), list()))))
        ;
        // @formatter:on
        Map<String, Object> state = new HashMap<>();
        state.put("result", 42);
        executeTest(module, state); // closprog1
    }

// closprog2

    @Test
    public void closprog2() {
        // @formatter:off
        // # Program requiring closures from arguments
        // def p(r, i):
        //     def sum():
        //         return r + i
        //     def diff():
        //         def q():
        //             return r - i
        //         return q()
        //     def prod():
        //         return r * i
        //     return prod() + sum() + diff()
        //
        // result = p(7, 4)
        mod module = Module(
    list(
        FunctionDef(
            "p",
            arguments(list(arg("r", null), arg("i", null)), null, list(), list(), null, list()),
            list(
                FunctionDef(
                    "sum",
                    arguments(list(), null, list(), list(), null, list()),
                    list(Return(BinOp(Name("r", Load), Add, Name("i", Load)))),
                    list(),
                    null),
                FunctionDef(
                    "diff",
                    arguments(list(), null, list(), list(), null, list()),
                    list(
                        FunctionDef(
                            "q",
                            arguments(list(), null, list(), list(), null, list()),
                            list(Return(BinOp(Name("r", Load), Sub, Name("i", Load)))),
                            list(),
                            null),
                        Return(Call(Name("q", Load), list(), list()))),
                    list(),
                    null),
                FunctionDef(
                    "prod",
                    arguments(list(), null, list(), list(), null, list()),
                    list(Return(BinOp(Name("r", Load), Mult, Name("i", Load)))),
                    list(),
                    null),
                Return(
                    BinOp(
                        BinOp(
                            Call(Name("prod", Load), list(), list()),
                            Add,
                            Call(Name("sum", Load), list(), list())),
                        Add,
                        Call(Name("diff", Load), list(), list())))),
            list(),
            null),
        Assign(list(Name("result", Store)), Call(Name("p", Load), list(Num(7), Num(4)), list()))))
        ;
        // @formatter:on
        Map<String, Object> state = new HashMap<>();
        state.put("result", 42);
        executeTest(module, state); // closprog2
    }

// closprog3

    @Test
    public void closprog3() {
        // @formatter:off
        // # Program requiring closures (mixed)
        // def p(ua, b): #(1,2)
        //     z = ua + b # 3
        //     def q(uc, d): #(1,3)
        //         y = ua + uc + z # 5
        //         def r(ue, f): #(1,5)
        //             x = (ua + uc) + (ue + f) + (y + z) # 16
        //             def s(uf, g): # (1,16)
        //                 return (ua + uc - ue) + (uf + g) + (x + y + z)
        //             return s(ue, x)
        //         return r(uc, y)
        //     return q(ua, z)
        // result = p(1, 2)
        mod module = Module(
    list(
        FunctionDef(
            "p",
            arguments(list(arg("ua", null), arg("b", null)), null, list(), list(), null, list()),
            list(
                Assign(list(Name("z", Store)), BinOp(Name("ua", Load), Add, Name("b", Load))),
                FunctionDef(
                    "q",
                    arguments(list(arg("uc", null), arg("d", null)), null, list(), list(), null, list()),
                    list(
                        Assign(
                            list(Name("y", Store)),
                            BinOp(BinOp(Name("ua", Load), Add, Name("uc", Load)), Add, Name("z", Load))),
                        FunctionDef(
                            "r",
                            arguments(list(arg("ue", null), arg("f", null)), null, list(), list(), null, list()),
                            list(
                                Assign(
                                    list(Name("x", Store)),
                                    BinOp(
                                        BinOp(
                                            BinOp(Name("ua", Load), Add, Name("uc", Load)),
                                            Add,
                                            BinOp(Name("ue", Load), Add, Name("f", Load))),
                                        Add,
                                        BinOp(Name("y", Load), Add, Name("z", Load)))),
                                FunctionDef(
                                    "s",
                                    arguments(
                                        list(arg("uf", null), arg("g", null)),
                                        null,
                                        list(),
                                        list(),
                                        null,
                                        list()),
                                    list(
                                        Return(
                                            BinOp(
                                                BinOp(
                                                    BinOp(
                                                        BinOp(Name("ua", Load), Add, Name("uc", Load)),
                                                        Sub,
                                                        Name("ue", Load)),
                                                    Add,
                                                    BinOp(Name("uf", Load), Add, Name("g", Load))),
                                                Add,
                                                BinOp(
                                                    BinOp(Name("x", Load), Add, Name("y", Load)),
                                                    Add,
                                                    Name("z", Load))))),
                                    list(),
                                    null),
                                Return(Call(Name("s", Load), list(Name("ue", Load), Name("x", Load)), list()))),
                            list(),
                            null),
                        Return(Call(Name("r", Load), list(Name("uc", Load), Name("y", Load)), list()))),
                    list(),
                    null),
                Return(Call(Name("q", Load), list(Name("ua", Load), Name("z", Load)), list()))),
            list(),
            null),
        Assign(list(Name("result", Store)), Call(Name("p", Load), list(Num(1), Num(2)), list()))))
        ;
        // @formatter:on
        Map<String, Object> state = new HashMap<>();
        state.put("result", 42);
        executeTest(module, state); // closprog3
    }

    // ======= End of generated examples ==========
}
