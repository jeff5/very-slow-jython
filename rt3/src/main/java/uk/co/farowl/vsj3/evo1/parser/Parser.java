package uk.co.farowl.vsj3.evo1.parser;

import java.math.BigInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import uk.co.farowl.vsj3.evo1.ast.*;

/**
 * The actual parser is generated automatically by the "pegen" parser generator.  This class provides the basic
 * infrastructure for the generated parser, such as consuming tokens in various forms, i.e. implementing the grammar
 * rules for the terminals.
 */
public abstract class Parser {

    protected final String filename;
    private final BufferedTokenizer tokenizer;
    protected int _level = 0;
    protected final boolean verbose;

    Parser(Tokenizer tokenizer, String filename, boolean verbose) {
        this.tokenizer = new BufferedTokenizer(tokenizer);
        this.filename = filename;
        this.verbose = verbose;
    }

    public static AST run_parser(String source, String filename) {
        return run_parser(source, filename, false);
    }

    public static AST run_parser(String source, String filename, boolean verbose) {
        Parser parser = new GeneratedParser(new Tokenizer(source), filename, verbose);
        return parser.start();
    }

    protected int mark() {
        return this.tokenizer.mark();
    }

    protected void reset(int mark) {
        this.tokenizer.reset(mark);
    }

    protected AST start() {
        return file();
    };

    protected AST start(ParserInputType inputType) {
        switch (inputType) {
            case FILE:
                return file();
            case SINGLE:
                return interactive();
            case EVAL:
                return eval();
            case FUNC_TYPE:
                return func_type();
            case FSTRING:
                return fstring();
            default:
                return start();
        }
    }

    protected abstract ASTMod file();

    protected abstract ASTMod interactive();

    protected abstract ASTMod eval();

    protected abstract AST func_type();

    protected abstract AST fstring();

    protected abstract boolean isKeyword(String name);

    protected abstract boolean isSoftKeyword(String name);

    public String getFilename() {
        return filename;
    }

    public String showpeek() {
        Token tok = tokenizer.peek();
        return ("" + tok.getStartLine() + "." + tok.getStartOffset() + ": " +
                tok.getTokenType().toString() + ":" + tok.getText());
    }

    private Token _expect(TokenType tokenType) {
        logl(String.format("expect<>() at %d: %s", getPos(), tokenType.toString()));
        Token tok = tokenizer.peek();
        if (tok.getTokenType() == tokenType) {
            tokenizer.advance();
            return tok;
        }
        return null;
    }

    protected int col_offset() {
        return tokenizer.getCurrentOffset();
    }

    protected int line_no() {
        return tokenizer.getCurrentLineNo();
    }

    protected int getStartLineno() {
        return tokenizer.getCurrentLineNo();
    }

    protected int getStartColOffset() {
        return tokenizer.getCurrentOffset();
    }

    protected int getEndLineno() {
        return tokenizer.getPrevLineNo();
    }

    protected int getEndColOffset() {
        return tokenizer.getPrevOffset();
    }

    protected int getPos() {
        return tokenizer.getPos();
    }

    protected Token getLastToken() {
        return tokenizer.getLastNonWhitespaceToken();
    }

    protected ASTExpr getLastTokenAsExpr() {
        return null;  // TODO
    }

    public Name name() {
        logl(String.format("name() at %d", getPos()));
        Token tok = tokenizer.peek();
        if (tok.getTokenType() == TokenType.NAME && !isKeyword(tok.getText())) {
            tokenizer.advance();
            return new Name(tok.string, _ExprContext.Load, tok.getStartLine(),
                    tok.getStartOffset(), tok.getEndLineno(), tok.getEndColOffset());
        }
        return null;
    }

    public ASTExpr number() {
        Token token = _expect(TokenType.NUMBER);
        if (token != null) {
            Object value;
            try {
                BigInteger v = new BigInteger(token.string);
                int iv = v.intValue();
                value = v.equals(BigInteger.valueOf(iv))? iv: v;
            } catch (NumberFormatException e) {
                value = Double.parseDouble(token.string);
            }
            return new Constant(value, null,
                    token.getLineno(), token.getColOffset(), token.getEndLineno(), token.getEndColOffset());
        }
        else
            return null;
    }

    public Token string() {
        return _expect(TokenType.STRING);
    }

    public Token op() {
        return _expect(TokenType.OP);
    }

