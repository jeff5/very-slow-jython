package uk.co.farowl.vsj2dy.evo4;

import uk.co.farowl.vsj2.evo4.Number;
import uk.co.farowl.vsj2.evo4.PyObject;

public class AbstractProxyModel {

    private AbstractProxyModel() {} // No instances

    public static PyObject negative(PyObject v) throws Throwable {
        return Number.negative(v);
    }

}
