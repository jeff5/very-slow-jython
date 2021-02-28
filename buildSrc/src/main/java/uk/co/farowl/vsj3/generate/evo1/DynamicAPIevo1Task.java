package uk.co.farowl.vsj3.generate.evo1;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * This is a Gradle task to generate classes that implement (some of)
 * the Python abstract object API. Each class is derived by this task
 * from a simple specification like:<pre>
 * # Name the methods to generate here
 * package uk.co.farowl.vsj2dy.evo4
 * class AbstractProxy
 * unary negative
 * binary add
 * binary subtract
 * binary multiply
 * </pre> Each line specifies the code template to use (which generator
 * method to call in this task) and a name for the method, which will be
 * generated using ASM. The generated method body is little more than an
 * invokedynamic instruction of the same signature. This name is also
 * embedded as a call site argument used by the run-time to bootstrap
 * the site.
 * <p>
 * The idea of this is to generate classes with methods we may call in
 * place of the Python Abstract API, opening the way to a first use of
 * {@code invokedynamic} and call sites that embed Python semantics. We
 * hope to prove that calls to these methods may be in-lined and
 * specialised by Java.
 */
public class DynamicAPIevo1Task extends AbstractCompile {

    /** Exception to throw on completion of task (or null). */
    protected Exception exc = null;

    @TaskAction
    void generateDynamicAPI() throws Exception {

        // Reassurance
        // System.out.println("generateDynamicAPI");
        // System.out.printf(" getSource() = %s\n",
        // getSource().getFiles());
        // System.out.printf(" getDestinationDirectory() = %s\n",
        // getDestinationDirectory().getAsFile().get());

        // For each source file, create a class file
        DirectoryProperty dest = getDestinationDirectory();

        for (File src : getSource().getFiles()) {
            generateClass(src, dest);
        }

        // Throw the first exception we found
        if (exc != null) { throw exc; }
    }

    /**
     * Process the unit in one file, making a class file in the
     * destination directory.
     *
     * @param src from which to read the source
     * @param destDir where the class file should be generated
     * @throws IOException source cannot be read or class written
     * @throws ParseError on errors in the source
     */
    private void generateClass(File src, DirectoryProperty destDir)
            throws IOException, ParseError {
        // Parse the source into a unit and write it out
        try (CompilationUnit unit = new CompilationUnit(
                new FileReader(src, Charset.forName("UTF-8")))) {
            unit.generateClass();

            // Locate class relative to the destination directory
            Directory classDir =
                    destDir.dir(unit.getPackagePath()).get();
            classDir.getAsFile().mkdirs();
            File classFile = classDir
                    .file(unit.getClassName() + ".class").getAsFile();

            // More reassurance
            System.out.printf("  generating %s\n", classFile);

            // Write the generated code to the class file
            try (FileOutputStream classFileStream =
                    new FileOutputStream(classFile)) {
                classFileStream.write(unit.classDefinition);
            }
        } catch (IOException | ParseError e) {
            System.err.println(
                    String.format("%s:%s", src, e.getMessage()));
            if (exc == null) { exc = e; }
        }
    }

    /**
     * Holds a class definition as bytes, its package name and its class
     * name (as strings). The purpose is to ensure we have not only the
     * bytes but also the information necessary to name the file they go
     * in.
     */
    static class CompilationUnit implements Opcodes, Closeable {

        protected String packagePath;
        protected String className;
        protected LineNumberReader source;
        protected Line line;
        protected EnumSet<Line.Kind> seen =
                EnumSet.noneOf(Line.Kind.class);
        protected ClassWriter cw;
        protected byte[] classDefinition;
        protected Set<String> names = new HashSet<String>();

        CompilationUnit(Reader r) {
            // Wrap the source for line numbers
            this.source = new LineNumberReader(r);
        }

        /** @return the package name in path form ("a/b/c") */
        String getPackagePath() {
            return packagePath;
        }

