/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.problems

import com.google.common.collect.Iterables
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.GroovyBuildScriptLanguage
import org.gradle.operations.problems.ProblemUsageProgressDetails

import static org.gradle.api.problems.fixtures.ReportingScript.getProblemReportingScript
import static org.gradle.util.internal.TextUtil.escapeString

class ProblemsApiBuildOperationIntegrationTest extends AbstractIntegrationSpec {
    def buildOperations = new BuildOperationsFixture(executer, testDirectoryProvider)

    def withReportProblemTask(@GroovyBuildScriptLanguage String taskActionMethodBody) {
        buildFile getProblemReportingScript(taskActionMethodBody)
    }

    def "can emit a problem with minimal configuration"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {}
        """

        when:
        run("reportProblem")

        then:
        def problem = Iterables.getOnlyElement(filteredProblemDetails(buildOperations))
        with(problem) {
            this.with(definition) {
                name == 'type'
                displayName == 'label'
                this.with(group) {
                    displayName == 'group label'
                    name == 'generic'
                    parent == null
                }
                documentationLink == null
            }
            severity == Severity.WARNING.name()
            contextualLabel == null
            solutions == []
            details == null
            originLocations.empty
            contextualLocations.size() == 1
            this.with(contextualLocations[0].fileLocation) {
                path == this.buildFile.absolutePath
                line == ProblemsServiceIntegrationTest.PROBLEM_LOCATION_LINE
                column == null
                length == null
            }
            failure == null
        }
    }

    def "can emit a problem with stack location"() {
        given:
        withReportProblemTask """
            ${problemIdScript()}
            problems.getReporter().report(problemId) {
                it.stackLocation()
            }
        """

        when:
        run('reportProblem')

        then:
        def problem = Iterables.getOnlyElement(filteredProblemDetails(buildOperations))
        with(problem) {
            this.with(definition) {
                name == 'type'
                displayName == 'label'
                this.with(group) {
                    displayName == 'group label'
                    name == 'generic'
                    parent == null
                }
                documentationLink == null
            }
            severity == Severity.WARNING.name()
            contextualLabel == null
            solutions == []
            details == null
            originLocations.size() == 1
            this.with(originLocations[0]) {
                this.with(fileLocation) {
                    path == this.buildFile.absolutePath
                    line == ProblemsServiceIntegrationTest.PROBLEM_LOCATION_LINE
                    column == null
                    length == null
                }
                stackTrace.find() { it.className == 'ProblemReportingTask' && it.methodName == 'run' }
            }
            contextualLocations.empty
            failure == null
        }
    }

    def "can emit a problem with all fields"() {
        given:
        def location0 = file('src/main/java/SourceFile0.java').absolutePath
        def location1 = file('src/main/java/SourceFile1.java').absolutePath
        def location2 = file('src/main/java/SourceFile2.java').absolutePath
        def location3 = file('src/main/java/SourceFile3.java').absolutePath
        def location4 = file('src/main/java/SourceFile4.java').absolutePath

        withReportProblemTask """
            ${ProblemGroup.name} problemGroupParent = ${ProblemGroup.name}.create("parent", "parent group label");
            ${ProblemGroup.name} problemGroup = ${ProblemGroup.name}.create("problem group", "problem group label", problemGroupParent);
            ${ProblemId.name} problemId = ${ProblemId.name}.create("type", "label", problemGroup)
            problems.getReporter().report(problemId) {
                it.contextualLabel("contextual label")
                  .documentedAt("https://example.org/doc")
                  .fileLocation("${escapeString(location0)}")
                  .lineInFileLocation("${escapeString(location1)}", 25)
                  .lineInFileLocation("${escapeString(location2)}", 35, 4)
                  .lineInFileLocation("${escapeString(location3)}", 45, 7, 10)
                  .offsetInFileLocation("${escapeString(location4)}", 55, 20)
                  .stackLocation()
                  .details("problem details")
                  .solution("solution 1")
                  .solution("solution 2")
                  .severity(Severity.ERROR)
                  .withException(new IllegalArgumentException("problem exception"))
            }
        """

        when:
        run('reportProblem')

        then:
        def problem = Iterables.getOnlyElement(filteredProblemDetails(buildOperations))
        with(problem) {
            this.with(definition) {
                name == 'type'
                displayName == 'label'
                this.with(group) {
                    displayName == 'problem group label'
                    name == 'problem group'
                    this.with(parent) {
                        displayName == 'parent group label'
                        name == 'parent'
                        parent == null
                    }
                }
                this.with(documentationLink) {
                    url == 'https://example.org/doc'
                }
            }
            severity == Severity.ERROR.name()
            contextualLabel == 'contextual label'
            solutions == ['solution 1', 'solution 2']
            details == 'problem details'
            originLocations.size() == 6
            this.with(originLocations[0]) {
                path == location0
                !containsKey('line')
                displayName == "file '${location0}'"
            }
            this.with(originLocations[1]) {
                path == location1
                line == 25
                column == null
                length == null
                displayName == "file '${location1}:25'"
            }
            this.with(originLocations[2]) {
                path == location2
                line == 35
                column == 4
                length == null
                displayName == "file '${location2}:35:4'"
            }
            this.with(originLocations[3]) {
                path == location3
                line == 45
                column == 7
                length == 10
                displayName == "file '${location3}:45:7:10'"
            }
            this.with(originLocations[4]) {
                path == location4
                offset == 55
                length == 20
                displayName == "offset in file '${location4}:55:20'"
            }
            contextualLocations.empty
            this.with(failure) {
                message == 'problem exception'
                stackTrace.startsWith('java.lang.IllegalArgumentException: problem exception')
            }
        }
    }

    def "obtain problems from included builds"() {
        given:
        settingsFile << """
            includeBuild("included")
        """
        buildTestFixture
            .withBuildInSubDir()
            .multiProjectBuild("included", ['sub1', 'sub2']) {
                file('sub1').file('build.gradle') << getProblemReportingScript("""
                    ${problemIdScript()}
                    problems.getReporter().report(problemId) {}
                """)
            }

        when:
        run(":included:sub1:reportProblem")

        then:
        def test = this
        def problem = Iterables.getOnlyElement(filteredProblemDetails(buildOperations))
        with(problem) {
            this.with(definition) {
                name == 'type'
                displayName == 'label'
                this.with(group) {
                    displayName == 'group label'
                    name == 'generic'
                    parent == null
                }
                documentationLink == null
            }
            severity == Severity.WARNING.name()
            contextualLabel == null
            solutions == []
            details == null
            originLocations.empty
            contextualLocations.size() == 1
            this.with(contextualLocations[0]) {
                this.with(fileLocation) {
                    path == test.file('included/sub1/build.gradle').absolutePath
                    line == ProblemsServiceIntegrationTest.PROBLEM_LOCATION_LINE
                    column == null
                    length == null
                }
            }
            failure == null
        }
    }

    static String problemIdScript() {
        """${ProblemGroup.name} problemGroup = ${ProblemGroup.name}.create("generic", "group label");
           ${ProblemId.name} problemId = ${ProblemId.name}.create("type", "label", problemGroup)"""
    }

    static Collection<Map<String, ?>> filteredProblemDetails(BuildOperationsFixture buildOperations) {
        List<Map<String, ?>> details = buildOperations.progress(ProblemUsageProgressDetails).details
        details
            .findAll { it.definition.name != 'executing-gradle-on-jvm-versions-and-lower'}
    }
}
