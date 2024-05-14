package uk.co.farowl.vsj3.evo1.ast;

public interface HasExprContext<T extends ASTExpr> {

    _ExprContext getCtx();

    void setCtx(_ExprContext ctx);

    T withCtx(_ExprContext ctx);
}
