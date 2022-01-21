package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** Placeholder to satisfy references. */
public class PyCode {

    public PyCode(int argcount, int posonlyargcount, int kwonlyargcount,
            int nlocals, int stacksize, int flags, Object code,
            Object consts, Object names, Object varnames,
            Object freevars, Object cellvars, Object filename,
            Object name, int firstlineno, Object lnotab) {
        // TODO Auto-generated constructor stub
    }

    /** The Python type {@code code}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("code", MethodHandles.lookup())
                    .flagNot(PyType.Flag.BASETYPE));

}
