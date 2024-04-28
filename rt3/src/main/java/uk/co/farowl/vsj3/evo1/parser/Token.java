package uk.co.farowl.vsj3.evo1.parser;

/**
 * A single token read from the input.  Each token contains a type and information about its location in the source
 * code, encoded as line-number and offset within the line.  Some tokens also have a `text`-property that contains
 * a value such as the name of the identifier or the actual number as a string.
 */
public class Token {

    private final TokenType tokenType;
    private final int start_line;
    private final int start_offset;

    public final String string;

    public Token(TokenType tokenType, int line, int offset) {
        this(tokenType, line, offset, tokenType.getText());
    }

    public Token(TokenType tokenType, int line, int offset, String string) {
        this.tokenType = tokenType;
        this.start_line = line;
        this.start_offset = offset;
        this.string = string;
    }

    public int getEndLine() {
        return this.start_line;
    }

    public int getEndOffset() {
        if (string != null)
            return this.start_offset + string.length();
        else
            return this.start_offset;
    }

    public int getStartLine() {
        return this.start_line;
    }

    public int getStartOffset() {
        return this.start_offset;
    }

    public int getEndLineno() {
        return this.start_line;
    }

    public int getEndColOffset() {
        return this.start_offset + string.length();
    }

    public int getLineno() {
        return this.start_line;
    }

    public int getColOffset() {
        return this.start_offset;
    }

    public String getText() {
        return string;
    }

    public TokenType getTokenType() {
        return this.tokenType;
    }

    @Override
    public String toString() {
        return "Token{" +
                "tokenType=" + tokenType +
                ", start_line=" + start_line +
                ", start_offset=" + start_offset +
                '}';
    }

    public static Token apply(TokenType tokenType, int line, int offset) {
        return new Token(tokenType, line, offset);
    }

    public static Token apply(TokenType tokenType, int line, int offset, int length) {
        return new SpanToken(tokenType, line, offset, length);
    }

    public static Token apply(TokenType tokenType, int line, int offset, String text) {
        return new TextToken(tokenType, line, offset, text);
    }

    public static Token apply(TokenType tokenType, int start_line, int start_offset,
                              int end_line, int end_offset, String text) {
        return new MultiLineToken(tokenType, start_line, start_offset, end_line, end_offset, text);
    }

    /**
     * A token that spans multiple lines.  This is almost exclusively a multiline-string.
     */
    public static class MultiLineToken extends Token {

        private final int end_line;
        private final int end_offset;

        MultiLineToken(TokenType tokenType, int start_line, int start_offset, int end_line, int end_offset, String text) {
            super(tokenType, start_line, start_offset, text);
            this.end_line = end_line;
            this.end_offset = end_offset;
        }

        @Override
        public int getEndLine() {
            return this.end_line;
        }

        @Override
        public int getEndOffset() {
            return this.end_offset;
        }
    }

    /**
     * A token that spans a range of characters of varying length, such as indentation.
     */
    public static class SpanToken extends Token {

        private final int length;

        SpanToken(TokenType tokenType, int line, int offset, int length) {
            super(tokenType, line, offset);
            this.length = length;
        }

        @Override
        public int getEndOffset() {
            return this.getStartOffset() + length;
        }
    }

    /**
     * A token carrying a meaningful 'text' that is not determined by the type of the token alone,
     * such as a string literal or a number.
     */
    public static class TextToken extends Token {

        TextToken(TokenType tokenType, int start_line, int start_offset, String text) {
            super(tokenType, start_line, start_offset, text);
        }

        @Override
        public String toString() {
            return "TextToken{" +
                    "tokenType=" + getTokenType() +
                    ", start_line=" + getStartLine() +
                    ", start_offset=" + getStartOffset() +
                    ", text='" + getText() + '\'' +
                    '}';
        }
    }
}
