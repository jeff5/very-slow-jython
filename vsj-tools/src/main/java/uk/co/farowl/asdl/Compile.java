package uk.co.farowl.asdl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;

import uk.co.farowl.asdl.ASDLParser.ModuleContext;
import uk.co.farowl.asdl.ast.AsdlTree;
import uk.co.farowl.asdl.ast.DefaultErrorHandler;
import uk.co.farowl.asdl.ast.ErrorHandler;
import uk.co.farowl.asdl.code.CodeTree;
import uk.co.farowl.asdl.code.CodeTree.Product;
import uk.co.farowl.asdl.code.Scope;

/**
 * A compiler for ASDL that may be invoked at at the command prompt, and its main program. An
 * instance of <code>Compile</code> holds the options that configure it, and the data structures
 * that are the intermediate products of compilation. The phases of compilation are orchestrated by
 * calls from the the client to methods on the <code>Compile</code> object.
 *
 */
public class Compile {

    // TODO More JUnit tests

    /**
     * Main program. For usage instructions invoke as:
     *
     * <pre>
     * java -cp ... uk.co.farowl.asdl.Compile -h
     *</pre>
     *
     * @param args
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ASDLErrors
     */
    public static void main(String[] args) throws FileNotFoundException, IOException {

        // Parse the command line
        Compile.Options options = new Compile.Options(args);

        // Help if asked (or if there was an error).
        if (options.commandLineError != null) {
            options.giveHelp();
            System.err.println(options.commandLineError);
            System.exit(1);
        }

        else if (options.giveHelp) {
            options.giveHelp();

        } else {
            // Options ok apparently. Let's get on with it.
            try {
                compileMain(options);
            } catch (Exception se) {
                System.err.println("Error: " + se);
            }
        }
    }

    /**
     * Implements the main action of the compiler once it is known there is no error, and we're
     * actually going to compile some source (not just print the usage message).
     *
     * @param options
     * @throws IOException
     * @throws ASDLErrors
     * @throws FileNotFoundException
     */
    private static void compileMain(Compile.Options options)
            throws IOException, ASDLErrors, FileNotFoundException {
        Compile compiler;
        try (InputStream inputStream = new FileInputStream(options.inputName)) {
            ANTLRInputStream input = new ANTLRInputStream(inputStream);
            input.name = options.inputName;
            compiler = new Compile(options, input);
            compiler.buildParseTree();

            // From the parse tree build an AST
            compiler.buildAST();

            // We can play back the ASDL from the AST
            if (options.dumpASDL) {
                System.out.println(compiler.emitASDL());
            }

            // From the AST tree build an tree representing generated code (language neutral)
            compiler.buildCodeTree();

            // Output generated code as specified (possibly to System.out)
            if (options.outputName == null) {
                compiler.emit(System.out);
            } else {
                try (PrintStream outputStream = new PrintStream(options.outputName)) {
                    compiler.emit(outputStream);
                }
            }
        }
    }

    private static class Options {

        private enum TemplateLocation {
            NONE, RESOURCE, FILE
        }

        /** If not null, there was an error and this is the description. */
        String commandLineError;
        /** -h present: give usage/help message. Also set on detection of usage errors. */
        boolean giveHelp;
        /** -g present: give usage/help message. Also set on detection of usage errors. */
        boolean groupFileSpecified;
        /** Name of the input file to read. */
        String inputName;
        /** -a flag present: dump (to console) ASDL equivalent to the input. */
        private boolean dumpASDL;
        /** Name of the output file to write. */
        String outputName;
        /** Define how to look for the templates group file. */
        TemplateLocation templatePath = TemplateLocation.RESOURCE;
        /** Name of the StringTemplate group file to use. */
        String groupfileName = "Java";
        /** Name of the template file to use. */
        String templateName = "main";

        /** Construct from command-line arguments. */
        Options(String[] args) {
            parseCommand(args);
        }

