package uk.co.farowl.asdl.gradle;

import java.nio.file.Path;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class ASDLPlugin implements Plugin<Project> {

    public static final String ASDL_CONFIGURATION_NAME = "asdl";

    @Override
    public void apply(Project project) {
        // Create the one default task
        project.getTasks().create("generateDataModel", ASDLTask.class, (task) -> {
            task.setSource("src/main/asdl");
            task.include("**/*.asdl");
            Path outputDirectory =
                    project.getBuildDir().toPath().resolve("generated-src/asdl/main");
            task.setOutputDirectory(outputDirectory);
        });
    }

}
