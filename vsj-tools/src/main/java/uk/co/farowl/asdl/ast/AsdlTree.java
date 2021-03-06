package uk.co.farowl.asdl.ast;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import uk.co.farowl.asdl.ASDLParser;
import uk.co.farowl.asdl.ASDLParser.IdContext;
import uk.co.farowl.asdl.ASTBuilderParseVisitor;

/**
 * This class defines the nodes in abstract syntax trees that represent the (partially) compiled
 * form of ASDL source. Any such node may be thought of as a sub-tree, since an instance cannot
 * exist without its complement of child nodes. All the nodes of the ASDL AST extend this base
 * class. The root node of the AST is always a {@link Module}.
 * <p>
 * From these node classes the AST representing an instance of a specification in ASDL may be
 * composed. These classes support traversal of the AST by objects that implement
 * AdslTree#{@link Visitor}.
 * <p>
 * ASDL is a language for describing ASTs and trees composed of these nodes are part of its
 * compiler. Do not confuse these AST node classes with the ones the compiler generates from the
 * ASDL source.
 */
public abstract class AsdlTree {

    /**
     * A reference into the parse tree, primarily used to specify the source location when
     * generating error messages (not null).
     */
    public final ParserRuleContext context;

    /**
     * Construct the base <code>AsdlTree</code> citing the place from which it originates, from
     * which all necessary error context may usually be found..
     *
     * @param context (not null) source in the parse tree
     */
    public AsdlTree(ParserRuleContext context) {
        this.context = context;
        if (context == null) {
            throw new IllegalArgumentException("Parse context cannot be null");
        }
    }

    /**
     * From the parse tree build an AST of the ASDL source.
     *
     * @param parseTree to build form
     * @return AST of the ASDL source
     */
    public static Module forModule(ASDLParser.ModuleContext parseTree) {
        // Using a visitor to the parse tree, construct an AST
        ASTBuilderParseVisitor astBuilder = new ASTBuilderParseVisitor();
        return astBuilder.visitModule(parseTree);
    }

    /**
     * Call the "visit" method on the visitor that is specific to this node's type. (See the
     * <code>Visitor</code> interface.) The visitor could call its own type-appropriate visit method
     * directly. In the case that the caller does not know the concrete type of {@code AsdlTree},
     * <code>accept</code> is a useful way of dispatching to a method that depends on the actual
     * types of both the node and the visitor.
     *
     * @param <T> The type of result each visit method must produce
     * @param visitor to walk round the tree
     * @return result of visit
     * @throws SemanticError from analysis in this pass
     */
    public abstract <T> T accept(Visitor<T> visitor) throws SemanticError;

    Collection<AsdlTree> children() {
        return Collections.emptyList();
    }

    /** Exception base class representing a semantic error blaming this node. */
    public class SemanticError extends Exception {

        /** Parse tree context (not null). */
        public final ParserRuleContext context;

        /**
         * Signal a semantic error.
         *
         * @param message to accompany the error
         */
        public SemanticError(String message) {
            super(message);
            this.context = AsdlTree.this.context;
        }

        @Override
        public String getMessage() {
            Token start = context.getStart();
            String message = String.format("line %d:%d: %s", start.getLine(),
                    start.getCharPositionInLine(), super.getMessage());
            return message;
        }
    }

    /** Something defined more than once. */
    public class Duplicate extends SemanticError {

        /**
         * Signal that attribute/member 'name' is a duplicate.
         *
         * @param typeOfThing duplicated
         * @param name duplicated
         */
        public Duplicate(String typeOfThing, String name) {
            super(String.format("duplicate %s name '%s'", typeOfThing, name));
        }
    }

    /** A name clash with an attribute. */
    public class Shadow extends SemanticError {

        /**
         * Signal that member 'name' clashes with an attribute of the same name.
         *
         * @param name that clashes
         */
        public Shadow(String name) {
            super(String.format("member '%s' clashes with an attribute of the same name", name));
        }
    }

    /**
     * Class representing an entire module. In EBNF: <pre>
     * module        ::= "module" Id "{" [definitions] "}"
     * definitions   ::= { TypeId "=" type }
     * type          ::= product | sum
     * product       ::= fields ["attributes" fields]
     * fields        ::= "(" { field, "," } field ")"
     * field         ::= TypeId ["?" | "*"] [id]
     * sum           ::= constructor { "|" constructor } ["attributes" fields]
     * constructor   ::= ConstructorId [fields]
     * </pre>
     */
    @SuppressWarnings("javadoc") // Mostly self-evident from the EBNF
    public static class Module extends AsdlTree {

        public final String name;
        public final List<Definition> defs;

