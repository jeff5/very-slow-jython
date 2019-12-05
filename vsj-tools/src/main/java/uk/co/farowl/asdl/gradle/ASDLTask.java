package uk.co.farowl.asdl.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
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
        compiler.setProjectRoot(getProject().getProjectDir().toPath());
        compiler.setSourceRoot(sourceRoot.getDir().toPath());
        for (File f : getSource()) {
            compiler.compile(f.toPath(), getOutputDirectory());
        }
    }

    private ConfigurableFileTree sourceRoot;

    @Override
    public void setSource(Object source) {
        // Ensure we have a single tree as the source collection
        sourceRoot = getProject().fileTree(source);
        super.setSource((Object)sourceRoot);
    }

    /**
     * An ASDL task works off a single source tree, so it is not possible to add source to this type
     * of task.
     *
     * @param sources
     * @return (throws)
     */
    @Override
    @Deprecated
    public SourceTask source(Object... sources) {
        throw new UnsupportedOperationException(
                "It is not possible to add source to this type of task");
    }

    @Input
    public String getTemplateName() {
        return compiler.getTemplateName();
    }

    public void setTemplateName(String templateName) {
        compiler.setTemplateName(templateName);
    }

    @Input
    @Optional
    public String getGroupName() {
        return compiler.getGroupName();
    }

    public void setGroupName(String groupName) {
        compiler.setGroupName(groupName);
    }

    @InputFile
    @Optional
    public Path getGroupFile() {
        return compiler.getGroupFile();
    }

    /** @param groupFile a StringTemplate group file defining the templates. */
    public void setGroupFile(Object groupFile) {
        compiler.setGroupFile(getProject().file(groupFile).toPath());
    }

    @Input
    @Optional
    public Map<String, Object> getParams() {
        return compiler.getParams();
    }

    /** @param params a map received by the template as the "params" argument. */
    public void setParams(Map<String, Object> params) {
        compiler.setParams(params);
    }

    /** @return the directory to receive data structure source files. */
    @OutputDirectory
    public Path getOutputDirectory() {
        return compiler.getOutputDirectory();
    }

    /** @param outputDirectory to receive data structure source files. */
    public void setOutputDirectory(Object outputDirectory) {
        compiler.setOutputDirectory(getProject().file(outputDirectory).toPath());
    }

}
