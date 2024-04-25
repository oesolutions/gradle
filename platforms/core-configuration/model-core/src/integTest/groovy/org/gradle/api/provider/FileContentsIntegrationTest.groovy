/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class FileContentsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            abstract class PrintProp extends DefaultTask {
                @Optional
                @Input
                abstract Property<String> getTextProp()

                @Optional
                @Input
                abstract Property<byte[]> getByteProp()

                @TaskAction
                void go() {
                    if(textProp.isPresent()) {
                        println("prop = \${textProp.get()}")
                    }
                    if(byteProp.isPresent()) {
                        println("prop = \${byteProp.get().join(",")}")
                    }
                }
            }
        """
    }

    def "can get file content as text"() {
        given:
        def testFile = file("test.file")
        testFile << "hello"
        buildFile << """
            task print(type: PrintProp) {
                textProp = providers.fileContents(layout.projectDirectory.file('test.file')).getAsText()
            }
        """

        when:
        succeeds("print")

        then:
        outputContains("prop = hello")
    }

    def "can get file content as bytes"() {
        given:
        def testFile = file("test.file")
        testFile << "ABC"
        buildFile << """
            task print(type: PrintProp) {
                byteProp = providers.fileContents(layout.projectDirectory.file('test.file')).getAsBytes()
            }
        """

        when:
        succeeds("print")

        then:
        outputContains("prop = 65,66,67")
    }

    def "can get file content with string encoding"() {
        given:
        def testFile = file("test.file")
        testFile.withWriter("UTF-8") { it.print("utf8") }
        buildFile << """
            task print(type: PrintProp) {
                textProp = providers.fileContents(layout.projectDirectory.file('test.file')).getAsText("UTF-8")
            }
        """

        when:
        succeeds("print")

        then:
        outputContains("prop = utf8")
    }

    def "can get file content with charset encoding"() {
        given:
        def testFile = file("test.file")
        testFile.withWriter("UTF-16") { it.print("utf16") }
        buildFile << """
            task print(type: PrintProp) {
                textProp = providers.fileContents(layout.projectDirectory.file('test.file')).getAsText(java.nio.charset.StandardCharsets.UTF_16)
            }
        """

        when:
        succeeds("print")

        then:
        outputContains("prop = utf16")
    }
}
