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
import java.lang.reflect.Type;
import java.util.Optional;
import org.jwcarman.methodical.ParameterInfo;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.ParameterResolver;

/**
 * {@link ParameterResolver} that binds JSON elements to method parameters using Gson. Pre-computes
 * the parameter name, index, and resolved type at {@link #bind} time; the per-invocation hot path
 * is a single {@link Gson#fromJson(JsonElement, Type)} call.
 */
public class GsonParameterResolver implements ParameterResolver<JsonElement> {

  private final Gson gson;

  public GsonParameterResolver(Gson gson) {
    this.gson = gson;
  }

  @Override
  public Optional<Binding<JsonElement>> bind(ParameterInfo info) {
    final String name = info.name();
    final int index = info.index();
    final Type type = info.resolvedType();
    return Optional.of(params -> resolve(params, name, index, type));
  }

  private Object resolve(JsonElement params, String name, int index, Type type) {
    if (params == null || params.isJsonNull()) {
      return null;
    }
    JsonElement element = extractElement(params, name, index);
    if (element == null || element.isJsonNull()) {
      return null;
    }
    try {
      return gson.fromJson(element, type);
    } catch (JsonSyntaxException e) {
      throw new ParameterResolutionException(
          String.format("Unable to deserialize parameter \"%s\": %s", name, e.getMessage()), e);
    }
  }

  private static JsonElement extractElement(JsonElement params, String name, int index) {
    if (params.isJsonObject()) {
      return params.getAsJsonObject().get(name);
    }
    if (params.isJsonArray()) {
      JsonArray array = params.getAsJsonArray();
      return index < array.size() ? array.get(index) : null;
    }
    return null;
  }
}
