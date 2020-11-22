package uk.co.farowl.vsj2.evo4;

import uk.co.farowl.vsj2.evo4.Slot.Cls;
import uk.co.farowl.vsj2.evo4.Slot.Self;

/** Some shorthands used to construct method signatures, etc.. */
interface ClassShorthand {

    static final Class<PyObject> O = PyObject.class;
    static final Class<PyUnicode> U = PyUnicode.class;
    static final Class<Self> S = Self.class;
    static final Class<Cls> C = Cls.class;
    static final Class<?> I = int.class;
    static final Class<?> B = boolean.class;
    static final Class<PyType> T = PyType.class;
    static final Class<?> V = void.class;
    static final Class<Comparison> CMP = Comparison.class;
    static final Class<PyTuple> TUPLE = PyTuple.class;
    static final Class<PyDict> DICT = PyDict.class;
    static final Class<PyObject[]> OA = PyObject[].class;
}