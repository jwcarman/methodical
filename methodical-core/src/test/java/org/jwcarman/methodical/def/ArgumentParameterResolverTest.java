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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.specular.TypeRef;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ArgumentParameterResolverTest {

  @SuppressWarnings("unused")
  static class Fixtures {
    public void takesString(String value) {}

    public void takesMap(Map<String, String> value) {}
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
  void supports_is_true_when_parameter_accepts_argument_type() {
    var resolver = new ArgumentParameterResolver<String>(TypeRef.of(String.class));
    assertThat(resolver.supports(infoFor("takesString"))).isTrue();
  }

  @Test
  void supports_is_false_when_parameter_does_not_accept_argument_type() {
    var resolver = new ArgumentParameterResolver<Integer>(TypeRef.of(Integer.class));
    assertThat(resolver.supports(infoFor("takesString"))).isFalse();
  }

  @Test
  void supports_respects_generic_invariance() {
    // Parameter is Map<String,String>, argument is Map<String,Object> — Java's invariance rejects.
    var resolver =
        new ArgumentParameterResolver<Map<String, Object>>(new TypeRef<Map<String, Object>>() {});
    assertThat(resolver.supports(infoFor("takesMap"))).isFalse();
  }

  @Test
  void supports_accepts_identical_parameterization() {
    var resolver =
        new ArgumentParameterResolver<Map<String, String>>(new TypeRef<Map<String, String>>() {});
    assertThat(resolver.supports(infoFor("takesMap"))).isTrue();
  }

  @Test
  void resolve_returns_argument_unchanged() {
    var resolver = new ArgumentParameterResolver<String>(TypeRef.of(String.class));
    assertThat(resolver.resolve(infoFor("takesString"), "hello")).isEqualTo("hello");
  }
}
