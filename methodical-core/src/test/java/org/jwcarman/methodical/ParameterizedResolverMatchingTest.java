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
package org.jwcarman.methodical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/**
 * Exercises generic-aware resolver dispatch: a resolver declared for {@code
 * ParameterResolver<Map<String,String>>} should only match when the invoker's argument type is
 * {@code Map<String,String>}, not {@code Map<String,Integer>}.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ParameterizedResolverMatchingTest {

  static class StringMapResolver implements ParameterResolver<Map<String, String>> {
    @Override
    public boolean supports(ParameterInfo info) {
      return info.accepts(String.class);
    }

    @Override
    public Object resolve(ParameterInfo info, Map<String, String> argument) {
      return argument.get(info.name());
    }
  }

  static class IntegerMapResolver implements ParameterResolver<Map<String, Integer>> {
    @Override
    public boolean supports(ParameterInfo info) {
      return info.accepts(Integer.class);
    }

    @Override
    public Object resolve(ParameterInfo info, Map<String, Integer> argument) {
      return argument.get(info.name());
    }
  }

  public static class Greeter {
    public String hello(String name) {
      return "hi " + name;
    }

    public String heyNumber(Integer count) {
      return "count=" + count;
    }
  }

  @Test
  void resolver_for_map_string_string_matches_when_argument_type_matches() throws Exception {
    var factory =
        new DefaultMethodInvokerFactory(List.of(new StringMapResolver(), new IntegerMapResolver()));
    Method method = Greeter.class.getMethod("hello", String.class);
    var invoker =
        factory.create(method, new Greeter(), new TypeRef<Map<String, String>>() {}, List.of());
    assertThat(invoker.invoke(Map.of("name", "world"))).isEqualTo("hi world");
  }

  @Test
  void resolver_for_map_string_integer_matches_independently() throws Exception {
    var factory =
        new DefaultMethodInvokerFactory(List.of(new StringMapResolver(), new IntegerMapResolver()));
    Method method = Greeter.class.getMethod("heyNumber", Integer.class);
    var invoker =
        factory.create(method, new Greeter(), new TypeRef<Map<String, Integer>>() {}, List.of());
    assertThat(invoker.invoke(Map.of("count", 42))).isEqualTo("count=42");
  }

  @Test
  void resolver_for_one_parameterization_does_not_match_another() throws Exception {
    // Only the StringMapResolver registered; caller passes Map<String,Integer> — should fail fast.
    var factory = new DefaultMethodInvokerFactory(List.of(new StringMapResolver()));
    Method method = Greeter.class.getMethod("heyNumber", Integer.class);
    var greeter = new Greeter();
    TypeRef<Map<String, Integer>> argumentType = new TypeRef<>() {};
    List<ParameterResolver<? super Map<String, Integer>>> emptyList = List.of();
    assertThatThrownBy(() -> factory.create(method, greeter, argumentType, emptyList))
        .isInstanceOf(ParameterResolutionException.class)
        .hasMessageContaining("No resolver found")
        .hasMessageContaining("Map<java.lang.String, java.lang.Integer>");
  }
}
