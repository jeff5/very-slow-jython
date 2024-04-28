#! /usr/bin/env python
"""
Generate Java code from an ASDL description.

The code is based on asdl_c.py, although specific to Java, of course.
"""
import sys
from typing import Optional, Callable

import asdl

from argparse import ArgumentParser
from pathlib import Path
from codegen.javagen import (
    JavaClass,
    JavaPseudoEnum,
    JavaVariable,
    JavaVisitor,
    JavaVisitorInterface,
    default_value_for_j_type,
    to_java_name,
)
from codegen.java_type_converter import (
    add_type,
    set_cache_file_path,
)


CORE_PACKAGE = 'org.python.objects'  # --core-package option default
AST_PACKAGE = 'org.python.ast'  # --ast-package option supersedes
BASE_TYPE = 'AST'


class AsdlJavaContext:
    """Context for Java code generation (in asdl_java)

    An instance holds root path for the generated Java files,
    plus the package names needed to read or create those files.
    """
    def __init__(self, dest_path: Path,
                 core_package: str, ast_package: str, base_type: str,
                 type_map: Path,
                 generator: str):
        """Create context for Java code generation

        Args:
            dest_path (Path): Path for the generated code directory
                Java files are placed at dest_path / java / package
            core_package (string): package name of Jython core classes
            ast_package (string): package name of AST classes
            base_type (string): name of base class of AST classes
            type_map (Path): path to C-Java type map to write/update
            generator (string): tool being used to create files.
                Usually __file__ will do.
        """
        self.dest_path = dest_path
        self.core_package = core_package
        self.ast_package = ast_package
        self.base_type = base_type
        self.generator = AsdlJavaContext._generator_name(generator)
        self.type_map = type_map
        self._ast_path = None  # meaning not computed/created
        # NamingVisitor fills and StructVisitor uses:
        self.asdl_java_type: dict[str, str]

    @staticmethod
    def _generator_name(generator: Optional[str]) -> Optional[str]:
        if generator:
            try:
                path = Path(generator).resolve()
                base = path.parent
                if path.stem == '__main__':
                    # tool is __main__ of package: use package
                    path = path.parent
                    base = path.parent
                while (base / '__init__.py').exists():
                    base = base.parent
                path = path.relative_to(base)
                return '/'.join(path.with_suffix('').parts)
            except FileNotFoundError:
                return generator
        else:
            return None

    def get_package_path(self) -> Path:
        """Get path for AST generated source directory (and create)"""
        if not self._ast_path:
            self._ast_path = self.dest_path.joinpath(
                *self.ast_package.split('.'))
            self._ast_path.mkdir(parents=True, exist_ok=True)
        return self._ast_path


def is_simple(sum_type):
    """Return True if a sum is simple.

    A sum is simple if its types have no fields and the sum itself has no attributes. Instances of these types are
    cached at C level, and they act like singletons when propagating parser generated nodes into Python level, e.g.
    unaryop = Invert | Not | UAdd | USub
    """

    return not (
        sum_type.attributes or
        any(constructor.fields for constructor in sum_type.types)
    )


