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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;

public class GsonParameterResolver implements ParameterResolver<JsonElement> {

  private final Gson gson;

  public GsonParameterResolver(Gson gson) {
    this.gson = gson;
  }

  @Override
  public boolean supports(ParameterInfo info) {
    return true;
  }

  @Override
  public Object resolve(ParameterInfo info, JsonElement params) {
    if (params == null || params.isJsonNull()) {
      return null;
    }
    JsonElement element = extractElement(info, params);
    if (element == null || element.isJsonNull()) {
      return null;
    }
    return deserialize(info, element);
  }

  private Object deserialize(ParameterInfo info, JsonElement element) {
    try {
      return gson.fromJson(element, info.resolvedType());
    } catch (JsonSyntaxException e) {
      throw new ParameterResolutionException(
          String.format("Unable to deserialize parameter \"%s\": %s", info.name(), e.getMessage()),
          e);
    }
  }

  private JsonElement extractElement(ParameterInfo info, JsonElement params) {
    if (params.isJsonObject()) {
      return params.getAsJsonObject().get(info.name());
    } else if (params.isJsonArray()) {
      JsonArray array = params.getAsJsonArray();
      return info.index() < array.size() ? array.get(info.index()) : null;
    }
    return null;
  }
}
