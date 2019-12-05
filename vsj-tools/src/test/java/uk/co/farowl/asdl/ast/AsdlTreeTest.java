package uk.co.farowl.asdl.ast;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.Test;

import uk.co.farowl.asdl.ASDLLexer;
import uk.co.farowl.asdl.ASDLParser;
import uk.co.farowl.asdl.ast.AsdlTree.Constructor;
import uk.co.farowl.asdl.ast.AsdlTree.Definition;
import uk.co.farowl.asdl.ast.AsdlTree.Module;
import uk.co.farowl.asdl.ast.AsdlTree.Product;
import uk.co.farowl.asdl.ast.AsdlTree.Sum;

/**
 * Compile modules and check elements of their ASTs explicitly.
 */
public class AsdlTreeTest {

    // @formatter:off
    private static final String source = "-- Improbable data model"
            + "\n" + "module Animals {" + "\n"
            + "--oink" + "\n"
            + "pig = (int id, string? name, pig* piglets)" + "\n"
            + "attributes(noise oink)" + "\n"
            + "-- baa" + "\n" + "sheep =" + "\n"
            + "Ram(float horns)" + "\n"
            + "| Ewe(float age, float* weights)" + "\n"
            + "-- Another comment" + "\n"
            + "attributes(int id, sheep* lambs)" + "\n"
            + "}\n";

    // @formatter:on

    /** Test attributes of the Module node. */
    @Test
    public void testModule() {
        Module module = getModule(source);
        assertEquals("Animals", module.name);
    }

    /** Test definitions named as expected. */
    @Test
    public void testDefinitions() {
        Module module = getModule(source);
        List<Definition> defs = module.defs;
        assertEquals(2, defs.size());
        Definition pig = defs.get(0);
        assertEquals("pig", pig.name);
        Definition sheep = defs.get(1);
        assertEquals("sheep", sheep.name);
    }

    /** Test example Product is properly constructed. */
    @Test
    public void testProduct() {
        Module module = getModule(source);
        Product pig = (Product)module.defs.get(0);
        assertNames(pig.members, f -> f.typeName, "int", "string", "pig");
        assertNames(pig.members, f -> f.cardinality.toString(), "SINGLE", "OPTIONAL", "SEQUENCE");
        assertNames(pig.members, f -> f.cardinality.marker, "", "?", "*");
        assertNames(pig.members, f -> f.name, "id", "name", "piglets");
        assertNames(pig.attributes, f -> f.typeName, "noise");
        assertNames(pig.attributes, f -> f.cardinality.marker, "");
        assertNames(pig.attributes, f -> f.name, "oink");
    }

    /** Test example Sum has proper sequence of constructors. */
    @Test
    public void testSum() {
        Module module = getModule(source);
        Sum sheep = (Sum)module.defs.get(1);
        assertNames(sheep.constructors, c -> c.name, "Ram", "Ewe");
        assertNames(sheep.attributes, f -> f.typeName, "int", "sheep");
        assertNames(sheep.attributes, f -> f.cardinality.marker, "", "*");
        assertNames(sheep.attributes, f -> f.name, "id", "lambs");
    }

    /** Test example Constructors are properly constructed. */
    @Test
    public void testConstructors() {
        Module module = getModule(source);
        Sum sheep = (Sum)module.defs.get(1);
        Constructor ram = sheep.constructors.get(0);
        assertNames(ram.members, f -> f.typeName, "float");
        assertNames(ram.members, f -> f.cardinality.marker, "");
        assertNames(ram.members, f -> f.name, "horns");
        Constructor ewe = sheep.constructors.get(1);
        assertNames(ewe.members, f -> f.typeName, "float", "float");
        assertNames(ewe.members, f -> f.cardinality.marker, "", "*");
        assertNames(ewe.members, f -> f.name, "age", "weights");
    }

    /** Parse a Field to an AST. */
    @Test
    public void testField() {
        String source = wrapField("int     name  ");
        AsdlTree ast = getAST(source);
        assertThat(ast.toString(), containsString("int name"));
    }

    /** Parse a Field to an AST. */
    @Test
    public void testField2() {
        String source = wrapField("thing     ?name  ");
        AsdlTree ast = getAST(source);
        assertThat(ast.toString(), containsString("thing? name"));
    }

    /** Parse a Field to an AST. */
    @Test
    public void testField3() {
        String source = wrapField("string  *    name  ");
        AsdlTree ast = getAST(source);
        assertThat(ast.toString(), containsString("string* name"));
    }

    /** Wrap a field declaration in a product in a module for testing. */
    private String wrapField(String fieldDecl) {
        return "module Test {\n-- cmnt\ntest = -- cmnt2\n(" + fieldDecl + ")\n}";
    }

    private Module getModule(String source) {
        return getAST(source);
    }

    private ASDLParser getParser(String src) {
        // Wrap the source string in a stream
        CharStream input = CharStreams.fromString(src, "<test>");
        // Wrap the input in a Lexer
        ASDLLexer lexer = new ASDLLexer(input);
        // Get ready to parse the token stream
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        return new ASDLParser(tokens);
    }

    private Module getAST(String source) {
        ASDLParser parser = getParser(source);
        return AsdlTree.forModule(parser.module());
    }

    /**
     * Custom assert method to check names against a reference list, in number and value.
     *
     * @param nodes a list of nodes of type <code>T</code> containing the names to be tested
     * @param nameFn applicable to nodes of type <code>T</code> to extract the name
     * @param names the values expected for those names
     */
    private <T extends AsdlTree> void assertNames(List<T> nodes, Function<T, String> nameFn,
            String... names) {
        assertEquals(names.length, nodes.size());
        int i = 0;
        for (T f : nodes) {
            assertEquals(names[i++], nameFn.apply(f));
        }
    }
}
