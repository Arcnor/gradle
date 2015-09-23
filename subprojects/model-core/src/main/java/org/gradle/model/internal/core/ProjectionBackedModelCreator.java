/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Action;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.util.List;

@ThreadSafe
public class ProjectionBackedModelCreator implements ModelCreator {
    private final ModelPath path;
    private final ModelRuleDescriptor descriptor;
    private final boolean ephemeral;
    private final ModelProjection projection;
    private final List<ModelProjection> projections;
    private final List<? extends ModelInitializer> registrationActions;
    private final List<? extends ModelInitializer> creatorActions;

    public ProjectionBackedModelCreator(
        ModelPath path,
        ModelRuleDescriptor descriptor,
        boolean ephemeral,
        final boolean hidden,
        List<ModelProjection> projections,
        List<? extends ModelInitializer> registrationActions,
        List<? extends ModelInitializer> creatorActions
    ) {
        this.path = path;
        this.descriptor = descriptor;
        this.ephemeral = ephemeral;
        this.registrationActions = ImmutableList.<ModelInitializer>builder()
            .addAll(registrationActions)
            .add(NodeInitializerActions.from(path, descriptor, new Action<MutableModelNode>() {
                @Override
                public void execute(MutableModelNode node) {
                    node.setHidden(hidden);
                }
            }))
            .build();
        this.creatorActions = ImmutableList.copyOf(creatorActions);

        this.projections = Lists.newArrayList(projections);
        this.projection = new ChainingModelProjection(this.projections);
    }

    @Override
    public List<? extends ModelInitializer> getRegistrationActions() {
        return registrationActions;
    }

    @Override
    public List<? extends ModelInitializer> getCreatorActions() {
        return creatorActions;
    }

    public ModelPath getPath() {
        return path;
    }

    public ModelPromise getPromise() {
        return projection;
    }

    public ModelAdapter getAdapter() {
        return projection;
    }

    @Override
    public ModelProjection getProjection() {
        return projection;
    }

    @Override
    public boolean isEphemeral() {
        return ephemeral;
    }

    public ModelRuleDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    public void addProjection(ModelProjection projection) {
        projections.add(projection);
    }
}
