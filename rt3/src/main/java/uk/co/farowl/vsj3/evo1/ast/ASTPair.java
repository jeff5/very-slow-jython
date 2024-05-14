package uk.co.farowl.vsj3.evo1.ast;

public abstract class ASTPair<U extends AST, V extends AST> extends AST {

    public abstract U getFirst();

    public abstract V getSecond();
}
