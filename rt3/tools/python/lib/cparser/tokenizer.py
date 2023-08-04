"""
A tokenizer for simple C programs and header files.

Please note that we do not support the entire C language, but only a small part as is needed to parse the Python header
files and grammar actions.  The selection of supported features stems from 'trial-and-error' and investigating the
original files in question.  This means that if the Python community makes some modification to these files, introducing
unexpected language features, you might have to extend this tokenizer as well.  Sorry about that.

The following parts of C are currently *not* supported (as far as we are aware, mind that other things might be missing
as well):

    * Floating point literals with exponents (i.e. `12.345` is fine, `1.25E+3` is not);
    * String literals that start with an `L` prefix;
    * `#if` statements only support single integers and the form `#if defined(XXX)` with `XXX` being a name;
    * Although `#include` statements are recognised as special forms, they are just returned as special tokens, but no
      actual import takes place;

There is support for macros in the tokenizer and, moreover, you can transfer macros from one tokenizer to another.
This allows you to scan one file to extract the macros and then apply them in other files as well.  Macro definitions
will be returned to you as special tokens.  However, in most cases you can simply ignore them because they will be
handled by the tokenizer itself.  The following types and elements of macros are supported:

    * `#define NAME`: definition of a simple name is treated as a macro that will expand to an empty list of tokens;
    * `#define NAME <VALUE(S)>`: if `NAME` occurs somewhere in your code, it will be replaced by the list of tokens
      specified as values;
    * `#define NAME(ARGS) <VALUE(S)>`: in this case, `ARGS` must be a comma-separated list of names, the last of which
      may be `...` for any number of arguments.  In the list of tokens `##__VA_ARGS__` will then be replaced by the list
      of actual arguments provided for `...`;
    * Macros are recursive, i.e. the tokenizer will expand macros that appear in macro definitions _when applying the
      first macro_!  In other words, no macros are expanded while scanning the `#define`-line, but once a macro is
      expanded and replaced by a list of tokens, each of these tokens may be a macro, in turn, that will then be
      expanded again, etc.
    * When applying a macro, the actual arguments must be simple names or function calls of the form `NAME(...)`.  We
      do not support arbitrary expressions, i.e. `MACRO(i++, N - i)` will not work.

We assume that the input we are processing is correct.  In general, this would be a very dangerous assumption.  However,
since this is specifically built to parse proven files from the Python ecosystem (i.e. files that have successfully
compiled for years), we feel this assumption is 'safe' enough here.

Author(s): Tobias Kohn
"""
from __future__ import annotations


