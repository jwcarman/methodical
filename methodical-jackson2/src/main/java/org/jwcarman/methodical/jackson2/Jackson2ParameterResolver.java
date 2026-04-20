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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.util.Optional;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;

/**
 * {@link ParameterResolver} that binds JSON nodes to method parameters using Jackson 2. Pre-builds
 * an {@link ObjectReader} per parameter at {@link #bind} time so the per-invocation hot path does
 * no reader construction, no type lookup, and no name resolution.
 */
public class Jackson2ParameterResolver implements ParameterResolver<JsonNode> {

  private final ObjectMapper mapper;

  public Jackson2ParameterResolver(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<Binding<JsonNode>> bind(ParameterInfo info) {
    final String name = info.name();
    final int index = info.index();
    final ObjectReader reader = mapper.readerFor(info.resolvedType());
    return Optional.of(params -> resolve(params, name, index, reader));
  }

  private Object resolve(JsonNode params, String name, int index, ObjectReader reader) {
    if (params == null || params.isNull()) {
      return null;
    }
    JsonNode node = extractNode(params, name, index);
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      return reader.readValue(mapper.treeAsTokens(node));
    } catch (IOException e) {
      throw new ParameterResolutionException(
          String.format("Unable to deserialize parameter \"%s\": %s", name, e.getMessage()), e);
    }
  }

  private static JsonNode extractNode(JsonNode params, String name, int index) {
    return switch (params.getNodeType()) {
      case OBJECT -> params.get(name);
      case ARRAY -> index < params.size() ? params.get(index) : null;
      default -> null;
    };
  }
}
