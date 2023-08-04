from datetime import datetime
from typing import List, TextIO, Optional
from pathlib import Path

from cparser import ParserContext
import cparser.ast as c_ast


INITIAL_TYPE_MAP = {
    'Py_Ellipsis': 'PyPegen.Ellipsis',
    'Py_False': 'PyPegen.False',
    'Py_None': 'PyPegen.None',
    'Py_True': 'PyPegen.True',
    'asdl': 'AST',
    'Py_ssize_t': 'int',
    'int': 'int',
    'int_ty': 'int',
    'asdl_arg': 'Arg',
    'Augoperator': 'AugOperator',
    'const char*': 'String',
    'PyExc_SyntaxError': 'PyExc.SyntaxError',
    'PyExc_IndentationError': 'PyExc.IndentationError',
    'identifier_ty': 'String',
}

INITIAL_METHODS = {}

INITIAL_ATTR_MAP = {
    'lineno': 'getLineno()',
    'col_offset': 'getColOffset()',
    'end_lineno': 'getEndLineno()',
    'end_col_offset': 'getEndColOffset()',
    'kind': 'getKind()',
    'feature_version': 'getFeatureVersion()',
    'id': 'getId()',
    'args': 'getArgs()',
    'keywords': 'getKeywords()',
    'key': 'getKey()',
    'value': 'getValue()',
}

xmacros = {
    'seq_LEN': (1, '#1.length'),
    'seq_GET': (2, '#1[#2]'),
}


class JavaGeneratorContext:
    """Context for Java code generation

    An instance holds root paths for the source tree (to locate C
    source files) and generated Java files (new and previous),
    plus the package names needed to read or create those files.
    """
    def __init__(self, cfiles: Path, dest_path: Path,
                 core_package: str, ast_package: str,
                 parser_package: str, parser_name: str,
                 type_map: Path,
                 generator: str):
        """Create context for Java code generation

        Args:
            cfiles (Path): Path for C source code directory
                We translate objects from these files to Java.
            dest_path (Path): Path for the generated code directory
                Java files are placed at dest_path / package.
            core_package (string): package name of Jython core classes
            ast_package (string): package name of AST classes
            parser_package (string): package name of parser classes
            parser_name (string): class name of the parser
            type_map (Path): path to C-Java type map to read
            generator (Path): tool being used to create files.
                Usually __file__ will do
        """
        self.cfiles = cfiles
        self.dest_path = dest_path
        self.core_package = core_package
        self.ast_package = ast_package
        self.parser_package = parser_package
        self.parser_name = parser_name
        self.type_map = type_map
        self._ast_path = None  # meaning not computed/created
        self._time = datetime.today()
        self._generator = generator
        tool = self._generator_name()
        self.java_autogen_comment = \
            "// Generated automatically{} at {}.\n".format(
                (" by " + tool if tool else ""),
                self._time.strftime('%d %b %Y %H:%M'))

    def _generator_name(self) -> Optional[str]:
        generator = self._generator
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

    def open_w_ast_source(self, name: str) -> TextIO:
        """Open Java AST source for write with the given class name"""
        if not self._ast_path:
            self._ast_path = self.dest_path.joinpath(
                *self.ast_package.split('.'))
            self._ast_path.mkdir(parents=True, exist_ok=True)
        filepath = self._ast_path.joinpath(name).with_suffix('.java')
        return open(filepath, 'w')


