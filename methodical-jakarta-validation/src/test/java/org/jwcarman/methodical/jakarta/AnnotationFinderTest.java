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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AnnotationFinderTest {

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
    public void hello() {}
  }

  static class Child extends Base {
    @Override
    public void hello() {}
  }

  interface OnlyIface {
    @Marker("only-iface")
    void run();
  }

  static class OnlyIfaceImpl implements OnlyIface {
    @Override
    public void run() {}
  }

  @Marker("annotated-class")
  static class AnnotatedOnly {
    public void hello() {}
  }

  static class Plain {
    public void hello() {}
  }

  interface Handler<T> {
    @Marker("generic-iface")
    void handle(T t);
  }

  static class StringHandler implements Handler<String> {
    @Override
    public void handle(String s) {}
  }

  static class AnnotatedStringHandler implements Handler<String> {
    @Override
    @Marker("subclass-method")
    public void handle(String s) {}
  }

  @Test
  void method_annotation_found_via_overridden_chain() throws Exception {
    Method m = Child.class.getMethod("hello");
    Marker found = AnnotationFinder.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("base-method");
  }

  @Test
  void method_annotation_found_on_interface() throws Exception {
    Method m = OnlyIfaceImpl.class.getMethod("run");
    Marker found = AnnotationFinder.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("only-iface");
  }

  @Test
  void method_annotation_returns_null_when_absent() throws Exception {
    Method m = Plain.class.getMethod("hello");
    assertThat(AnnotationFinder.findOnMethod(m, Marker.class)).isNull();
  }

  @Test
  void class_annotation_found_directly() {
    Marker found = AnnotationFinder.findOnClass(AnnotatedOnly.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("annotated-class");
  }

  @Test
  void class_annotation_found_via_superclass() {
    Marker found = AnnotationFinder.findOnClass(Child.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("super-class");
  }

  @Test
  void class_annotation_returns_null_when_absent() {
    assertThat(AnnotationFinder.findOnClass(Plain.class, Marker.class)).isNull();
  }

  @Test
  void method_annotation_found_on_generic_interface_despite_erasure() throws Exception {
    Method m = StringHandler.class.getMethod("handle", String.class);
    Marker found = AnnotationFinder.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("generic-iface");
  }

  @Test
  void method_annotation_on_overriding_method_wins_over_generic_interface() throws Exception {
    Method m = AnnotatedStringHandler.class.getMethod("handle", String.class);
    Marker found = AnnotationFinder.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("subclass-method");
  }
}
