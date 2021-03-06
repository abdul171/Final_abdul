/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality;

import groovy.lang.GroovyObjectSupport;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.util.Collection;

/**
 * Base Code Quality Extension.
 */
public abstract class CodeQualityExtension extends GroovyObjectSupport {

    private String toolVersion;
    private Collection<SourceSet> sourceSets;
    private boolean ignoreFailures;
    private File reportsDir;

    /**
     * The version of the code quality tool to be used.
     */
    public String getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    /**
     * The source sets to be analyzed as part of the <tt>check</tt> and <tt>build</tt> tasks.
     */
    public Collection<SourceSet> getSourceSets() {
        return sourceSets;
    }

    public void setSourceSets(Collection<SourceSet> sourceSets) {
        this.sourceSets = sourceSets;
    }

    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Whether or not to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    public boolean getIgnoreFailures() {
        return isIgnoreFailures();
    }

    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    /**
     * The directory where reports will be generated.
     */
    public File getReportsDir() {
        return reportsDir;
    }

    public void setReportsDir(File reportsDir) {
        this.reportsDir = reportsDir;
    }
}
