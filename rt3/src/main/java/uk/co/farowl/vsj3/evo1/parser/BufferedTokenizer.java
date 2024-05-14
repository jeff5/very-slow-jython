package uk.co.farowl.vsj3.evo1.parser;

import java.util.ArrayList;

public class BufferedTokenizer {

    private final Tokenizer tokenizer;
    private final ArrayList<Token> tokens = new ArrayList<>();
    private int prev_lineno = 0;
    private int prev_offset = 0;
    private int lineno = 0;
    private int offset = 0;
    private int pos = 0;

    BufferedTokenizer(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        while (tokenizer.hasNext())
            tokens.add(tokenizer.next());
        updateMetaInformation();
    }

    public void advance() {
        if (pos < tokens.size() || tokenizer.hasNext()) {
            pos++;
            updateMetaInformation();
        }
    }

    protected Token get() {
        int index = this.pos;
        while (index < tokens.size() && tokenizer.hasNext())
            tokens.add(tokenizer.next());
        if (0 <= index && index < tokens.size())
            return tokens.get(index);
        else
            return null;
    }

    public int getCurrentLineNo() {
        return lineno;
    }

    public int getCurrentOffset() {
        return offset;
    }

    public Token getLastNonWhitespaceToken() {
        int i = pos - 1;
        while (i > 0 && tokens.get(i).getTokenType().isWhitespace())
            i--;
        if (i >= 0)
            return tokens.get(i);
        else
            return null;
    }

    public int getPrevLineNo() {
        return prev_lineno;
    }

    public int getPrevOffset() {
        return prev_offset;
    }

    public int mark() {
        return pos;
    }

    public Token next() {
        Token result = get();
        if (result != null) {
            pos++;
            updateMetaInformation();
        }
        return result;
    }

    public Token peek() {
        return get();
    }

    public int getPos() {
        return pos;
    }

    public void reset(int mark) {
        if (0 <= mark && mark <= tokens.size()) {
            pos = mark;
            updateMetaInformation();
        }
    }

    private void updateMetaInformation() {
        if (pos > 0) {
            Token tok = tokens.get(pos - 1);
            prev_lineno = tok.getEndLine();
            prev_offset = tok.getEndOffset();
        } else {
            prev_lineno = 0;
            prev_offset = 0;
        }
        if (pos < tokens.size()) {
            Token tok = tokens.get(pos);
            lineno = tok.getStartLine();
            offset = tok.getStartOffset();
        }
    }
}
