package uk.co.farowl.asdl.ast;

/** Error handler as accepted by the AST processing and code generation classes. */
public interface ErrorHandler {

    /**
     * Report an error, specified as an exception.
     *
     * @param se the error
     */
    void report(AsdlTree.SemanticError se);

    /**
     * Return cumulative total of errors reported.
     *
     * @return cumulative total
     */
    int getNumberOfErrors();
}
