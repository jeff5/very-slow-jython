package uk.co.farowl.asdl.ast;

import uk.co.farowl.asdl.ast.AsdlTree.SemanticError;

/**
 * An error handler for use by anything that processes the AST, and which counts and prints to
 * <code>System.err</code> the reported errors.
 */
public class DefaultErrorHandler implements ErrorHandler {

    /** Counter of errors. */
    protected int errors;

    public DefaultErrorHandler() {
        errors = 0;
    }

    @Override
    public void report(SemanticError se) {
        errors += 1;
        System.err.println(se.getMessage());
    }

    @Override
    public int getNumberOfErrors() {
        return errors;
    }
}
