package uk.co.farowl.asdl.code;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import uk.co.farowl.asdl.ast.AsdlTree;
import uk.co.farowl.asdl.ast.AsdlTree.Cardinality;
import uk.co.farowl.asdl.ast.ErrorHandler;

/**
 * This class defines an abstract syntax tree for ASDL compiled to a particular target language, and
 * ready for output code generation. It is still quite close in structure to the {@link AsdlTree}
 * from which it is derived.
 * <p>
 * A significant difference is that where a list of named elements appears in a node of the
 * <code>AsdlTree</code>, here we will normally have an order-preserving map from names to
 * definitions, guaranteeing that repeated declarations and other obstacles are not present, at
 * least by the time we come to use the tree for code generation.
 */
public class CodeTree {

    /** Root node of this <code>CodeTree</code>. */
    public final Module root;

    /** Construct the AST from the result of parsing an ASDL module. */
    public CodeTree(Scope<Definition> scope, AsdlTree.Module tree, ErrorHandler errorHandler) {

        // Ensure the module has all its definitions, but without the fields.
        DefinitionBuilder definitionBuilder = new DefinitionBuilder(scope, errorHandler);
        root = definitionBuilder.buildModule(tree);

        // Visit the definitions to add the fields, now referring to the definition of their types.
        ProductFieldAdder productFieldAdder =
                new ProductFieldAdder(root, definitionBuilder.products, errorHandler);
        productFieldAdder.addFields();

        // Visit the definitions to add the fields, now referring to the definition of their types.
        SumFieldAdder sumFieldAdder = new SumFieldAdder(root, definitionBuilder.sums, errorHandler);
        sumFieldAdder.addFields();
    }

    @Override
    public String toString() {
        return root.toString();
    }

    /**
     * Holder for two names: the one given in ASDL source and one that is compatible with the output
     * language. All the {@link Node}s in the {@link CodeTree} that need a name use this structure.
     * Upon construction, it takes the name in ASDL source. Optionally, later, the <code>Name</code>
     * may be given a name for use in generated code.
     */
    public static class Name {

        /** Name as given in ASDL source. */
        final String asdl;
        /** The ASDL source, or an alternative specified with {@link #setCode(String)}. */
        String code;

        /** Initially the ASDL name and the code name are the same. */
        public Name(String asdlName) {
            code = asdl = asdlName;
        }

        /** Assign a replacement name to be used in generated code. */
        void setCode(String codeName) {
            this.code = codeName;
        }

        /** Return the name to be used in code. */
        @Override
        public String toString() {
            return code;
        }
    }

    /** All the nodes of the <code>CodeTree</code> implement this interface. */
    public interface Node {

        /**
         * Call the "visit" method on the visitor that is specific to this node's type. (See the
         * <code>Visitor</code> interface.) The visitor could call its own type-appropriate visit
         * method directly. In the case that the caller does not know the concrete type of <Node>,
         * <code>accept</code> is a useful way of dispatching to a method that depends on the actual
         * types of both the node and the visitor.
         *
         * @param visitor
         * @return
         */
        abstract <T> T accept(Visitor<T> visitor);
    }

    public static class Module implements Node {

        /** The name of the module. */
        public final Name name;
        /** The <code>Scope</code> in which to create type <code>Definitions</code>. */
        public final Scope<Definition> scope;
        /** Look-up from type name to defined ASDL type. */
        public List<Definition> defs;

        /**
         * Create a <code>Module</code>: the definitions are filled later. A new scope is created
         * that nests inside the one named in the constructor.
         *
         * @param name the name (in the ASDL source) of the module
         * @param scope the symbol table enclosing this module's scope
         */
        public Module(String name, Scope<Definition> enclosingScope) {
            this.name = new Name(name);
            this.scope = new Scope<>(enclosingScope);
            this.defs = new ArrayList<>();
        }

        /** Module is the container for built-in types if it is the nameless module. */
        public boolean isBuiltin() {
            return "".equals(name.toString());
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitModule(this);
        }

        @Override
        public String toString() {
            return String.format("module %s %s", name, defs);
        }

    }

    /** Class representing one definition, which may be a sum or product type. */
    public static abstract class Definition implements Node {

        /** The name defined by the definition. */
        public final Name name;
        /** The {@link Module} in which this was defined. */
        public final Module module;
        /** Attributes stored on an instance of the type (common across constructors of a sum). */
        public final List<Field> attributes;

        /**
         * Create a <code>Definition</code>: the attributes are filled later.
         *
         * @param name the name (in the ASDL source) of the type.
         * @param module in which this was defined.
         * @param attributeCount number of attributes possessed.
         */
        public Definition(String name, Module module, int attributeCount) {
            this.name = new Name(name);
            this.module = module;
            this.attributes = new ArrayList<>(attributeCount);
        }

        public boolean isSum() {
            return this instanceof Sum;
        }

        /** A definition that is simple allows for certain simplifications in the generated code. */
        public abstract boolean isSimple();

        /** A definition is a built-in if it is in the nameless module. */
        public boolean isBuiltin() {
            return module.isBuiltin();
        }

        /**
         * The attributes are all of
         * built-in type (and therefore has no child nodes).
         *
         * @return whether this type contains only built-ins
         */
        public abstract boolean hasOnlyBuiltin();

        /**
         * The attributes are all either
         * of simple type or of built-in type. If simple types are not represented by nodes (an option in
         * the generation of code) such a definition has no child nodes.
         *
         * @return whether this type contains only simple types or built-ins
         */
        public abstract boolean hasOnlySimple();
    }

