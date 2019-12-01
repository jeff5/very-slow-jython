package uk.co.farowl.asdl;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.stringtemplate.v4.AutoIndentWriter;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import org.stringtemplate.v4.STWriter;

import uk.co.farowl.asdl.ASDLParser.ModuleContext;
import uk.co.farowl.asdl.ast.AsdlTree;
import uk.co.farowl.asdl.ast.DefaultErrorHandler;
import uk.co.farowl.asdl.ast.ErrorHandler;
import uk.co.farowl.asdl.code.CodeTree;
import uk.co.farowl.asdl.code.Scope;

/**
 * Compiler for ASDL to a data model in Java, or another language specified by an
 * externally-provided StringTemplate group file. Interface methods are provided to support the ASDL
 * plug-in for Gradle.
 */
public class ASDLCompiler {

    /** Generate code using the internal group file by default. */
    public static final String DEFAULT_GROUP_NAME = "Java";
    /** Start at the main tempate by default. */
    public static final String DEFAULT_TEMPLATE_NAME = "main";
    /** Default output filename pattern is {@code "%s.java"}. */
    public static final String DEFAULT_OUTPUT_NAME_FORMAT = "%s.java";

    // 0 for no output.
    private int debug = 1;

    private Path projectRoot = Paths.get("");
    private Path sourceRoot = projectRoot;
    private Path outputDirectory = sourceRoot;
    private Path groupFile = null;
    private String groupName = DEFAULT_GROUP_NAME;
    private String templateName = DEFAULT_TEMPLATE_NAME;
    private String outputNameFormat = DEFAULT_OUTPUT_NAME_FORMAT;
    private Map<String, Object> params = new HashMap<>();

