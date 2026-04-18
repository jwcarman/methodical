/*
 * Copyright © 2026 James Carman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jwcarman.methodical.jakarta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AnnotationsTest {

  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @interface Marker {
    String value();
  }

  interface Iface {
    @Marker("iface-method")
    void hello();
  }

  @Marker("super-class")
  static class Base implements Iface {
    @Override
    @Marker("base-method")
    public void hello() {
      // fixture: only used for annotation lookup
    }
  }

  static class Child extends Base {
    @Override
    public void hello() {
      // fixture: only used for annotation lookup
    }
  }

  interface OnlyIface {
    @Marker("only-iface")
    void run();
  }

  static class OnlyIfaceImpl implements OnlyIface {
    @Override
    public void run() {
      // fixture: only used for annotation lookup
    }
  }

  @Marker("annotated-class")
  static class AnnotatedOnly {
    public void hello() {
      // fixture: only used for annotation lookup
    }
  }

  static class Plain {
    public void hello() {
      // fixture: only used for annotation lookup
    }
  }

  interface BareInterface {
    void doSomething();
  }

  static class UnannotatedParent {
    public void hello() {
      // fixture: has method, lacks annotation — exercises lookup's annotation==null branch
    }
  }

  static class UnannotatedParentChild extends UnannotatedParent {
    @Override
    public void hello() {
      // fixture: only used for annotation lookup
    }
  }

  interface MixedSiblings {
    void target();

    void otherMethod();

    void target(int x);
  }

  static class MixedSiblingsImpl implements MixedSiblings {
    @Override
    public void target() {
      // fixture: unannotated — forces lookup to walk interface's declared methods
    }

    @Override
    public void otherMethod() {
      // fixture: different-name sibling, exercises lookup's name-mismatch branch
    }

    @Override
    public void target(int x) {
      // fixture: different-arity sibling, exercises lookup's arity-mismatch branch
    }
  }

  abstract static class GenericBase<T> {
    public abstract void handle(T t);
  }

  static class StringChild extends GenericBase<String> {
    @Override
    public void handle(String s) {
      // fixture: produces a compiler-generated bridge handle(Object)
    }
  }

  static class StringGrandchild extends StringChild {
    @Override
    public void handle(String s) {
      // fixture: forces findOnMethod to lookup on StringChild, which has a bridge
    }
  }

  interface GenericWithSiblings<T> {
    @Marker("generic-iface-with-siblings")
    void op(T t);
  }

  static class ConcreteWithSiblings implements GenericWithSiblings<String> {
    @Override
    public void op(String s) {
      // fixture: target for bridge resolution
    }

    public void op(String a, String b) {
      // fixture: different-arity sibling, exercises resolveBridged's arity-mismatch branch
    }

    public void other() {
      // fixture: different-name sibling, exercises resolveBridged's name-mismatch branch
    }
  }

  interface Handler<T> {
    @Marker("generic-iface")
    void handle(T t);
  }

  static class StringHandler implements Handler<String> {
    @Override
    public void handle(String s) {
      // fixture: only used for annotation lookup
    }
  }

  static class AnnotatedStringHandler implements Handler<String> {
    @Override
    @Marker("subclass-method")
    public void handle(String s) {
      // fixture: only used for annotation lookup
    }
  }

  @Test
  void method_annotation_found_via_overridden_chain() throws Exception {
    Method m = Child.class.getMethod("hello");
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("base-method");
  }

  @Test
  void method_annotation_found_on_interface() throws Exception {
    Method m = OnlyIfaceImpl.class.getMethod("run");
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("only-iface");
  }

  @Test
  void method_annotation_returns_null_when_absent() throws Exception {
    Method m = Plain.class.getMethod("hello");
    assertThat(Annotations.findOnMethod(m, Marker.class)).isNull();
  }

  @Test
  void method_on_unannotated_interface_skips_superclass_walk() throws Exception {
    Method m = BareInterface.class.getDeclaredMethod("doSomething");
    assertThat(Annotations.findOnMethod(m, Marker.class)).isNull();
  }

  @Test
  void class_walk_terminates_when_interface_superclass_is_null() {
    assertThat(Annotations.findOnClass(BareInterface.class, Marker.class)).isNull();
  }

  @Test
  void lookup_skips_supertype_method_when_unrelated_annotation_present() throws Exception {
    Method m = UnannotatedParentChild.class.getMethod("hello");
    assertThat(Annotations.findOnMethod(m, Marker.class)).isNull();
  }

  @Test
  void lookup_walks_past_name_and_arity_mismatches() throws Exception {
    Method m = MixedSiblingsImpl.class.getMethod("target");
    assertThat(Annotations.findOnMethod(m, Marker.class)).isNull();
  }

  @Test
  void lookup_skips_bridge_methods_in_supertype() throws Exception {
    Method m = StringGrandchild.class.getMethod("handle", String.class);
    assertThat(Annotations.findOnMethod(m, Marker.class)).isNull();
  }

  static class EmptyDeclaring {
    // fixture: empty class used as a mock bridge's declaring class
  }

  @Test
  void bridge_resolution_falls_back_to_input_when_no_sibling_exists() {
    // Defensive path: javac always emits bridges with a non-bridge sibling, but we still handle
    // the degenerate case. Mock a bridge method whose declaring class has no declared methods.
    Method bridge = mock(Method.class);
    when(bridge.isBridge()).thenReturn(true);
    when(bridge.getName()).thenReturn("mysteryMethod");
    when(bridge.getParameterCount()).thenReturn(0);
    when(bridge.getAnnotation(Marker.class)).thenReturn(null);
    Class<?> declaring = EmptyDeclaring.class;
    when(bridge.getDeclaringClass()).thenAnswer(invocation -> declaring);

    assertThat(Annotations.findOnMethod(bridge, Marker.class)).isNull();
  }

  @Test
  void bridge_resolution_walks_past_non_matching_siblings() {
    Method bridge = null;
    for (Method m : ConcreteWithSiblings.class.getDeclaredMethods()) {
      if (m.isBridge()) {
        bridge = m;
        break;
      }
    }
    assertThat(bridge).as("compiler should have emitted a bridge for op(Object)").isNotNull();
    Marker found = Annotations.findOnMethod(bridge, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("generic-iface-with-siblings");
  }

  @Test
  void class_annotation_found_directly() {
    Marker found = Annotations.findOnClass(AnnotatedOnly.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("annotated-class");
  }

  @Test
  void class_annotation_found_via_superclass() {
    Marker found = Annotations.findOnClass(Child.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("super-class");
  }

  @Test
  void class_annotation_returns_null_when_absent() {
    assertThat(Annotations.findOnClass(Plain.class, Marker.class)).isNull();
  }

  @Test
  void method_annotation_found_on_generic_interface_despite_erasure() throws Exception {
    Method m = StringHandler.class.getMethod("handle", String.class);
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("generic-iface");
  }

  @Test
  void method_annotation_on_overriding_method_wins_over_generic_interface() throws Exception {
    Method m = AnnotatedStringHandler.class.getMethod("handle", String.class);
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("subclass-method");
  }

  // ---------------------------------------------------------------------------
  // Additional fixtures
  // ---------------------------------------------------------------------------

  static class SuperMarked {
    @Marker("superclass")
    public void hello() {
      // fixture: only used for annotation lookup
    }
  }

  static class SubMarked extends SuperMarked {
    @Override
    @Marker("subclass")
    public void hello() {
      // fixture: only used for annotation lookup
    }
  }

  static class Grandparent {
    @Marker("grandparent")
    public void greet() {
      // fixture: only used for annotation lookup
    }
  }

  static class Parent extends Grandparent {}

  static class GrandChild extends Parent {}

  interface SuperIface {
    @Marker("super-iface")
    void ping();
  }

  interface SubIface extends SuperIface {}

  static class SubIfaceImpl implements SubIface {
    @Override
    public void ping() {
      // fixture: only used for annotation lookup
    }
  }

  static class BothAnnotatedParent {
    @Marker("parent-class-method")
    public void act() {
      // fixture: only used for annotation lookup
    }
  }

  interface BothAnnotatedIface {
    @Marker("interface-method")
    void act();
  }

  static class BothAnnotatedChild extends BothAnnotatedParent implements BothAnnotatedIface {
    @Override
    public void act() {
      // fixture: only used for annotation lookup
    }
  }

  static class OverloadSuper {
    @Marker("one-arg")
    public void work(String a) {
      // fixture: only used for annotation lookup
    }

    public void work(String a, String b) {
      // fixture: only used for annotation lookup
    }
  }

  static class OverloadChild extends OverloadSuper {
    @Override
    public void work(String a) {
      // fixture: only used for annotation lookup
    }

    @Override
    public void work(String a, String b) {
      // fixture: only used for annotation lookup
    }
  }

  static class SubstringSuper {
    @Marker("handle-all")
    public void handleAll() {
      // fixture: only used for annotation lookup
    }
  }

  static class SubstringChild extends SubstringSuper {
    public void handle() {
      // fixture: only used for annotation lookup
    }
  }

  static class ZeroArgSuper {
    @Marker("zero-arg")
    public void tick() {
      // fixture: only used for annotation lookup
    }
  }

  static class ZeroArgChild extends ZeroArgSuper {
    @Override
    public void tick() {
      // fixture: only used for annotation lookup
    }
  }

  static class ConcreteIntHandler implements Handler<Integer> {
    @Override
    @Marker("concrete")
    public void handle(Integer i) {
      // fixture: only used for annotation lookup
    }
  }

  static class WithParamsNoAnnotations {
    public void take(String a, int b) {
      // fixture: only used for annotation lookup
    }
  }

  static class PrivateAnnotated {
    @Marker("private-method")
    private void secret() {
      // fixture: only used for annotation lookup
    }
  }

  @Marker("direct-class")
  static class DirectWinsChild extends SuperMarkedClass {}

  @Marker("super-only")
  static class SuperMarkedClass {}

  @Marker("gp-class")
  static class ClassGrandparent {}

  static class ClassParent extends ClassGrandparent {}

  static class ClassGrandChild extends ClassParent {}

  @Marker("direct-iface")
  interface DirectlyAnnotatedIface {}

  static class ImplementsAnnotatedIface implements DirectlyAnnotatedIface {}

  @Marker("super-annotated-iface")
  interface AnnotatedSuperIface {}

  interface ExtendsAnnotatedIface extends AnnotatedSuperIface {}

  static class ImplementsExtendsAnnotated implements ExtendsAnnotatedIface {}

  static class SuperWithAnnotatedIface implements DirectlyAnnotatedIface {}

  static class ChildOfSuperWithAnnotatedIface extends SuperWithAnnotatedIface {}

  @Marker("class-wins")
  static class ClassWinsOverIface implements DirectlyAnnotatedIface {}

  @Marker("diamond-a")
  interface DiamondA {
    void doIt();
  }

  interface DiamondB {
    @Marker("diamond-b-method")
    void doIt();
  }

  static class DiamondImpl implements DiamondA, DiamondB {
    @Override
    public void doIt() {
      // fixture: only used for annotation lookup
    }
  }

  // ---------------------------------------------------------------------------
  // findOnMethod tests
  // ---------------------------------------------------------------------------

  @Test
  void method_direct_annotation_wins_over_superclass() throws Exception {
    Method m = SubMarked.class.getMethod("hello");
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("subclass");
  }

  @Test
  void method_annotation_found_through_multi_level_superclass_chain() throws Exception {
    Method m = GrandChild.class.getMethod("greet");
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("grandparent");
  }

  @Test
  void method_annotation_found_through_super_interface() throws Exception {
    Method m = SubIfaceImpl.class.getMethod("ping");
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("super-iface");
  }

  @Test
  void method_superclass_chain_wins_over_interface() throws Exception {
    Method m = BothAnnotatedChild.class.getMethod("act");
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("parent-class-method");
  }

  @Test
  void method_overloads_distinguished_by_arity_picks_one_arg() throws Exception {
    Method oneArg = OverloadChild.class.getMethod("work", String.class);
    Marker found = Annotations.findOnMethod(oneArg, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("one-arg");
  }

  @Test
  void method_overloads_distinguished_by_arity_two_arg_is_not_annotated() throws Exception {
    Method twoArg = OverloadChild.class.getMethod("work", String.class, String.class);
    assertThat(Annotations.findOnMethod(twoArg, Marker.class)).isNull();
  }

  @Test
  void method_name_does_not_match_by_substring() throws Exception {
    Method m = SubstringChild.class.getMethod("handle");
    assertThat(Annotations.findOnMethod(m, Marker.class)).isNull();
  }

  @Test
  void method_annotation_on_zero_arg_method_resolved_via_arity() throws Exception {
    Method m = ZeroArgChild.class.getMethod("tick");
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("zero-arg");
  }

  @Test
  void method_bridge_resolves_to_concrete_sibling() {
    Method bridge = null;
    for (Method candidate : ConcreteIntHandler.class.getDeclaredMethods()) {
      if (candidate.isBridge() && candidate.getName().equals("handle")) {
        bridge = candidate;
        break;
      }
    }
    assertThat(bridge).as("synthetic bridge method expected on ConcreteIntHandler").isNotNull();
    Marker found = Annotations.findOnMethod(bridge, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("concrete");
  }

  @Test
  void method_with_parameters_returns_null_when_no_annotation_anywhere() throws Exception {
    Method m = WithParamsNoAnnotations.class.getMethod("take", String.class, int.class);
    assertThat(Annotations.findOnMethod(m, Marker.class)).isNull();
  }

  @Test
  void method_private_method_annotation_is_found() throws Exception {
    Method m = PrivateAnnotated.class.getDeclaredMethod("secret");
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("private-method");
  }

  // ---------------------------------------------------------------------------
  // findOnClass tests
  // ---------------------------------------------------------------------------

  @Test
  void class_direct_annotation_wins_over_superclass() {
    Marker found = Annotations.findOnClass(DirectWinsChild.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("direct-class");
  }

  @Test
  void class_annotation_found_through_multi_level_superclass_chain() {
    Marker found = Annotations.findOnClass(ClassGrandChild.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("gp-class");
  }

  @Test
  void class_annotation_found_on_directly_implemented_interface() {
    Marker found = Annotations.findOnClass(ImplementsAnnotatedIface.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("direct-iface");
  }

  @Test
  void class_annotation_found_on_super_interface() {
    Marker found = Annotations.findOnClass(ImplementsExtendsAnnotated.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("super-annotated-iface");
  }

  @Test
  void class_annotation_found_on_superclasses_interface() {
    Marker found = Annotations.findOnClass(ChildOfSuperWithAnnotatedIface.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("direct-iface");
  }

  @Test
  void class_chain_wins_over_interface() {
    Marker found = Annotations.findOnClass(ClassWinsOverIface.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("class-wins");
  }

  @Test
  void class_annotation_found_on_one_of_two_diamond_interfaces() {
    // DiamondA is declared first on the implements clause, so LinkedHashSet yields it first.
    Marker found = Annotations.findOnClass(DiamondImpl.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("diamond-a");
  }

  @Test
  void method_annotation_found_on_one_of_two_diamond_interfaces() throws Exception {
    // Only DiamondB annotates doIt(); DiamondA does not. Should still find DiamondB's annotation.
    Method m = DiamondImpl.class.getMethod("doIt");
    Marker found = Annotations.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("diamond-b-method");
  }

  // ---------------------------------------------------------------------------
  // Utility-class shape
  // ---------------------------------------------------------------------------

  @Test
  void annotations_class_is_final_with_private_constructor() throws Exception {
    assertThat(Modifier.isFinal(Annotations.class.getModifiers())).isTrue();
    Constructor<Annotations> ctor = Annotations.class.getDeclaredConstructor();
    assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
    ctor.setAccessible(true);
    assertThat(ctor.newInstance()).isNotNull();
  }
}
