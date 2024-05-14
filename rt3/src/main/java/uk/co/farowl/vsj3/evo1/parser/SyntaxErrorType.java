package uk.co.farowl.vsj3.evo1.parser;

/**
 * Various errors riased by the tokenizer.
 *
 * In the original Python code these errors are part of the tokenizer code.  We assembled them into a separate enum and file
 * to ensure a common format for error messages and allow them to be potentially localised.
 */
public enum SyntaxErrorType {

    EOF(11),
    BAD_TOKEN(13),
    SYNTAX_ERROR(14),
    TAB_SPACE(18),
    DEDENT(21),
    EOF_IN_STRING(23),
    EOL_IN_STRING(24),
    LINE_CONT(25),
    INVALID_CHAR(26, "invalid character '%c' (U+%s)"),
    BAD_SINGLE(27),

    // These are all syntax errors, hence a value of `14`:
    SYNTAX_UNMATCHED_BRACKET(14, "unmatched '%c'"),
    SYNTAX_BRACKET_MISMATCH(14, "closing parenthesis '%c' does not match opening parenthesis '%c'"),
    SYNTAX_BRACKET_MISMATCH_MULTILINE(14, "closing parenthesis '%c' does not match opening parenthesis '%c' on line %d"),
    SYNTAX_INVALID_DIGIT_IN_OCT_LITERAL(14, "invalid digit '%c' in octal literal"),
    SYNTAX_INVALID_DIGIT_IN_BIN_LITERAL(14, "invalid digit '%c' in binary literal"),
    SYNTAX_INVALID_DEC_LITERAL(14, "invalid decimal literal"),
    SYNTAX_INVALID_HEX_LITERAL(14, "invalid hexadecimal literal"),
    SYNTAX_INVALID_OCT_LITERAL(14, "invalid octal literal"),
    SYNTAX_INVALID_BIN_LITERAL(14, "invalid binary literal"),
    SYNTAX_LEADING_ZEROES(14, "leading zeros in decimal integer literals are not permitted; use an 0o prefix for octal integers");

    private final String message;
    private final int value;

    SyntaxErrorType(int value) {
        this.message = null;
        this.value = value;
    }

    SyntaxErrorType(int value, String message) {
        this.message = message;
        this.value = value;
    }

    public String getMessage(Object... params) {
        if (params.length == 0 || this.message == null)
            return this.message;
        return String.format(this.message, params);
    }

    public int getValue() {
        return this.value;
    }
}