class ActionVisitor(c_ast.CodeGenVisitor):

    def __init__(self, translator: "ActionTranslator"):
        super().__init__()
        self.names = set()
        self.translator = translator

    def translate_type(self, type: c_ast.CType):
        return self.translator.translate_type(type)

    def visit_Attribute(self, node: c_ast.Attribute):
        # Special case: (X).v.Call.Y / (X).v.Name.Y are used as special type casts
        if (
            '_PyAST_' + node.name in self.translator.type_map and
            isinstance(node.value, c_ast.Attribute) and
            node.value.name == 'v'
        ):
            base = node.value.value
            if isinstance(base, c_ast.TypeCast):
                base = base.value
            return f"(({node.name}) {self.visit(base)})"
        value = self.visit(node.value)
        name = self.translator.attr_map.get(node.name, node.name)
        return f"{value}.{name}"

    def visit_Call(self, node: c_ast.Call):
        func = self.visit(node.func)
        if func.startswith('#.'):
            func = self.visit(node.args[0]) + func[1:]
            args = self.visit(node.args[1:])
        elif func in self.translator.methods:
            if func == 'PyPegen.newTypeComment' and\
                    len(self.translator.methods[func]) == 1:
                return self.visit(node.args[1])
            params = self.translator.methods[func]
            if len(params) > 0 and params[-1] == 'PyArena':
                args = self.visit(node.args[1:-1])
            else:
                args = self.visit(node.args[1:])
        else:
            args = self.visit(node.args)
        if len(args) > 0 and args[-1].endswith('.arena'):
            args = args[:-1]
        (n_args, text) = xmacros.get(func, (-1, None))
        if n_args == len(args) and isinstance(text, str):
            for i, arg in enumerate(args):
                text = text.replace(f"#{i+1}", arg)
            return text
        return f"{func}({', '.join(args)})"

    def visit_Const(self, node: c_ast.Const):
        if node.value == 'NULL':
            return 'null'
        return str(node.value)

    def visit_IfExpr(self, node: c_ast.IfExpr):
        test = self.visit(node.test)
        body = self.visit(node.body)
        orelse = self.visit(node.orelse)
        if isinstance(node.test, c_ast.Name):
            test = f"({test} != null)"
        return f"({test} ? {body} : {orelse})"

    def visit_Name(self, node: c_ast.Name):
        name = node.value
        if name.startswith('asdl_'):
            name = name[5:]
        if name in self.translator.type_map:
            name = self.translator.type_map[name]
            idx = name.find('(')
            if idx >= 0:
                name = name[:idx]
        if '.' not in name:
            self.names.add(name)
        return name

    def visit_TypeCast(self, node: c_ast.TypeCast):
        # Special case: we remove all `CHECK_CALL`s together with their type casts
        if (
            isinstance(node.value, c_ast.Call) and
            isinstance(node.value.func, c_ast.Name) and
            node.value.func.value in ('CHECK_CALL', 'CHECK_CALL_NULL_ALLOWED')
        ):
            return self.visit(node.value.args[1])
        type = self.translate_type(node.type)
        return f"({type}){self.visit(node.value)}"


class JavaEnumWriter:

    def __init__(self, raw_name: str, name: str,
                 context: JavaGeneratorContext):
        self.raw_name = raw_name
        self.name = '_' + name
        self.context = context
        self._fields = []

    def add_field(self, name: str):
        self._fields.append(name)

    def generate_body(self) -> str:
        code = [f"  public static final {self.name} {field};" for field in self._fields]
        code.append("\n  static {")
        code.extend(f"    {field} = new {self.name}();" for field in self._fields)
        code.append("  }")
        return '\n'.join(code)

    def write_to_file(self):
        with self.context.open_w_ast_source(self.name) as f:
            f.write(self.context.java_autogen_comment)
            f.write(f"package {self.context.ast_package};\n\n")
            f.write(f"public final class {self.name} extends AST {{\n")
            f.write(self.generate_body())
            f.write("\n}\n")


def create_enum_type(name: str, fields: List[str],
                     translator: "ActionTranslator"):
    ew = JavaEnumWriter(name, name, translator.generator_context)
    for field in fields:
        ew.add_field(field)
        translator.type_map[field] = f"_{name}.{field}"
    ew.write_to_file()


def create_struct_pair(name: str, fields: List[c_ast.VarDecl],
                       translator: "ActionTranslator"):

    def get_type(type):
        type = str(type)
        is_seq = type.endswith("_seq*")
        if is_seq:
            type = type[:-5]
        t = translator.type_map.get(type, None)
        if t is not None:
            type = t
        elif type.endswith('_ty'):
            type = type[:-3].title().replace('_', '')
        if is_seq:
            type += '[]'
        return type

    fields = [(get_type(f.type), f.names[0][0]) for f in fields]
    is_pair = len(fields) == 2 and name.endswith('Pair')
    context = translator.generator_context
    with context.open_w_ast_source(name) as f:
        f.write(context.java_autogen_comment)
        f.write(f"package {context.ast_package};\n\n")
        if is_pair:
            t = ', '.join(tp for (tp, _) in fields)
            t = f" extends ASTPair<{t}>"
        else:
            t = " extends AST"
        f.write(f"public class {name}{t} {{\n")

        for (f_type, f_name) in fields:
            f.write(f"  private final {f_type} {f_name};\n")

        # Write constructor
        a = ', '.join(f"{f_type} {f_name}" for (f_type, f_name) in fields)
        c = '\n    '.join(f"this.{n} = {n};" for (_, n) in fields)
        f.write(f"\n  public {name} ({a}) {{\n    {c}\n  }}\n")

        for (f_type, f_name) in fields:
            f.write(f"\n  public {f_type} get{f_name.title().replace('_', '')}() {{\n    return this.{f_name};\n  }}\n")

        if is_pair:
            for (f_type, f_name), n in zip(fields, ("First", "Second")):
                f.write(f"\n  @Override\n  public {f_type} get{n}() {{\n    return this.{f_name};\n  }}\n")

        f.write("}\n")