        /**
         * Set the package name, accepting path or dotted form ("a/b/c"
         * or "a.b.c").
         *
         * @param path specified package name
         */
        void setPackagePath(String path) {
            if (path.contains("."))
                path = path.replace('.', '/');
            packagePath = path;
        }

        /** @return the class name (simple name) */
        String getClassName() {
            return className;
        }

        void generateClass() throws IOException, ParseError {

            // Wrap the source for line numbers
            // LineNumberReader source = new LineNumberReader(r);
            boolean done = false;
            String name;

            while (!done) {
                switch (nextLine()) {
                    case EOF:
                        assertAfter(Line.Kind.PACKAGE, Line.Kind.CLASS);
                        done = true;
                        endClass();
                        break;
                    case PACKAGE:
                        // Read a package declaration
                        assertNotDuplicate();
                        setPackagePath(line.arg[0]);
                        break;
                    case CLASS:
                        // Begin a class (in the specified package)
                        assertAfter(Line.Kind.PACKAGE);
                        assertNotDuplicate();
                        className = line.arg[0];
                        beginClass();
                        break;
                    case UNARY:
                        // Process method declarations repeatedly
                        assertAfter(Line.Kind.PACKAGE, Line.Kind.CLASS);
                        name = line.arg[0];
                        assertNotDuplicate(name);
                        emitUnaryOperation(name);
                        break;
                    case BINARY:
                        // Process method declarations repeatedly
                        assertAfter(Line.Kind.PACKAGE, Line.Kind.CLASS);
                        name = line.arg[0];
                        assertNotDuplicate(name);
                        emitBinaryOperation(name);
                        break;
                    case ERROR:
                        throw new ParseError(line, "not recognised");
                    default:  // and BLANK
                        break;
                }
            }
        }

        private void beginClass() {
            String name = packagePath + '/' + className;
            cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
                    + ClassWriter.COMPUTE_FRAMES);
            cw.visit(V11, ACC_PUBLIC, name, null,
                    JO_TYPE.getInternalName(), new String[] {});
        }

        private void endClass() {
            cw.visitEnd();
            classDefinition = cw.toByteArray();
        }

        private void emitUnaryOperation(String name) {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC,
                    name, UNARY_TYPE.getDescriptor(), null, null);

            // Body of method
            mv.visitCode();

            // return op(<name>)(v)
            mv.visitVarInsn(ALOAD, 0);
            mv.visitInvokeDynamicInsn(name, UNARY_TYPE.getDescriptor(),
                    BOOTSTRAP_H);
            mv.visitInsn(ARETURN);

            // Stack and frame dimensions computed by the ClassWriter
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        private void emitBinaryOperation(String name) {
            MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC,
                    name, BINARY_TYPE.getDescriptor(), null, null);

            // Body of method
            mv.visitCode();