class TokenType:
    AMPERSAND = 'AMPERSAND'
    ARROW = 'ARROW'
    AUGASSIGN = 'AUGASSIGN'
    COLON = 'COLON'
    COMMA = 'COMMA'
    COMPARE = 'COMPARE'
    DECREMENT = 'DECREMENT'
    DEFINE = 'DEFINE'
    DIRECTIVE = 'DIRECTIVE'
    DIV_OPERATOR = 'DIV_OPERATOR'
    DOT = 'DOT'
    ELLIPSIS = 'ELLIPSIS'
    EQUAL = 'EQUAL'
    IDENTIFIER = 'IDENTIFIER'
    INCLUDE = 'INCLUDE'
    INCREMENT = 'INCREMENT'
    KEYWORD = 'KEYWORD'
    LEFT_BRACE = 'LEFT_BRACE'
    LEFT_BRACKET = 'LEFT_BRACKET'
    LEFT_PAR = 'LEFT_PAR'
    MINUS = 'MINUS'
    NEWLINE = 'NEWLINE'
    NULL = 'NULL'
    NUMBER = 'NUMBER'
    OPERATOR = 'OPERATOR'
    PLUS = 'PLUS'
    QUESTION_MARK = 'QUESTION_MARK'
    RIGHT_BRACE = 'RIGHT_BRACE'
    RIGHT_BRACKET = 'RIGHT_BRACKET'
    RIGHT_PAR = 'RIGHT_PAR'
    SEMICOLON = 'SEMICOLON'
    SHIFT_OPERATOR = 'SHIFT_OPERATOR'
    STAR = 'STAR'
    STRING = 'STRING'
    SYMBOL = 'SYMBOL'
    UNARY_OP = 'UNARY_OP'
    VARARGS = 'VARARGS'

    _single_char_symbols = {
        '(': LEFT_PAR,
        ')': RIGHT_PAR,
        '[': LEFT_BRACKET,
        ']': RIGHT_BRACKET,
        '{': LEFT_BRACE,
        '}': RIGHT_BRACE,
        ';': SEMICOLON,
        ',': COMMA,
        ':': COLON,
        '.': DOT,
        '?': QUESTION_MARK,
        '=': EQUAL,
        '*': STAR,
        '+': PLUS,
        '-': MINUS,
        '~': UNARY_OP,
        '!': UNARY_OP,
        '&': AMPERSAND,
        '/': DIV_OPERATOR,
        '%': DIV_OPERATOR,
        '|': OPERATOR,
        '^': OPERATOR,
        '<': COMPARE,
        '>': COMPARE,
    }

    _double_char_symbols = {
        '->': ARROW,
        '==': COMPARE,
        '!=': COMPARE,
        '<=': COMPARE,
        '>=': COMPARE,
        '+=': AUGASSIGN,
        '-=': AUGASSIGN,
        '*=': AUGASSIGN,
        '/=': AUGASSIGN,
        '%=': AUGASSIGN,
        '&=': AUGASSIGN,
        '|=': AUGASSIGN,
        '^=': AUGASSIGN,
        '&&': OPERATOR,
        '||': OPERATOR,
        '<<': SHIFT_OPERATOR,
        '>>': SHIFT_OPERATOR,
        '++': INCREMENT,
        '--': DECREMENT,
    }

    _triple_char_symbols = {
        '...': ELLIPSIS,
        '>>=': AUGASSIGN,
        '<<=': AUGASSIGN,
    }


# C keywords
KEYWORDS = {
    'auto', 'break', 'case', 'const', 'continue', 'do', 'default', 'else', 'enum', 'extern', 'for',
    'if', 'goto', 'register', 'return', 'sizeof', 'static', 'struct', 'switch', 'typedef', 'union',
    'volatile', 'while',
}


class Macro:
    """
    This represents a C macro.

    The arguments are a list of names (strings) and the body is a list of tokens.
    """

    def __init__(self, name: str, args: list|None, body: list):
        self.name = name
        self.args = args
        self.body = body
        self.var_args = args and args[-1] == '...'
        if self.var_args:
            self.args = self.args[:-1]

    def __repr__(self):
        args = ', '.join(self.args) if self.args else ''
        return f"macro {self.name}({args})->[{', '.join(str(item) for item in self.body)}]"

    def apply(self, tokens) -> list:

        def read_arg():
            result = []
            token = next(tokens)
            level = 0
            while not (level == 0 and token[0] in (TokenType.COMMA, TokenType.RIGHT_PAR)):
                result.append(token)
                if token[0] in (TokenType.LEFT_PAR, TokenType.LEFT_BRACKET):
                    level += 1
                elif token[0] in (TokenType.RIGHT_PAR, TokenType.RIGHT_BRACKET):
                    level -= 1
                token = next(tokens)
            return result, token

        args = {}
        var_args = []
        if len(self.args) > 0:
            nxt = next(tokens)
            assert nxt[0] is TokenType.LEFT_PAR, nxt
            nxt = (TokenType.COMMA, None, None)
            for arg in self.args:
                assert nxt[0] is TokenType.COMMA
                args[arg], nxt = read_arg()
            if self.var_args and nxt[0] is TokenType.COMMA:
                level = 0
                while level >= 0:
                    token = next(tokens)
                    var_args.append(token)
                    if token[0] in (TokenType.LEFT_PAR, TokenType.LEFT_BRACKET):
                        level += 1
                    elif token[0] in (TokenType.RIGHT_PAR, TokenType.RIGHT_BRACKET):
                        level -= 1
                nxt = var_args.pop()
            assert nxt[0] is TokenType.RIGHT_PAR, nxt[0]
        result = []
        for token in self.body:
            if token[0] is TokenType.IDENTIFIER and token[2] in args:
                result.extend( args[token[2]] )
            elif token[0] is TokenType.VARARGS:
                result.extend( var_args )
            else:
                result.append( token )
        return result


