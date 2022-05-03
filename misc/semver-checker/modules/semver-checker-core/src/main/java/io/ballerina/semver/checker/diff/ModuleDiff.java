/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.semver.checker.diff;

import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.projects.Module;
import io.ballerina.semver.checker.comparator.FunctionComparator;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Represents all the source code changes within a single Ballerina module.
 *
 * @since 2201.2.0
 */
public class ModuleDiff extends DiffImpl {

    private final Module newModule;
    private final Module oldModule;

    private ModuleDiff(Module newModule, Module oldModule) {
        this.newModule = newModule;
        this.oldModule = oldModule;
    }

    public Optional<Module> getNewModule() {
        return Optional.ofNullable(newModule);
    }

    public Optional<Module> getOldModule() {
        return Optional.ofNullable(oldModule);
    }

    @Override
    public DiffType getType() {
        return super.getType();
    }

    public List<FunctionDiff> getFunctionDiffs() {
        return childDiffs.stream().filter(iDiff -> iDiff instanceof FunctionDiff)
                .map(iDiff -> (FunctionDiff) iDiff)
                .collect(Collectors.toUnmodifiableList());
    }

    public static class Builder implements DiffBuilder {

        private final ModuleDiff moduleDiff;

        public Builder(Module newModule, Module oldModule) {
            moduleDiff = new ModuleDiff(newModule, oldModule);
        }

        @Override
        public Optional<ModuleDiff> build() {
            if (!moduleDiff.getChildDiffs().isEmpty()) {
                moduleDiff.computeCompatibilityLevel();
                moduleDiff.setType(DiffType.MODIFIED);
                return Optional.of(moduleDiff);
            }

            return Optional.empty();
        }

        @Override
        public DiffBuilder withType(DiffType diffType) {
            moduleDiff.setType(diffType);
            return this;
        }

        @Override
        public DiffBuilder withCompatibilityLevel(CompatibilityLevel compatibilityLevel) {
            moduleDiff.setCompatibilityLevel(compatibilityLevel);
            return this;
        }

        public void withFunctionAdded(FunctionDefinitionNode function) {
            FunctionDiff.Builder funcDiffBuilder = new FunctionDiff.Builder(function, null);
            funcDiffBuilder.withCompatibilityLevel(CompatibilityLevel.MINOR)
                    .build()
                    .ifPresent(moduleDiff.childDiffs::add);
        }

        public void withFunctionRemoved(FunctionDefinitionNode function) {
            FunctionDiff.Builder funcDiffBuilder = new FunctionDiff.Builder(null, function);
            funcDiffBuilder.withCompatibilityLevel(CompatibilityLevel.MAJOR)
                    .build()
                    .ifPresent(moduleDiff.childDiffs::add);
        }

        public void withFunctionChanged(FunctionDefinitionNode newFunction, FunctionDefinitionNode oldFunction) {
            new FunctionComparator(newFunction, oldFunction).computeDiff().ifPresent(moduleDiff.childDiffs::add);
        }
    }
}
