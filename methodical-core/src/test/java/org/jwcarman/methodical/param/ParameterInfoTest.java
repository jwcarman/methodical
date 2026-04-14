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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jwcarman.specular.TypeRef;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ParameterInfoTest {

  @SuppressWarnings("unused")
  static class Fixtures {
    public void raw(String s) {
      // no-op for test fixture
    }

    public void paramMap(Map<String, String> m) {
      // no-op for test fixture
    }

    public void paramObjectMap(Map<String, Object> m) {
      // no-op for test fixture
    }

    public void paramWildcard(Map<?, ?> m) {
      // no-op for test fixture
    }

    public void paramUpperBound(Map<String, ? extends CharSequence> m) {
      // no-op for test fixture
    }

    public void paramNested(List<Map<String, String>> l) {
      // no-op for test fixture
    }
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

  @Nested
  class type_accessors {

    @Test
    void resolved_type_returns_erased_raw_class() {
      assertThat(infoFor("paramMap").resolvedType()).isEqualTo(Map.class);
    }

    @Test
    void resolved_type_for_non_parameterized_returns_the_class() {
      assertThat(infoFor("raw").resolvedType()).isEqualTo(String.class);
    }

    @Test
    void generic_type_returns_the_full_parameterized_type() {
      assertThat(infoFor("paramMap").genericType().getTypeName())
          .isEqualTo("java.util.Map<java.lang.String, java.lang.String>");
    }

    @Test
    void generic_type_for_non_parameterized_returns_the_class() {
      assertThat(infoFor("raw").genericType()).isEqualTo(String.class);
    }
  }

  @Nested
  class raw_types {

    @Test
    void identity_is_accepted() {
      assertThat(infoFor("raw").accepts(String.class)).isTrue();
    }

    @Test
    void supertype_is_rejected() {
      assertThat(infoFor("raw").accepts(Object.class)).isFalse();
    }

    @Test
    void unrelated_type_is_rejected() {
      assertThat(infoFor("raw").accepts(Integer.class)).isFalse();
    }
  }

  @Nested
  class parameterized_invariance {

    @Test
    void identical_parameterization_is_accepted() {
      assertThat(infoFor("paramMap").accepts(new TypeRef<Map<String, String>>() {})).isTrue();
    }

    @Test
    void narrower_argument_into_wider_parameter_is_rejected() {
      assertThat(infoFor("paramObjectMap").accepts(new TypeRef<Map<String, String>>() {}))
          .isFalse();
    }

    @Test
    void wider_argument_into_narrower_parameter_is_rejected() {
      assertThat(infoFor("paramMap").accepts(new TypeRef<Map<String, Object>>() {})).isFalse();
    }
  }

  @Nested
  class wildcards {

    @Test
    void unbounded_wildcard_parameter_accepts_any_parameterization() {
      ParameterInfo info = infoFor("paramWildcard");
      assertThat(info.accepts(new TypeRef<Map<String, String>>() {})).isTrue();
      assertThat(info.accepts(new TypeRef<Map<Integer, Object>>() {})).isTrue();
    }

    @Test
    void upper_bounded_parameter_accepts_in_bound_argument() {
      assertThat(infoFor("paramUpperBound").accepts(new TypeRef<Map<String, String>>() {}))
          .isTrue();
    }

    @Test
    void upper_bounded_parameter_rejects_out_of_bound_argument() {
      assertThat(infoFor("paramUpperBound").accepts(new TypeRef<Map<String, Object>>() {}))
          .isFalse();
    }
  }

  @Nested
  class cross_kind {

    @Test
    void raw_subclass_of_parameterized_parameter_is_accepted() {
      assertThat(infoFor("paramMap").accepts(new TypeRef<HashMap<String, String>>() {})).isTrue();
    }

    @Test
    void raw_class_argument_into_parameterized_parameter_is_accepted_unchecked() {
      assertThat(infoFor("paramMap").accepts(Map.class)).isTrue();
    }

    @Test
    void incompatible_raw_class_is_rejected() {
      assertThat(infoFor("paramMap").accepts(List.class)).isFalse();
    }

    @Test
    void nested_parameterized_identity_is_accepted() {
      assertThat(infoFor("paramNested").accepts(new TypeRef<List<Map<String, String>>>() {}))
          .isTrue();
    }

    @Test
    void nested_parameterized_mismatch_is_rejected() {
      assertThat(infoFor("paramNested").accepts(new TypeRef<List<Map<String, Integer>>>() {}))
          .isFalse();
    }

    @Test
    void accepts_type_overload_delegates() {
      java.lang.reflect.Type raw = new TypeRef<Map<String, String>>() {}.getType();
      assertThat(infoFor("paramMap").accepts(raw)).isTrue();
    }
  }
}
