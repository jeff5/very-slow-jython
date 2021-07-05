# PyFloat.py: A generator for Java files that define the Python float


from dataclasses import dataclass

from . import ImplementationGenerator

@dataclass
class TypeInfo:
    # Java name of a Java class ("PyFloat", "Integer", etc.)
    name: str
    # Expression with one {} that converts that to primitive double
    conv: str


@dataclass
class OpInfo:
    # Name of the operation ("__add__", "__neg__").
    name: str
    # Expression for the result
    expr: str
    # self operand name
    self: str = 'self'
    # other operand name (binary)
    other: str = 'other'


class PyFloatGenerator(ImplementationGenerator):

    # The canonical and adopted implementations in PyFloat.java,
    # as there are no further accepted self-classes.
    ACCEPTED_CLASSES = [
        TypeInfo('PyFloat', '{}.value'),
        TypeInfo('Double', '{}.doubleValue()'),
    ]

    # These classes, the accepted types of int,  may occur as the
    # second operand in binary operations. Order is not significant.
    OPERAND_CLASSES = ACCEPTED_CLASSES + [
        TypeInfo('Integer', '{}.doubleValue()'),
        TypeInfo('BigInteger', 'PyLong.convertToDouble({})'),
        TypeInfo('PyLong', 'PyLong.convertToDouble({}.value)'),
        TypeInfo('Boolean', '({}.booleanValue() ? 1.0 : 0.0)'),
    ]

    # Operations may simply be codified as a return expression, since
    # all operand types may be converted to primitive double.
    UNARY_OPS = [
        OpInfo('__abs__', 'Math.abs({})'),
        OpInfo('__neg__', '-{}'),
        OpInfo('__repr__', 'Double.toString({})'),
    ]
    PREDICATE_OPS = [
        OpInfo('__bool__', '{} != 0.0'),
    ]
    BINARY_OPS = [
        OpInfo('__add__', '{} + {}', 'v', 'w'),
        OpInfo('__radd__', '{1} + {0}', 'w', 'v'),
        OpInfo('__sub__', '{} - {}', 'v', 'w'),
        OpInfo('__rsub__', '{1} - {0}', 'w', 'v'),
        OpInfo('__mul__', '{} * {}', 'v', 'w'),
        OpInfo('__rmul__', '{1} * {0}', 'w', 'v'),
    ]
    BINARY_PREDICATE_OPS = [
        OpInfo('__lt__', '{} < {}'),
        OpInfo('__eq__', '{} == {}'),
    ]

    # Emit methods selectable by a single type
    def special_methods(self, e):

        # Emit the unary operations
        for op in self.UNARY_OPS:
            self.emit_heading(e, op.name)
            for t in self.ACCEPTED_CLASSES:
                self.special_unary(e, op, t)

        # Emit the unary predicate operations
        for op in self.PREDICATE_OPS:
            self.emit_heading(e, op.name)
            for t in self.ACCEPTED_CLASSES:
                self.special_predicate(e, op, t)

        # Emit the binary operations op(T, Object)
        for op in self.BINARY_OPS:
            self.emit_heading(e, op.name)
            for vt in self.ACCEPTED_CLASSES:
                self.special_binary_object(e, op, vt)

        # Emit the binary predicate operations op(T, Object)
        for op in self.BINARY_PREDICATE_OPS:
            self.emit_heading(e, op.name)
            for vt in self.ACCEPTED_CLASSES:
                self.special_binary_predicate_object(e, op, vt)

    # Emit methods selectable by a pair of types (for call sites)
    def special_binops(self, e):

        # Emit the binary operations
        for op in self.BINARY_OPS:
            for vt in self.ACCEPTED_CLASSES:
                for wt in self.OPERAND_CLASSES:
                    self.special_binary(e, op, vt, wt)

    def special_unary(self, e, op, t):
        e.emit_line('static Object ')
        e.emit(op.name).emit('(').emit(t.name).emit(' self) {')
        # This is how we convert self to a double
        dbl = t.conv.format('self')
        # This is the action specific to the operation
        expr = op.expr.format(dbl)
        with e.indentation():
            e.emit_line('return ').emit(expr).emit(';')
        e.emit_line('}').emit_line()

    def special_predicate(self, e, op, t):
        e.emit_line('static boolean ')
        e.emit("{}({} {}) {{".format(op.name, t.name, op.self))
        # This is how we convert self to a double
        dbl = t.conv.format(op.self)
        # This is the action specific to the operation
        expr = op.expr.format(dbl)
        with e.indentation():
            e.emit_line('return ').emit(expr).emit(';')
        e.emit_line('}').emit_line()

    def special_binary_object(self, e, op, st):
        e.emit_line('static Object ')
        e.emit("{}({} {}, Object {}) {{".format(
            op.name, st.name, op.self, op.other))
        with e.indentation():
            # This is how we convert self to a double
            sdbl = st.conv.format(op.self)
            # This is how we convert Object other to a double
            odbl = "convert({})".format(op.other)
            # This is the action specific to the operation
            e.emit_line("try {")
            with e.indentation():
                expr = op.expr.format(sdbl, odbl)
                e.emit_line('return ').emit(expr).emit(';')
            e.emit_line('} catch (NoConversion e) ')
            e.emit('{ return Py.NotImplemented; ').emit('}')
        e.emit_line('}').emit_line()

    def special_binary_predicate_object(self, e, op, st):
        e.emit_line('static Object ')
        e.emit("{}({} {}, Object {}) {{".format(
            op.name, st.name, op.self, op.other))
        with e.indentation():
            # This is how we convert self to a double
            sdbl = st.conv.format(op.self)
            # This is how we convert Object other to a double
            odbl = "convert({})".format(op.other)
            # This is the action specific to the operation
            e.emit_line("try {")
            with e.indentation():
                expr = op.expr.format(sdbl, odbl)
                e.emit_line('return ').emit(expr).emit(';')
            e.emit_line('} catch (NoConversion e) ')
            e.emit('{ return Py.NotImplemented; ').emit('}')
        e.emit_line('}').emit_line()

    # Emit one binary operation, for example:
    #    private static Object __add__(Double v, Integer w) {
    #        return v.doubleValue() + w.doubleValue();
    #    }
    def special_binary(self, e, op, st, ot):
        e.emit_line('static Object ')
        e.emit("{}({} {}, {} {}) {{".format(
            op.name, st.name, op.self, ot.name, op.other))
        with e.indentation():
            # This is how we convert self to a double
            sdbl = st.conv.format(op.self)
            # This is how we convert other to a double
            odbl = ot.conv.format(op.other)
            # This is the action specific to the operation
            expr = op.expr.format(sdbl, odbl)
            e.emit_line('return ').emit(expr).emit(';')
        e.emit_line('}').emit_line()

