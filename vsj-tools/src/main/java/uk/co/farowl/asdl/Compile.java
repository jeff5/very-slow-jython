package uk.co.farowl.asdl;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import uk.co.farowl.asdl.ASDLCompiler.ASDLErrors;

/**
 * A compiler for ASDL that may be invoked at at the command prompt, and its main program. This is a
 * wrapper around {@code ASDLCompile} that sets options from the command line.
 */
public class Compile {

    /**
     * Main program. For usage instructions invoke as:
     *
     * <pre>
     * java -cp ... uk.co.farowl.asdl.Compile -h
     *</pre>
     *
     * @param args
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

        // Create an instance of the compiler to use, then configure it from the options.
        ASDLCompiler compiler = new ASDLCompiler();

        if (options.groupFile != null) {
            compiler.setGroupFile(Paths.get(options.groupFile));
        } else if (options.groupName != null) {
            compiler.setGroupName(options.groupName);
        }

        if (options.templateName != null) {
            compiler.setTemplateName(options.templateName);
        }

        compiler.setSourceRoot(Paths.get(options.sourceDir));
        compiler.setOutputNameFormat(options.outputNameFormat);

        // Compile the ASDL source
        Path asdlSource = Paths.get(options.inputName);
        Path outputDirectory = Paths.get(options.outputDir);
        compiler.compile(asdlSource, outputDirectory);
    }

    private static class Options {

        /** If not null, there was an error and this is the description. */
        String commandLineError;
        /** -h present: give usage/help message. Also set on detection of usage errors. */
        boolean giveHelp;
        /** Name of the input file to read. */
        String inputName;
        /** Name of the output directory to write. */
        String sourceDir = "";
        /** Name of the output directory to write. */
        String outputDir = "";
        /** Name of the template file to use (or null). */
        String outputNameFormat = "%s.java";
        /** Name of the StringTemplate group file to use (or null). */
        String groupFile;
        /** Name of the StringTemplate built-in group to use (or null). */
        String groupName;
        /** Name of the template file to use (or null). */
        String templateName;
        /** Arguments to parse. */
        final String args[];
        /** Index to unprocessed argument. */
        int argp = 0;

        /** Construct from command-line arguments. */
        Options(String[] args) {
            this.args = args;
            parseCommand();
        }

        /** Parse command arguments to local variables. */
        private void parseCommand() {
            while (!error() && argp < args.length) {
                String arg = args[argp++];
                if (arg.length() >= 2 && arg.charAt(0) == '-') {
                    // It's a switch
                    switch (arg) {
                        case "-h":
                            giveHelp = true;
                            break;
                        case "-s":
                            sourceDir = argValue("source root");
                            break;
                        case "-d":
                            outputDir = argValue("output directory");
                            break;
                        case "-f":
                            outputNameFormat = argValue("file name format");
                            break;
                        case "-G":
                            groupName = argValue("group name");
                            break;
                        case "-g":
                            groupFile = argValue("group file");
                            break;
                        case "-t":
                            templateName = argValue("template name");
                            break;
                        default:
                            setError("Unknown option: " + arg);
                            break;
                    }
                } else {
                    // It's a file
                    if (inputName == null) {
                        inputName = arg;
                    } else {
                        setError("Spurious file name: " + arg);

                    }
                }
            }

            // Consistency checks
            if (!giveHelp) {
                if (groupFile != null && groupName != null) {
                    // Options imply some code generation to do
                    setError("Cannot specify both -g and -G.");
                } else if (inputName == null) {
                    setError("Must specify <infile> when generating code.");
                }
            }

            // If there was an error, give help unasked.
            giveHelp |= error();
        }

        /** Process value associated with argument. */
        String argValue(String purpose) {
            if (argp < args.length) {
                return args[argp++];
            } else {
                setError(args[argp] + " missing " + purpose);
                return null;
            }
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
            System.out.println(" ...  [-h] [-d <outdir>] [-f <format>]"
                    + " [-g <groupfile> | -G <groupname>] [-t <template>] <infile>");
            System.out.println("-h  Output this help and stop");
            System.out.println("-s  Source root (relative path to infile determines package)");
            System.out.println("-d  Destination directory");
            System.out.println("-f  Output name format (default \"%s.java\")");
            System.out.println("-g  Use StringTemplate <groupfile>");
            System.out.println("-G  Use internal StringTemplate <groupname> (default \"Java\")");
            System.out.println("-t  Template to use within group (default \"main\")");
            System.out.println("Option -h cancels code generation.");
        }
    }

}
