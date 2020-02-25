package uk.co.farowl.vsj2.evo3;

/** Holder for objects appearing in the closure of a function. */
class Cell {

    PyObject obj;

    Cell(PyObject obj) { this.obj = obj; }

    @Override
    public String toString() {
        return String.format("<cell [%.80s]>", obj);
    }
    static final Cell[] EMPTY_ARRAY = new Cell[0];
}
