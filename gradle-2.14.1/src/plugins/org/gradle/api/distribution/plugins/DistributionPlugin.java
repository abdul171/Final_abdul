/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.distribution.plugins;

import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.*;
import org.gradle.api.distribution.Distribution;
import org.gradle.api.distribution.internal.DefaultDistributionContainer;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Tar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.DeprecationLogger;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * <p>A {@link Plugin} to package project as a distribution.</p>
 */
@Incubating
public class DistributionPlugin extends GroovyObjectSupport implements Plugin<ProjectInternal> {
    /**
     * Name of the main distribution
     */
    public static final String MAIN_DISTRIBUTION_NAME = "main";
    public static final String TASK_INSTALL_NAME = "installDist";

    private static final String DISTRIBUTION_GROUP = "distribution";
    private static final String TASK_DIST_ZIP_NAME = "distZip";
    private static final String TASK_DIST_TAR_NAME = "distTar";
    private static final String TASK_ASSEMBLE_NAME = "assembleDist";

    private final Instantiator instantiator;
    private final FileOperations fileOperations;

    @Inject
    public DistributionPlugin(Instantiator instantiator, FileOperations fileOperations) {
        this.instantiator = instantiator;
        this.fileOperations = fileOperations;
    }

    @Override
    public void apply(final ProjectInternal project) {
        project.getPluginManager().apply(BasePlugin.class);
        DefaultDistributionContainer distributions = project.getExtensions().create("distributions", DefaultDistributionContainer.class, Distribution.class, instantiator, fileOperations);

        // TODO - refactor this action out so it can be unit tested
        distributions.all(new Action<Distribution>() {
            @Override
            public void execute(final Distribution dist) {
                ((IConventionAware) dist).getConventionMapping().map("baseName", new Callable<Object>() {
                    @Override
                    public Object call() throws Exception {
                        return dist.getName().equals(MAIN_DISTRIBUTION_NAME) ? project.getName() : String.format("%s-%s", project.getName(), dist.getName());
                    }
                });

                dist.getContents().from("src/" + dist.getName() + "/dist");
                Task zipTask = DeprecationLogger.whileDisabled(new Factory<Task>() {
                    @Override
                    public Task create() {
                        return addZipTask(project, dist);
                    }
                });
                Task tarTask = DeprecationLogger.whileDisabled(new Factory<Task>() {
                    @Override
                    public Task create() {
                        return addTarTask(project, dist);
                    }
                });
                addAssembleTask(project, dist, zipTask, tarTask);
                addInstallTask(project, dist);
            }
        });
        distributions.create(MAIN_DISTRIBUTION_NAME);
    }

    public Task addZipTask(Project project, Distribution distribution) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("addZipTask");
        String taskName = TASK_DIST_ZIP_NAME;
        if (!MAIN_DISTRIBUTION_NAME.equals(distribution.getName())) {
            taskName = distribution.getName() + "DistZip";
        }

        return configureArchiveTask(project, taskName, distribution, Zip.class);
    }

    public Task addTarTask(Project project, Distribution distribution) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("addTarTask");
        String taskName = TASK_DIST_TAR_NAME;
        if (!MAIN_DISTRIBUTION_NAME.equals(distribution.getName())) {
            taskName = distribution.getName() + "DistTar";
        }

        return configureArchiveTask(project, taskName, distribution, Tar.class);
    }

    private <T extends AbstractArchiveTask> Task configureArchiveTask(Project project, String taskName, final Distribution distribution, Class<T> type) {
        final T archiveTask = project.getTasks().create(taskName, type);
        archiveTask.setDescription("Bundles the project as a distribution.");
        archiveTask.setGroup(DISTRIBUTION_GROUP);
        archiveTask.getConventionMapping().map("baseName", new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                if (distribution.getBaseName() == null || distribution.getBaseName().equals("")) {
                    throw new GradleException("Distribution baseName must not be null or empty! Check your configuration of the distribution plugin.");
                }
                return distribution.getBaseName();
            }
        });

        Callable<String> baseDir = new Callable<String>() {
            @Override
            public String call() throws Exception {
                // For backwards compatibility, we need to simulate the exact behaviour of the previously uses Groovy minus operator
                String toBeRenamedIfExists = "." + archiveTask.getExtension();
                return archiveTask.getArchiveName().replaceFirst(toBeRenamedIfExists, "");
            }
        };

        CopySpec childSpec = project.copySpec();
        childSpec.into(baseDir);
        childSpec.with(distribution.getContents());
        archiveTask.with(childSpec);
        ArchivePublishArtifact archiveArtifact = new ArchivePublishArtifact(archiveTask);
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(archiveArtifact);
        return archiveTask;
    }

    private void addInstallTask(final Project project, final Distribution distribution) {
        String taskName = TASK_INSTALL_NAME;
        if (!MAIN_DISTRIBUTION_NAME.equals(distribution.getName())) {
            taskName = "install" + StringGroovyMethods.capitalize(distribution.getName()) + "Dist";
        }

        Sync installTask = project.getTasks().create(taskName, Sync.class);
        installTask.setDescription("Installs the project as a distribution as-is.");
        installTask.setGroup(DISTRIBUTION_GROUP);
        installTask.with(distribution.getContents());
        installTask.into(new Callable<File>() {
            @Override
            public File call() throws Exception {
                return project.file("" + project.getBuildDir() + "/install/" + distribution.getBaseName());
            }
        });
    }

    private void addAssembleTask(Project project, Distribution distribution, Task... tasks) {
        String taskName = TASK_ASSEMBLE_NAME;
        if (!MAIN_DISTRIBUTION_NAME.equals(distribution.getName())) {
            taskName = "assemble" + StringGroovyMethods.capitalize(distribution.getName()) + "Dist";
        }

        Task assembleTask = project.getTasks().create(taskName);
        assembleTask.setDescription("Assembles the " + distribution.getName() + " distributions");
        assembleTask.setGroup(DISTRIBUTION_GROUP);
        assembleTask.dependsOn((Object[]) tasks);
    }
}