class ActionTranslator:
    """State for a series of action translations

    An instance holds reference information derived by reading the C
    source of the .h and .c files that support compilation in CPython,
    and their mapping to Java, plus the paths and package names needed
    to read or create files.
    """
    def __init__(self, generator_context: JavaGeneratorContext):
        self.generator_context = generator_context
        self.type_map = dict(INITIAL_TYPE_MAP)
        self.attr_map = dict(INITIAL_ATTR_MAP)
        self.methods = dict(INITIAL_METHODS)
        self.parser_context = ParserContext()

        mapfile = self.generator_context.type_map
        with open(mapfile) as f:
            for line in f:
                if line and not line.startswith('//'):
                    c_name, java_expr = line.strip().split('->')
                    self.type_map[c_name] = java_expr

        cfiles = self.generator_context.cfiles
        parser = self.parser_context
        parser.define_macro('Py_LOCAL_INLINE(t)', 't')
        parser.add_types('PyTypeObject', 'PyObject', 'Token')
        a = parser.parse_file(cfiles / 'pegen.h')
        parser.parse_file(cfiles / 'pegen.c')
        ah = parser.parse_file(cfiles / 'action_helpers.c')

        # We need to extract and generate code for `TARGETS_TYPE`
        for item in a:
            if isinstance(item, c_ast.TypeDecl):
                if isinstance(item.type, c_ast.EnumType):
                    create_enum_type(item.name, item.type.names, self)
                elif isinstance(item.type, c_ast.StructType) and item.name.endswith('Pair'):
                    create_struct_pair(item.name, item.type.fields, self)
                elif isinstance(item.type, c_ast.StructType) and item.name in ('SlashWithDefault', 'StarEtc', 'AugOperator'):
                    create_struct_pair(item.name, item.type.fields, self)

            elif isinstance(item, c_ast.FunctionDecl):
                if item.name.startswith('_PyPegen_') or item.name.startswith('_Pypegen_'):
                    name = item.name[9:].title().replace('_', '')
                    name = 'PyPegen.' + name[0].lower() + name[1:]
                    self.type_map[item.name] = name + '()'
                elif item.name.isupper():
                    name = item.name.title().replace('_', '')
                    name = 'PyPegen.' + name[0].lower() + name[1:]
                    self.type_map[item.name] = name + '()'
                else:
                    name = item.name
                if len(item.params) > 0 and str(item.params[0].type) == 'Parser*':
                    self.methods[name] = [self.translate_type(p.type) for p in item.params[1:]]
                    #a = ', '.join(f"{self.translate_type(p.type)} {p.name if p.name is not None else '???'}" for p in item.params[1:])
                    #n = name[name.index('.')+1:] if '.' in name else name
                    #print(f"  public static {self.translate_type(item.type)} {n}({a}) {{}}")

        # We filter out all the constructor patterns from 'action_helpers.c' and replace them by actual constructors
        # Sure, we mainly do this just for fun and because we can ;-)
        for item in ah:
            stmts, retStmt = None, None
            if (
                isinstance(item, c_ast.FunctionDecl) and
                isinstance(item.body, c_ast.Body) and
                len(stmts := item.body.stmts) >= 3 and
                isinstance(stmts[0], c_ast.VarDecl) and
                isinstance(stmts[1], c_ast.If) and
                isinstance(stmts[-1], c_ast.Return) and
                item.type == stmts[0].type and
                isinstance(stmts[0].init_value, c_ast.Call) and
                stmts[0].init_value.func_name == '_PyArena_Malloc' and
                isinstance(stmts[1].test, c_ast.UnaryOp) and
                stmts[1].test.op == '!' and
                isinstance(stmts[1].test.value, c_ast.Name) and
                stmts[1].test.value.value == (var_name := stmts[0].name) and
                (
                        (isinstance(stmts[1].body, c_ast.Body) and
                         len(stmts[1].body.stmts) == 1 and
                         isinstance(retStmt:=stmts[1].body.stmts[0], c_ast.Return)
                        )
                        or
                        isinstance(retStmt:=stmts[1].body, c_ast.Return)
                ) and
                isinstance(retStmt.expr, c_ast.Const) and
                retStmt.expr.value == 'NULL' and
                isinstance(stmts[-1].expr, c_ast.Name) and
                stmts[-1].expr.value == var_name and
                all(
                    isinstance(s, c_ast.Expression) and
                    isinstance(s.expr, c_ast.Assignment) and
                    isinstance(s.expr.target, c_ast.Attribute) and
                    isinstance(s.expr.source, c_ast.Name) and
                    isinstance(s.expr.target.value, c_ast.Name) and
                    s.expr.target.value.value == var_name
                    for s in stmts[2:-1]
                )
            ):
                # Unfortunately, there is a special case that we cannot express as a single constructor in Java
                if str(item.type) == 'KeywordOrStarred*':
                    self.type_map[item.name] = 'PyPegen.keywordOrStarred()'
                    self.methods['PyPegen.keywordOrStarred'] = [self.translate_type(p.type) for p in item.params[1:]]
                else:
                    name = f"new {self.translate_type(item.type)}"
                    self.type_map[item.name] = name + "()"
                    if str(item.params[0].type) == 'Parser*':
                        self.methods[name] = [self.translate_type(p.type) for p in item.params[1:]]

        #parser.parse_file(base_path.joinpath('Include', 'internal', 'pycore_asdl.h'))
        #parser.parse_file(base_path.joinpath('Parser', 'pegen.h'))
        #parser.parse_file(base_path.joinpath('Parser', 'pegen.c'))
        #parser.parse_file(base_path.joinpath('Parser', 'action_helpers.c'))

        parser.define_macro('INVALID_VERSION_CHECK(p, version, msg, node)',
                                  '((this.feature_version >= version) ? node : '
                                  'RAISE_SYNTAX_ERROR("%s only supported in Python 3.%i and greater", msg, version))')

        # Add all the available types to the parser's type list
        for key in self.type_map:
            if '.' not in key and key.endswith('_ty'):
                parser.add_types(key)

        #for key in self.type_map:
        #    print(f"{key:>40} => {self.type_map[key]}")

    def translate_action(self, action: str) -> (str, set):
        #print("TRANSLATING:", action)
        ast = self.parser_context.parse_expr(action)
        av = ActionVisitor(self)
        #print(">>>", av.visit(ast))
        result = av.visit(ast)
        return (result, av.names)


    def translate_type(self, type: str|None|c_ast.CType) -> str:
        if type is None:
            return 'Object'
        if not isinstance(type, str):
            type = str(type)
        tp = self.type_map.get(type, None)
        if tp is not None:
            return tp

        is_seq = 0
        if type.endswith('[]'):
            is_seq += 1
            type = type[:-2]
        if type.endswith('_seq*'):
            type = type[:-5]
            is_seq += 1
        elif type == 'void*':
            return 'Object'
        elif type.endswith('*'):
            type = type[:-1]
        if type.startswith('asdl_'):
            type = type[5:] + '_ty'
        if type in self.type_map:
            tp = self.type_map[type]
        elif type[0].isupper() and '_' not in type:
            tp = type
        elif type in ('void', 'int'):
            tp = type
        elif type == 'const char':
            tp = "String"
        else:
            tp = type.title().replace('_', '')
        tp += '[]' * is_seq
        return tp


