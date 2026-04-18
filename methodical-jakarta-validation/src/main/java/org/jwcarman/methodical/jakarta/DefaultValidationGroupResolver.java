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
package org.jwcarman.methodical.jakarta;

import java.lang.reflect.Method;
import java.util.Objects;

public final class DefaultValidationGroupResolver implements ValidationGroupResolver {

  private final Class<?>[] defaultGroups;

  public DefaultValidationGroupResolver(Class<?>[] defaultGroups) {
    this.defaultGroups = Objects.requireNonNull(defaultGroups, "defaultGroups").clone();
  }

  @Override
  public Class<?>[] resolveGroups(Object target, Method method) {
    MethodValidation annotation = findAnnotation(target, method);
    if (annotation != null && annotation.groups().length > 0) {
      return annotation.groups();
    }
    return defaultGroups.clone();
  }

  private MethodValidation findAnnotation(Object target, Method method) {
    MethodValidation onMethod = Annotations.findOnMethod(method, MethodValidation.class);
    if (onMethod != null) {
      return onMethod;
    }
    Class<?> type = target != null ? target.getClass() : method.getDeclaringClass();
    return Annotations.findOnClass(type, MethodValidation.class);
  }
}
