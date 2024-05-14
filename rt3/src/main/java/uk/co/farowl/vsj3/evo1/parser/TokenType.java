package uk.co.farowl.vsj3.evo1.parser;

/**
 * This is a translation of CPython's `token.c`.  It provides the various token types.
 *
 * Sources:
 * https://github.com/python/cpython/blob/master/Include/token.h
 * https://github.com/python/cpython/blob/master/Parser/token.c
 */
public enum TokenType {

    ENDMARKER(0),
    NAME(1),
    NUMBER(2),
    STRING(3),
    NEWLINE(4),
    INDENT(5),
    DEDENT(6),
    LPAR(7, "("),
    RPAR(8, ")"),
    LSQB(9, "["),
    RSQB(10, "]"),
    COLON(11, ":"),
    COMMA(12, ","),
    SEMI(13, ";"),
    PLUS(14, "+"),
    MINUS(15, "-"),
    STAR(16, "*"),
    SLASH(17, "/"),
    VBAR(18, "|"),
    AMPER(19, "&"),
    LESS(20, "<"),
    GREATER(21, ">"),
    EQUAL(22, "="),
    DOT(23, "."),
    PERCENT(24, "%"),
    LBRACE(25, "{"),
    RBRACE(26, "}"),
    EQEQUAL(27, "=="),
    NOTEQUAL(28, "!="),
    LESSEQUAL(29, "<="),
    GREATEREQUAL(30, ">="),
    TILDE(31, "~"),
    CIRCUMFLEX(32, "^"),
    LEFTSHIFT(33, "<<"),
    RIGHTSHIFT(34, ">>"),
    DOUBLESTAR(35, "**"),
    PLUSEQUAL(36, "+="),
    MINEQUAL(37, "-="),
    STAREQUAL(38, "*="),
    SLASHEQUAL(39, "/="),
    PERCENTEQUAL(40, "%="),
    AMPEREQUAL(41, "&="),
    VBAREQUAL(42, "|="),
    CIRCUMFLEXEQUAL(43, "^="),
    LEFTSHIFTEQUAL(44, "<<="),
    RIGHTSHIFTEQUAL(45, ">>="),
    DOUBLESTAREQUAL(46, "**="),
    DOUBLESLASH(47, "//"),
    DOUBLESLASHEQUAL(48, "//="),
    AT(49, "@"),
    ATEQUAL(50, "@="),
    RARROW(51, "->"),
    ELLIPSIS(52, "..."),
    COLONEQUAL(53, ":="),
    OP(54),
    AWAIT(55, "await"),
    ASYNC(56, "async"),
    TYPE_IGNORE(57),
    TYPE_COMMENT(58),
    ERRORTOKEN(59),
    COMMENT(60),
    ENCODING(61);

    private final int value;
    private final String text;

    TokenType(int value, String text) {
        this.value = value;
        this.text = text;
    }

    TokenType(int value) {
        this.value = value;
        this.text = null;
    }

    public String getText() {
        return this.text;
    }

    public int getTextLength() {
        if (this.text != null)
            return this.text.length();
        else
            return 0;
    }

    public int getValue() {
        return this.value;
    }

    public boolean isTerminal() {
        return this.value < 0x100;
    }

    public boolean isNonTerminal() {
        return this.value >= 0x100;
    }

    public boolean isEOF() {
        return this == ENDMARKER;
    }

    public boolean isWhitespace() {
        return this == ENDMARKER || this == NEWLINE || this == INDENT || this == DEDENT;
    }

    public static TokenType oneChar(char c1) {
        switch (c1) {
            case '%': return PERCENT;
            case '&': return AMPER;
            case '(': return LPAR;
            case ')': return RPAR;
            case '*': return STAR;
            case '+': return PLUS;
            case ',': return COMMA;
            case '-': return MINUS;
            case '.': return DOT;
            case '/': return SLASH;
            case ':': return COLON;
            case ';': return SEMI;
            case '<': return LESS;
            case '=': return EQUAL;
            case '>': return GREATER;
            case '@': return AT;
            case '[': return LSQB;
            case ']': return RSQB;
            case '^': return CIRCUMFLEX;
            case '{': return LBRACE;
            case '|': return VBAR;
            case '}': return RBRACE;
            case '~': return TILDE;
        }
        return OP;
    }

    public static TokenType twoChars(char c1, char c2) {
        switch (c1) {
            case '!':
                switch (c2) {
                    case '=': return NOTEQUAL;
                }
                break;
            case '%':
                switch (c2) {
                    case '=': return PERCENTEQUAL;
                }
                break;
            case '&':
                switch (c2) {
                    case '=': return AMPEREQUAL;
                }
                break;
            case '*':
                switch (c2) {
                    case '*': return DOUBLESTAR;
                    case '=': return STAREQUAL;
                }
                break;
            case '+':
                switch (c2) {
                    case '=': return PLUSEQUAL;
                }
                break;
            case '-':
                switch (c2) {
                    case '=': return MINEQUAL;
                    case '>': return RARROW;
                }
                break;
            case '/':
                switch (c2) {
                    case '/': return DOUBLESLASH;
                    case '=': return SLASHEQUAL;
                }
                break;
            case ':':
                switch (c2) {
                    case '=': return COLONEQUAL;
                }
                break;
            case '<':
                switch (c2) {
                    case '<': return LEFTSHIFT;
                    case '=': return LESSEQUAL;
                    case '>': return NOTEQUAL;
                }
                break;
            case '=':
                switch (c2) {
                    case '=': return EQEQUAL;
                }
                break;
            case '>':
                switch (c2) {
                    case '=': return GREATEREQUAL;
                    case '>': return RIGHTSHIFT;
                }
                break;
            case '@':
                switch (c2) {
                    case '=': return ATEQUAL;
                }
                break;
            case '^':
                switch (c2) {
                    case '=': return CIRCUMFLEXEQUAL;
                }
                break;
            case '|':
                switch (c2) {
                    case '=': return VBAREQUAL;
                }
                break;
        }
        return OP;
    }

    public static TokenType threeChars(char c1, char c2, char c3) {
        switch (c1) {
            case '*':
                switch (c2) {
                    case '*':
                        switch (c3) {
                            case '=': return DOUBLESTAREQUAL;
                        }
                        break;
                }
                break;
            case '.':
                switch (c2) {
                    case '.':
                        switch (c3) {
                            case '.': return ELLIPSIS;
                        }
                        break;
                }
                break;
            case '/':
                switch (c2) {
                    case '/':
                        switch (c3) {
                            case '=': return DOUBLESLASHEQUAL;
                        }
                        break;
                }
                break;
            case '<':
                switch (c2) {
                    case '<':
                        switch (c3) {
                            case '=': return LEFTSHIFTEQUAL;
                        }
                        break;
                }
                break;
            case '>':
                switch (c2) {
                    case '>':
                        switch (c3) {
                            case '=': return RIGHTSHIFTEQUAL;
                        }
                        break;
                }
                break;
        }
        return OP;
    }
}