class RawTokenizer:
    """
    The raw tokenizer returns tokens from the C source code without worrying about preprocessor directives or any
    other semantics.
    """

    def __init__(self, source: str):
        self.source = source
        self.index = 0
        self.cache = None
        self._want_line_break = False

    def __iter__(self):
        return self

    def __next__(self):
        self.fill_cache()
        token = self.cache
        self.cache = None
        if token is None:
            raise StopIteration()
        else:
            return token

    def _consume(self, index, count_or_text, token_type):
        if isinstance(count_or_text, int):
            s = self.source[index:index+count_or_text]
            self.index = index + count_or_text
            return (token_type, index, s)
        elif isinstance(count_or_text, str):
            self.index = index
            return (token_type, index, count_or_text)

    def fill_cache(self):
        if self.cache is None and self.index < len(self.source):
            self.cache = self.read_token()

    def read_token(self):
        i = self.index
        src = self.source
        # Skip all whitespace and comments, including newlines masked with a backslash
        want_nl = self._want_line_break
        while True:
            while i < len(src) and src[i] <= ' ':
                if src[i] == '\n' and want_nl:
                    self._want_line_break = False
                    return self._consume(i, 1, TokenType.NEWLINE)
                i += 1
            if i+1 < len(src) and src[i:i+2] == '//':
                while i < len(src) and src[i] != '\n':
                    i += 1
                if i < len(src):
                    i += 1
            elif i+1 < len(src) and src[i:i+2] == '/*':
                i += 4
                while i < len(src) and src[i-2:i] != '*/':
                    i += 1
            elif i+1 < len(src) and src[i:i+2] == '\\\n':
                i += 2
            else:
                break

        if i >= len(src):
            self.index = len(src)
            return None

        ch = src[i] if i < len(src) else ''

        if ch == '#':
            j = i
            while j < len(src) and src[j] == '#':
                j += 1
            self._want_line_break = self._want_line_break or ((j == i + 1) and (i == 0 or src[i-1] == '\n'))
            while j < len(src) and src[j].isidentifier():
                j += 1
            token = self._consume(i, j-i, TokenType.DIRECTIVE)
            if token[2] == '##__VA_ARGS__':
                return (TokenType.VARARGS, token[1], token[2])
            else:
                return token

        elif ch.isidentifier():
            j = i
            while j < len(src) and (src[j].isidentifier() or src[j].isdigit()):
                j += 1
            return self._consume(i, j-i, TokenType.IDENTIFIER)

        elif ch == '0' and i + 2 < len(src) and src[i + 1] in 'Xx' and src[i + 2].isdigit():
            # Hexadecimal values
            j = i
            i += 2
            while i < len(src) and src[i] in '0123456789ABCDEFabcdef':
                i += 1
            return self._consume(j, i-j, TokenType.NUMBER)

        elif ch.isdigit():
            # We currently only support integer values and simple floating point values without exponent
            j = i
            while i < len(src) and src[i].isdigit():
                i += 1
            if i + 1 < len(src) and src[i] == '.':
                i += 1
                while i < len(src) and src[i].isdigit():
                    i += 1
            return self._consume(j, i-j, TokenType.NUMBER)

        elif ch == '.' and i + 1 < len(src) and src[i + 1].isdigit():
            j = i
            i += 1
            while i < len(src) and src[i].isdigit():
                i += 1
            return self._consume(j, i-j, TokenType.NUMBER)

        elif ch == '"' or ch == '\'':
            j = i
            i += 1
            while i < len(src) and src[i] != ch:
                if src[i] == '\\' and i + 1 < len(src):
                    i += 2
                else:
                    i += 1
            if i < len(src):
                i += 1
            return self._consume(j, i-j, TokenType.STRING)

        elif src[i:i+3] in TokenType._triple_char_symbols:
            return self._consume(i, 3, TokenType._triple_char_symbols[src[i:i+3]])

        elif src[i:i+2] in TokenType._double_char_symbols:
            return self._consume(i, 2, TokenType._double_char_symbols[src[i:i+2]])

        elif ch in TokenType._single_char_symbols:
            return self._consume(i, 1, TokenType._single_char_symbols[ch])

        else:
            return self._consume(i, 1, TokenType.SYMBOL)