    /** Specify the project root, relative to which file names will be represented in comments. */
    public void setProjectRoot(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    /** Specify the root relative to which the package name of the source is interpreted. */
    public void setSourceRoot(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    /** The root relative to which the package name of the source is interpreted. */
    public Path getSourceRoot() {
        return sourceRoot;
    }

    /** Specify the root relative to which the output is generated using the package name. */
    public void setOutputDirectory(Path outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    /** The root relative to which the output is generated using the package name. */
    public Path getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Specify the file name to give the output within package-directory as a format string (default
     * "%s.java").
     */
    public void setOutputNameFormat(String outputNameFormat) {
        this.outputNameFormat = outputNameFormat;
    }

    /** The root relative to which the output is generated using the package name. */
    public String getOutputNameFormat() {
        return outputNameFormat;
    }

    /** Specify the built-in template set to use when generating output (default "Java"). */
    public void setGroupName(String groupName) {
        this.groupName = groupName;
        groupFile = null;
    }

    /** The name of the built-in template group used when generating output (default "Java"). */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Specify an external StringTemplate group file containing the template group to use when
     * generating output, instead of any internal template group (see {@link #getGroupName()} to be
     * ignored.
     */
    public void setGroupFile(Path groupFile) {
        this.groupFile = groupFile;
        groupName = null;
    }

    /** An external StringTemplate group file containing the template group. */
    public Path getGroupFile() {
        return groupFile;
    }

    /** Specify the template to begin at (rendering the ASDL "module"). */
    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    /** The template this compiler will begin at (rendering the ASDL "module"). */
    public String getTemplateName() {
        return templateName;
    }

    /**
     * Parameters (of any kind), accessible to the templates as {@code params}. Particular template
     * group files may give these a particular meaning (or ignore ones they don't support). The Java
     * template supports the following:
     * <table>
     * <tr>
     * <td>useEnum</td>
     * <td>Boolean</td>
     * <td>use enum to represent simple "sum" types</td>
     * </tr>
     * <tr>
     * <td>enumIsNode</td>
     * <td>Boolean</td>
     * <td>generated enums implement the Node class</td>
     * </tr>
     * <tr>
     * <td>base</td>
     * <td>String</td>
     * <td>if defined, generated nodes all extend this class</td>
     * </tr>
     * </table>
     *
     * @param params to copy into the stored parameters
     */
    public void setParams(Map<String, Object> params) {
        this.params.putAll(params);
    }

    /**
     * Parameters accessible to the templates as {@code params}.
     *
     * @return table being used to store these parameters
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * Render a particular ASDL source using the currently-specified configuration.
     *
     * @param asdlSource the ASDL source file to render.
     * @param outputDirectory location relative to which generated code is written.
     * @throws IOException
     * @throws ASDLErrors
     */
    public void compile(Path asdlSource, Path outputDirectory) throws IOException, ASDLErrors {

        // Manipulate the source file name to deduce the package names and output file location.
        List<String> packagePath = new LinkedList<>();
        Path outputFile = getOutputFile(packagePath, asdlSource, outputDirectory);

        if (debug >= 1) {
            System.out.printf("  * ASDLCompiler in:     %s\n", rel(asdlSource));
            System.out.printf("  * ASDLCompiler out:    %s\n", rel(outputFile));
            System.out.printf("  * ASDLCompiler params: %s\n", params);
        }

        // From the ASDL source, build a parse tree.
        CharStream input = CharStreams.fromPath(asdlSource);
        ModuleContext parseTree = buildParseTree(input);

        // From the parse tree build an AST of the ASDL source
        AsdlTree.Module ast = AsdlTree.forModule(parseTree);

        // We can play back the ASDL from the AST
        if (debug >= 2) {
            System.out.println(renderASDL(ast));
        }

        // From the AST (of the ASDL) build a language neutral representation of the data model
        CodeTree code = buildCodeTree(ast, asdlSource);

        // Output generated code as specified
        emit(outputFile, code.root, asdlSource, packagePath);

    }

    /**
     * Produce an output file path and a list of package names based on the source file name and the
     * source root directory. Suppose {@code asdlSource} is the path {@code "a/b/c/d/name.asdl"},
     * that the source root (see {@link #getSourceRoot()} is {@code "a/b"}, {@code outputDirectory}
     * is {@code "x/y/z"}, and the destination file format (see {@link #getOutputNameFormat()}) is
     * the default "%s.java". Then the method treats {@code "name.asdl}" as belonging to a package
     * {@code "c/d"}. It returns {@code "x/y/z/c/d/name.java"} for the output path and the packages
     * {@code "c"} and {@code "d"} are added to {@code packagePath}.
     * <p>
     * If {@code asdlSource} is not under the declared source root, it is {@code "p/q/r/name.asdl"}
     * for example, then no package names can be deduced and the return is
     * {@code "x/y/z/name.java"}.
     *
     * @param packagePath initially empty list to which package names are added as described.
     * @param asdlSource source file location ({@code sourceRoot/c/d/name.asdl})
     * @param outputDirectory on which to base returned file path
     * @return corresponding output path ({@code outputDirectory/c/d/name.java})
     */
    private Path getOutputFile(List<String> packagePath, Path asdlSource, Path outputDirectory) {
        Path relativeOutputFile;
        Path sourceRoot = getSourceRoot();
        if (debug >= 2) {
            System.out.printf("  * sourceRoot:         %s\n", rel(sourceRoot));
            System.out.printf("  * asdlSource:         %s\n", rel(asdlSource));
        }

        if (asdlSource.startsWith(sourceRoot)) {
            // The source file is below the source root. Compile to package in output directory.
            Path relativeSource = sourceRoot.relativize(asdlSource);
            int pkgCount = relativeSource.getNameCount() - 1;
            if (pkgCount >= 0) {
                String sourceFileName = relativeSource.getName(pkgCount).toString();
                Path packagePart = relativeSource.subpath(0, pkgCount);
                for (int i = 0; i < pkgCount; i++) {
                    packagePath.add(relativeSource.getName(i).toString());
                }
                String outputFileName = makeOutputFileName(sourceFileName);
                relativeOutputFile = packagePart.resolve(outputFileName);
                if (debug >= 2) {
                    System.out.printf("  * relativeSource:     %s\n", relativeSource);
                    System.out.printf("  * sourceFileName:     %s\n", sourceFileName);
                    System.out.printf("  * packagePart:        %s\n", packagePart);
                    System.out.printf("  * relativeOutputFile: %s\n", relativeOutputFile);
                }
            } else {
                // asdlSource == sourceRoot ?
                throw new IllegalArgumentException("source file name is root directory");
            }

        } else {
            // The source file is not below the declared source root. Compile to output directory.
            Path fileOnly = asdlSource.getFileName();
            if (fileOnly != null) {
                String sourceFileName = fileOnly.toString();
                String outputFileName = makeOutputFileName(sourceFileName);
                relativeOutputFile = Paths.get(outputFileName);
                if (debug >= 2) {
                    System.out.printf("  * fileOnly:           %s\n", fileOnly);
                    System.out.printf("  * sourceFileName:     %s\n", sourceFileName);
                    System.out.printf("  * relativeOutputFile: %s\n", relativeOutputFile);
                }
            } else {
                // asdlSource == "" ?
                throw new IllegalArgumentException("source file name is empty");
            }
        }

        return outputDirectory.resolve(relativeOutputFile);
    }

    /** Remove ".asdl" (if any) and add ".java" or whatever {@link #getOutputNameFormat()} says. */
    private String makeOutputFileName(String sourceFileName) {
        String baseFileName;
        if (sourceFileName.endsWith(".asdl")) {
            baseFileName = sourceFileName.substring(0, sourceFileName.length() - 5);
        } else {
            baseFileName = sourceFileName;
        }
        return String.format(getOutputNameFormat(), baseFileName);
    }

    /**
     * Thrown once by the parsing phase if there are any syntax errors. Each error is reported by
     * the parser on {@code System.err}.
     */
    public class ASDLErrors extends Exception {

        protected final int errors;
        protected final String kind;
        protected final String sourceName;

        public ASDLErrors(String sourceName, String kind, int numberOfErrors) {
            this.sourceName = sourceName;
            this.kind = kind;
            this.errors = numberOfErrors;
        }

        @Override
        public String toString() {
            return String.format("%s errors (%d) in: %s", kind, errors, sourceName);
        }
    }

    /** Called whenever there is a semantic error in processing the AST. */
    final ErrorHandler errorHandler = new DefaultErrorHandler();

    /**
     * Compile the source (actually an ANTLR stream) into a new parse tree. The parser emits parse
     * errors to {@code System.err}, but generally recovers to continue the parse. Errors are
     * counted, and if the count is positive, this method will throw
     * {@link ASDLException.SyntaxErrors}.
     *
     * @throws ASDLErrors when syntax errors
     */
    public ModuleContext buildParseTree(CharStream input) throws ASDLErrors {

        // Wrap the input in a Lexer
        ASDLLexer lexer = new ASDLLexer(input);

        // Parse the token stream with the generated ASDL parser
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ASDLParser parser = new ASDLParser(tokens);
        ModuleContext parseTree = parser.module();

        // System.out.println(parseTree.toStringTree(parser));
        int errors = parser.getNumberOfSyntaxErrors();
        if (errors > 0) {
            throw new ASDLErrors(parser.getSourceName(), "Syntax", errors);
        }

        return parseTree;
    }

    /**
     * Build an AST from the source already parsed by {@link #buildParseTree(CharStream)}.
     *
     * @param ast of the ASDL source
     * @param asdlSource the ASDL source path (for error messages)
     * @return a translation of the ASDL source AST to a form that supports code generation
     * @throws ASDLErrors if semantic errors are discovered
     */
    private CodeTree buildCodeTree(AsdlTree.Module ast, Path asdlSource) throws ASDLErrors {
        // Set up the global scope enclosing the module: nameless with no enclosing scope
        CodeTree.Module globalModule = new CodeTree.Module("", null);
        Scope<CodeTree.Definition> globalTypes = globalModule.scope;

        // Name the built-in types (without a particular language binding)
        for (String typeName : asdlTypes) {
            // Treat them as Product types with zero members and attributes
            CodeTree.Definition def = new CodeTree.Product(typeName, globalModule, 0, 0);
            assert def.isSimple();
            globalModule.scope.defineOrNull(typeName, def);
        }

        // Compile the code tree from the AST
        CodeTree code = new CodeTree(globalTypes, ast, errorHandler);
        if (debug >= 3) {
            System.out.println(code.root.toString());
        }

        int errors = errorHandler.getNumberOfErrors();
        if (errors > 0) {
            throw new ASDLErrors(asdlSource.toString(), "Semantic", errors);
        }

        return code;
    }

    /** Types pre-defined for the ASDL. (Unfortunately, this seems to evolve with use.) */
    private static final List<String> asdlTypes =
            Arrays.asList("identifier", "int", "string", "object", "constant");

    /**
     * Emit the data model using a StringTemplate group and the template named in the configuration.
     * When the template is invoked, the symbols {@code asdlCodeRoot} and {@code command} will have
     * been defined. {@code command} is an aggregate object (see {@code StringTemplate.addAggr}),
     * with elements {@code tool}, {@code file}, {@code groupfile} and {@code template} intended
     * primarily to generate a header describing how the output was generated. {@code asdlCodeRoot}
     * is an object of type {@link CodeTree.Module} that gives access to the translated source ASDL.
     */
    private void emit(Path outputFile, CodeTree.Module asdlCodeRoot, Path asdlSource,
            List<String> asdlPackagePath) throws IOException {

        // Get a string template group from the configured group name resource or file.
        STGroup stg;
        if (groupName != null) {
            // A named resource holds the group file
            String name = groupName + ".stg";
            URL url = AsdlTree.class.getResource(name);
            if (url == null) {
                throw new IllegalArgumentException("'" + name + "' is not a built-in group file.");
            }
            stg = new STGroupFile(url, "UTF-8", '<', '>');
        } else if (groupFile != null) {
            // The group file is external
            stg = new STGroupFile(groupFile.toString());
        } else {
            // A group file/name was provided through setGroupFile/Name but was null.
            throw new IllegalArgumentException("Group file or name is null");
        }

        // Create a StringTemplate from the named template in the group
        ST st = stg.getInstanceOf(templateName);
        if (st == null) {
            throw new IllegalArgumentException("Template not found: " + templateName);
        }

        // Define the tree to drive the template as a template variable.
        st.add("asdlCodeRoot", asdlCodeRoot);

        // Supply the package as the list of enclosing directory names down from the source root.
        st.add("asdlPackagePath", asdlPackagePath);

        // Supply the user-defined parameters.
        st.add("params", params);

        // Add the metadata as a template variable.
        String toolName = getClass().getSimpleName();
        String group = groupFile == null ? groupName : rel(groupFile);
        st.addAggr("command.{tool, file, groupfile, template}", toolName, rel(asdlSource), group,
                templateName);

        // Render the tree onto the output file
        Files.createDirectories(outputFile.getParent());
        try (Writer out = Files.newBufferedWriter(outputFile, Charset.forName("UTF-8"))) {
            STWriter wr = new AutoIndentWriter(out);
            wr.setLineWidth(70);
            st.write(wr, Locale.getDefault());
        }
    }

    /**
     * Represent a file path as a string, relative to the project root if possible. This is mostly
     * motivated by the problem that, when generating Java output, backslashes are treated as
     * introducing escapes (or backslash-u at least). The mangled result is nice for human
     * consumption, but not for use as a file specification.
     *
     * @param path to represent
     * @return string form of path
     */
    private String rel(Path path) {
        Path relpath = projectRoot.relativize(path);
        if (relpath.getNameCount() >= path.getNameCount()) {
            // That seemed to make it worse: fall back on a URI (absolute)
            return path.toUri().toString();
        } else {
            // Relative path is shorter: swap pesky backslashes.
            String p = relpath.toString();
            if (File.separatorChar == '\\') {
                p = p.replace(File.separatorChar, '/');
            }
            return p;
        }
    }

    /** Return string of reconstructed ASDL from arbitrary sub-tree. */
    private static String renderASDL(AsdlTree node) {
        URL url = AsdlTree.class.getResource("ASDL.stg");
        STGroup stg = new STGroupFile(url, "UTF-8", '<', '>');
        ST st = stg.getInstanceOf("emit");
        st.add("node", node);
        return st.render();
    }

}