class StructVisitor(asdl.VisitorBase):
    """
    Visitor to generate typedefs for AST.
    """

    def __init__(self, context: AsdlJavaContext):
        super().__init__()
        self.context = context
        self.package = context.ast_package
        self.cls_stack = [context.base_type]
        self.default_visitor = JavaVisitor("ASTDefaultVisitor", base_cls=None,
                                           package=self.package, modifiers=['public'])
        self.visitor_stack = [(self.cls_stack[0], self.default_visitor)]
        self.java_classes = [self.default_visitor]

    def write_java_classes(self):
        """
        Writes all generated Java-classes to disk.
        """
        ctx = self.context
        path = ctx.get_package_path()
        generator = ctx.generator
        for jc in self.java_classes:
            jc.save_to_file(path, generator)

    def begin_class(self, name: str,
                    cls_type: Callable[..., JavaClass] = JavaClass,
                    create_visitor: bool = False):
        base_cls = self.cls_stack[-1]
        cls = cls_type(name, base_cls=base_cls, package=self.package, modifiers=['public'])
        self.cls_stack.append(cls)
        self.java_classes.append(cls)
        if cls.visitable:
            for (_, visitor) in self.visitor_stack:
                visitor.add_visit_method(cls)
        if create_visitor:
            iface = JavaVisitorInterface(name + 'Visitor',
                                         package=self.package)
            self.visitor_stack.append((cls, iface))
            self.java_classes.append(iface)
            # The default visitor implements this interface too
            self.default_visitor.add_interface(iface)
        return cls

    def end_class(self):
        cls = self.cls_stack.pop()
        if self.visitor_stack and self.visitor_stack[-1][0] == cls:
            self.visitor_stack.pop()

    def get_java_type(self, asdl_type_name: str, is_seq: bool = False) -> str:
        """Map an ASDL type to its Java type"""
        if is_seq:
            return self.get_java_type(asdl_type_name) + '[]'  # or PyList ?
        return self.context.asdl_java_type[asdl_type_name]

    @property
    def current_class(self):
        return self.cls_stack[-1]

    def visitModule(self, mod):
        for dfn in mod.dfns:
            self.visit(dfn)

    def visitType(self, type):
        self.visit(type.value, type.name)

    def visitSum(self, sum, asdl_name):
        if is_simple(sum):
            self.sum_as_enum(sum, asdl_name)
        else:
            self.sum_with_constructors(sum, asdl_name)

    def sum_as_enum(self, sum, asdl_name):
        java_name = self.get_java_type(asdl_name)
        cls = self.begin_class(java_name, JavaPseudoEnum)
        for field in sum.types:
            cls.add(field.name)
        self.end_class()

    def sum_with_constructors(self, sum, asdl_name):
        java_name = self.get_java_type(asdl_name)
        cls = self.begin_class(java_name, JavaClass, True)
        for field in sum.attributes:
            type = self.get_java_type(field.type)
            init = default_value_for_j_type(type)
            cls.add(JavaVariable(field.name, type, init))
        for t in sum.types:
            self.visit(t)
        self.end_class()

    def visitConstructor(self, cons):
        cls = self.begin_class(self.get_java_type(cons.name))
        for field in cons.fields:
            self.visit(field)
        self.end_class()

    def visitField(self, field):
        java_type = self.get_java_type(field.type, field.seq)
        self.current_class.add(
            JavaVariable(field.name, java_type, is_optional=field.opt))

    def visitProduct(self, product, asdl_name):
        cls = self.begin_class(self.get_java_type(asdl_name))
        for field in product.fields:
            self.visit(field)
        for field in product.attributes:
            # rudimentary attribute handling
            type = self.get_java_type(field.type)
            assert type in asdl.builtin_types, type
            cls.add(JavaVariable(field.name, type))
        self.end_class()


