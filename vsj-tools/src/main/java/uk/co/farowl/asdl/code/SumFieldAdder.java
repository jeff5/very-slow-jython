package uk.co.farowl.asdl.code;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.co.farowl.asdl.ast.AsdlTree;
import uk.co.farowl.asdl.ast.ErrorHandler;
import uk.co.farowl.asdl.code.CodeTree.Constructor;
import uk.co.farowl.asdl.code.CodeTree.Module;
import uk.co.farowl.asdl.code.CodeTree.Product;
import uk.co.farowl.asdl.code.CodeTree.Sum;

/**
 * This class is a visitor on the ASDL AST, that adds {@link CodeTree.Field}s to the partial
 * {@link CodeTree.Module}, produced by a {@link DefinitionBuilder}. When creating
 * {@link CodeTree.Field}s this visitor resolves the names in the visited {@link AsdlTree.Field}
 * entries against the definitions as a symbol table for type names.
 */
class SumFieldAdder extends FieldAdder {

    /** The <code>Sum</code> (AST and code) objects to revisit in this phase. */
    private final Map<AsdlTree.Sum, Sum> sums;

    /**
     * Construct a visitor for adding fields to sums.
     *
     * @param module within which to resolve type names.
     * @param sums to revisit during {@link #addFields()}.
     * @param handler for semantic errors (e.g. repeat definitions of fields)
     */
    SumFieldAdder(Module module, Map<AsdlTree.Sum, Sum> sums, ErrorHandler handler) {
        super(module, handler);
        this.sums = sums;
    }

    /** The current sum we are working on if {@link #addFields()} was called. */
    private Sum currentSum;

    @Override
    void addFields() {
        for (AsdlTree.Sum sum : sums.keySet()) {
            currentSum = sums.get(sum);
            visitSum(sum);
        }
    }

    @Override
    public Module visitModule(AsdlTree.Module module) {
        return null;            // Never visited
    }

    @Override
    public Sum visitSum(AsdlTree.Sum sum) {
        // Collect attribute names and check for duplicates
        setAttributeNames(sum.attributes);
        // Iterate over the attributes adding them as fields to the target Sum
        for (AsdlTree.Field a : sum.attributes) {
            currentSum.attributes.add(visitField(a));
        }
        // Iterate over the constructors adding them to the target Sum
        // Check for duplicate constructors using a simple set
        Set<String> names = new HashSet<>();
        for (AsdlTree.Constructor c : sum.constructors) {
            if (!names.add(c.name)) {
                errorHandler.report(c.new Duplicate("constructor", c.name));
            }
            currentSum.constructors.add(visitConstructor(c));
        }
        return currentSum;
    }

    @Override
    public Constructor visitConstructor(AsdlTree.Constructor constructor) {
        // Create a Constructor matching the one from the AST
        List<AsdlTree.Field> members = constructor.members;
        Constructor con = new Constructor(constructor.name, members.size());
        // Iterate over the members adding them as fields to the target Constructor
        memberNames.clear();
        for (AsdlTree.Field m : members) {
            checkMemberName(m);
            con.members.add(visitField(m));
        }
        return con;
    }

    @Override
    public Product visitProduct(AsdlTree.Product product) {
        return null;            // Never visited
    }
}
