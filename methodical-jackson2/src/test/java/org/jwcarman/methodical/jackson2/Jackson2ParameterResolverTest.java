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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.ParameterInfo;

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
    Object result = resolver.resolve(info, params);
    assertThat(result).isEqualTo("Alice");
  }

  @Test
  void shouldResolveFromArrayParamsByPosition() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    JsonNode params = mapper.readTree("[\"Alice\"]");
    Object result = resolver.resolve(info, params);
    assertThat(result).isEqualTo("Alice");
  }

  @Test
  void shouldReturnNullForNullParams() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    Object result = resolver.resolve(info, null);
    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullForMissingKey() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    JsonNode params = mapper.readTree("{\"other\": \"value\"}");
    Object result = resolver.resolve(info, params);
    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullForNullNodeValue() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    JsonNode params = mapper.readTree("{\"name\": null}");
    Object result = resolver.resolve(info, params);
    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullForArrayOutOfBounds() throws Exception {
    ParameterInfo info = paramInfo("name", 5, String.class);
    JsonNode params = mapper.readTree("[\"Alice\"]");
    Object result = resolver.resolve(info, params);
    assertThat(result).isNull();
  }

  @Test
  void shouldDeserializeComplexTypes() throws Exception {
    ParameterInfo info = paramInfo("value", 0, int.class);
    JsonNode params = mapper.readTree("{\"value\": 42}");
    Object result = resolver.resolve(info, params);
    assertThat(result).isEqualTo(42);
  }

  @Test
  void shouldAlwaysReturnTrueForSupports() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    assertThat(resolver.supports(info)).isTrue();
  }

  @Test
  void shouldReturnNullForJsonNullParams() throws Exception {
    ParameterInfo info = paramInfo("name", 0, String.class);
    JsonNode params = mapper.readTree("null");
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
    public void method(String name, int value) {}
  }
}
