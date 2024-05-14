"""
A parser for simple C programs and header files.

Please note that we do not support the entire C language, but only a small part as is needed to parse the Python header
files and grammar actions.
"""
from __future__ import annotations
from .ast import *
from .tokenizer import TokenType, Tokenizer
from pathlib import Path


def is_first_of_expr(token_type):
    return token_type in (
        TokenType.IDENTIFIER,
        TokenType.LEFT_PAR,
        TokenType.MINUS,
        TokenType.NULL,
        TokenType.NUMBER,
        TokenType.PLUS,
        TokenType.STRING,
        TokenType.UNARY_OP,
    )


class Parser:
    def __init__(self, tokenizer: Tokenizer, parser: Parser|None = None):
        self.tokenizer = tokenizer
        self.types = { 'char', 'double', 'float', 'int', 'long', 'short', 'unsigned', 'void' }
        self.structs = set()
        self.tokens = list(tokenizer)
        self.index = 0
        if parser is not None:
            self.types.update(parser.types)
            self.structs.update(parser.structs)

    def add_type(self, name: str):
        self.types.add(name)

    def add_types(self, names: List[str]):
        for name in names:
            self.types.add(name)

    def match(self, token_type, value=None):
        i = self.index
        tokens = self.tokens
        if i < len(tokens) and tokens[i][0] is token_type and (value is None or tokens[i][2] == value):
            self.index = i + 1
            return True
        else:
            return False

    def next(self):
        i = self.index
        tokens = self.tokens
        if i < len(tokens):
            self.index = i + 1
            return tokens[i]
        else:
            return None

    def next_name(self):
        token_type, pos, name = self.next()
        if not token_type is TokenType.IDENTIFIER:
            raise SyntaxError(f"[{pos}] name expected, but '{token_type}' found")
        return name

    def has_next(self) -> bool:
        return self.index < len(self.tokens)

    def peek(self, index=0):
        i = self.index + index
        tokens = self.tokens
        if i < len(tokens):
            return tokens[i][0]
        else:
            return None

    def peek_seq(self, count):
        i = self.index
        tokens = self.tokens
        if i < len(tokens):
            return [ tkn[0] for tkn in tokens[i:i+count] ]
        else:
            return []

    def peek_value(self, index=0):
        i = self.index + index
        tokens = self.tokens
        if i < len(tokens):
            return tokens[i][2]
        else:
            return None

    @property
    def pos(self):
        idx = self.index
        if 0 <= idx < len(self.tokens):
            return self.tokens[idx][1]
        else:
            return -1

    def skip_statement(self):
        """
        This method skips everything until the next semicolon.  This is needed because we only support a small subset
        and need to recover when encountering an unknown statement.
        """
        level = 0
        while not (level == 0 and self.match(TokenType.SEMICOLON)):
            token_type, _, _ = self.next()
            if token_type in (TokenType.LEFT_PAR, TokenType.LEFT_BRACE, TokenType.LEFT_BRACKET):
                level += 1
            elif token_type in (TokenType.RIGHT_PAR, TokenType.RIGHT_BRACE, TokenType.RIGHT_BRACKET):
                level -= 1

    def parse(self):
        result = []
        while self.has_next():
            if self.peek() in (TokenType.DEFINE, TokenType.INCLUDE):
                self.next()
            else:
                decl = self.parse_decl()
                result.append(decl)
        return Body(result)

    def parse_decl(self):
        if self.match(TokenType.KEYWORD, 'typedef'):
            type = self.parse_type()
            name = self.next_name()
            self.types.add(name)
            self.match(TokenType.SEMICOLON)
            return TypeDecl(type, name)
        else:
            return self.parse_var_decl()

    def parse_var_decl(self):
        decor = 'static' if self.match(TokenType.KEYWORD, 'static') else ''
        decor += ' inline' if self.match(TokenType.IDENTIFIER, 'inline') else ''
        type = self.parse_type()
        name = self.next_name()
        if self.match(TokenType.LEFT_PAR):
            params = self.parse_params()
            if self.match(TokenType.SEMICOLON):
                body = None
            else:
                body = self.parse_stmt()
            result = FunctionDecl(type, name, params, body)
            return Decorated(decor, result) if decor else result
        if self.match(TokenType.EQUAL):
            init = self.parse_expr()
            names = [(name, init)]
        elif self.match(TokenType.LEFT_BRACKET):
            dim = []
            while not self.match(TokenType.RIGHT_BRACKET):
                dim.append(self.next()[2])
            type = ArrayType(type, ''.join(dim))
            if self.match(TokenType.EQUAL):
                init = self.parse_expr()
            else:
                init = None
            names = [(name, init)]
        else:
            names = [(name, None)]
        while self.match(TokenType.COMMA):
            name = self.next_name()
            if self.match(TokenType.EQUAL):
                init = self.parse_expr()
                names.append((name, init))
            else:
                names.append((name, None))
        if self.match(TokenType.SEMICOLON):
            result = VarDecl(type, names)
            return Decorated(decor, result) if decor else result
        else:
            raise SyntaxError(f"[{self.pos}] unexpected token: '{self.peek()}'")

    def parse_params(self) -> list:
        args = []
        while not self.match(TokenType.RIGHT_PAR):
            if self.match(TokenType.ELLIPSIS):
                args.append( EllipsisArg() )
            else:
                type = self.parse_type()
                if self.peek() is TokenType.IDENTIFIER:
                    name = self.next_name()
                else:
                    name = None
                args.append( VarDecl(type, name) )
            self.match(TokenType.COMMA)
        return args

    def check_pointer_star(self, type):
        while self.match(TokenType.STAR):
            type = PointerType(type)
        return type

    def parse_type(self) -> CType:
        if self.match(TokenType.KEYWORD, 'struct'):
            if self.peek() is TokenType.IDENTIFIER:
                name = self.next_name()
            else:
                name = None
            if self.match(TokenType.LEFT_BRACE):
                if name is not None:
                    if name in self.structs:
                        raise SyntaxError(f"[{self.pos}] redeclaration of struct '{name}'")
                    self.structs.add(name)
                fields = []
                while not self.match(TokenType.RIGHT_BRACE):
                    fields.append(self.parse_var_decl())
                return self.check_pointer_star( StructType(name, fields) )
            elif name is not None:
                return self.check_pointer_star( BaseType('struct ' + name) )
            else:
                raise SyntaxError(f"[{self.pos}] invalid struct")

        elif self.match(TokenType.KEYWORD, 'enum'):
            names = []
            self.match(TokenType.LEFT_BRACE)
            while not self.match(TokenType.RIGHT_BRACE):
                names.append( self.next_name() )
                self.match(TokenType.COMMA)
            return EnumType(names)

        elif self.match(TokenType.KEYWORD, 'const'):
            return ConstType(self.parse_type())

        elif self.match(TokenType.LEFT_PAR):
            result = self.parse_type()
            self.match(TokenType.RIGHT_PAR)
            return result

        else:
            name = self.next_name()
            if name in ('signed', 'unsigned') and self.peek() is TokenType.IDENTIFIER and self.peek_value() in self.types:
                name += ' ' + self.next_name()
            result = self.check_pointer_star( BaseType(name) )
            if (self.peek_seq(4) == [TokenType.LEFT_PAR, TokenType.IDENTIFIER, TokenType.RIGHT_PAR, TokenType.LEFT_PAR] and
                self.peek_value(1) == 'func'):
                self.index += 4
                params = []
                while not self.match(TokenType.RIGHT_PAR):
                    params.append( self.parse_type() )
                    self.match(TokenType.COMMA)
                return FuncType(result, params)
            else:
                return result

    def parse_stmt(self):
        if self.match(TokenType.LEFT_BRACE):
            stmts = []
            while not self.match(TokenType.RIGHT_BRACE):
                stmts.append( self.parse_stmt() )
            return Body(stmts)
        elif self.match(TokenType.SEMICOLON):
            return EmptyStatement()
        elif self.peek() is TokenType.IDENTIFIER and self.peek_value() in self.types:
            return self.parse_var_decl()
        elif self.peek_seq(2) == [TokenType.IDENTIFIER, TokenType.COLON]:
            name = self.next_name()
            self.index += 1
            return Label(name)
        elif self.peek() is TokenType.KEYWORD and self.peek_value() in ('const', 'struct'):
            return self.parse_var_decl()
        elif self.peek() is TokenType.DEFINE:
            self.next()
            return self.parse_stmt()
        elif self.peek() is TokenType.KEYWORD:
            _, pos, keyword = self.next()

            if keyword == 'return':
                if not self.match(TokenType.SEMICOLON):
                    expr = self.parse_expr()
                    self.match(TokenType.SEMICOLON)
                    return Return(expr)
                else:
                    return Return(None)

            elif keyword == 'if':
                self.match(TokenType.LEFT_PAR)
                test = self.parse_expr()
                self.match(TokenType.RIGHT_PAR)
                body = self.parse_stmt()
                if self.match(TokenType.KEYWORD, 'else'):
                    orelse = self.parse_stmt()
                else:
                    orelse = None
                return If(test, body, orelse)

            elif keyword == 'while':
                self.match(TokenType.LEFT_PAR)
                test = self.parse_expr()
                self.match(TokenType.RIGHT_PAR)
                body = self.parse_stmt()
                return While(test, body)

            elif keyword == 'for':
                self.match(TokenType.LEFT_PAR)
                init = self.parse_expr_or_decl() if self.peek() is not TokenType.SEMICOLON else None
                self.match(TokenType.SEMICOLON)
                test = self.parse_expr() if self.peek() is not TokenType.SEMICOLON else None
                self.match(TokenType.SEMICOLON)
                incr = self.parse_expr_or_decl() if self.peek() is not TokenType.RIGHT_PAR else None
                self.match(TokenType.RIGHT_PAR)
                body = self.parse_stmt()
                return For(init, test, incr, body)

            elif keyword == 'do':
                body = self.parse_stmt()
                if self.match(TokenType.KEYWORD, 'while'):
                    self.match(TokenType.LEFT_PAR)
                    test = self.parse_expr()
                    self.match(TokenType.RIGHT_PAR)
                    self.match(TokenType.SEMICOLON)
                    return DoWhile(test, body)

            elif keyword == 'goto':
                name = self.next_name()
                self.match(TokenType.SEMICOLON)
                return Goto(name)

            elif keyword == 'break':
                self.match(TokenType.SEMICOLON)
                return Break()

            elif keyword == 'continue':
                self.match(TokenType.SEMICOLON)
                return Continue()

            elif keyword == 'static':
                decor = 'static'
                if self.peek() is TokenType.IDENTIFIER and self.peek_value() == 'inline':
                    decor += ' inline'
                decl = self.parse_decl()
                return Decorated(decor, decl)

            elif keyword == 'switch':
                return self.parse_switch()

            self.skip_statement()
        elif self.peek_seq(2) == [TokenType.IDENTIFIER, TokenType.IDENTIFIER]:
            self.types.add(self.peek_value())
            return self.parse_var_decl()
        elif self.peek_seq(3) == [TokenType.IDENTIFIER, TokenType.STAR, TokenType.IDENTIFIER]:
            self.types.add(self.peek_value())
            return self.parse_var_decl()
        else:
            result = Expression(self.parse_expr())
            if not self.match(TokenType.SEMICOLON):
                self.skip_statement()
            return result

    def parse_switch(self) -> Stmt:

        def parse_case_body():
            stmts = []
            while True:
                if self.peek() is TokenType.KEYWORD and self.peek_value() in ('case', 'default'):
                    break
                if self.peek() is TokenType.RIGHT_BRACE:
                    break
                stmts.append(self.parse_stmt())
            return stmts

        self.match(TokenType.LEFT_PAR)
        subject = self.parse_expr()
        self.match(TokenType.RIGHT_PAR)
        self.match(TokenType.LEFT_BRACE)
        cases = []
        while not self.match(TokenType.RIGHT_BRACE):
            if self.match(TokenType.KEYWORD, 'case'):
                label = self.parse_atom()
                self.match(TokenType.COLON)
                stmts = parse_case_body()
                cases.append( Case(label, stmts) )
            elif self.match(TokenType.KEYWORD, 'default'):
                self.match(TokenType.COLON)
                stmts = parse_case_body()
                cases.append( Case(None, stmts) )
            else:
                raise SyntaxError(f"[{self.pos}] unexpected symbol in switch-statement")
        return Switch(subject, cases)

    def parse_expr_or_decl(self) -> Stmt:
        if self.peek() is TokenType.IDENTIFIER and self.peek_value() in self.types:
            return self.parse_var_decl()
        elif self.peek() is TokenType.KEYWORD:
            if self.peek_value() == 'struct':
                return self.parse_var_decl()
            else:
                SyntaxError(f"[{self.pos}] unexpected symbol")
        else:
            return Expression(self.parse_expr())

    def parse_expr(self) -> Expr:
        value = self.parse_ternary()
        if self.match(TokenType.EQUAL):
            source = self.parse_expr()
            value = Assignment(value, source)
        elif self.peek() is TokenType.AUGASSIGN:
            op = self.next()[2][:-1]
            source = self.parse_expr()
            value = AugAssignment(value, op, source)
        return value

    def parse_ternary(self) -> Expr:
        value = self.parse_bitwise()
        if self.match(TokenType.QUESTION_MARK):
            test = value
            body = self.parse_ternary()
            self.match(TokenType.COLON)
            orelse = self.parse_ternary()
            return IfExpr(test, body, orelse)
        else:
            return value

    def parse_bitwise(self) -> Expr:
        value = self.parse_compare()
        while self.peek() in (TokenType.OPERATOR, TokenType.AMPERSAND):
            op = self.next()
            right = self.parse_compare()
            value = BinOp(value, op[2], right)
        return value

    def parse_compare(self) -> Expr:
        value = self.parse_shift()
        if self.peek() is TokenType.COMPARE:
            op = self.next()
            right = self.parse_shift()
            value = Comparison(value, op[2], right)
        return value

    def parse_shift(self) -> Expr:
        value = self.parse_sum()
        if self.peek() is TokenType.SHIFT_OPERATOR:
            op = self.next()
            right = self.parse_sum()
            value = BinOp(value, op[2], right)
        return value

    def parse_sum(self) -> Expr:
        value = self.parse_product()
        while self.peek() in (TokenType.PLUS, TokenType.MINUS):
            op = self.next()
            right = self.parse_product()
            value = BinOp(value, op[2], right)
        return value

    def parse_product(self) -> Expr:
        value = self.parse_deref()
        while self.peek() in (TokenType.DIV_OPERATOR, TokenType.STAR):
            op = self.next()
            right = self.parse_deref()
            value = BinOp(value, op[2], right)
        return value

    def parse_deref(self) -> Expr:
        deref = self.match(TokenType.STAR)
        addr = self.match(TokenType.AMPERSAND)
        value = self.parse_atom()
        if addr:
            value = AddressOf(value)
        if deref:
            value = Deref(value)
        return self.parse_trailer( value )

    def parse_trailer(self, expr):
        while True:
            if self.match(TokenType.LEFT_PAR):
                args = []
                while self.peek() is not TokenType.RIGHT_PAR:
                    args.append( self.parse_expr() )
                    self.match(TokenType.COMMA)
                self.match(TokenType.RIGHT_PAR)
                expr = Call(expr, args)
            elif self.match(TokenType.LEFT_BRACKET):
                index = self.parse_expr()
                self.match(TokenType.RIGHT_BRACKET)
                expr = Subscript(expr, index)
            elif self.match(TokenType.ARROW) or self.match(TokenType.DOT):
                attr = self.next_name()
                expr = Attribute(expr, attr)
            else:
                break

        if self.peek() in (TokenType.DECREMENT, TokenType.INCREMENT):
            _, _, op = self.next()
            expr = UnarySuffixOp(op, expr)

        return expr

    def parse_atom(self) -> Expr:
        token = self.next()
        if token is None:
            raise SyntaxError(f"[{self.pos}] unexpected end of file")
        token_type, _, value = token

        if token_type is TokenType.LEFT_PAR:
            # Either an expression in parentheses or a type cast
            if self.peek() is TokenType.IDENTIFIER and self.peek_value() in self.types:
                tp = self.parse_type()
                self.match(TokenType.RIGHT_PAR)
                expr = TypeCast(tp, self.parse_deref())
            elif self.peek_seq(3) == [TokenType.IDENTIFIER, TokenType.STAR, TokenType.RIGHT_PAR]:
                tp = self.parse_type()
                self.match(TokenType.RIGHT_PAR)
                expr = TypeCast(tp, self.parse_deref())
            else:
                expr = self.parse_expr()
                self.match(TokenType.RIGHT_PAR)

        elif token_type is TokenType.IDENTIFIER:
            expr = Name(value)

        elif token_type in (TokenType.NULL, TokenType.NUMBER, TokenType.STRING):
            # Constant value
            expr = Const(value)

        elif token_type in (TokenType.PLUS, TokenType.MINUS, TokenType.UNARY_OP):
            # Unary value
            op = value
            value = self.parse_atom()
            if isinstance(value, Const):
                expr = Const(op + value.value)
            else:
                expr = UnaryOp(op, value)

        elif token_type is TokenType.LEFT_BRACE:
            values = []
            while not self.match(TokenType.RIGHT_BRACE):
                values.append(self.parse_atom())
                self.match(TokenType.COMMA)
            expr = ConstArray(values)

        elif token_type is TokenType.KEYWORD and value == 'sizeof':
            if self.peek_seq(2) == [TokenType.LEFT_PAR, TokenType.IDENTIFIER] and self.peek_value(1) in self.types:
                expr = self.parse_type()
            else:
                expr = self.parse_atom()
            expr = SizeOf(expr)

        elif token_type in (TokenType.DECREMENT, TokenType.INCREMENT):
            expr = self.parse_atom()
            expr = UnaryOp(value, expr)

        else:
            raise SyntaxError(f"[{self.pos}] unexpected symbol: '{token_type}' ('{value}')")

        return expr


