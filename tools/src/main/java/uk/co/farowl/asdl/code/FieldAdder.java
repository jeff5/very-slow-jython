package uk.co.farowl.asdl.code;

import java.util.HashSet;
import java.util.List;

import uk.co.farowl.asdl.ast.AsdlTree;
import uk.co.farowl.asdl.ast.ErrorHandler;
import uk.co.farowl.asdl.code.CodeTree.Definition;
import uk.co.farowl.asdl.code.CodeTree.Field;
import uk.co.farowl.asdl.code.CodeTree.Module;
import uk.co.farowl.asdl.code.CodeTree.Node;

/**
 * Base class for visitors on the ASDL AST that add {@link CodeTree.Field}s to the partial
 * {@link CodeTree.Module}, produced by a {@link DefinitionBuilder}. When creating
 * {@link CodeTree.Field}s this visitor resolves the names in the visited {@link AsdlTree.Field}
 * entries against the definitions, treating it as a symbol table for type names.
 */
abstract class FieldAdder implements AsdlTree.Visitor<Node> {

    /** Module within which to resolve type names. */
    protected final Module module;
    /** Called whenever there is a semantic error in processing the AST. */
    protected final ErrorHandler errorHandler;
    /** Set of attribute names in the current <code>Sum</code> to check members against. */
    protected HashSet<String> attributeNames = new HashSet<>();
    /** Set of member names in the current <code>Constructor</code> or <code>Product</code>. */
    protected HashSet<String> memberNames = new HashSet<>();

    /**
     * Construct a visitor to ASDL trees for the purpose of adding fields to the specified
     * <code>CodeTree</code> module
     *
     * @param module to which to add fields
     * @param handler for semantic errors (e.g. repeat definitions of fields)
     */
    protected FieldAdder(Module module, ErrorHandler handler) {
        this.module = module;
        this.errorHandler = handler;
    }

    /** Traverse the definitions (provided to the constructor) and add the fields from the AST. */
    abstract void addFields();

    @Override
    public Module visitModule(AsdlTree.Module module) {
        return null;            // Never visited
    }

    /**
     * Accumulate attribute names in {@link #attributeNames} and check for duplicates. This is also
     * used to check that members (whether in a <code>Product</code> or in the constructors of a
     * <code>Sum</code>) do not clash with attributes.
     *
     * @param attributes
     */
    protected void setAttributeNames(List<AsdlTree.Field> attributes) {
        attributeNames.clear();
        for (AsdlTree.Field a : attributes) {
            if (!attributeNames.add(a.name)) {
                errorHandler.report(a.new Duplicate("attribute", a.name));
            }
        }
    }

    /**
     * Check that the name of the given member (which may be in a Constructor or Product does not
     * duplicate an existing entry in {@link #memberNames} or {@link #attributeNames}. Note that
     * correct operation depends on a preparatory call to {@link #setAttributeNames(List)} and then
     * clearing {@link #memberNames} each time a new member list is considered in the visitor
     * method.
     *
     * @param member
     */
    protected void checkMemberName(AsdlTree.Field member) {
        String name = member.name;
        // Check whether the name clashes with another member
        if (!memberNames.add(name)) {
            errorHandler.report(member.new Duplicate("member", name));
        }
        // Check whether the name clashes with an attribute
        if (attributeNames.contains(name)) {
            errorHandler.report(member.new Shadow(name));
        }
    }

    @Override
    public Field visitField(AsdlTree.Field field) {
        Definition type = module.scope.definition(field.typeName);
        Field f = new Field(type, field.cardinality, field.name);
        return f;
    }

}