        /**
         * Parse command arguments to local variables.
         *
         * @return true iff the program should run the compiler. (False=give help instead.)
         */
        private void parseCommand(String[] args) {
            int files = 0, argp = 0;
            argloop : while (argp < args.length) {
                String arg = args[argp++];
                if (arg.length() >= 2 && arg.charAt(0) == '-') {
                    // It's a switch
                    switch (arg) {
                        case "-h":
                            giveHelp = true;
                            break;
                        case "-a":
                            dumpASDL = true;
                            break;
                        case "-r":
                            if (argp < args.length) {
                                groupfileName = args[argp++];
                            } else {
                                setError(arg + " missing resource name");
                            }
                            break;
                        case "-g":
                            groupFileSpecified = true;
                            if (argp < args.length) {
                                groupfileName = args[argp++];
                            } else {
                                setError(arg + " missing group file name");
                            }
                            break;
                        case "-t":
                            if (argp < args.length) {
                                templateName = args[argp++];
                            } else {
                                setError(arg + " missing template name");
                            }
                            break;
                        default:
                            setError("Unknown option: " + arg);
                            break argloop;
                    }
                } else {
                    // It's a file
                    switch (files++) {
                        case 0:
                            inputName = arg;
                            break;
                        case 1:
                            outputName = arg;
                            break;
                        default:
                            setError("Spurious file name: " + arg);
                            break argloop;
                    }
                }
            }

            // Constraints implied by options

            if (giveHelp || dumpASDL) {
                // Cancel code generation
                templatePath = TemplateLocation.NONE;
            } else if (groupFileSpecified) {
                // Switch to interpreting group file as file path
                templatePath = TemplateLocation.FILE;
            }

            // Consistency checks

            if (templatePath != TemplateLocation.NONE) {
                // Options imply some code generation to do
                if (inputName == null) {
                    setError("Must specify <infile> when generating code.");
                }
            } else {
                // Options imply we are not generating code
                if (dumpASDL) {
                    if (inputName == null) {
                        setError("Must specify <infile> when dumping ASDL.");
                    }
                } else if (inputName == null) {
                    setError("Cannot specify <infile> when not generating code.");
                }
            }
            // If there was an error, give help unasked.
            giveHelp |= error();
        }

        /** Declare there was an error, but do not overwrite existing error. */
        void setError(String msg) {
            if (!error() && msg != null) {
                commandLineError = msg;
            }
        }

        /** True iff an error has been declared. */
        boolean error() {
            return commandLineError != null;
        }

        void giveHelp() {
            System.out.println("Arguments:");
            System.out.println(" ...  -ah [-g <groupfile> | -r <resource>] [-t <template>]"
                    + "  <infile> <outfile>");
            System.out.println("-a  Output ASDL equivalent to <infile> to stdout");
            System.out.println("-h  Output this help and stop");
            System.out.println("-g  Generate <outfile> using StringTemplate <groupfile>");
            System.out.println("-r  Generate <outfile> using internal StringTemplate <resource>");
            System.out.println("-t  Generate <outfile> using template named <template>");
            System.out.println("By default resource=Java and template=main."
                    + "Options -a and -h cancel code generation.");
        }
    }

    /**
     * Thrown once by the parsing phase if there are any syntax errors. Each error is reported by
     * the parser on <code>System.err</code>.
     */
    public class ASDLErrors extends Exception {

        protected final int errors;
        protected final String kind;

        public ASDLErrors(String kind, int numberOfErrors) {
            this.kind = kind;
            this.errors = numberOfErrors;
        }

        @Override
        public String toString() {
            return String.format("%s errors (%d) in: %s", kind, errors, options.inputName);
        }
    }

    Options options;
    ANTLRInputStream input;
    ModuleContext parseTree;
    AsdlTree.Module ast;
    CodeTree.Module globalModule;
    CodeTree code;

    /** Called whenever there is a semantic error in processing the AST. */
    final ErrorHandler errorHandler = new DefaultErrorHandler();

    /**
     * Create a compiler attached to the given source stream. This source must represent one
     * complete ASDL module. We require an <code>ANTLRInputStream</code> rather than support several
     * different source types (<code>String</code>, <code>InputStream</code>, <code>Reader</code>,
     * etc.).
     *
     * @param options specifying input name etc.
     * @param input representing the source module
     */
    public Compile(Options options, ANTLRInputStream input) {
        this.options = options;
        this.input = input;
    }

    /**
     * Compile the source (actually an ANTLR input stream) into a new parse tree. The parser emits
     * parse errors to <code>System.err</code>, but generally recovers to continue the parse. Errors
     * are counted, and if the count is positive, this method will throw
     * {@link ASDLException.SyntaxErrors}.
     *
     * @throws IOException from reading the input
     * @throws ASDLErrors when syntax errors
     */
    public void buildParseTree() throws IOException, ASDLErrors {

        // Wrap the input in a Lexer
        ASDLLexer lexer = new ASDLLexer(input);

        // Parse the token stream with the generated ASDL parser
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ASDLParser parser = new ASDLParser(tokens);
        parseTree = parser.module();

        // System.out.println(parseTree.toStringTree(parser));
        int errors = parser.getNumberOfSyntaxErrors();
        if (errors > 0) {
            throw new ASDLErrors("Syntax", errors);
        }
    }

