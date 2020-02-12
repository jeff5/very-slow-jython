..  generated-code/sequences-and-indexing.rst

Sequences and Indexing
######################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo2``
    and ``rt2/src/test/java/.../vsj2/evo2/PyByteCode3.java``
    in the project source.

Tuple Creation and Indexing
***************************

We're going to consider what is necessary to execute
a trivial fragment of Python using ``tuple``\s like this:

..  code-block:: python

    c = 22.0
    d = (20, "hello", c)
    b = d[1]
    c = d[2] + d[0]

The compiled byte code brings us new opcodes
``BUILD_TUPLE`` and ``BINARY_SUBSCR``:

..  code-block:: none

  2           0 LOAD_CONST               0 (22.0)
              2 STORE_NAME               0 (c)

  3           4 LOAD_CONST               1 (20)
              6 LOAD_CONST               2 ('hello')
              8 LOAD_NAME                0 (c)
             10 BUILD_TUPLE              3
             12 STORE_NAME               1 (d)

  4          14 LOAD_NAME                1 (d)
             16 LOAD_CONST               3 (1)
             18 BINARY_SUBSCR
             20 STORE_NAME               2 (b)

  5          22 LOAD_NAME                1 (d)
             24 LOAD_CONST               4 (2)
             26 BINARY_SUBSCR
             28 LOAD_NAME                1 (d)
             30 LOAD_CONST               5 (0)
             32 BINARY_SUBSCR
             34 BINARY_ADD
             36 STORE_NAME               0 (c)
             38 LOAD_CONST               6 (None)
             40 RETURN_VALUE

We see that ``BUILD_TUPLE`` works with values pushed to the stack,
the opcode argument telling us how many.
If we add a constructor to ``PyTuple`` for this case,
we can code this operation as:

..  code-block:: java

                    case Opcode.BUILD_TUPLE:
                        sp -= oparg; // STACK_SHRINK(oparg)
                        w = new PyTuple(valuestack, sp, oparg);
                        valuestack[sp++] = w; // PUSH
                        break;

Accessing an element (``BINARY_SUBSCR``) is simple enough here,
but the supporting method is surprisingly complicated:

..  code-block:: java

                    case Opcode.BINARY_SUBSCR: // w[u]
                        w = valuestack[--sp]; // POP
                        u = valuestack[sp - 1]; // TOP
                        res = Abstract.getItem(u, w);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

We follow the code structure of CPython, at least for now.
``Abstract.getItem(u, w)`` is a call to the abstract object interface.
The CPython version of this method (in ``Include/abstract.c``) is:

..  code-block:: C

    PyObject *
    PyObject_GetItem(PyObject *o, PyObject *key)
    {
        PyMappingMethods *m;
        PySequenceMethods *ms;

        if (o == NULL || key == NULL) {
            return null_error();
        }

        m = o->ob_type->tp_as_mapping;
        if (m && m->mp_subscript) {
            PyObject *item = m->mp_subscript(o, key);
            assert((item != NULL) ^ (PyErr_Occurred() != NULL));
            return item;
        }

        ms = o->ob_type->tp_as_sequence;
        if (ms && ms->sq_item) {
            if (PyIndex_Check(key)) {
                Py_ssize_t key_value;
                key_value = PyNumber_AsSsize_t(key, PyExc_IndexError);
                if (key_value == -1 && PyErr_Occurred())
                    return NULL;
                return PySequence_GetItem(o, key_value);
            }
            else {
                return type_error("sequence index must "
                                  "be integer, not '%.200s'", key);
            }
        }

        /* ... handling of __class_getitem__ not shown */

        return type_error("'%.200s' object is not subscriptable", o);
    }

We see that as with the abstract interface for arithmetic,
we have to defend explicitly against ``NULL`` objects,
and ``NULL`` slots.
The other obvious feature is that Python has to test for two slots:
``mp_subscript``  in the mapping protocol and
``sq_item`` in the sequence one (within ``PySequence_GetItem``).
Clearly this is because item access ``[]`` applies to mappings
as well as sequences.

There is a third clause in CPython that we'll ignore.
This exists to support expressions with two types like ``List[int]``,
occurring in type hints.

We might expect that the mapping protocol would be empty for ``tuple``,
but in fact ``tuple``,
and almost every other built-in sequence type,
defines it.
It is therefore the mapping clause that normally takes the call,
not the sequence one.

Our implementation of this takes advantage of the ``EmptyException``
convention that saves us testing slots continually.
If the mapping slot is empty, ``EmptyException`` is thrown,
and we fall through to the sequence slot:

..  code-block:: java

    class Abstract {
        // ...
        static PyObject getItem(PyObject o, PyObject key) throws Throwable {
            PyType oType = o.getType();
            try {
                MethodHandle mh = oType.mapping.subscript;
                return (PyObject) mh.invokeExact(o, key);
            } catch (EmptyException e) {}

            if (Slot.SQ.item.isDefinedFor(oType)) {
                // For a sequence (only), key must have index-like type
                if (Slot.NB.index.isDefinedFor(key.getType())) {
                    int k = Number.asSize(key, IndexError::new);
                    return Sequence.getItem(o, k);
                } else
                    throw typeError(MUST_BE_INT_NOT, key);
            } else
                throw typeError(NOT_SUBSCRIPTABLE, o);
        }
    }

In order to fill the ``tuple`` type's ``mapping.subscript`` slot,
and ``sequence.item`` slot,
it is only necessary to define the correctly-named methods:

..  code-block:: java

    class PyTuple implements PyObject {
        // ...
        static PyObject item(PyObject s, int i) {
            try {
                return ((PyTuple) s).value[i];
            } catch (IndexOutOfBoundsException e) {
                throw new IndexError("tuple index out of range");
            } catch (ClassCastException e) {
                throw PyObjectUtil.typeMismatch(s, TYPE);
            }
        }

        static PyObject subscript(PyObject s, PyObject item)
                throws Throwable {
            try {
                PyTuple self = (PyTuple) s;
                PyType itemType = item.getType();
                if (Slot.NB.index.isDefinedFor(itemType)) {
                    int i = Number.asSize(item, IndexError::new);
                    if (i < 0) { i += self.value.length; }
                    return item(self, i);
                }
                // else if item is a PySlice { ... } not implemented
                else
                    throw Abstract.indexTypeError(self, item);
            } catch (ClassCastException e) {
                throw PyObjectUtil.typeMismatch(s, TYPE);
            }
        }
    }

Notice that the ``item`` slot takes a Java ``int`` as the index,
while the ``subscript`` slot takes an object
that it then interprets using slice or end-relative semantics.
We haven't defined slices yet.


List Creation and Indexing
**************************


..  code-block:: java


..  code-block:: java


..  code-block:: java


..  code-block:: java