    public Token softKeyword() {
        Token token = tokenizer.peek();
        if (token.getTokenType() == TokenType.NAME && isSoftKeyword(token.string)) {
            tokenizer.advance();
            return token;
        }
        return null;
    }

    public Token expect(TokenType tokenType) {
        logl(String.format("expect() at %d: %s", getPos(), tokenType.toString()));
        Token tok = tokenizer.peek();
        if (tok.getTokenType() == tokenType && !isKeyword(tok.getText())) {
            tokenizer.advance();
            return tok;
        }
        return null;
    }

    public Token expectStr(String tokenType) {
        logl(String.format("expectStr() at %d: %s", getPos(), tokenType));
        Token tok = tokenizer.peek();
        if (tok.getTokenType().toString().equals(tokenType)) {
            if (isKeyword(tokenType))
                return null;
            else {
                tokenizer.advance();
                return tok;
            }
        }
        String tokenText = tok.getTokenType().getText();
        if (tokenText != null && tokenText.equals(tokenType)) {
            tokenizer.advance();
            return tok;
        }
        return null;
    }

    public Token expectStr(char tokenType) {
        return expectStr(Character.toString(tokenType));
    }

    public Token expectKeyword(String keyword) {
        logl(String.format("expectKeyword() at %d: %s", getPos(), keyword));
        Token tok = tokenizer.peek();
        if (tok.getTokenType() == TokenType.NAME && tok.getText().equals(keyword)) {
            logl("-> There is a match: " + keyword);
            tokenizer.advance();
            return tok;
        }
        else
            logl("->" + tok.getText() + "==" + keyword);
        return null;
    }

    public Token expect_forced(Token tkn, String expectation) {
        if (tkn == null)
            throw new RuntimeException(expectation);    // TODO: this should be a syntax error
        return tkn;
    }

    public boolean positive_lookahead(Supplier<?> func) {
        int m = mark();
        boolean result = func.get() != null;
        reset(m);
        return result;
    }

    public boolean positive_lookahead(Function<String, ?> func, String arg) {
        int m = mark();
        boolean result = func.apply(arg) != null;
        reset(m);
        return result;
    }

    public boolean positive_lookahead(Function<String, ?> func, char arg) {
        int m = mark();
        boolean result = func.apply(Character.toString(arg)) != null;
        reset(m);
        return result;
    }

    public boolean positive_lookahead(Function<TokenType, ?> func, TokenType arg) {
        int m = mark();
        boolean result = func.apply(arg) != null;
        reset(m);
        return result;
    }

    public boolean positive_lookahead(String... symbols) {
        int m = mark();
        boolean result = false;
        for (String symbol : symbols) {
            if (expectStr(symbol) != null || expectKeyword(symbol) != null)
                result = true;
        }
        reset(m);
        return result;
    }

    public boolean negative_lookahead(Supplier<?> func) {
        int m = mark();
        boolean result = func.get() == null;
        reset(m);
        return result;
    }

    public boolean negative_lookahead(Function<String, ?> func, String arg) {
        int m = mark();
        boolean result = func.apply(arg) == null;
        reset(m);
        return result;
    }

    public boolean negative_lookahead(Function<String, ?> func, char arg) {
        int m = mark();
        boolean result = func.apply(Character.toString(arg)) == null;
        reset(m);
        return result;
    }

    public boolean negative_lookahead(Function<TokenType, ?> func, TokenType arg) {
        int m = mark();
        boolean result = func.apply(arg) == null;
        reset(m);
        return result;
    }

    public boolean negative_lookahead(String... symbols) {
        int m = mark();
        boolean result = true;
        for (String symbol : symbols) {
            if (expectStr(symbol) != null || expectKeyword(symbol) != null)
                result = false;
        }
        reset(m);
        return result;
    }

    protected void log(String s) {
        if (verbose) {
            System.out.print(new String(new char[this._level * 2]).replace('\0', ' '));
            System.out.println(s);
        }
    }

    protected void logl(String s) {
        if (verbose) {
            System.out.print(new String(new char[this._level * 2]).replace('\0', ' '));
            System.out.print(s);
            System.out.printf(" (looking at %s)%n", showpeek());
        }
    }

    protected void log(String s, Object o) {
        if (o != null)
            log(s + o.toString());
        else
            log(s + "null");
    }

    public int getFeatureVersion() {
        return 6;
    }

    protected String typeComment() {
        return null;
    }
}
