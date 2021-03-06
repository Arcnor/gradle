/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

import static org.gradle.language.java.JavaIntegrationTesting.applyJavaPlugin

class JavaCompilationAgainstApiDependenciesIntegrationTest extends AbstractIntegrationSpec {
   def "api dependencies are visible from all source sets"() {
        given:
        applyJavaPlugin(buildFile)
        buildFile << '''
model {
    components {
        A(JvmLibrarySpec) {
            api {
                dependencies {
                    library "B"
                }
            }
            sources {
                other(JavaSourceSet) {
                    source.srcDir "src/other"
                }
            }
        }
        B(JvmLibrarySpec) {}
    }
}
'''

        file('src/A/java/a/A1.java') << 'package a; class A1 extends b.B {}'
        file('src/other/a/A2.java') << 'package a; class A2 extends b.B {}'
        file('src/B/java/b/B.java') << 'package b; public class B {}'

        expect:
        succeeds 'AJar'
    }
}