class ParserContext:
    """
    The ParserContext is meant for parsing an entire series of files in sequence, such as, e.g., the header-file
    followed by the implementing C-code.
    """

    def __init__(self):
        self.macros = {}
        self.types = set()
        self.structs = set()
        self.ast = []

    def add_type(self, name: str):
        if name.startswith('struct '):
            self.structs.add(name[7:])
        elif name != '':
            self.types.add(name)

    def add_types(self, *names):
        for name in names:
            self.add_type(name)

    def define_macro(self, name: str, body: str):
        Tokenizer(f"#define {name} {body}\n", self.macros).read_all()

    def parse(self, text: str):
        tokenizer = Tokenizer(text, self.macros)
        parser = Parser(tokenizer)
        parser.types.update(self.types)
        parser.structs.update(self.structs)
        ast = parser.parse()
        self.types.update(parser.types)
        self.structs.update(parser.structs)
        if isinstance(ast, Body):
            self.ast.extend(ast.stmts)
        elif isinstance(ast, (list, tuple)):
            self.ast.extend(ast)
        elif isinstance(ast, C_AST):
            self.ast.append(ast)
        return ast

    def parse_expr(self, text: str):
        tokenizer = Tokenizer(text, self.macros)
        parser = Parser(tokenizer)
        parser.types.update(self.types)
        parser.structs.update(self.structs)
        ast = parser.parse_expr()
        self.types.update(parser.types)
        self.structs.update(parser.structs)
        if isinstance(ast, Body):
            self.ast.extend(ast.stmts)
        elif isinstance(ast, (list, tuple)):
            self.ast.extend(ast)
        elif isinstance(ast, C_AST):
            self.ast.append(ast)
        return ast

    def parse_file(self, filename: Path|str):
        with open(filename) as f:
            text = f.read()
        return self.parse(text)