            // return op(<name>)(v, w)
            mv.visitVarInsn(ALOAD, 0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitInvokeDynamicInsn(name, BINARY_TYPE.getDescriptor(),
                    BOOTSTRAP_H);
            mv.visitInsn(ARETURN);

            // Stack and frame dimensions computed by the ClassWriter
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        // Strings needed to designate significant packages
        private static final String PYTHON_PKG =
                "uk/co/farowl/vsj3/evo1";
        private static final String LANG_PKG = "java/lang";
        private static final String INVOKE_PKG = LANG_PKG + "/invoke";

        // Strings needed to designate significant classes

        /** Type Java {@code Object} */
        private static final Type JO_TYPE =
                Type.getType(classDescr(LANG_PKG, "Object"));

        /** Type Python {@code object} ({@code java.lang.Object}) */
        private static Type PO_TYPE = JO_TYPE;

        private static final Type CALL_SITE_TYPE =
                Type.getType(classDescr(INVOKE_PKG, "CallSite"));
        private static final Type LOOKUP_TYPE = Type.getType(
                classDescr(INVOKE_PKG, "MethodHandles", "Lookup"));
        private static final Type METHOD_TYPE_TYPE =
                Type.getType(classDescr(INVOKE_PKG, "MethodType"));
        private static final Type RT_TYPE =
                Type.getType(classDescr(PYTHON_PKG, "PyRT"));
        private static final Type STRING_TYPE =
                Type.getType(classDescr(LANG_PKG, "String"));

        /** Type of a unary operation */
        private static Type UNARY_TYPE =
                Type.getMethodType(PO_TYPE, PO_TYPE);
        /** Type of a binary operation */
        private static Type BINARY_TYPE =
                Type.getMethodType(PO_TYPE, PO_TYPE, PO_TYPE);
        /** Type of simple bootstrap */
        private static Type DESCR_BOOTSTRAP =
                Type.getMethodType(CALL_SITE_TYPE, LOOKUP_TYPE,
                        STRING_TYPE, METHOD_TYPE_TYPE);

        /** ASM Handle for PyRT bootstrap */
        private static Handle BOOTSTRAP_H = new Handle(
                Opcodes.H_INVOKESTATIC, RT_TYPE.getInternalName(),
                "bootstrap", DESCR_BOOTSTRAP.getDescriptor(), false);

        /**
         * Concatenate package ("a/b/c") and class name ("T", "U")
         * strings to make a JVM internal class name ("La/b/c/T$U;").
         */
        private static String classDescr(String pkg, String... cls) {
            StringBuilder b = new StringBuilder(100);
            b.append('L').append(pkg).append('/').append(cls[0]);
            for (int i = 1; i < cls.length; i++)
                b.append('$').append(cls[i]);
            return b.append(';').toString();
        }

        // Parser support --------------------------------------------

        /**
         * Advance to next (or first) line.
         *
         * @return the {@link DynamicAPITask.Line.Kind} of the new line
         * @throws IOException when reading {@link #source}
         */
        Line.Kind nextLine() throws IOException {
            // Add previously seen kind to seen kinds
            if (line != null) { seen.add(line.kind); }
            // Advance current position
            line = Line.recognise(source);
            return line.kind;
        }

        /** Release resources only needed during compilation. */
        @Override
        public void close() throws IOException {
            if (source != null) { source.close(); source = null; }
        }

        /**
         * The current kind of line may only appear once all of the
         * given kinds of line have been seen.
         *
         * @param kinds to have seen already
         * @throws ParseError if they have not all been seen
         */
        void assertAfter(Line.Kind... kinds) throws ParseError {
            // All of the named kinds must have been seen
            for (Line.Kind k : kinds) {
                if (!seen.contains(k))
                    // We have not seen one of the required types
                    // Customise message if same at current
                    throw new ParseError(line,
                            String.format(
                                    "%s without preceding %s statement",
                                    line.kind, k));
            }
        }

        /**
         * The current kind of line may not appear after any of the
         * given kinds of line have already been seen.
         *
         * @param kinds not to have seen
         * @throws ParseError if any have been
         */
        void assertNotAfter(Line.Kind... kinds) throws ParseError {
            // None of the named kinds may have been seen
            for (Line.Kind k : kinds) {
                if (seen.contains(k))
                    // We have seen one of the disallowed types
                    throw new ParseError(line,
                            String.format(
                                    "%s not allowed after %s statement",
                                    line.kind, k));
            }
        }

        /** The current kind of line may appear only once in a unit. */
        void assertNotDuplicate() throws ParseError {
            // Which is to say, it cannot occur after itself
            if (seen.contains(line.kind))
                throw new ParseError(line, String
                        .format("repeated %s statement", line.kind));
        }

        /** A name may be defined only once in a unit. */
        void assertNotDuplicate(String name) throws ParseError {
            if (names.contains(name))
                throw new ParseError(line, String
                        .format("repeated definition of '%s'", name));
        }
    }

