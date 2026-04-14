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
package org.jwcarman.methodical.param;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.Argument;
import org.jwcarman.methodical.Named;
import org.jwcarman.specular.TypeRef;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ParameterInfoAnnotationTest {

  @SuppressWarnings("unused")
  static class Fixtures {
    public void annotated(@Named("custom") @Argument String value) {}

    public void bare(String value) {}
  }

  private static ParameterInfo infoFor(String methodName) {
    for (Method m : Fixtures.class.getMethods()) {
      if (m.getName().equals(methodName)) {
        Parameter p = m.getParameters()[0];
        return ParameterInfo.of(p, 0, TypeRef.parameterType(p));
      }
    }
    throw new AssertionError("method not found: " + methodName);
  }

  @Test
  void annotation_returns_present_annotation() {
    ParameterInfo info = infoFor("annotated");
    assertThat(info.annotation(Named.class))
        .isPresent()
        .get()
        .extracting(Named::value)
        .isEqualTo("custom");
  }

  @Test
  void annotation_returns_empty_when_absent() {
    ParameterInfo info = infoFor("bare");
    assertThat(info.annotation(Named.class)).isEmpty();
  }

  @Test
  void has_annotation_reports_true_when_present() {
    assertThat(infoFor("annotated").hasAnnotation(Argument.class)).isTrue();
  }

  @Test
  void has_annotation_reports_false_when_absent() {
    assertThat(infoFor("bare").hasAnnotation(Argument.class)).isFalse();
  }
}
