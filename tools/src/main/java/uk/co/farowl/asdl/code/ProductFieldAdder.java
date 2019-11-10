package uk.co.farowl.asdl.code;

import java.util.Map;

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
class ProductFieldAdder extends FieldAdder {

    /** Collect the <code>Product</code> (AST and code) objects to revisit in the next phase. */
    private final Map<AsdlTree.Product, Product> products;

    /**
     * Construct a visitor for adding fields to products.
     *
     * @param module within which to resolve type names.
     * @param products to revisit during {@link #addFields()}.
     * @param handler for semantic errors (e.g. repeat definitions of fields)
     */
    ProductFieldAdder(Module module, Map<AsdlTree.Product, Product> products,
            ErrorHandler handler) {
        super(module, handler);
        this.products = products;
    }

    /** The current product we are working on if {@link #addFields()} was called. */
    private Product currentProduct;

    @Override
    void addFields() {
        for (AsdlTree.Product product : products.keySet()) {
            currentProduct = products.get(product);
            visitProduct(product);
        }
    }

    @Override
    public Product visitProduct(AsdlTree.Product product) {
        // Collect attribute names and check for duplicates
        setAttributeNames(product.attributes);
        // Iterate over the attributes adding them as fields to the target Product
        for (AsdlTree.Field a : product.attributes) {
            currentProduct.attributes.add(visitField(a));
        }
        // Iterate over the members adding them as fields to the target Product
        memberNames.clear();
        for (AsdlTree.Field m : product.members) {
            checkMemberName(m);
            currentProduct.members.add(visitField(m));
        }
        return currentProduct;
    }

    @Override
    public Sum visitSum(AsdlTree.Sum sum) {
        return null;            // Never visited
    }

    @Override
    public Constructor visitConstructor(AsdlTree.Constructor constructor) {
        return null;            // Never visited
    }
}
