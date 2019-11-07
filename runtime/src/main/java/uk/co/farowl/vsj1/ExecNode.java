package uk.co.farowl.vsj1;

import java.lang.invoke.CallSite;

/**
 * A base class for nodes in the AST or another tree that supports their
 * execution as code.
 */
public class ExecNode {
    public CallSite site;
}
