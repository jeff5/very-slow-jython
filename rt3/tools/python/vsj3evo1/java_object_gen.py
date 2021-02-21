# vsj3evo1/java_object_gen Emit Java
#
# This is a tool used from the rt3.gradle build file to generate object
# implementation methods, such as __neg__ and __rsub__, in Java.
# It processes Java files looking for a few simple markers, which it
# replaces with blocks of method definitions.
#
# See the files in rt3/src/main/javaTemplate for examples.

import sys
import os
import re
import argparse
import srcgen
from re import match
from contextlib import closing
from dataclasses import dataclass


class ImplementationTemplateProcessorFactory:

    def __init__(self, source_dir, dest_dir, verbose=False):
        self.src_dir = os.path.relpath(source_dir)
        self.dst_dir = os.path.relpath(dest_dir)
        self.verbose = verbose
        # Check source directory
        if not os.path.isdir(self.src_dir):
            parser.error(f'no such directory {self.src_dir}')
        # Ensure destination directory
        if not os.path.isdir(self.dst_dir):
            os.makedirs(self.dst_dir, exist_ok=True)
        # Confirm
        if self.verbose:
            # cwd is the project directory e.g. ~/rt3
            cwd = os.getcwd()
            print(f'  Current dir = {cwd}')
            print(f'  templates from {self.src_dir} to {self.dst_dir}')

    def get_processor(self, package, name):
        return ImplementationTemplateProcessor(self, package, name)


class ImplementationTemplateProcessor:

    # Patterns marker lines in template files.
    # Each has a group 1 that captures the indentation.
    OBJECT_TEMPLATE = re.compile(
            r'([\t ]*)//\s*\$OBJECT_TEMPLATE\$\s*(\w+)')
    SPECIAL_METHODS = re.compile(r'([\t ]*)//\s*\$SPECIAL_METHODS\$')
    SPECIAL_BINOPS = re.compile(r'([\t ]*)//\s*\$SPECIAL_BINOPS\$')
    MANGLED = re.compile(r'(([\t ]*)//\s*\($\w+\$)')

    def __init__(self, factory, package, name):
        self.factory = factory
        self.package = package
        self.name = name
        self.templateClass = ImplementationTemplate
        self.template = None
        self.emitterClass = srcgen.IndentedEmitter

    def open_src(self):
        return open(
            os.path.join(self.factory.src_dir, self.package, self.name),
                    'r', encoding='utf-8')

    def open_dst(self):
        location = os.path.join(self.factory.dst_dir, self.package)
        os.makedirs(location, exist_ok=True)
        return open(
            os.path.join(location, self.name),
                    'w', encoding='utf-8', newline='\n')

    def process(self):
        if self.factory.verbose:
            print(f"    process {self.name}")
        with self.open_src() as src:
            with self.open_dst() as dst:
                self.process_lines(src, dst)

    def process_lines(self, src, dst):

        def emitter(m):
            indent = (len(m[1].expandtabs(4)) + 3) // 4
            return self.emitterClass(dst, 70, indent)

        for line in src:

            if m := self.OBJECT_TEMPLATE.match(line):
                templateName = m[2]
                self.templateClass = globals()[templateName]
                self.template = self.templateClass()
                with closing(emitter(m)) as e:
                    self.template.emit_object_template(e, src)

            elif m := self.SPECIAL_METHODS.match(line):
                with closing(emitter(m)) as e:
                    self.template.special_methods(e)

            elif m := self.SPECIAL_BINOPS.match(line):
                with closing(emitter(m)) as e:
                    self.template.special_binops(e)

            elif m := self.MANGLED.match(line):
                print("Mangled template directive?",
                        m[2], file=sys.stderr)
                dst.write(line)

            else:
                dst.write(line)


@dataclass
class TypeInfo:
    # Java class name of an implementation ("PyFloat", "Integer", etc.)
    name: str
    # Expression with one {} that converts to primitive double
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