        public Module(ASDLParser.ModuleContext ctx, List<Definition> defs) {
            super(ctx);
            this.name = ctx.id().getText();
            this.defs = Collections.unmodifiableList(defs);
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

    /**
     * Class representing one definition, which may be a sum or product type. The facility to
     * specify attributes on a product type seems to be a Python addition, used as a notational
     * convenience. In EBNF: <pre>
     * definition : TypeId '=' type ;
     * type : product | sum ;
     * product : fields attributes? ;
     * sum : constructor ( '|' constructor )* attributes? ;
     * constructor : ConstructorId fields? ;
     * attributes : Attributes fields ;
     * </pre>
     */
    public abstract static class Definition extends AsdlTree {

        /** Name ({@code TypeId}) being defined. */
        public final String name;
        /** Attributes of the {@code Definition}. */
        public final List<Field> attributes;

        /**
         * Create one definition, which may be a sum or product type.
         *
         * @param ctx parse tree context
         * @param attributes to list as the attributes of this {@code Definition}
         */
        protected Definition(ASDLParser.DefinitionContext ctx, List<Field> attributes) {
            super(ctx);
            this.name = ctx.TypeId().getText();
            this.attributes = Collections.unmodifiableList(attributes);
        }

        /** @return whether this is a {@code Sum}. */
        public boolean isSum() {
            return this instanceof Sum;
        }
    }

    /**
     * Class representing one sum-type definition. In EBNF: <pre>
     * sum : constructor ( '|' constructor )* attributes? ;
     * constructor : ConstructorId fields? ;
     * attributes : Attributes fields ;
     * </pre>
     */
    public static class Sum extends Definition {

        /** Constructors (alternatives) for the definition. */
        public final List<Constructor> constructors;

        /**
         * Construct a <code>Sum</code> AST node. Note the use of the
         * <code>DefinitionContext</code>, which provides the name being defined and satisfies the
         * super-class constructor.
         *
         * @param ctx parse tree context: note use of <code>DefinitionContext</code>
         * @param constructors (alternatives) for the definition
         * @param attributes to list as the attributes of this {@code Sum}
         */
        public Sum(ASDLParser.DefinitionContext ctx, List<Constructor> constructors,
                List<Field> attributes) {
            super(ctx, attributes);
            this.constructors = Collections.unmodifiableList(constructors);
        }

        @Override
        public <T> T accept(Visitor<T> visitor) throws SemanticError {
            return visitor.visitSum(this);
        }

        /**
         * A sum is simple if it has no members or attributes.
         *
         * @return whether sum is simple
         */
        public boolean isSimple() {
            if (attributes == null || !attributes.isEmpty()) {
                return false;
            }
            for (Constructor c : constructors) {
                if (!c.members.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            String fmt = "%s = Sum(%s, attr=%s)";
            return String.format(fmt, name, constructors, attributes);
        }
    }

    /**
     * Class representing one product-type definition. The facility to specify attributes on a
     * product type seems to be a Python addition, used as a notational convenience. In EBNF: <pre>
     * product : fields attributes? ;
     * constructor : ConstructorId fields? ;
     * attributes : Attributes fields ;
     * </pre>
     */
    @SuppressWarnings("javadoc") // Mostly self-evident from the EBNF
    public static class Product extends Definition {

        public final List<Field> members;

        /**
         * Construct a <code>Product</code> AST node. Note the use of the
         * <code>DefinitionContext</code>, which provides the name being defined and satisfies the
         * super-class constructor.
         *
         * @param ctx parse tree context: note use of <code>DefinitionContext</code>
         * @param members to list as the members of this {@code Product}
         * @param attributes to list as the attributes of this {@code Product}
         */
        public Product(ASDLParser.DefinitionContext ctx, List<Field> members,
                List<Field> attributes) {
            super(ctx, attributes);
            this.members = Collections.unmodifiableList(members);
        }

        @Override
        public <T> T accept(Visitor<T> visitor) throws SemanticError {
            return visitor.visitProduct(this);
        }

        @Override
        public String toString() {
            String fmt = "%s = Product(%s, attr=%s)";
            return String.format(fmt, name, members, attributes);
        }
    }

    @SuppressWarnings("javadoc") // Mostly self-evident from the EBNF
    public static class Constructor extends AsdlTree {

        public final String name;
        public final List<Field> members;

        public Constructor(ASDLParser.ConstructorContext ctx, List<Field> members) {
            super(ctx);
            this.name = ctx.ConstructorId().getText();
            this.members = Collections.unmodifiableList(members);
        }

        @Override
        public <T> T accept(Visitor<T> visitor) throws SemanticError {
            return visitor.visitConstructor(this);
        }

        @Override
        public String toString() {
            return name + members;
        }
    }

    @SuppressWarnings("javadoc") // Mostly self-evident from the EBNF
    public enum Cardinality {
        SINGLE(""), OPTIONAL("?"), SEQUENCE("*");

        public final String marker;

        private Cardinality(String marker) {
            this.marker = marker;
        }
    };

    @SuppressWarnings("javadoc") // Mostly self-evident from the EBNF
    public static class Field extends AsdlTree {

        public final String typeName;
        public final Cardinality cardinality;
        public final String name;

        public Field(ASDLParser.FieldContext ctx, Cardinality cardinality) {
            super(ctx);
            this.typeName = ctx.TypeId().getText();
            this.cardinality = cardinality;
            // A field may be declared without a name
            IdContext id = ctx.id();
            this.name = (id == null) ? null : id.getText();
        }

        @Override
        public <T> T accept(Visitor<T> visitor) throws SemanticError {
            return visitor.visitField(this);
        }

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer(typeName);
            sb.append(cardinality.marker);
            if (name != null) {
                sb.append(' ').append(name);
            }
            return sb.toString();
        }
    }

    /**
     * There is a method in the <code>Visitor</code> interface for each <em>concrete</em> type of
     * {@link AsdlTree}.
     *
     * @param <T> The type of result each visit method must produce.
     */
    @SuppressWarnings("javadoc")
    public interface Visitor<T> {

        T visitModule(Module module);

        T visitSum(Sum sum) throws SemanticError;

        T visitProduct(Product product) throws SemanticError;

        T visitConstructor(Constructor constructor) throws SemanticError;

        T visitField(Field field) throws SemanticError;
    }
}