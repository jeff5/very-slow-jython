# PyLong.py: A generator for Java files that define the Python float

# This generator writes PyLongMethods.java and PyLongBinops.java .

from dataclasses import dataclass
from typing import Callable

from . import ImplementationGenerator


BIG_TYPES = {"PyLong", "BigInteger", "Object"}

@dataclass
class TypeInfo:
    # Java name of a Java class ("PyLong", "Integer", etc.)
    name: str
    # Function that converts that to primitive long
    as_long: Callable = lambda x: f'{x}.longValue()'
    # Expression with one {} that converts that to a BigInteger
    as_big: Callable = lambda x: f'BigInteger.valueOf({x}.longValue())'

def pylong_as_big(x):
    return f'{x}.value'

def pylong_as_long(x):
    return f'{x}.value.longValue() /* XXX */'  # Probably a bad idea.

def big_as_long(x):
    return f'{x}.longValue() /* XXX */'  # Probably a bad idea.

def bool_as_big(x):
    return f'({x} ? ONE : ZERO)'

def bool_as_long(x):
    return f'({x} ? 1L : 0L)'


@dataclass
class OpInfo:
    # Name of the operation ("__add__", "__neg__", etc.).
    name: str


def missing_unary_method(op, t):
    return f'// Missing {op.name}({t.name})'

def unary_method(op:"UnaryOpInfo", t:TypeInfo):
    if t.name in BIG_TYPES:
        return f'''
        static Object {op.name}({t.name} self) {{
            return {op.big_op(t.as_big("self"))};
        }}
        '''
    else:
        return f'''
        static Object {op.name}({t.name} self) {{
            long r = {op.long_op(t.as_long("self"))};
            int s = (int) r;
            return s == r ? s : BigInteger.valueOf(r);
        }}
        '''


@dataclass
class UnaryOpInfo(OpInfo):
    # Function returning expression using long as common type
    long_op: Callable
    # Function returning expression using BigInteger as common type
    big_op: Callable
    # Function to compute the method as a string
    method: Callable = unary_method


def string_method(op:"StringOpInfo", t:TypeInfo):
    # In due course, remove the Py.str() wrapper
    if t.name in BIG_TYPES:
        return f'''
        static Object {op.name}({t.name} self) {{
            return Py.str({op.big_op(t.as_big("self"))});
        }}
        '''
    else:
        return f'''
        static Object {op.name}({t.name} self) {{
           return Py.str({op.long_op(t.as_long("self"))});
        }}
        '''


@dataclass
class StringOpInfo(OpInfo):
    # Function returning expression using long as common type
    long_op: Callable
    # Function returning expression using BigInteger as common type
    big_op: Callable
    # Function to compute the method as a string
    method: Callable = string_method


def missing_binary_method(op:OpInfo, t1:TypeInfo, t2:TypeInfo):
    return f'// Missing {op.name}({t1.name}, {t2.name})'

def binary_method(op:OpInfo, t1:TypeInfo, t2:TypeInfo):

    if not op.reflected:
        # signature is Object op(t1 v, t2 w), expr is v op w
        vt, a1, wt, a2 = t1, 'v', t2, 'w'
    else:
        # signature is Object op(t1 w, t2 v), expr is v op w
        vt, a1, wt, a2 = t2, 'w', t1, 'v'
    signature = f'Object {op.name}({t1.name} {a1}, {t2.name} {a2})'

    if t1.name in BIG_TYPES or t2.name in BIG_TYPES:
        expr = f'{op.big_op(vt.as_big("v"), wt.as_big("w"))}'
        if t2.name == "Object":
            # An Object argument implies we call toBig: deal with
            # possibly needless BigInteger and possible exception.
            return f'''
            static {signature} {{
                try {{
                    return toInt({expr});
                }} catch (NoConversion e) {{
                    return Py.NotImplemented;
                }}
            }}
            '''
        else:
            return f'''
            static {signature} {{
                return {expr};
            }}
            '''
    else:
        expr = f'{op.long_op(vt.as_long("v"), wt.as_long("w"))}'
        return f'''
        static {signature} {{
            long r = {expr};
            int s = (int) r;
            return s == r ? s : BigInteger.valueOf(r);
        }}
        '''

@dataclass
class BinaryOpInfo(OpInfo):
    # self operator long_op
    long_op: str
    # method name in BigInteger
    big_op: str
    # True if reflected operation
    reflected: bool = False
    # Function to compute the method as a string
    method: Callable = binary_method


