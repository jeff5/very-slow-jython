package uk.co.farowl.asdl.gradle;

import java.nio.file.Path;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * A Gradle plug-in that compiles Abstract Syntax Description Language, a language used to describe
 * data structures such as the AST of (in particular) Python. Example of use: <pre>
 *  plugins {
 *      id 'antlr'
 *      id 'uk.co.farowl.asdl.compiler'
 *  }
 *
 *  dependencies {
 *      antlr "org.antlr:antlr4:4.7" // use ANTLR version 4
 *      testCompile 'junit:junit:4.13.1'
 *  }
 *
 *  // Import the plug-in task so we can use it as a task type.
 *  import uk.co.farowl.asdl.gradle.ASDLTask
 *
 *
 *  def generatedAsdl = "$buildDir/generated-src/asdl"
 *
 *  sourceSets {
 *      main {
 *          java {
 *              srcDir "$generatedAsdl/main"
 *          }
 *      }
 *      test {
 *          java {
 *              srcDir "$generatedAsdl/test"
 *          }
 *      }
 *  }
 *
 *  generateGrammarSource {
 *      arguments += ["-visitor"]
 *  }
 *
 *  // Generate TreePython.java from TreePython.asdl via specialised group file.
 *  generateDataModel {
 *      params = ["useEnum" : true, "enumIsNode" : false, "base" : "ExecNode"]
 *  }
 *
 *  // Manually insert the dependency of compilation on generation
 *  compileJava {
 *      dependsOn generateDataModel
 *  }
 *</pre>
 */
public class ASDLPlugin implements Plugin<Project> {

    /** Conventional name of the source configuration (under "src/main", "src/test", etc.). */
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
