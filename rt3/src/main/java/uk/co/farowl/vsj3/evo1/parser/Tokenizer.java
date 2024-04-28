package uk.co.farowl.vsj3.evo1.parser;

import java.util.*;

/**
 * The Tokenizer is an iterator that takes a `CharSequence` and returns the tokens in the provided text.
 *
 * While we follow very closely the original CPython implementation, there are some changes (in part due to the Java
 * platform).  Most of all, we separate the tokenizer from the decoding of the unicode text from a byte stream.  The
 * tokenizer here assumes that the input is provided as a Java (unicode) string and does not care about the encoding
 * of the original source.
 *
 * Sources:
 * https://github.com/python/cpython/blob/master/Parser/tokenizer.c
 * https://github.com/python/cpython/blob/master/Parser/tokenizer.h
 */
public class Tokenizer implements Iterator<Token> {

    /* A single location can produce any number of tokens as in the case of DEDENT tokens.  In order to account for
     * this possibility, we use a queue internally.  The `get` method does then not return the next token, but adds
     * the read tokens to the queue and the `next()` method reads from the queue. */
    protected final Queue<Token> tokens = new ArrayDeque<>();

    protected final int TABSIZE = 8;    /* Don't ever change this -- it would break the portability of Python code */
    protected final Stack<Integer> indentation = new Stack<>();      /* Stack of indents */
    protected final Stack<Integer> parenIndent = new Stack<>();
    protected final Stack<Character> parenStack = new Stack<>();
    protected int first_lineno = 0;     /* First line of a single line or multi line string
                                           expression (cf. issue 16806) */
    protected int lineno = 0;           /* Current line number */
    protected int paren_level = 0;      /* () [] {} Parentheses nesting level */
                                        /* Used to allow free continuations inside them */

    private boolean _done = false;

    private boolean at_beginning_of_line = true;
    private int pos = 0;
    private int line_start = 0;
    private final CharSequence source;
    private int token_start = 0;
    private int multi_line_start = 0;

    protected Object prompt;

    public Tokenizer(CharSequence source) {
        this.source = source;
        if (source.length() > 0) {
            lineno = 1;
            indentation.push(0);
        }
    }

    @Override
    public boolean hasNext() {
        if (tokens.isEmpty())
            get();
        return !tokens.isEmpty();
    }

    @Override
    public Token next() {
        if (tokens.isEmpty())
            get();
        return tokens.remove();
    }

    private void add(TokenType tokenType) {
        int offset = token_start - line_start;
        tokens.add(Token.apply(tokenType, lineno, offset));
    }

    private void add(TokenType tokenType, int length) {
        int offset = token_start - line_start;
        tokens.add(Token.apply(tokenType, lineno, offset, length));
    }

    private void add(TokenType tokenType, String s) {
        int offset = token_start - line_start;
        tokens.add(Token.apply(tokenType, lineno, offset, s));
    }

    private void addError(SyntaxErrorType errorType) {
        int offset = token_start - line_start;
        tokens.add(new ErrorToken(errorType, lineno, offset, pos - token_start));
    }

    private void addError(SyntaxErrorType errorType, int length, Object... params) {
        int offset = token_start - line_start;
        tokens.add(new ErrorToken(errorType, lineno, offset, length, params));
    }

    private char nextc() {
        if (pos < source.length())
            return source.charAt(pos++);
        else
            return '\0';
    }

    private char peekc() {
        if (pos < source.length())
            return source.charAt(pos);
        else
            return '\0';
    }

    private char peekcAhead() {
        int index = pos + 1;
        if (index < source.length())
            return source.charAt(index);
        else
            return '\0';
    }

    /**
     * Returns `true` if the provided character is a valid start character for an identifier.
     *
     * This differs slightly from Python itself in that we rely on Java's `Character.isUnicodeIdentifierStart` to
     * determine whether the provide character is valid if it is above the ASCII range.
     */
    private boolean is_potential_identifier_start(char c) {
        if (c <= 127)
            return Character.isLetter(c) || c == '_';
        else
            return Character.isUnicodeIdentifierStart(c);
    }

    /**
     * Returns `true` if the provided character is a valid part character for an identifier.
     *
     * In addition to the characters allowed for starting an identifier, an identifier may also contain digits.
     *
     * This differs slightly from Python itself in that we rely on Java's `Character.isUnicodeIdentifierPart` to
     * determine whether the provide character is valid if it is above the ASCII range.
     */
    private boolean is_potential_identifier_char(char c) {
        if (c <= 127)
            return Character.isLetterOrDigit(c) || c == '_';
        else
            return Character.isUnicodeIdentifierPart(c);
    }

