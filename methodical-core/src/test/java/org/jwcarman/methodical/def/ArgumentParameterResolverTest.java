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
package org.jwcarman.methodical.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.Argument;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ArgumentParameterResolverTest {

  @SuppressWarnings("unused")
  static class Fixtures {
    public void takesString(String value) {
      // no-op for test fixture
    }

    public void takesStringWithAnnotation(@Argument String value) {
      // no-op for test fixture
    }

    public void takesMapWithAnnotation(@Argument Map<String, String> value) {
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

  @Test
  void bind_returns_empty_when_parameter_lacks_argument_annotation() {
    var resolver = new ArgumentParameterResolver<String>(TypeRef.of(String.class));
    assertThat(resolver.bind(infoFor("takesString"))).isEmpty();
  }

  @Test
  void bind_returns_binding_when_parameter_has_argument_annotation_and_type_matches() {
    var resolver = new ArgumentParameterResolver<String>(TypeRef.of(String.class));
    Optional<ParameterResolver.Binding<String>> binding =
        resolver.bind(infoFor("takesStringWithAnnotation"));
    assertThat(binding).isPresent();
    assertThat(binding.orElseThrow().resolve("hello")).isEqualTo("hello");
  }

  @Test
  void bind_throws_when_parameter_has_argument_annotation_but_type_incompatible() {
    var resolver = new ArgumentParameterResolver<Integer>(TypeRef.of(Integer.class));
    assertThatThrownBy(() -> resolver.bind(infoFor("takesStringWithAnnotation")))
        .isInstanceOf(ParameterResolutionException.class)
        .hasMessageContaining("@Argument")
        .hasMessageContaining("Integer")
        .hasMessageContaining("String");
  }

  @Test
  void bind_respects_generic_invariance() {
    // Parameter is @Argument Map<String,String>, argument is Map<String,Object>: Java invariance
    // rejects.
    var resolver =
        new ArgumentParameterResolver<Map<String, Object>>(new TypeRef<Map<String, Object>>() {});
    assertThatThrownBy(() -> resolver.bind(infoFor("takesMapWithAnnotation")))
        .isInstanceOf(ParameterResolutionException.class);
  }

  @Test
  void bind_accepts_identical_parameterization() {
    var resolver =
        new ArgumentParameterResolver<Map<String, String>>(new TypeRef<Map<String, String>>() {});
    assertThat(resolver.bind(infoFor("takesMapWithAnnotation"))).isPresent();
  }

  @Test
  void binding_returns_argument_unchanged() {
    var resolver = new ArgumentParameterResolver<String>(TypeRef.of(String.class));
    ParameterResolver.Binding<String> binding =
        resolver.bind(infoFor("takesStringWithAnnotation")).orElseThrow();
    assertThat(binding.resolve("world")).isEqualTo("world");
  }
}
