"""
An AST for simple C programs and header files.

Please note that we do not support the entire C language, but only a small part as is needed to parse the Python header
files and grammar actions.
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import Any, List


def _dataclass(cls):
    cls = dataclass(cls)
    cls._fields = tuple(n for n in cls.__annotations__)
    return cls

##############################################

class C_AST:
    _fields = ()

class Expr(C_AST):
    pass

class Stmt(C_AST):
    pass

class Decl(Stmt):
    pass

##############################################

class CType(C_AST):
    pass

@_dataclass
class ArrayType(CType):
    type: CType
    dimension: str

    def __str__(self):
        return f"{self.type}[{self.dimension}]"

@_dataclass
class BaseType(CType):
    name: str

    def __str__(self):
        return self.name

@_dataclass
class ConstType(CType):
    type: CType

    def __str__(self):
        return 'const ' + str(self.type)

@_dataclass
class EnumType(CType):
    names: List[str]

@_dataclass
class FuncType(CType):
    return_type: CType
    params: List[CType]

@_dataclass
class PointerType(CType):
    base: CType

    def __str__(self):
        return str(self.base) + '*'

@_dataclass
class StructType(CType):
    name: str
    fields: list

############################################################################################

@_dataclass
class AddressOf(Expr):
    value: Expr

@_dataclass
class Assignment(Expr):
    target: Expr
    source: Expr

@_dataclass
class Attribute(Expr):
    value: Expr
    name: str

@_dataclass
class AugAssignment(Expr):
    target: Expr
    op: str
    source: Expr

@_dataclass
class BinOp(Expr):
    left: Expr
    op: str
    right: Expr

@_dataclass
class Body(Stmt):
    stmts: List[Stmt]

    def __iter__(self):
        return iter(self.stmts)

class Break(Stmt):
    pass

@_dataclass
class Call(Expr):
    func: Expr
    args: List[Expr]

    @property
    def func_name(self) -> str|None:
        if isinstance(self.func, Name):
            return self.func.value
        else:
            return None

@_dataclass
class Case(Stmt):
    label: Expr|None
    stmts: List[Stmt]

@_dataclass
class Comparison(Expr):
    left: Expr
    op: str
    right: Expr

@_dataclass
class Const(Expr):
    value: Any

@_dataclass
class ConstArray(Expr):
    values: List

class Continue(Stmt):
    pass

@_dataclass
class Deref(Expr):
    value: Expr

@_dataclass
class Decorated(Decl):
    decor: str
    decl: Decl

@_dataclass
class DoWhile(Stmt):
    test: Expr
    body: Stmt

class EllipsisArg(Expr):

    @property
    def name(self):
        return '...'

    @property
    def type(self):
        return None

class EmptyStatement(Stmt):
    pass

@_dataclass
class Expression(Stmt):
    expr: Expr

@_dataclass
class For(Stmt):
    init: Stmt
    test: Expr
    incr: Stmt
    body: Stmt

@_dataclass
class FunctionDecl(Decl):
    type: CType
    name: str
    params: list
    body: Stmt|None

@_dataclass
class Goto(Stmt):
    target: str

@_dataclass
class If(Stmt):
    test: Expr
    body: Stmt
    orelse: Stmt

@_dataclass
class IfExpr(Expr):
    test: Expr
    body: Expr
    orelse: Expr

@_dataclass
class Label(Stmt):
    name: str

@_dataclass
class Name(Expr):
    value: str

@_dataclass
class Return(Stmt):
    expr: Expr|None

@_dataclass
class SizeOf(Expr):
    target: CType

@_dataclass
class Subscript(Expr):
    value: Expr
    index: Expr

@_dataclass
class Switch(Stmt):
    subject: Expr
    cases: List[Case]

@_dataclass
class TypeCast(Expr):
    type: CType
    value: Expr

@_dataclass
class TypeDecl(Decl):
    type: CType
    name: str

@_dataclass
class UnaryOp(Expr):
    op: str
    value: Expr

@_dataclass
class UnarySuffixOp(Expr):
    op: str
    value: Expr

class VarDecl(Decl):

    def __init__(self, type: CType, names: str|List[(str, Expr|None)]):
        self.type = type
        if isinstance(names, str):
            self.names = [(names, None)]
        else:
            self.names = names

    def __repr__(self):
        if len(self.names) == 1:
            n, i = self.names[0]
            name = str(n) if i is None else f"{n}:={i}"
            return f"VarDecl(type={self.type}, name={name})"
        else:
            names = [str(n) if i is None else f"{n}:={i}" for (n, i) in self.names]
            return f"VarDecl(type={self.type}, names={names})"

    @property
    def init_value(self):
        if self.names is not None and len(self.names) == 1:
            return self.names[0][1]
        else:
            return None

    @property
    def name(self):
        if self.names is not None and len(self.names) == 1:
            return self.names[0][0]
        else:
            return None

    _fields = ('type', 'names')

@_dataclass
class While(Stmt):
    test: Expr
    body: Stmt

############################################################################################

class Visitor:

    def visit(self, node):
        if isinstance(node, C_AST):
            method = getattr(self, 'visit_' + node.__class__.__name__, self.default_visit)
            return method(node)
        elif isinstance(node, list):
            return [self.visit(item) for item in node]
        elif isinstance(node, tuple):
            return tuple(self.visit(item) for item in node)

    def default_visit(self, node: C_AST):
        for field in getattr(node, '_fields', ()):
            self.visit(getattr(node, field))
        return node


class CodeGenVisitor(Visitor):

    def translate_type(self, type: CType):
        return str(type)

    def visit_Assignment(self, node: Assignment):
        target = self.visit(node.target)
        source = self.visit(node.source)
        return f"{target} = {source};"

    def visit_Attribute(self, node: Attribute):
        value = self.visit(node.value)
        return f"{value}.{node.name}"
    
    def visit_AugAssignment(self, node: AugAssignment):
        target = self.visit(node.target)
        source = self.visit(node.source)
        return f"{target} {node.op}= {source};"
    
    def visit_BinOp(self, node: BinOp):
        left = self.visit(node.left)
        right = self.visit(node.right)
        return f"({left} {node.op} {right})"
    
    def visit_Body(self, node: Body):
        code = [self.visit(stmt).replace('\n', '\n  ') for stmt in node.stmts]
        return '{\n  ' + '\n  '.join(code) + '}'
    
    def visit_Break(self, node: Break):
        return "break;"
    
    def visit_Call(self, node: Call):
        func = self.visit(node.func)
        args = self.visit(node.args)
        return f"{func}({', '.join(args)})"
    
    def visit_Comparison(self, node: Comparison):
        left = self.visit(node.left)
        right = self.visit(node.right)
        return f"({left} {node.op} {right})"
    
    def visit_Const(self, node: Const):
        return str(node.value)

    def visit_Continue(self, node: Continue):
        return "continue;"

    def visit_Expression(self, node: Expression):
        return f"{self.visit(node.expr)};"

    def visit_For(self, node: For):
        init = self.visit(node.init) if node.init is not None else ''
        test = self.visit(node.test) if node.test is not None else ''
        incr = self.visit(node.incr) if node.incr is not None else ''
        body = self.visit(node.body)
        if not body.startswith('{'):
            body = '\n  ' + body
        return f"for ({init}; {test}; {incr}){body}"

    def visit_If(self, node: If):
        test = self.visit(node.test)
        body = self.visit(node.body)
        if not body.startswith('{'):
            body = '\n  ' + body
        result = f"if ({test}) {body}"
        if node.orelse:
            orelse = self.visit(node.orelse)
            if orelse.startswith('{'):
                result += f"else {body}"
            else:
                result += f"else\n  {body}"
        return result

    def visit_IfExpr(self, node: IfExpr):
        test = self.visit(node.test)
        body = self.visit(node.body)
        orelse = self.visit(node.orelse)
        return f"({test} ? {body} : {orelse})"

    def visit_Name(self, node: Name):
        return node.value

    def visit_Return(self, node: Return):
        if node.expr:
            return f"return {self.visit(node.expr)};"
        else:
            return "return;"

    def visit_Subscript(self, node: Subscript):
        value = self.visit(node.value)
        index = self.visit(node.index)
        return f"{value}[{index}]"

    def visit_TypeCast(self, node: TypeCast):
        type = self.translate_type(node.type)
        return f"({type}){self.visit(node.value)}"

    def visit_UnaryOp(self, node: UnaryOp):
        value = self.visit(node.value)
        return f"{node.op}{value}"

    def visit_UnarySuffixOp(self, node: UnarySuffixOp):
        value = self.visit(node.value)
        return f"{value}{node.op}"

    def visit_VarDecl(self, node: VarDecl):
        names = []
        for (name, init) in node.names:
            if init:
                names.append(f"{name} = {self.visit(init)}")
            else:
                names.append(name)
        return f"{node.type} {', '.join(names)};"

    def visit_While(self, node: While):
        test = self.visit(node.test)
        body = self.visit(node.body)
        if not body.startswith('{'):
            body = '\n  ' + body
        return f"while ({test}) {body}"
