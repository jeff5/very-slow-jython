// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import java.util.EnumSet;
import java.util.Map;

import uk.co.farowl.vsj4.core.JVM17Code.JVM17Layout;
import uk.co.farowl.vsj4.internal.Util;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * A {@link PyFrame} for executing Python compiled to JVM 17 byte code.
 */
public abstract class JVM17Frame extends PyFrame<JVM17Code> {

    /**
     * The code object this frame is executing, exposed as read-only
     * {@code f_code}.
     */
    final JVM17Code code;

    /**
     * The built-in objects from {@link #func}, wrapped (if necessary)
     * to make it a {@code Map}. Inside the wrapper it will be accessed
     * using the Python mapping protocol.
     */
    protected final Map<Object, Object> builtins;

    /**
     * Create a {@code JVM17Frame}, which is a {@code PyFrame} with the
     * storage and mechanism to execute a module or isolated code object
     * (compiled to a {@link JVM17Code}.
     *
     * @param func that this frame executes
     * @param code code object of the {@code func}
     * @param locals local name space (may be {@code null})
     */
    public JVM17Frame(PyFunction func, JVM17Code code, Object locals) {
        super(func);
        assert func.code == code;
        this.code = code;

        // The need for a dictionary of locals depends on the code
        EnumSet<CodeFlag> traits = code.flags;
        if (traits.contains(CodeFlag.NEWLOCALS)) {
            // Ignore locals argument
            if (traits.contains(CodeFlag.OPTIMIZED)) {
                // We can create it later but probably won't need to
                this.locals = null;
            } else {
                this.locals = new PyDict();
            }
        } else if (locals == null) {
            // Default to same as globals.
            this.locals = func.globals;
        } else {
            /*
             * Use supplied locals. As it may not implement j.u.Map, we
             * wrap any Python object as a Map. Depending on the
             * operations attempted, this may break later.
             */
            // TODO wrap any Python object as a Map
            this.locals = locals;
        }

        // Locally present the func.__builtins__ as a Map
        this.builtins = PyMapping.map(func.builtins);
    }

    /**
     *
     * {@inheritDoc}
     * <p>
     * This method implements the frame push and pop, but it calls
     * {@link #body()} to execute the compiled code. It also relieves
     * {@code body()} of exception handling, where this is not explicit
     * in the code itself, converting exceptions to their Python
     * equivalents.
     */
    // TODO Implement exception handling for Python frames
    @Override
    Object eval() {
        try {
            return body();
        } catch (Throwable t) {
            // Just propagate the error for now. This is a stop-gap.
            throw Util.asUnchecked(t);
        }
    }

    /**
     * Subclasses define this method to define the behaviour of the
     * function or module. It is called by {@link #eval()}, which wraps
     * it in appropriate frame and exception handling. Any kind of
     * exception may be thrown: Python {@link PyBaseException
     * BaseException} or a Java {@code Throwable} of any kind.
     *
     * @return value of the function (or {@code None})
     * @throws Throwable from the implementation
     */
    public abstract Object body() throws Throwable;

    @Override
    JVM17Code getCode() { return code; }

    @Override
    JVM17Code.Wrapper getWrapper() {
        // For the JVM PyFrame, the code object supplies the wrapper
        return code.new Wrapper(this);
    }

    @Override
    // Compare CPython PyFrame_FastToLocalsWithError in frameobject.c
    // Also PyFrame_FastToLocals in frameobject.c
    void fastToLocals() {
        // We re-use the frame locals dict, if we have one.
        PyDict locals;
        if (this.locals instanceof PyDict d) {
            locals = d;
        } else if (this.locals == null) {
            // Let's have one!
            this.locals = locals = Py.dict();
        } else {
            throw new InterpreterError("non-dict frame locals.");
        }

        // Work through the frame pulling out names and values
        JVM17Code.Wrapper wrapper = getWrapper();
        JVM17Layout layout = code.layout;
        int n = layout.size();

        for (int i = 0; i < n; i++) {
            Object value = wrapper.getLocal(i);
            if (value instanceof PyCell cell) { value = cell.get(); }
            // In general, we are adjusting pre-existing dictionary.
            String key = layout.name(i);
            if (value == null) {
                locals.remove(key);
            } else {
                locals.put(key, value);
            }
        }
    }

    // Plumbing -----------------------------------------------------
}