    /**
     * Build an AST from the source already parsed by
     * {@link #buildParseTree(ANTLRInputStream, String)}.
     */
    public void buildAST() {
        if (parseTree == null) {
            throw new java.lang.IllegalStateException("No source has been parsed");
        }
        ast = AsdlTree.forModule(parseTree);
        // System.out.println(ast.toString());
    }

    /** Emit reconstructed source from enclosed AST using StringTemplate */
    public String emitASDL() {
        return emitASDL(ast);
    }

    /**
     * Build an AST from the source already parsed by
     * {@link #buildParseTree(ANTLRInputStream, String)}.
     *
     * @throws ASDLErrors
     */
    public void buildCodeTree() throws ASDLErrors {
        if (ast == null) {
            throw new java.lang.IllegalStateException("No AST has been built");
        }

        // Set up the global scope enclosing the module
        globalModule = new CodeTree.Module("", null); // Nameless and no enclosing scope
        Scope<CodeTree.Definition> globalTypes = globalModule.scope;

        // Name the built-in types (without a particular language binding)
        for (String type : asdlTypes) {
            defineGlobal(type);
        }

        // Compile the code tree from the AST
        code = new CodeTree(globalTypes, ast, errorHandler);
        // System.out.println(code.root.toString());

        int errors = errorHandler.getNumberOfErrors();
        if (errors > 0) {
            throw new ASDLErrors("Semantic", errors);
        }
    }

    private static List<String> asdlTypes =
            Arrays.asList("identifier", "string", "bytes", "int", "object", "singleton");

    /** Declare a built-in type as a Definition in {@link #globalModule}. */
    private void defineGlobal(String typeName) {
        CodeTree.Definition def = new Product(typeName, globalModule, 0, 0);
        globalModule.scope.defineOrNull(typeName, def);
    }

    /**
     * Output generated code as specified in {@link #options} (possibly to stdout)
     *
     * @throws FileNotFoundException
     */
    public void emit(PrintStream outputStream) throws FileNotFoundException {
        STGroup stg;
        switch (options.templatePath) {
            case RESOURCE:
                String name = options.groupfileName + ".stg";
                URL url = AsdlTree.class.getResource(name);
                if (url == null) {
                    throw new java.io.FileNotFoundException(name);
                }
                stg = new STGroupFile(url, "UTF-8", '<', '>');
                outputStream.println(emitFromStringTemplate(stg));
                break;
            case FILE:
                stg = new STGroupFile(options.groupfileName);
                outputStream.println(emitFromStringTemplate(stg));
                break;
            case NONE:
                break;
        }
    }

    /**
     * Emit enclosed AST using a StringTemplate group and the template named in the options. When
     * the template is invoked, the symbols <code>command</code> and <code>asdlCodeRoot</code> will
     * have been defined. <code>command</code> is an aggregate object (see StringTemplate.addAggr),
     * with elements <code>tool</code>, <code>file</code>, <code>groupfile</code>,
     * <code>template</code> intended primarily to generate a header describing how the output was
     * generated. <code>asdlCodeRoot</code> is an object of type {@link CodeTree.Module} that gives
     * access to the translated source ASDL.
     */
    public String emitFromStringTemplate(STGroup stg) {
        ST st = stg.getInstanceOf(options.templateName);
        if (st == null) {
            throw new IllegalArgumentException("Template not found: " + options.templateName);
        }
        String toolName = getClass().getSimpleName();
        st.addAggr("command.{tool, file, groupfile, template}", toolName, options.inputName,
                options.groupfileName, options.templateName);
        st.add("asdlCodeRoot", code.root);
        return st.render();
    }

    /** Emit reconstructed source from arbitrary sub-tree using StringTemplate */
    static String emitASDL(AsdlTree node) {
        URL url = AsdlTree.class.getResource("ASDL.stg");
        STGroup stg = new STGroupFile(url, "UTF-8", '<', '>');
        ST st = stg.getInstanceOf("emit");
        st.add("node", node);
        return st.render();
    }
}
