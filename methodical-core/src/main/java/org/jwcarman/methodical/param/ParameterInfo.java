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
package org.jwcarman.methodical.param;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import org.jwcarman.methodical.Named;

/**
 * Metadata about a method parameter, resolved at factory creation time.
 *
 * @param parameter the raw Java parameter
 * @param index the positional index in the method signature
 * @param name the resolved name ({@link Named} annotation value, or Java parameter name)
 * @param resolvedType the resolved raw type (generics resolved against the target class)
 * @param genericType the generic type from the method signature
 */
public record ParameterInfo(
    Parameter parameter, int index, String name, Class<?> resolvedType, Type genericType) {

  public static ParameterInfo of(
      Parameter parameter, int index, Class<?> resolvedType, Type genericType) {
    Named named = parameter.getAnnotation(Named.class);
    String name = named != null ? named.value() : parameter.getName();
    return new ParameterInfo(parameter, index, name, resolvedType, genericType);
  }
}