    /** Class representing one sum-type definition. */
    public static class Sum extends Definition {

        public final List<Constructor> constructors;

        /**
         * Create a <code>Sum</code> definition: the constructors and attributes are filled later.
         *
         * @param name the name (in the generated code) of the type
         * @param module in which this was defined.
         * @param constructorCount number of constructors in the sum
         * @param attributeCount number of attributes possessed
         */
        public Sum(String name, Module module, int constructorCount, int attributeCount) {
            super(name, module, attributeCount);
            this.constructors = new ArrayList<>(constructorCount);
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitSum(this);
        }

        /** A sum is simple if it has no attributes and no constructor has any members. */
        @Override
        public boolean isSimple() {
            if (isBuiltin()) {
                return true;
            } else if (attributes.size() > 0) {
                return false;
            } else {
                for (Constructor c : constructors) {
                    if (c.members.size() > 0) {
                        return false;
                    }
                }
                return true;
            }
        }

        @Override
        public boolean hasOnlyBuiltin() {
            return allBuiltin(attributes);
        }

        @Override
        public boolean hasOnlySimple() {
            return allSimple(attributes);
        }

        @Override
        public String toString() {
            String fmt = "%s(%s, attr=%s)";
            return String.format(fmt, name, constructors, attributes);
        }
    }

    /**
     * Class representing one product-type definition. The facility to specify attributes on a
     * product type seems to be a Python addition, used as a notational convenience.
     */
    public static class Product extends Definition {

        public final List<Field> members;

        /**
         * Create a <code>Product</code> definition: the members and attributes are filled later.
         *
         * @param name the name (in the generated code) of the type
         * @param module in which this was defined.
         * @param memberCount number of members possessed
         * @param attributeCount number of attributes possessed
         */
        public Product(String name, Module module, int memberCount, int attributeCount) {
            super(name, module, attributeCount);
            this.members = new ArrayList<>(memberCount);
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitProduct(this);
        }

        /** A product is simple if it is a built-in or has no attributes or members. */
        @Override
        public boolean isSimple() {
            return isBuiltin() || (attributes.size() == 0 && members.size() == 0);
        }

        /**
         * {@inheritDoc}
         * For a product, this must also be true of the members.
         */
        @Override
        public boolean hasOnlyBuiltin() {
            return allBuiltin(attributes) && allBuiltin(members);
        }

        /**
         * {@inheritDoc}
         * For a product, this must also be true of the members.
         */
        @Override
        public boolean hasOnlySimple() {
            return allSimple(attributes) && allSimple(members);
        }

        @Override
        public String toString() {
            String fmt = "%s(%s, attr=%s)";
            return String.format(fmt, name, members, attributes);
        }
    }

    public static class Constructor implements Node {

        public final Name name;
        public final List<Field> members;

        /**
         * Create a <code>Constructor</code> clause: the members are filled later.
         *
         * @param name the name (in the ASDL source) of the type defined
         * @param memberCount number of members possessed
         */
        public Constructor(String name, int memberCount) {
            this.name = new Name(name);
            this.members = new ArrayList<>(memberCount);
        }

        /**
         * The constructor-type is of a type whose members are all of built-in type (and therefore
         * has no child nodes).
         *
         * @return whether this constructor-type contains only built-ins
         */
        public boolean hasOnlyBuiltin() {
            return allBuiltin(members);
        }

        /**
         * The constructor-type is of a type whose members are all of either simple type or built-in
         * type. If simple types are not represented by nodes (an option in the generation of code)
         * such a definition has no child nodes.
         *
         * @return whether this constructor-type contains only simple types or built-ins
         */
        public boolean hasOnlySimple() {
            return allSimple(members);
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitConstructor(this);
        }

        @Override
        public String toString() {
            return name.toString() + members.toString();
        }
    }

    public static class Field implements Node {

        public final Name name;
        public final Definition type;
        public final Cardinality cardinality;

        public Field(Definition type, Cardinality cardinality, String name) {
            this.type = type;
            this.cardinality = cardinality;
            this.name = new Name(name);
        }

        public final boolean isOptional() {
            return cardinality == Cardinality.OPTIONAL;
        }

        public final boolean isSequence() {
            return cardinality == Cardinality.SEQUENCE;
        }

        @Override
        public <T> T accept(Visitor<T> visitor) {
            return visitor.visitField(this);
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append(type == null ? "<unresolved>" : type.name.toString());
            sb.append(cardinality.marker);
            if (name != null) {
                sb.append(' ').append(name);
            }
            return sb.toString();
        }
    }

    /**
     * There is a method in the <code>Visitor</code> interface for each <em>concrete</em> type of
     * {@link CodeTree.Node}.
     */
    public interface Visitor<T> {

        T visitModule(Module module);

        T visitSum(Sum sum);

        T visitProduct(Product product);

        T visitConstructor(Constructor constructor);

        T visitField(Field field);
    }

    /** True iff all the Fields in a collection are built-in types. */
    private static boolean allBuiltin(Collection<Field> fields) {
        for (Field f : fields) {
            if (!f.type.isBuiltin()) {
                return false;
            }
        }
        return true;
    }

    /** True iff all the Fields in a collection are built-in types. */
    private static boolean allSimple(Collection<Field> fields) {
        for (Field f : fields) {
            if (!f.type.isSimple()) {
                return false;
            }
        }
        return true;
    }
}