class Tokenizer:
    """
    The tokenizer considers preprocessor directives and puts them into special tokens or ignores any part inside if-
    structures where the test evaluates to false / 0.  Moreover, the tokenizer will expand all known macros.
    """

    def __init__(self, source: str, macros: dict|Tokenizer|None = None):
        if len(source) > 0 and source[-1] != '\n':
            source += '\n'
        self.raw_tokenizer = RawTokenizer(source)
        self.cache = []
        self.if_stack = []
        if isinstance(macros, Tokenizer):
            self.macros = macros.macros
        elif macros is not None:
            self.macros = macros
        else:
            self.macros = {}

    def __iter__(self):
        return self

    def __next__(self):
        if len(self.cache) > 0:
            token = self.cache.pop(0)
            (token_type, pos, directive) = token
        else:
            token = next(self.raw_tokenizer)
            (token_type, pos, directive) = token
            if token_type is TokenType.DIRECTIVE:
                line = []
                tokens = []
                while True:
                    token_type, _, text = token = next(self.raw_tokenizer)
                    if token_type is TokenType.NEWLINE:
                        break
                    else:
                        line.append(text)
                        tokens.append(token)
                text = ''.join(line)
                if directive == '#if':
                    if line[0] == 'defined' and line[1] == '(' and line[3] == ')':
                        self.if_stack.append( line[2] in self.macros )
                    elif len(line) == 1:
                        self.if_stack.append( bool(eval(text)) )
                    else:
                        raise SyntaxError(f"cannot evaluate: '{text}'")
                elif directive == '#ifdef':
                    self.if_stack.append( text in self.macros )
                elif directive == '#ifndef':
                    self.if_stack.append( text not in self.macros )
                elif directive == '#endif':
                    del self.if_stack[-1]
                elif directive == '#else':
                    self.if_stack[-1] = not self.if_stack[-1]
                elif directive == '#define':
                    if len(line) == 1:
                        self.macros[text] = Macro(text, None, [])
                    else:
                        name = line[0]
                        if line[1] == '(' and ')' in line[:-1]:
                            i = line.index(')') + 1
                            text = ''.join(line[:i]) + ' := ' + ''.join(line[i:])
                            args = line[2:i:2]
                            self.macros[name] = Macro(name, args, tokens[i:])
                        else:
                            text = line[0] + ' := ' + ''.join(line[1:])
                            self.macros[name] = Macro(name, [], tokens[1:])
                        if len(self.if_stack) == 0 or self.if_stack[-1]:
                            return (TokenType.DEFINE, pos, text)
                elif directive == '#include':
                    if len(self.if_stack) == 0 or self.if_stack[-1]:
                        return (TokenType.INCLUDE, pos, text)
                return self.__next__()

        if len(self.if_stack) == 0 or self.if_stack[-1]:
            if token_type is TokenType.IDENTIFIER:
                macro = self.macros.get(directive, None)
                if macro is not None:
                    self.cache = macro.apply(self) + self.cache
                    return self.__next__()
                elif directive in KEYWORDS:
                    return (TokenType.KEYWORD, pos, directive)
                elif directive == 'NULL':
                    return (TokenType.NULL, pos, directive)
            return token
        else:
            return self.__next__()

    def define(self, name: str, body: str):
        list(Tokenizer(f"#define {name} {body}\n", self.macros))

    def match(self, token_type):
        if self.peek() is token_type:
            next(self)
            return True
        else:
            return False

    def peek(self):
        if not self.cache:
            try:
                token = next(self)
                self.cache.insert(0, token)
            except StopIteration:
                return None
        else:
            token = self.cache[0]
        return token[0]

    def read_all(self):
        for _ in self:
            pass