class ImplementationTemplate:

    # Adjust the indent to match that requested
    def set_indent(self, i):
        self.emitter.indent = i

    # Create a warning comment
    def emit_object_template(self, e, src):
        name = getattr(src, 'name', '?').replace('\\', '/')
        e.emit_line("/*")
        e.emit_line(" * Generated by java_object_gen using ")
        e.emit(f"template {self.__class__.__name__}.")
        e.emit_line(f" * Source: {name}")
        e.emit_line(" */")

    # Emit methods selectable by a single type
    def special_methods(self, e):
        pass

    # Emit methods selectable by a pair of types (for call sites)
    def special_binops(self, e):
        pass

    def emit_object_plumbing(self, e):
        pass


class PyLongTemplate(ImplementationTemplate):
    pass


class PyFloatTemplate(ImplementationTemplate):

    # The canonical + adopted implementations in PyFloat.java,
    # as there are no further accepted self-classes.
    ACCEPTED_CLASSES = [
        TypeInfo('PyFloat', '{}.value'),
        TypeInfo('Double', '{}.doubleValue()'),
    ]
    OPERAND_CLASSES = ACCEPTED_CLASSES + [
        #TypeInfo('Integer', '{}.doubleValue()'),
        #TypeInfo('BigInteger', 'PyLong.convertToDouble({})'),
        #TypeInfo('PyLong', 'PyLong.convertToDouble({}.value)'),
        #TypeInfo('Boolean', '({}.booleanValue() ? 1.0 : 0.0)'),
    ]

    # Operations may simply be codified as a return expression, since
    # all operand types may be converted to primitive double.
    UNARY_OPS = [
        OpInfo('__abs__', 'Math.abs({})'),
        OpInfo('__neg__', '-{}'),
        # In due course, just make it a String
        OpInfo('__repr__', 'Py.str(Double.toString({}))'),
    ]
    PREDICATE_OPS = [
        OpInfo('__bool__', '{} != 0.0'),
    ]
    BINARY_OPS = [
        OpInfo('__add__', '{} + {}', 'v', 'w'),
        OpInfo('__radd__', '{1} + {0}', 'w', 'v'),
        OpInfo('__sub__', '{} - {}', 'v', 'w'),
        OpInfo('__rsub__', '{1} - {0}', 'w', 'v'),
    ]
    BINARY_PREDICATE_OPS = [
        OpInfo('__lt__', '{} < {}'),
        OpInfo('__eq__', '{} == {}'),
    ]

    # Emit methods selectable by a single type
    def special_methods(self, e):

        # Emit the unary operations
        for op in self.UNARY_OPS:
            for t in self.ACCEPTED_CLASSES:
                self.special_unary(e, op, t)

        # Emit the unary operations
        for op in self.PREDICATE_OPS:
            for t in self.ACCEPTED_CLASSES:
                self.special_predicate(e, op, t)

        # Emit the binary operations op(T, Object)
        for op in self.BINARY_OPS:
            for vt in self.ACCEPTED_CLASSES:
                self.special_binary_object(e, op, vt)

        # Emit the binary predicate operations op(T, Object)
        for op in self.BINARY_PREDICATE_OPS:
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


def get_parser():
    parser = argparse.ArgumentParser(
            prog='java_object_gen',
            description='Generate Python object implementations.'
        )

    parser.add_argument('source_dir',
            help='Template directory (to process)')
    parser.add_argument('dest_dir',
            help='Destination directory (in build tree)')
    parser.add_argument('--verbose', '-v', action='store_true',
            help='Show more information')
    return parser


def main():
    # Parse the command line to argparse arguments
    args = get_parser().parse_args()

    # Embed results of parse into factory
    factory = ImplementationTemplateProcessorFactory(
            args.source_dir, args.dest_dir, args.verbose)

    # Process files
    src_dir = args.source_dir
    for dirpath, dirnames, filenames in os.walk(src_dir):
        # Any .java files here?
        javanames = [n for n in filenames
                        if os.path.splitext(n)[1].lower() == '.java']
        if javanames:
            package = os.path.relpath(dirpath, src_dir)
            for name in javanames:
                proc = factory.get_processor(package, name)
                proc.process()


if __name__ == '__main__':
    main()

