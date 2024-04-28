package uk.co.farowl.vsj3.evo1.parser;

public class ErrorToken extends Token {

    private final SyntaxErrorType errorType;
    private final int length;
    private final String message;
    private final Object[] params;

    public ErrorToken(SyntaxErrorType errorType, int line, int offset, int length, Object... params) {
        super(TokenType.ERRORTOKEN, line, offset);
        this.errorType = errorType;
        this.length = length;
        this.message = errorType.getMessage(params);
        this.params = params;
    }

    public SyntaxErrorType getErrorType() {
        return this.errorType;
    }

    public String getMessage() {
        return this.message;
    }

    public Object[] getParams() {
        return this.params;
    }

    @Override
    public int getEndOffset() {
        return super.getStartOffset() + length;
    }
}