    /**
     * A parser for lines in the specification of the generated code,
     * and the data structure representing one line.
     */
    static class Line {

        // Types of line in the source
        static enum Kind {
            EOF, BLANK, ERROR, PACKAGE, CLASS, UNARY, BINARY
        }

        // Leading space allowed on any line
        private static final String S = "^\\s*";
        // Trailing comment allowed on any line
        private static final String C = "\\s*(#.*)?$";
        // #comment
        private static final Pattern comment = Pattern.compile("^" + C);
        // package name[.name]*
        private static final Pattern packageDecl =
                Pattern.compile(S + "package\\s+(\\w+(\\.\\w+)*)" + C);
        // class name
        private static final Pattern classDecl =
                Pattern.compile(S + "class\\s+(\\w+)" + C);
        // unary name
        private static final Pattern unaryDecl =
                Pattern.compile(S + "unary\\s+(\\w+)" + C);
        // binary name
        private static final Pattern binaryDecl =
                Pattern.compile(S + "binary\\s+(\\w+)" + C);

        final Kind kind;
        final int lineno;
        final String line;
        final String[] arg;

        Line(Kind kind, int lineno, String line, String... args) {
            this.kind = kind;
            this.lineno = lineno;
            this.line = line;
            this.arg = args;
        }

        /**
         * Classify a given line, creating a <b>Line</b> result.
         *
         * @param lineno to embed in the Line
         * @param line to analyse (or <b>null</b> as end marker)
         * @return analysed result
         */
        static Line recognise(int lineno, String line) {
            Matcher m;
            if (line == null)
                return new Line(Kind.EOF, lineno, "");
            else if (line.length() == 0
                    || comment.matcher(line).matches())
                return new Line(Kind.BLANK, lineno, line);
            else if ((m = packageDecl.matcher(line)).matches())
                return new Line(Kind.PACKAGE, lineno, line, m.group(1));
            else if ((m = classDecl.matcher(line)).matches())
                return new Line(Kind.CLASS, lineno, line, m.group(1));
            else if ((m = unaryDecl.matcher(line)).matches())
                return new Line(Kind.UNARY, lineno, line, m.group(1));
            else if ((m = binaryDecl.matcher(line)).matches())
                return new Line(Kind.BINARY, lineno, line, m.group(1));
            return new Line(Kind.ERROR, lineno, line);
        }

        /**
         * Get the next non-comment line (or end of file).
         *
         * @param source of lines
         * @return parse of the line
         */
        static Line recognise_old(LineNumberReader source) {
            Line result = null;
            String line = "";
            while (result == null || result.kind == Kind.BLANK) {
                try {
                    line = source.readLine();
                    if (line == null)
                        result = new Line(Kind.EOF,
                                source.getLineNumber(), "");
                    else
                        result = recognise(source.getLineNumber(),
                                line);
                } catch (IOException e) {
                    // Reading a line failed (line is last read or "")
                    result = new Line(Kind.ERROR,
                            source.getLineNumber(), line,
                            e.getMessage());
                }
            }
            return result;
        }

        /**
         * Get the next non-comment line.
         *
         * @param source of lines
         * @return parse of the line
         * @throws IOException from reading the {@code source}
         */
        static Line recognise(LineNumberReader source)
                throws IOException {
            Line result = null;
            String line = "";
            while (result == null || result.kind == Kind.BLANK) {
                line = source.readLine();
                result = recognise(source.getLineNumber(), line);
            }
            return result;
        }

        @Override
        public String toString() {
            return String.format("%4d %s %s \"%s\"", lineno, kind,
                    Arrays.toString(arg), line);
        }

    }

    static class ParseError extends Exception {

        ParseError(Line line, String msg) {
            super(String.format("line %d: %s.\n    \"%s\"", line.lineno,
                    msg, line.line));

        }
    }
}
