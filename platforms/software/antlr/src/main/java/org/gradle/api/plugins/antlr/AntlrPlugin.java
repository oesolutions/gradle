/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.antlr;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultSourceSet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.antlr.internal.DefaultAntlrSourceDirectorySet;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.file.FilePathUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.List;

import static org.gradle.api.plugins.antlr.internal.AntlrSpec.PACKAGE_ARG;

/**
 * A plugin for adding Antlr support to {@link org.gradle.api.plugins.JavaPlugin java projects}.
 *
 * @see <a href="https://docs.gradle.org/current/userguide/antlr_plugin.html">ANTLR plugin reference</a>
 */
public abstract class AntlrPlugin implements Plugin<Project> {
    public static final String ANTLR_CONFIGURATION_NAME = "antlr";
    private final ObjectFactory objectFactory;

    @Inject
    public AntlrPlugin(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaLibraryPlugin.class);

        // set up a configuration named 'antlr' for the user to specify the antlr libs to use in case
        // they want a specific version etc.
        Configuration antlrConfiguration = ((ProjectInternal) project).getConfigurations().resolvableDependencyScopeLocked(ANTLR_CONFIGURATION_NAME, conf -> {
            conf.defaultDependencies(dependencies -> dependencies.add(project.getDependencies().create("antlr:antlr:2.7.7@jar")));
        });

        JavaPluginHelper.getJavaComponent(project).getMainFeature().getApiConfiguration().extendsFrom(antlrConfiguration);

        // Wire the antlr configuration into all antlr tasks
        project.getTasks().withType(AntlrTask.class).configureEach(antlrTask -> antlrTask.getConventionMapping().map("antlrClasspath", () -> project.getConfigurations().getByName(ANTLR_CONFIGURATION_NAME)));

        project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().all(
            new Action<SourceSet>() {
                @Override
                public void execute(final SourceSet sourceSet) {
                    // for each source set we will:
                    // 1) Add a new 'antlr' virtual directory mapping
                    AntlrSourceDirectorySet antlrSourceSet = createAntlrSourceDirectorySet(((DefaultSourceSet) sourceSet).getDisplayName(), objectFactory);
                    sourceSet.getExtensions().add(AntlrSourceDirectorySet.class, AntlrSourceDirectorySet.NAME, antlrSourceSet);
                    final String srcDir = "src/" + sourceSet.getName() + "/antlr";
                    antlrSourceSet.srcDir(srcDir);
                    sourceSet.getAllSource().source(antlrSourceSet);

                    // 2) create an AntlrTask for this sourceSet following the gradle
                    //    naming conventions via call to sourceSet.getTaskName()
                    final String taskName = sourceSet.getTaskName("generate", "GrammarSource");

                    // 3) Set up the Antlr output directory
                    final String outputDirectoryName = project.getBuildDir() + "/generated-src/antlr/" + sourceSet.getName();
                    final File outputDirectory = new File(outputDirectoryName);

                    // 4) Register a source-generating task, and
                    TaskProvider<AntlrTask> antlrTask = project.getTasks().register(taskName, AntlrTask.class, task -> {
                        task.setDescription("Processes the " + sourceSet.getName() + " Antlr grammars.");
                        // 4.1) set up convention mapping for default sources (allows user to not have to specify)
                        task.setSource(antlrSourceSet);
                        task.setOutputDirectory(outputDirectory);
                    });

                    // 5) Add that task's outputs to the Java source set
                    sourceSet.getJava().srcDir(antlrTask.map(task -> {
                        String relativeOutputDirectory = project.relativePath(task.getOutputDirectory());
                        return project.file(deriveGeneratedSourceRootDirectory(relativeOutputDirectory, task.getArguments()));
                    }));
                }
            });
    }

    /**
     * Derives the root source directory from the configuration of the Antlr task.
     *
     * If the package name has been added to the arguments, it is likely that the task output directory has also been adjusted to include the package structure.
     * If so, we can derive the source directory by removing the relative package path from the output directory.  Otherwise, we assume the output directory is
     * the root of the generated sources.
     *
     * This logic can be removed once we make it an error to set the package name via the arguments and require the use of the 'packageName' property instead.
     */
    private static String deriveGeneratedSourceRootDirectory(String outputDirectoryPath, List<String> arguments) {
        // If the package argument is present, remove the package from the path if it has been added.
        if (arguments.contains(PACKAGE_ARG)) {
            int packageIndex = arguments.indexOf(PACKAGE_ARG);
            if (packageIndex + 1 < arguments.size()) {
                String packageRelativePath = arguments.get(packageIndex + 1).replace('.', '/');
                return FilePathUtil.maybeRemoveTrailingSegments(outputDirectoryPath, packageRelativePath);
            }
        }
        // Otherwise, we assume the output directory is the root of the generated sources.
        return outputDirectoryPath;
    }

    private static AntlrSourceDirectorySet createAntlrSourceDirectorySet(String parentDisplayName, ObjectFactory objectFactory) {
        String name = parentDisplayName + ".antlr";
        String displayName = parentDisplayName + " Antlr source";
        AntlrSourceDirectorySet antlrSourceSet = objectFactory.newInstance(DefaultAntlrSourceDirectorySet.class, objectFactory.sourceDirectorySet(name, displayName));
        antlrSourceSet.getFilter().include("**/*.g");
        antlrSourceSet.getFilter().include("**/*.g4");
        return antlrSourceSet;
    }
}