if __name__ == '__main__':

    # __file__ = src/python/pegen/action_translator.py
    action_translator = ActionTranslator(
        JavaGeneratorContext(
            # Paths reflect Pegen2 project layout
            Path(__file__).parents[2] / 'c',
            Path(__file__).parents[3] / 'generated',
            'org.python.object',
            'org.python.ast',
            'org.python.parser',
            'GeneratedParser',
            __file__
        )
    )

    def _translate_action(text: str):
        try:
            print(f"\nTRANSLATE: '{text}'")
            result, names = action_translator.translate_action(text)
            print(f"      -->: '{result}'")
            print(f"    names: '{names}'")
        except SyntaxError as se:
            print(se)

    # Display some examples of action translation
    _translate_action('Py_True')
    _translate_action('CHECK_VERSION ( stmt_ty , 6 , "Variable annotation syntax is" , _PyAST_AnnAssign ( CHECK ( expr_ty , _PyPegen_set_expr_context ( p , a , Store ) ) , b , c , 1 , EXTRA ) )')
    _translate_action('_PyAST_AsyncFunctionDef ( n -> v . Name . id , ( params ) ? params : CHECK ( arguments_ty , _PyPegen_empty_arguments ( p ) ) , b , NULL , a , NEW_TYPE_COMMENT ( p , tc ) , EXTRA )')
    # Fails if we use generated .c_java_type_cache as intended instead of legacy asdl_types file:
    _translate_action('CHECK_VERSION ( stmt_ty , 5 , "Async functions are" , _PyAST_AsyncFunctionDef ( n -> v . Name . id , ( params ) ? params : CHECK ( arguments_ty , _PyPegen_empty_arguments ( p ) ) , b , NULL , a , NEW_TYPE_COMMENT ( p , tc ) , EXTRA ) )')