class NamingVisitor(asdl.VisitorBase):
    """
    Visitor to survey the names used in AST elements in C and Java.

    We survey only the types defined (and constructor names), to
    ensure we encounter them before the fields or attributes that
    may use these types.
    """

    # ASDL's 4 builtin types are: identifier, int, string, and constant
    _ASDL_JAVA_TYPE = dict(
        identifier='String',
        int='int',
        string='String',
        constant='Object',
    )

    def __init__(self, context: AsdlJavaContext):
        super().__init__()
        self.context = context
        context.asdl_java_type = dict(NamingVisitor._ASDL_JAVA_TYPE)
        self.asdl_java_type = context.asdl_java_type
        self.c_java_type: dict[str, str] = dict()

    def add_asdl(self, asdl_name: str, java_name: str):
        self.asdl_java_type[asdl_name] = java_name

    def add_c(self, c_name: str, java_name: str):
        self.c_java_type[c_name] = java_name

    def visitModule(self, mod: asdl.Module):
        for dfn in mod.dfns:
            self.visit(dfn)

    def visitType(self, t: asdl.Type):
        self.visit(t.value, t.name)

    def visitSum(self, sum: asdl.Sum, name: str):
        if is_enum := is_simple(sum):
            java_name = to_java_name(name, prefix='_', start_with_upper=True)
        else:
            java_name = to_java_name(name, prefix='AST', start_with_upper=True)
        self.add_asdl(name, java_name)
        # CPython has a corresponding type name_ty
        self.add_c(f'{name}_ty', java_name)
        for cons in sum.types:
            self.visit(cons, is_enum, java_name)

    def visitConstructor(self, cons: asdl.Constructor, is_enum: bool, qualifier: str):
        name = cons.name
        java_name = to_java_name(name)
        self.add_asdl(name, java_name)
        if is_enum:
            # CPython has an enum constant called name, that in Java
            # is scoped within the pseudo-enum class.
            self.add_c(name, f'{qualifier}.{java_name}')
        else:
            # CPython has a factory _PyAST_name returning name_ty,
            # that in Java becomes a constructor call.
            self.add_c(f'_PyAST_{name}', f'new {java_name}')

    def visitProduct(self, product: asdl.Product, name: str):
        java_name = to_java_name(name, start_with_upper=True)
        self.add_asdl(name, java_name)
        # CPython has a corresponding type name_ty
        self.add_c(f'{name}_ty', java_name)
        # CPython has a factory returning name_ty called:
        self.add_c(f'_PyAST_{name}', f'new {java_name}')


def write_source(context: AsdlJavaContext, mod):
    v = StructVisitor(context)
    v.visit(mod)
    v.write_java_classes()
    print("Output written to", context.get_package_path())


def main(context: AsdlJavaContext, input_file: str, dump_module=False):
    set_cache_file_path(context.type_map)

    # Build an AST of the ASDL that describes the AST of the language.
    mod = asdl.parse(input_file)
    if dump_module:
        print('Parsed Module:')
        print(mod)
    if not asdl.check(mod):
        sys.exit(1)

    # Gather the names of data types and their translations to Java.
    # We do this before we encounter them as field types.
    names = NamingVisitor(context)
    names.visit(mod)

    # Pre-load java_type_converter global dictionary with C decisions.
    for key, value in names.c_java_type.items():
        add_type(key, value)

    # Walk the tree generating Java classes for the types it defines.
    # These are the AST classes we use in the parser.
    write_source(context, mod)


if __name__ == "__main__":
    prog_dir = Path(__file__).parent
    parser = ArgumentParser()
    parser.add_argument("input_file", type=Path, nargs='?',
                        default=Path(__file__).with_name('Python.asdl'),
                        help="ASDL defining the nodes (default: built-in Python.asdl)")
    parser.add_argument("output_dir", type=Path, nargs='?',
                        default=Path.cwd().joinpath('generated'),
                        help="output directory for generated code (default: ./generated)")
    parser.add_argument("-a", "--ast-package", default=AST_PACKAGE,
                        help=f"AST Java package name (default: {AST_PACKAGE})")
    parser.add_argument("-b", "--base-type",
                        default=BASE_TYPE,
                        help=f"Base class name in AST package (default: {BASE_TYPE})")
    parser.add_argument("-c", "--core-package",
                        default=CORE_PACKAGE,
                        help=f"Core Jython package name (default: {CORE_PACKAGE})")
    parser.add_argument("-t", "--type-map", type=Path,
                        default=None,
                        help=f"C-Java type map to write (default: output_dir/.c_java_type_cache)")
    parser.add_argument("-d", "--dump-module", action="store_true")

    args = parser.parse_args()

    if args.type_map is None:
        args.type_map = args.output_dir / '.c_java_type_cache'

    main(AsdlJavaContext(
            args.output_dir,
            args.core_package,
            args.ast_package,
            args.base_type,
            args.type_map,
            __file__
        ),
        args.input_file,
        args.dump_module)
