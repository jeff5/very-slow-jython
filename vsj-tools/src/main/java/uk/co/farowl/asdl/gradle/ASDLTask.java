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
        super.setSource((Object) sourceRoot);
    }

    /**
     * An ASDL task works off a single source tree, so it is not possible to add source to this type
     * of task.
     *
     * @param sources ignored
     * @return never (throws)
     * @throws UnsupportedOperationException always
     */
    @Override
    @Deprecated
    public SourceTask source(Object... sources) throws UnsupportedOperationException {
        throw new UnsupportedOperationException(
                "It is not possible to add source to this type of task");
    }

    /**
     * The template the compiler will begin at (rendering the ASDL "module").
     *
     * @return template to begin at
     */
    @Input
    public String getTemplateName() {
        return compiler.getTemplateName();
    }

    /**
     * Specify the template to begin at (rendering the ASDL "module").
     *
     * @param templateName template to begin at
     */
    public void setTemplateName(String templateName) {
        compiler.setTemplateName(templateName);
    }

    /**
     * The name of the built-in template group used when generating output (default "Java").
     *
     * @return built-in template set
     */
    @Input
    @Optional
    public String getGroupName() {
        return compiler.getGroupName();
    }

    /**
     * Specify the built-in template set to use when generating output (default "Java").
     *
     * @param groupName built-in template set
     */
    public void setGroupName(String groupName) {
        compiler.setGroupName(groupName);
    }

    /**
     * An external StringTemplate group file containing the template group.
     *
     * @return external StringTemplate group file
     */
    @InputFile
    @Optional
    public Path getGroupFile() {
        return compiler.getGroupFile();
    }

    /**
     * Specify an external StringTemplate group file containing the template group to use when
     * generating output, instead of any internal template group (see {@link #getGroupName()} to be
     * ignored.
     *
     * @param groupFile project-relative StringTemplate group file
     */
    public void setGroupFile(Object groupFile) {
        compiler.setGroupFile(getProject().file(groupFile).toPath());
    }

    /**
     * Parameters accessible to the templates as {@code params}.
     *
     * @return table being used to store these parameters
     */
    @Input
    @Optional
    public Map<String, Object> getParams() {
        return compiler.getParams();
    }

    /**
     * Parameters (of any kind), accessible to the templates as {@code params}. Particular template
     * group files may give these a particular meaning (or ignore ones they don't support). The Java
     * template supports the following:
     * <table>
     * <caption>Parameters supported in the Java template</caption>
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
        compiler.setParams(params);
    }

    /**
     * The root relative to which the output is generated using the package name.
     *
     * @return root relative to which the output is generated
     */
    @OutputDirectory
    public Path getOutputDirectory() {
        return compiler.getOutputDirectory();
    }

    /**
     * Specify the root relative to which the output is generated using the package name.
     *
     * @param outputDirectory relative to which the output is generated (project-relative path)
     */
    public void setOutputDirectory(Object outputDirectory) {
        compiler.setOutputDirectory(getProject().file(outputDirectory).toPath());
    }

}
