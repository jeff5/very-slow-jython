package uk.co.farowl.asdl.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceTask;
import org.gradle.api.tasks.TaskAction;

import uk.co.farowl.asdl.ASDLCompiler;
import uk.co.farowl.asdl.ASDLCompiler.ASDLErrors;

/**
 * Generate source language files for building data structures (typically an Abstract Syntax Tree)
 * specified in Abstract Syntax Definition Language (ASDL). All the sources in a given ASDL source
 * set are translated using the same template, which is defined in the task
 * {@code generate<i>sourceSet</i>FromASDL}
 */
public class ASDLTask extends SourceTask {

    private ASDLCompiler compiler = new ASDLCompiler();

    @TaskAction
    void generateFromASDL() throws IOException, ASDLErrors {
        System.out.printf("*** ASDL generate into %s\n", getOutputDirectory());
        Path groupFile = getGroupFile();
        System.out.printf("  * using template \"%s\" from group %s\n", getTemplateName(),
                groupFile != null ? groupFile : compiler.getGroupName());
        for (File f : getSource()) {
            System.out.printf("\n ** %s\n", f);
            compiler.compile(f.toPath(), getOutputDirectory());
        }
    }

    @InputFiles
    public Path getSourceRoot() {
        return compiler.getSourceRoot();
    }

    /** @param sourceRoot directory beneath whic to find the ASDL source. */
    public void setSourceRoot(Path sourceRoot) {
        //setSource(sourceRoot);
        compiler.setSourceRoot(sourceRoot);
    }



    @Input
    public String getTemplateName() {
        return compiler.getTemplateName();
    }

    public void setTemplateName(String templateName) {
        compiler.setTemplateName(templateName);
    }

    @InputFile
    @Optional
    public Path getGroupFile() {
        return compiler.getGroupFile();
    }

    /** @param groupFile a StringTemplate group file defining the templates. */
    public void setGroupFile(Path groupFile) {
        compiler.setGroupFile(groupFile);
    }

    /** @return the directory to receive data structure source files. */
    @OutputDirectory
    public Path getOutputDirectory() {
        return compiler.getOutputDirectory();
    }

    /** @param outputDirectory to receive data structure source files. */
    public void setOutputDirectory(Path outputDirectory) {
        compiler.setOutputDirectory(outputDirectory);
    }

}
