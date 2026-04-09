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
package org.jwcarman.methodical.jackson3;

import org.jwcarman.methodical.MethodInvocationException;
import org.jwcarman.methodical.ParameterInfo;
import org.jwcarman.methodical.ParameterResolver;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class Jackson3ParameterResolver implements ParameterResolver<JsonNode> {

  private final ObjectMapper mapper;

  public Jackson3ParameterResolver(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public boolean supports(ParameterInfo info) {
    return true;
  }

  @Override
  public Object resolve(ParameterInfo info, JsonNode params) {
    if (params == null || params.isNull()) {
      return null;
    }
    JsonNode node = extractNode(info, params);
    if (node == null || node.isNull()) {
      return null;
    }
    return deserialize(info, node);
  }

  private JsonNode extractNode(ParameterInfo info, JsonNode params) {
    return switch (params.getNodeType()) {
      case OBJECT -> params.get(info.name());
      case ARRAY -> info.index() < params.size() ? params.get(info.index()) : null;
      default -> null;
    };
  }

  private Object deserialize(ParameterInfo info, JsonNode node) {
    try {
      return mapper.readerFor(info.resolvedType()).readValue(mapper.treeAsTokens(node));
    } catch (JacksonException e) {
      throw new MethodInvocationException(
          String.format("Unable to deserialize parameter \"%s\": %s", info.name(), e.getMessage()),
          e);
    }
  }
}