class PyLongGenerator(ImplementationGenerator):

    # The canonical and adopted implementations in PyInteger.java,
    # as there are no further accepted self-classes.
    ACCEPTED_CLASSES = [
        TypeInfo('PyLong', pylong_as_long, pylong_as_big),
        TypeInfo('BigInteger', big_as_long, lambda x: x),
        TypeInfo('Integer'),
        TypeInfo('Boolean', bool_as_long, bool_as_big),
    ]
    OPERAND_CLASSES = ACCEPTED_CLASSES + [
    ]
    OBJECT_CLASS = TypeInfo('Object', None, lambda x: f'toBig({x})')

    # Operations have to provide versions in which Integer and
    # BigInteger are the common type to which arguments are converted.

    UNARY_OPS = [
        UnaryOpInfo('__abs__', lambda x: f'Math.abs({x})',
            lambda x: f'{x}.abs()'),
        UnaryOpInfo('__neg__', lambda x: f'-{x}',
            lambda x: f'{x}.negate()'),
    ]
    STRING_OPS = [
        StringOpInfo('__repr__', lambda x: f'Long.toString({x})',
            lambda x: f'{x}.toString()'),
    ]
#     PREDICATE_OPS = [
#         UnaryOpInfo('__bool__', '{} != 0.0'),
#     ]
    BINARY_OPS = [
        BinaryOpInfo('__add__', lambda x, y: f'{x} + {y}', 
            lambda x, y: f'{x}.add({y})'),
        BinaryOpInfo('__radd__', lambda x, y: f'{x} + {y}', 
            lambda x, y: f'{x}.add({y})', True),
        BinaryOpInfo('__sub__', lambda x, y: f'{x} - {y}', 
            lambda x, y: f'{x}.subtract({y})'),
        BinaryOpInfo('__rsub__', lambda x, y: f'{x} - {y}', 
            lambda x, y: f'{x}.subtract({y})', True),
        BinaryOpInfo('__mul__', lambda x, y: f'{x} * {y}', 
            lambda x, y: f'{x}.multiply({y})'),
        BinaryOpInfo('__rmul__', lambda x, y: f'{x} * {y}', 
            lambda x, y: f'{x}.multiply({y})', True),

        BinaryOpInfo('__and__', lambda x, y: f'{x} & {y}', 
            lambda x, y: f'{x}.and({y})'),
        BinaryOpInfo('__rand__', lambda x, y: f'{x} & {y}', 
            lambda x, y: f'{x}.and({y})', True),
        BinaryOpInfo('__or__', lambda x, y: f'{x} | {y}', 
            lambda x, y: f'{x}.or({y})'),
        BinaryOpInfo('__ror__', lambda x, y: f'{x} | {y}', 
            lambda x, y: f'{x}.or({y})', True),
        BinaryOpInfo('__xor__', lambda x, y: f'{x} ^ {y}', 
            lambda x, y: f'{x}.xor({y})'),
        BinaryOpInfo('__rxor__', lambda x, y: f'{x} ^ {y}', 
            lambda x, y: f'{x}.xor({y})', True),
    ]
#     BINARY_PREDICATE_OPS = [
#         BinaryOpInfo('__lt__', '{} < {}'),
#         BinaryOpInfo('__eq__', '{} == {}'),
#     ]

    # Emit methods selectable by a single type
    def special_methods(self, e):

        # Emit the string unary operations
        for op in self.STRING_OPS:
            for t in self.ACCEPTED_CLASSES:
                self.special_unary(e, op, t)

        # Emit the unary operations
        for op in self.UNARY_OPS:
            for t in self.ACCEPTED_CLASSES:
                self.special_unary(e, op, t)

        # Emit the unary predicate operations
#         for op in self.PREDICATE_OPS:
#             for t in self.ACCEPTED_CLASSES:
#                 self.special_predicate(e, op, t)

        # Emit the binary operations op(T, Object)
        for op in self.BINARY_OPS:
            for vt in self.ACCEPTED_CLASSES:
                self.special_binary(e, op, vt, self.OBJECT_CLASS)

        # Emit the binary predicate operations op(T, Object)
#         for op in self.BINARY_PREDICATE_OPS:
#             for vt in self.ACCEPTED_CLASSES:
#                 self.special_binary_predicate_object(e, op, vt)

    # Emit methods selectable by a pair of types (for call sites)
    def special_binops(self, e):

        # Emit the binary operations
        for op in self.BINARY_OPS:
            for vt in self.ACCEPTED_CLASSES:
                for wt in self.OPERAND_CLASSES:
                    self.special_binary(e, op, vt, wt)

    def left_justify(self, text):
        lines = list()
        # Find common leading indent
        common = 999
        for line in text.splitlines():
            # Discard trailing space
            line = line.rstrip()
            # Discard empty lines
            if (n:=len(line)) > 0:
                space = n - len(line.lstrip())
                if space < common: common = space
                lines.append(line)
        if common == 999: common = 0
        # Remove this common prefix
        clean = list()
        for line in lines:
            clean.append(line[common:])
        return clean

    def special_unary(self, e, op, t):
        method = op.method(op, t)
        method = self.left_justify(method)
        e.emit_lines(method)

    def special_binary(self, e, op, t1, t2):
        method = op.method(op, t1, t2)
        method = self.left_justify(method)
        e.emit_lines(method)