    protected void get() {
        boolean blankline = false;       // Completely blank lines are ignored/skipped
        if (at_beginning_of_line) {
            line_start = pos;
            at_beginning_of_line = false;
            blankline = getIndentation();
        }
        char c;

        /* Skip spaces */
        do {
            c = nextc();
        } while (c == ' ' || c == '\t' || c == '\014');

        token_start = pos-1;
        /* Skip comment, unless it's a type comment */
        if (c == '#') {
            while (c != '\0' && c != '\n') {
                c = nextc();
            }

            // Line 1325
            // if (tok->type_comments) {}
            // TODO: Add proper support for type comments
        }

        /* End of file */
        if (c == '\0') {
            while (indentation.size() > 1) {
                indentation.pop();
                add(TokenType.DEDENT);
            }
            if (!_done) {
                add(TokenType.ENDMARKER);
                _done = true;
            }
            return;
        }

        /* Identifier (most frequent token!) */
        if (is_potential_identifier_start(c)) {
            /* Process the various legal combinations of b"", r"", u"", and f"". */
            boolean saw_b = false, saw_r = false, saw_u = false, saw_f = false;
            while (true) {
                if (!(saw_b || saw_u || saw_f) && (c == 'b' || c == 'B'))
                    saw_b = true;
            /* Since this is a backwards compatibility support literal we don't
               want to support it in arbitrary order like byte literals. */
                else if (!(saw_b || saw_u || saw_r || saw_f)
                        && (c == 'u'|| c == 'U')) {
                    saw_u = true;
                }
                /* ur"" and ru"" are not supported */
                else if (!(saw_r || saw_u) && (c == 'r' || c == 'R')) {
                    saw_r = true;
                }
                else if (!(saw_f || saw_b || saw_u) && (c == 'f' || c == 'F')) {
                    saw_f = true;
                }
                else {
                    break;
                }
                c = nextc();
                if (c == '"' || c == '\'') {
                    letter_quote(c);
                }
            }
            while (is_potential_identifier_char(c)) {
                c = nextc();
            }
            pos--;
            String s = source.subSequence(token_start, pos).toString();
            add(TokenType.NAME, s);
            return;
        }

        /* Newline */
        if (c == '\r' && peekc() == '\n') {
            pos++;
            c = '\n';
        }
        if (c == '\n') {
            at_beginning_of_line = true;
            if (blankline || paren_level > 0) {
                lineno++;
                get();
            } else {
                add(TokenType.NEWLINE);
                lineno++;
            }
            return;
        }

        /* Period or number starting with period? */
        if (c == '.') {
            c = peekc();
            if (Character.isDigit(c)) {
                fraction();
                return;
            } else if (c == '.') {
                if (peekcAhead() == '.') {
                    pos += 2;
                    add(TokenType.ELLIPSIS);
                    return;
                }
            }
            add(TokenType.DOT);
            return;
        }

        /* Number */
        if (Character.isDigit(c)) {
            number(c);
            return;
        }

        /* String */
        if (c == '\'' || c == '"') {
            letter_quote(c);
            return;
        }

        /* Line continuation */
        if (c == '\\') {
            c = nextc();
            if (c == '\r' && peekc() == '\n') {
                c = nextc();
            }
            if (c != '\n')
                addError(SyntaxErrorType.LINE_CONT);
            if (peekc() == '\0')
                addError(SyntaxErrorType.EOF);
            line_start = pos;
            lineno++;
            get();
            return;
        }

        /* Check for two-character token */
        {
            TokenType token = TokenType.twoChars(c, peekc());
            if (token != TokenType.OP) {
                char c2 = nextc();
                TokenType token3 = TokenType.threeChars(c, c2, peekc());
                if (token3 != TokenType.OP) {
                    pos++;
                    token = token3;
                }
                add(token);
                return;
            }
        }

        /* Keep track of parentheses nesting level */
        // tok->parenlinenostack[tok->level] = tok->lineno;
        switch (c) {
            case '(', '[', '{' -> {
                parenStack.push(c);
                parenIndent.push(lineno);
                paren_level++;
            }
            case ')', ']', '}' -> {
                if (paren_level == 0) {
                    addError(SyntaxErrorType.SYNTAX_UNMATCHED_BRACKET, 1, c);
                    return;
                }
                paren_level--;
                char opening = parenStack.pop();
                int opening_line = parenIndent.pop();
                if (!((opening == '(' && c == ')') ||
                      (opening == '[' && c == ']') ||
                      (opening == '{' && c == '}'))) {
                    if (opening_line != lineno)
                        addError(SyntaxErrorType.SYNTAX_BRACKET_MISMATCH_MULTILINE, 1,
                                c, opening, opening_line);
                    else
                        addError(SyntaxErrorType.SYNTAX_BRACKET_MISMATCH, 1, c, opening);
                    return;
                }
            }
        }

        /* Check for invalid unicode characters that are not identifiers */
        if (c > 127)
            addError(SyntaxErrorType.INVALID_CHAR, 1, c, Integer.toHexString(c));
        else
            add(TokenType.oneChar(c));
    }

