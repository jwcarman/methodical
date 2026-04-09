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
package org.jwcarman.methodical.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.jwcarman.methodical.param.ParameterInfo;

class GsonParameterResolverTest {

  private Gson gson;
  private GsonParameterResolver resolver;

  @BeforeEach
  void setUp() {
    gson = new Gson();
    resolver = new GsonParameterResolver(gson);
  }

  @Test
  void shouldResolveFromObjectParamsByName() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    JsonElement params = JsonParser.parseString("{\"name\": \"Alice\"}");
    Object result = resolver.resolve(info, params);
    assertThat(result).isEqualTo("Alice");
  }

  @Test
  void shouldResolveFromArrayParamsByPosition() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    JsonElement params = JsonParser.parseString("[\"Alice\"]");
    Object result = resolver.resolve(info, params);
    assertThat(result).isEqualTo("Alice");
  }

  static Stream<Arguments> nullCases() throws Exception {
    return Stream.of(
        Arguments.of("null params", null, "name", 0),
        Arguments.of("missing key", JsonParser.parseString("{\"other\": \"value\"}"), "name", 0),
        Arguments.of("null node value", JsonParser.parseString("{\"name\": null}"), "name", 0),
        Arguments.of("array out of bounds", JsonParser.parseString("[\"Alice\"]"), "name", 5),
        Arguments.of("JsonNull params", JsonNull.INSTANCE, "name", 0));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nullCases")
  void shouldReturnNullWhenExpected(
      String description, JsonElement params, String paramName, int index) throws Exception {
    ParameterInfo info = paramInfo(paramName, index, String.class);
    Object result = resolver.resolve(info, params);
    assertThat(result).isNull();
  }

  @Test
  void shouldDeserializeComplexTypes() throws Exception {
    ParameterInfo info = paramInfo("value", 0, int.class);
    JsonElement params = JsonParser.parseString("{\"value\": 42}");
    Object result = resolver.resolve(info, params);
    assertThat(result).isEqualTo(42);
  }

  @Test
  void shouldAlwaysReturnTrueForSupports() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    assertThat(resolver.supports(info)).isTrue();
  }

  @Test
  void shouldReturnNullForScalarParams() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    JsonElement params = JsonParser.parseString("\"just a string\"");
    Object result = resolver.resolve(info, params);
    assertThat(result).isNull();
  }

  private ParameterInfo paramInfo(String paramName, int index, Class<?> type) throws Exception {
    Method method = TestTarget.class.getMethod("method", String.class, int.class);
    Parameter param =
        paramName.equals("name") ? method.getParameters()[0] : method.getParameters()[1];
    return ParameterInfo.of(param, index, type, type);
  }

  public static class TestTarget {
    public void method(String name, int value) {
      // no-op
    }
  }
}
