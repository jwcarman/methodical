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
package org.jwcarman.methodical.jackson2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.specular.TypeRef;

class Jackson2ParameterResolverTest {

  private ObjectMapper mapper;
  private Jackson2ParameterResolver resolver;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    resolver = new Jackson2ParameterResolver(mapper);
  }

  @Test
  void shouldResolveFromObjectParamsByName() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    JsonNode params = mapper.readTree("{\"name\": \"Alice\"}");
    Object result = resolver.bind(info).orElseThrow().resolve(params);
    assertThat(result).isEqualTo("Alice");
  }

  @Test
  void shouldResolveFromArrayParamsByPosition() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    JsonNode params = mapper.readTree("[\"Alice\"]");
    Object result = resolver.bind(info).orElseThrow().resolve(params);
    assertThat(result).isEqualTo("Alice");
  }

  static Stream<Arguments> nullCases() throws Exception {
    ObjectMapper m = new ObjectMapper();
    return Stream.of(
        Arguments.of("null params", null, "name", 0),
        Arguments.of("missing key", m.readTree("{\"other\": \"value\"}"), "name", 0),
        Arguments.of("null node value", m.readTree("{\"name\": null}"), "name", 0),
        Arguments.of("array out of bounds", m.readTree("[\"Alice\"]"), "name", 5),
        Arguments.of("JsonNull params", m.readTree("null"), "name", 0));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nullCases")
  void shouldReturnNullWhenExpected(
      String description, JsonNode params, String paramName, int index) throws Exception {
    ParameterInfo info = paramInfo(paramName, index, String.class);
    Object result = resolver.bind(info).orElseThrow().resolve(params);
    assertThat(result).isNull();
  }

  @Test
  void shouldDeserializeComplexTypes() throws Exception {
    ParameterInfo info = paramInfo("value", 0, int.class);
    JsonNode params = mapper.readTree("{\"value\": 42}");
    Object result = resolver.bind(info).orElseThrow().resolve(params);
    assertThat(result).isEqualTo(42);
  }

  @Test
  void shouldAlwaysBindSuccessfully() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    assertThat(resolver.bind(info)).isPresent();
  }

  @Test
  void shouldReturnNullForScalarParams() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    JsonNode params = mapper.readTree("\"just a string\"");
    Object result = resolver.bind(info).orElseThrow().resolve(params);
    assertThat(result).isNull();
  }

  @Test
  void shouldThrowMethodInvocationExceptionOnDeserializationError() throws Exception {
    ParameterInfo info = paramInfo("value", 0, int.class);
    JsonNode params = mapper.readTree("{\"value\": \"not a number\"}");
    assertThatThrownBy(() -> resolver.bind(info).orElseThrow().resolve(params))
        .isInstanceOf(ParameterResolutionException.class)
        .hasMessageContaining("Unable to deserialize parameter");
  }

  private ParameterInfo paramInfo(String paramName, int index, Class<?> type) throws Exception {
    Method method = TestTarget.class.getMethod("method", String.class, int.class);
    Parameter param =
        paramName.equals("name") ? method.getParameters()[0] : method.getParameters()[1];
    return ParameterInfo.of(param, index, TypeRef.of(type));
  }

  public static class TestTarget {
    public void method(String name, int value) {
      // no-op
    }
  }
}