    private boolean getIndentation() {
        int col = 0;
        boolean blankline = false;
        char c;
        for (;;) {
            c = nextc();
            if (c == ' ') {
                col++;
            }
            else if (c == '\t') {
                col = (col / TABSIZE + 1) * TABSIZE;
            }
            else if (c == '\014') {/* Control-L (formfeed) */
                col = 0;           /* For Emacs users */
            }
            else if (c == '\0') {
                return true;
            }
            else {
                break;
            }
        }
        pos--;
        if (c == '#' || c == '\n' || c == '\\') {
            /* Lines with only whitespace and/or comments
               and/or a line continuation character
               shouldn't affect the indentation and are
               not passed to the parser as NEWLINE tokens,
               except *totally* empty lines in interactive
               mode, which signal the end of a command group. */
            if (col == 0 && c == '\n' && prompt != null) {
                blankline = false; /* Let it through */
            }
            else if (prompt != null && lineno == 1) {
                /* In interactive mode, if the first line contains
                   only spaces and/or a comment, let it through. */
                blankline = false;
                col = 0;
            }
            else {
                blankline = true; /* Ignore completely */
            }
            /* We can't jump back right here since we still
               may need to skip to the end of a comment */
        }
        if (!blankline && paren_level == 0) {
            if (col > indentation.peek()) {
                token_start = line_start;
                indentation.push(col);
                add(TokenType.INDENT, pos - token_start);
            }
            else /* if (col < indentation.peek()) */ {
                token_start = pos;
                while (indentation.peek() > 0 && col < indentation.peek()) {
                    indentation.pop();
                    add(TokenType.DEDENT);
                }
                if (col != indentation.peek()) {
                    token_start = 0;
                    addError(SyntaxErrorType.DEDENT);
                }
            }
        }
        return blankline;
    }

    private void letter_quote(char c) {
        char quote = c;
        int quote_size = 1;             /* 1 or 3 */
        int end_quote_size = 0;

        /* Nodes of type STRING, especially multi line strings
           must be handled differently in order to get both
           the starting line number and the column offset right.
           (cf. issue 16806) */
        first_lineno = lineno;
        multi_line_start = line_start;

        /* Find the quote size and start of string */
        c = nextc();
        if (c == quote) {
            c = nextc();
            if (c == quote) {
                quote_size = 3;
            } else {
                end_quote_size = 1;     /* empty string found */
            }
        }
        if (c != quote) {
            pos--;
        }

        /* Get rest of string */
        while (end_quote_size != quote_size) {
            c = nextc();
            if (c == '\0') {
                if (quote_size == 3) {
                    addError(SyntaxErrorType.EOF_IN_STRING);
                } else {
                    addError(SyntaxErrorType.EOL_IN_STRING);
                }
                return;
            }
            if (quote_size == 1 && c == '\n') {
                addError(SyntaxErrorType.EOL_IN_STRING);
                at_beginning_of_line = true;
                lineno++;
                line_start = pos;
                return;
            }
            if (c == quote) {
                end_quote_size += 1;
            }
            else {
                end_quote_size = 0;
                if (c == '\\') {
                    nextc();  /* skip escaped char */
                }
                else if (c == '\n') {
                    lineno++;
                    line_start = pos;
                }
            }
        }
        // TODO: Get this right...!!!
        String s = source.subSequence(token_start, pos).toString();
        add(TokenType.STRING, s);
    }

    private boolean isxdigit(char c) {
        return Character.isDigit(c) || ('A' <= c && c <= 'F') || ('a' <= c && c <= 'f');
    }

    private boolean decimal_tail() {
        char c;
        while (true) {
            do {
                c = nextc();
            } while (Character.isDigit(c));
            if (c != '_') {
                break;
            }
            if (!Character.isDigit(peekc())) {
                addError(SyntaxErrorType.SYNTAX_INVALID_DEC_LITERAL);
                return false;
            }
        }
        pos--;
        return true;
    }

