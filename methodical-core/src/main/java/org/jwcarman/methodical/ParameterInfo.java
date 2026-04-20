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

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Optional;
import org.jwcarman.specular.TypeRef;

/**
 * Metadata about a method parameter, resolved at factory creation time.
 *
 * @param parameter the raw Java parameter
 * @param index the positional index in the method signature
 * @param name the resolved name ({@link Named} annotation value, or Java parameter name)
 * @param type the resolved type (type variables substituted against the target class's hierarchy)
 */
public record ParameterInfo(Parameter parameter, int index, String name, TypeRef<?> type) {

  public static ParameterInfo of(Parameter parameter, int index, TypeRef<?> type) {
    String name =
        Optional.ofNullable(parameter.getAnnotation(Named.class))
            .map(Named::value)
            .orElse(parameter.getName());
    return new ParameterInfo(parameter, index, name, type);
  }

  /**
   * The erased raw class of this parameter's resolved type.
   *
   * @return the raw class
   */
  public Class<?> resolvedType() {
    return type.getRawType();
  }

  /**
   * The full generic type of this parameter (with type variables substituted where possible).
   *
   * @return the generic type
   */
  public Type genericType() {
    return type.getType();
  }

  /**
   * Whether a value of the given raw type can be passed to this parameter.
   *
   * @param argumentType the candidate argument type
   * @return {@code true} if assignment-compatible
   */
  public boolean accepts(Class<?> argumentType) {
    return type.isAssignableFrom(argumentType);
  }

  /**
   * Whether a value of the given (possibly parameterized) type can be passed to this parameter.
   *
   * @param argumentType the candidate argument type
   * @return {@code true} if assignment-compatible
   */
  public boolean accepts(TypeRef<?> argumentType) {
    return type.isAssignableFrom(argumentType);
  }

  /**
   * Low-level variant accepting any {@link Type}.
   *
   * @param argumentType the candidate argument type
   * @return {@code true} if assignment-compatible
   */
  public boolean accepts(Type argumentType) {
    return type.isAssignableFrom(argumentType);
  }

  /**
   * Returns the given annotation if present on this parameter, otherwise {@link Optional#empty()}.
   *
   * @param annotationType the annotation class
   * @param <T> the annotation type
   * @return the annotation, if present
   */
  public <T extends Annotation> Optional<T> annotation(Class<T> annotationType) {
    return Optional.ofNullable(parameter.getAnnotation(annotationType));
  }

  /**
   * Whether the parameter is annotated with the given annotation.
   *
   * @param annotationType the annotation class
   * @return {@code true} if present
   */
  public boolean hasAnnotation(Class<? extends Annotation> annotationType) {
    return parameter.isAnnotationPresent(annotationType);
  }
}
