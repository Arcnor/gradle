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
package org.gradle.jvm.internal.apigen.abi;

import com.google.common.collect.Lists;

import java.util.List;

public class ClassSig {
    private final int version;
    private final int access;
    private final String name;
    private final String signature;
    private final String superName;
    private final String[] interfaces;
    private final List<AnnotationSig> annotations = Lists.newArrayList();

    public ClassSig(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.version = version;
        this.access = access;
        this.name = name;
        this.signature = signature;
        this.superName = superName;
        this.interfaces = interfaces;
    }

    public AnnotationSig addAnnotation(String desc, boolean visible) {
        AnnotationSig sig = new AnnotationSig(desc, visible);
        annotations.add(sig);
        return sig;
    }

    public int getAccess() {
        return access;
    }

    public List<AnnotationSig> getAnnotations() {
        return annotations;
    }

    public String[] getInterfaces() {
        return interfaces;
    }

    public String getName() {
        return name;
    }

    public String getSignature() {
        return signature;
    }

    public String getSuperName() {
        return superName;
    }

    public int getVersion() {
        return version;
    }
}