    private void number(char c) {
        if (c == '0') {
            /* Hex, octal or binary -- maybe. */
            c = nextc();
            if (c == 'x' || c == 'X') {
                /* Hex */
                c = nextc();
                do {
                    if (c == '_') {
                        c = nextc();
                    }
                    if (!isxdigit(c)) {
                        pos--;
                        addError(SyntaxErrorType.SYNTAX_INVALID_HEX_LITERAL);
                        return;
                    }
                    do {
                        c = nextc();
                    } while (isxdigit(c));
                } while (c == '_');
            } else if (c == 'o' || c == 'O') {
                /* Octal */
                c = nextc();
                do {
                    if (c == '_') {
                        c = nextc();
                    }
                    if (c < '0' || c >= '8') {
                        pos--;
                        if (Character.isDigit(c))
                            addError(SyntaxErrorType.SYNTAX_INVALID_DIGIT_IN_OCT_LITERAL, pos - token_start, c);
                        else
                            addError(SyntaxErrorType.SYNTAX_INVALID_OCT_LITERAL);
                        return;
                    }
                    do {
                        c = nextc();
                    } while ('0' <= c && c < '8');
                } while (c == '_');
                if (Character.isDigit(c)) {
                    addError(SyntaxErrorType.SYNTAX_INVALID_DIGIT_IN_OCT_LITERAL, pos - token_start, c);
                    return;
                }
            } else if (c == 'b' || c == 'B') {
                /* Binary */
                c = nextc();
                do {
                    if (c == '_') {
                        c = nextc();
                    }
                    if (c != '0' && c != '1') {
                        pos--;
                        if (Character.isDigit(c)) {
                            addError(SyntaxErrorType.SYNTAX_INVALID_DIGIT_IN_BIN_LITERAL, pos - token_start, c);
                        } else {
                            addError(SyntaxErrorType.SYNTAX_INVALID_BIN_LITERAL);
                        }
                        return;
                    }
                    do {
                        c = nextc();
                    } while (c == '0' || c == '1');
                } while (c == '_');
                if (Character.isDigit(c)) {
                    addError(SyntaxErrorType.SYNTAX_INVALID_DIGIT_IN_BIN_LITERAL, pos - token_start, c);
                    return;
                }
            } else {
                boolean nonzero = false;
                /* maybe old-style octal; c is first char of it */
                /* in any case, allow '0' as a literal */
                while (true) {
                    if (c == '_') {
                        c = nextc();
                        if (!Character.isDigit(c)) {
                            pos--;
                            addError(SyntaxErrorType.SYNTAX_INVALID_DEC_LITERAL);
                            return;
                        }
                    }
                    if (c != '0') {
                        break;
                    }
                    c = nextc();
                }
                if (Character.isDigit(c)) {
                    nonzero = true;
                    if (!decimal_tail())
                        return;
                }
                if (c == '.') {
                    fraction();
                    return;
                }
                else if (c == 'e' || c == 'E') {
                    exponent();
                    return;
                }
                else if (c == 'j' || c == 'J') {
                    // Consume the j/J
                }
                else if (nonzero) {
                    /* Old-style octal: now disallowed. */
                    pos--;
                    addError(SyntaxErrorType.SYNTAX_LEADING_ZEROES);
                    return;
                } else
                    pos--;
            }
        } else {
            /* Decimal */
            if (!decimal_tail())
                return;
            c = nextc();
            if (c == '.') {
                fraction();
                return;
            } else if (c == 'e' || c == 'E') {
                exponent();
                return;
            } else if (!(c == 'j' || c == 'J'))
                pos--;
        }
        String s = source.subSequence(token_start, pos).toString();
        add(TokenType.NUMBER, s);
    }

    private void fraction() {
        if (Character.isDigit(peekc())) {
            if (!decimal_tail())
                return;
        }
        char c = peekc();
        if (c == 'e' || c == 'E') {
            pos++;
            exponent();
        } else {
            if (c == 'j' || c == 'J')
                pos++;
            String s = source.subSequence(token_start, pos).toString();
            add(TokenType.NUMBER, s);
        }
    }

    private void exponent() {
        char c = nextc();
        if (c == '+' || c == '-') {
            c = peekc();
            if (Character.isDigit(c)) {
                if (!decimal_tail())
                    return;
            } else {
                addError(SyntaxErrorType.SYNTAX_INVALID_DEC_LITERAL);
                return;
            }
        } else
        if (Character.isDigit(c)) {
            if (!decimal_tail())
                return;
        } else
            pos -= 2;
        c = peekc();
        if (c == 'j' || c == 'J')
            pos++;
        String s = source.subSequence(token_start, pos).toString();
        add(TokenType.NUMBER, s);
    }
}
