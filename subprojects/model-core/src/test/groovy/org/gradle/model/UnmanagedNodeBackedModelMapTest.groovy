/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectFactory
import org.gradle.api.Namer
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.api.internal.DefaultPolymorphicDomainObjectContainer
import org.gradle.api.internal.rules.RuleAwareNamedDomainObjectFactoryRegistry
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.reflect.ObjectInstantiationException
import org.gradle.model.collection.internal.ChildNodeInitializerStrategyAccessors
import org.gradle.model.collection.internal.ModelMapModelProjection
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor
import org.gradle.model.internal.fixture.ModelRegistryHelper
import org.gradle.model.internal.manage.instance.ManagedInstance
import org.gradle.model.internal.registry.UnboundModelRulesException
import org.gradle.model.internal.type.ModelType
import org.gradle.model.internal.type.ModelTypes
import spock.lang.Specification

import static org.gradle.util.TextUtil.normaliseLineSeparators

class UnmanagedNodeBackedModelMapTest extends Specification {

    def type = new ModelType<NamedThing>() {}

    static class NamedThing implements Named {
        String name
        String other

        NamedThing(String name) {
            this.name = name
        }
    }

    static class SpecialNamedThing extends NamedThing implements Special {
        SpecialNamedThing(String name) {
            super(name)
        }
    }

    static interface Special {}

    @Managed
    static interface NamedThingInterface extends Named {
        String getOther()
        void setOther(String string)
    }

    @Managed
    static interface SpecialNamedThingInterface extends NamedThingInterface, Special {
        String getSpecial()
        void setSpecial(String string)
    }

    static class Container<T> extends DefaultPolymorphicDomainObjectContainer<T> implements RuleAwareNamedDomainObjectFactoryRegistry<T>, NamedEntityInstantiator<T> {
        Container(Class<T> type, Instantiator instantiator, Namer<? super T> namer) {
            super(type, instantiator, namer)
        }

        @Override
        <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory, ModelRuleDescriptor descriptor) {
            registerFactory(type, factory)
        }
    }

    def containerPath = ModelPath.path("container")
    def modelMapType = new ModelType<ModelMap<NamedThing>>() {}
    def registry = new ModelRegistryHelper()
    def itemType = ModelType.of(NamedThing)

    def setup() {
        registry.register(
            ModelRegistrations.bridgedInstance(
                ModelReference.of("container", new ModelType<NamedEntityInstantiator<NamedThing>>() {}),
                { name, type -> DirectInstantiator.instantiate(type, name) } as NamedEntityInstantiator
            )
                .descriptor("container")
                .withProjection(ModelMapModelProjection.unmanaged(
                    itemType,
                    ChildNodeInitializerStrategyAccessors.of(NodeBackedModelMap.createUsingParentNode(itemType)))
                )
                .build()
        )
    }

    void mutate(@DelegatesTo(ModelMap) Closure<? super ModelMap<NamedThing>> action) {
        def mutator = new AbstractModelActionWithView<ModelMap<NamedThing>>(
            ModelReference.of(
                containerPath,
                ModelTypes.modelMap(NamedThing)
            ),
            new SimpleModelRuleDescriptor("foo")) {
                @Override
                protected void execute(MutableModelNode modelNode, ModelMap<NamedThing> view, List<ModelView<?>> inputs) {
                    new ClosureBackedAction<? super ModelMap<NamedThing>>(action).execute(view)
                }
        }

        registry.configure(ModelActionRole.Mutate, mutator)
    }

    void realize() {
        registry.realizeNode(containerPath)
    }

    void selfClose() {
        registry.atState(containerPath, ModelNode.State.SelfClosed)
    }

    def "can define an item with name"() {
        when:
        mutate { create("foo") }
        realize()

        then:
        realizeChild("foo").name == "foo"
    }

    private NamedThing realizeChild(String name) {
        registry.realize(containerPath.child(name), ModelType.of(NamedThing))
    }

    def "does not eagerly create item"() {
        when:
        mutate {
            create("foo")
            create("bar")
        }
        selfClose()

        then:
        registry.state("container.foo") == ModelNode.State.Registered

        when:
        realize()

        then:
        registry.state("container.foo") == ModelNode.State.GraphClosed
    }

    def "can define item with custom type"() {
        when:
        mutate { create("foo", SpecialNamedThing) }
        realize()

        then:
        realizeChild("foo") instanceof SpecialNamedThing
    }

    def "can define item using filtered collection"() {
        when:
        mutate {
            withType(SpecialNamedThing).create("foo")
            withType(NamedThing).create("bar")
        }
        realize()

        then:
        realizeChild("foo") instanceof SpecialNamedThing
        realizeChild("bar") instanceof NamedThing
    }

    def "fails when using filtered collection to define item of type that is not assignable to collection item type"() {
        when:
        mutate {
            withType(String).create("foo")
        }
        realize()

        then:
        ModelRuleExecutionException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.message == "Cannot create an item of type java.lang.String as this is not a subtype of $NamedThing.name."
    }

    def "can register config rules for item"() {
        when:
        mutate {
            create("foo") {
                other = "changed"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "changed"
    }

    def "can register config rule and type for item"() {
        when:
        mutate {
            create("foo", SpecialNamedThing) {
                other = "changed"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "changed"
    }

    def "can query collection size"() {
        when:
        mutate {
            assert size() == 0
            assert it.isEmpty()

            create("a")
            create("b")

            assert size() == 2
            assert !isEmpty()
        }

        then:
        realizeAsModelMap().size() == 2
    }

    private ModelMap<NamedThing> realizeAsModelMap() {
        registry.realize(containerPath, modelMapType)
    }

    def "can query filtered collection size"() {
        when:
        mutate {
            create("a")
            create("b", SpecialNamedThing)

            assert withType(SpecialNamedThing).size() == 1
            assert withType(Special).size() == 1
            assert withType(NamedThing).size() == 2
            assert withType(String).size() == 0

            assert !withType(SpecialNamedThing).isEmpty()
            assert withType(String).isEmpty()
        }

        then:
        realizeAsModelMap().withType(SpecialNamedThing).size() == 1
    }

    def "can query collection membership"() {
        when:
        mutate {
            assert !containsKey("a")
            assert !containsKey(12)

            create("a")
            create("b")

            assert it.containsKey("a")
        }

        then:
        realizeAsModelMap().containsKey("a")
    }

    def "can query filtered collection membership"() {
        when:
        mutate {
            assert !withType(NamedThing).containsKey("a")
            assert !withType(Integer).containsKey(12)

            create("a")
            create("b", SpecialNamedThing)

            assert withType(Object).containsKey("a")
            assert withType(NamedThing).containsKey("a")
            assert !withType(SpecialNamedThing).containsKey("a")
            assert !withType(Special).containsKey("a")
            assert !withType(String).containsKey("a")

            assert withType(Object).containsKey("b")
            assert withType(NamedThing).containsKey("b")
            assert withType(SpecialNamedThing).containsKey("b")
            assert withType(Special).containsKey("b")
            assert !withType(String).containsKey("b")
        }

        then:
        realizeAsModelMap().withType(SpecialNamedThing).containsKey("b")
    }

    def "can query collection keys"() {
        when:
        mutate {
            assert keySet().isEmpty()

            create("a")
            create("b")

            assert keySet() as List == ["a", "b"]
        }

        then:
        realizeAsModelMap().keySet() as List == ["a", "b"]
    }

    def "can access values"() {
        when:
        mutate {
            create("a") { other = "first" }
            create("b") { other = "second" }
        }

        then:
        realizeAsModelMap().values()*.other as Set == ["first", "second"] as Set
    }

    def "can query filtered collection keys"() {
        when:
        mutate {
            assert withType(NamedThing).keySet().isEmpty()
            assert withType(String).keySet().isEmpty()

            create("b", SpecialNamedThing)
            create("a")

            assert withType(NamedThing).keySet() as List == ["a", "b"]
            assert withType(SpecialNamedThing).keySet() as List == ["b"]
            assert withType(Special).keySet() as List == ["b"]
            assert withType(String).keySet().isEmpty()
        }

        then:
        realizeAsModelMap().withType(Special).keySet() as List == ["b"]
    }

    def "can register mutate rule for item with name"() {
        when:
        mutate {
            named("foo") {
                assert other == "original"
                other = "changed"
            }
            create("foo") {
                other = "original"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "changed"
    }

    def "can register mutate rule for item with name using filtered container"() {
        when:
        mutate {
            withType(Object).named("foo") {
                other += " Object"
            }
            withType(Special).named("foo") {
                other += " Special"
            }
            withType(SpecialNamedThing).named("foo") {
                other += " SpecialNamedThing"
            }
            create("foo", SpecialNamedThing) {
                other = "types:"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "types: Object Special SpecialNamedThing"
    }

    def "fails when named item does not have view with appropriate type"() {
        when:
        mutate {
            withType(String).named("foo") {
            }
            create("foo")
        }
        realize()

        then:
        InvalidModelRuleException e = thrown()
        e.cause instanceof ModelRuleBindingException
        e.cause.message.startsWith("Model reference to element 'container.foo' with type java.lang.String is invalid due to incompatible types.")
    }

    static class SetOtherToName extends RuleSource {
        @Mutate
        void set(NamedThing thing) {
            thing.other = thing.name
        }
    }

    /**
     * This test documents the current behaviour, not necessarily the desired.
     *
     * Ideally, we'd get a failure here indicating that container item 'foo' is not String & NamedThing
     */
    def "rules targeting item of mismatched type are allowed"() {
        when:
        mutate {
            withType(String).named("foo", SetOtherToName)
            create("foo")
        }
        realize()

        then:
        realizeChild("foo").other == "foo"
    }

    def "can register mutate rule for all items using filtered container"() {
        when:
        mutate {
            withType(Named).all {
                other += " Named"
            }
            withType(String).all {
                other += " String"
            }
            withType(NamedThing).all {
                other += " NamedThing"
            }
            withType(Special).all {
                other += " Special"
            }
            withType(SpecialNamedThing).all {
                other += " SpecialNamedThing"
            }
            create("foo") {
                other = "types:"
            }
            create("bar", SpecialNamedThing) {
                other = "types:"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "types: Named NamedThing"
        realizeChild("bar").other == "types: Named NamedThing Special SpecialNamedThing"
    }

    def "can register mutate rule for all items"() {
        when:
        mutate {
            all {
                assert other == "original"
                other = "changed"
            }
            create("foo") {
                other = "original"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "changed"
    }

    def "can register mutate rule for all items with specific type"() {
        when:
        mutate {
            withType(Named) {
                other += " Named"
            }
            withType(String) {
                other += " String"
            }
            withType(Special) {
                other += " Special"
            }
            withType(SpecialNamedThing) {
                other += " SpecialNamedThing"
            }
            create("foo") {
                other = "foo:"
            }
            create("bar", SpecialNamedThing) {
                other = "bar:"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "foo: Named"
        realizeChild("bar").other == "bar: Named Special SpecialNamedThing"
    }

    def "can register defaults rule for all items"() {
        when:
        mutate {
            all {
                other += " all{}"
            }
            create("foo") {
                other += " create()"
            }
            beforeEach {
                other = "beforeEach{}"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "beforeEach{} create() all{}"
    }

    def "can register defaults rule for all items with type"() {
        when:
        mutate {
            beforeEach(Named) {
                other = "Named"
            }
            beforeEach(String) {
                other += " String"
            }
            beforeEach(Special) {
                other += " Special"
            }
            beforeEach(SpecialNamedThing) {
                other += " SpecialNamedThing"
            }
            create("foo") {
                other += " create(foo)"
            }
            create("bar", SpecialNamedThing) {
                other += " create(bar)"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "Named create(foo)"
        realizeChild("bar").other == "Named Special SpecialNamedThing create(bar)"
    }

    def "can register finalize rule for all items"() {
        when:
        mutate {
            all {
                other += " all{}"
            }
            afterEach {
                other += " afterEach{}"
            }
            create("foo") {
                other = "create()"
            }
        }
        realize()

        then:
        realizeChild("foo").other == "create() all{} afterEach{}"
    }

    def "provides groovy DSL"() {
        when:
        mutate {
            foo {
                assert other == "original"
                other = "changed"
            }
            foo(NamedThing) {
                other = "original"
            }
            bar(SpecialNamedThing)
        }
        realize()

        then:
        realizeChild("foo").other == "changed"
        realizeChild("bar") instanceof SpecialNamedThing
    }

    class MutableValue {
        String value
    }

    class Bean {
        String name
        String value
    }

    class SpecialBean extends Bean {
        String other
    }

    def "sensible error is thrown when trying to apply a class that does not extend RuleSource as a scoped rule"() {
        def mmType = ModelTypes.modelMap(MutableValue)

        registry
            .modelMap("values", MutableValue) { it.registerFactory(MutableValue) { new MutableValue() } }
            .mutate {
            it.descriptor("mutating elements").path "values" type mmType action { c ->
                c.create("element")
                c.named("element", Object)
            }
        }

        when:
        registry.realize(ModelPath.path("values"), ModelType.UNTYPED)

        then:
        ModelRuleExecutionException e = thrown()
        e.cause.class == InvalidModelRuleDeclarationException
        e.cause.message == "Type java.lang.Object is not a valid model rule source: rule source classes must directly extend org.gradle.model.RuleSource"
    }

    static class ElementRules extends RuleSource {
        @Mutate
        void connectElementToInput(Bean element, String input) {
            element.value = input
        }
    }

    def "inputs of a rule from an inner source are not realised if the rule is not required"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)
        def events = []
        registry
            .register("input", "input") { events << "input created" }
            .modelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
            .mutate {
            it.path "beans" type mmType action { c ->
                events << "collection mutated"
                c.create("element") { events << "$it.name created" }
                c.named("element", ElementRules)
            }
        }

        when:
        registry.atState(ModelPath.path("beans"), ModelNode.State.SelfClosed)

        then:
        events == ["collection mutated"]

        when:
        registry.atState(ModelPath.path("beans"), ModelNode.State.GraphClosed)

        then:
        events == ["collection mutated", "element created", "input created"]
    }

    def "model rule with by-path dependency on non task related collection element's child that does exist passes validation"() {
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .registerInstance("foo", new Bean())
            .mutate {
            it.path("foo").type(Bean).action("beans.element.mutable", ModelType.of(MutableValue)) { Bean subject, MutableValue input ->
                subject.value = input.value
            }
        }
        .modelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
            .mutate {
            it.path "beans" type mmType action { c ->
                c.create("element")
            }
        }
        .mutate {
            it.path "beans.element" node {
                it.addLink(registry.instanceRegistration("beans.element.mutable", new MutableValue(value: "bar")))
            }
        }

        when:
        registry.bindAllReferences()

        then:
        noExceptionThrown()
    }

    static class ByTypeSubjectBoundToScopeChildRule extends RuleSource {
        @Mutate
        void mutateScopeChild(MutableValue value) {
            value.value = "foo"
        }
    }

    def "model rule with by-type dependency on non task related collection element's child that does exist passes validation"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .modelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
            .mutate {
            it.path "beans" type mmType action { c ->
                c.create("element")
                c.named("element", ByTypeSubjectBoundToScopeChildRule)
            }
        }
        .mutate {
            it.path "beans.element" descriptor "element child" node {
                it.addLink(registry.instanceRegistration("beans.element.mutable", new MutableValue()))
            }
        }

        when:
        registry.bindAllReferences()

        then:
        noExceptionThrown()
    }

    def "adding an unbound scoped rule for an element that is never created results in an error upon validation if the scope parent has been self closed"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .modelMap("beans", Bean) { it.registerFactory(Bean) { new Bean(name: it) } }
            .mutate {
            it.path "beans" type mmType action { c ->
                c.named("element", ElementRules)
            }
        }

        when:
        registry.atState(ModelPath.path("beans"), ModelNode.State.SelfClosed)
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        normaliseLineSeparators(e.message).contains '''
  UnmanagedNodeBackedModelMapTest.ElementRules#connectElementToInput
    subject:
      - <no path> UnmanagedNodeBackedModelMapTest.Bean (parameter 1) [*]
          scope: beans.element
    inputs:
      - <no path> String (parameter 2) [*]
'''
    }

    static class SetOther extends RuleSource {
        @Mutate
        void set(SpecialBean bean, String other) {
            bean.other = other
            bean.value = "changed"
        }
    }

    def "can add rule source to all items of type"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)
        registry
            .modelMap("beans", Bean) {
            it.registerFactory(Bean) { new Bean(name: it) }
            it.registerFactory(SpecialBean) { new SpecialBean(name: it) }
        }
        .registerInstance("s", "other")
            .mutate {
            it.path("beans").type(mmType).action { c ->
                c.create("b1", Bean)
                c.create("b2", Bean)
                c.create("sb1", SpecialBean)
                c.create("sb2", SpecialBean)
                c.withType(SpecialBean, SetOther)
            }
        }

        expect:
        registry.node("s").state == ModelNode.State.Registered

        when:
        registry.atState("beans", ModelNode.State.SelfClosed)

        then:
        registry.node("s").state == ModelNode.State.Registered
        registry.get("beans.b1", Bean).value != "changed"
        registry.node("s").state == ModelNode.State.Registered

        when:
        def sb2 = registry.get("beans.sb2", SpecialBean)

        then:
        sb2.other == "other"
        registry.node("s").state == ModelNode.State.GraphClosed

        when:
        def sb1 = registry.get("beans.sb1", SpecialBean)

        then:
        sb1.other == "other"
    }

    static class SetProp extends RuleSource {
        @Mutate
        void m(@Path("foo") Bean bean) {}
    }

    def "when targeting by type, paths are interpreted relative to item"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .modelMap("beans", Bean) {
            it.registerFactory(Bean) { new Bean(name: it) }
            it.registerFactory(SpecialBean) { new SpecialBean(name: it) }
        }
        .registerInstance("s", "other")
            .mutate {
            it.path("beans").type(mmType).action { c ->
                c.create("b1", Bean)
                c.create("sb1", SpecialBean)
                c.withType(SpecialBean, SetProp)
            }
        }

        when:
        registry.atState("beans", ModelNode.State.SelfClosed)
        registry.get("beans.sb1", SpecialBean)
        registry.bindAllReferences()

        then:
        UnboundModelRulesException e = thrown()
        e.rules.size() == 1
        e.rules.first().mutableInputs.first().path == "beans.sb1.foo"
    }

    static class SetValue extends RuleSource {
        @Mutate
        void set(Bean bean) {
            bean.value = "changed"
        }
    }

    def "when targeting by type, can have rule use more general type than target"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .modelMap("beans", Bean) {
            it.registerFactory(Bean) { new Bean(name: it) }
            it.registerFactory(SpecialBean) { new SpecialBean(name: it) }
        }

        .registerInstance("s", "other")
            .mutate {
            it.path("beans").type(mmType).action { c ->
                c.create("sb1", SpecialBean)
                c.withType(SpecialBean, SetValue)
            }
        }

        when:
        registry.atState("beans", ModelNode.State.SelfClosed)

        then:
        registry.get("beans.sb1", SpecialBean).value == "changed"
    }

    def "when targeting by type, can have rule use more specific type than target"() {
        given:
        def mmType = ModelTypes.modelMap(Bean)

        registry
            .modelMap("beans", Bean) {
            it.registerFactory(Bean) { new Bean(name: it) }
            it.registerFactory(SpecialBean) { new SpecialBean(name: it) }
        }

        .registerInstance("s", "other")
            .mutate {
            it.path("beans").type(mmType).action { c ->
                c.create("sb1", SpecialBean)
                c.withType(Bean, SetOther)
            }
        }

        when:
        registry.atState("beans", ModelNode.State.SelfClosed)

        then:
        registry.get("beans.sb1", SpecialBean).other == "other"
    }

    def "cannot add when realized"() {
        when:
        realizeAsModelMap().create("foo")

        then:
        thrown ModelViewClosedException
    }

    def "is managed instance"() {
        when:
        mutate {
            assert it instanceof ManagedInstance
            assert withType(SpecialNamedThingInterface) instanceof ManagedInstance
        }

        then:
        realize()
    }

    def "reasonable error message when creating a non-constructible type"() {
        when:
        mutate { create("foo", List) }
        realize()

        then:
        def e = thrown ModelRuleExecutionException
        e.cause instanceof ObjectInstantiationException
        e.cause.message == "Could not create an instance of type java.util.List."
    }

}
