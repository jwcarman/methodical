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
package org.jwcarman.methodical.reflect;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;
import org.apache.commons.lang3.reflect.TypeUtils;

/** Reflection utilities for resolving generic type parameters. */
public final class Types {

  private Types() {}

  /**
   * Resolves the concrete type argument at the given index from a concrete class that extends or
   * implements a generic defining class.
   *
   * @param concreteClass the concrete class with bound type parameters
   * @param definingClass the generic class or interface declaring the type parameter
   * @param varIndex the index of the type parameter (0-based)
   * @param <T> the concrete type
   * @param <P> the defining type
   * @param <C> the resolved type (typically {@code Class<?>})
   * @return the resolved type argument
   */
  @SuppressWarnings("unchecked")
  public static <T extends P, P, C extends Type> C typeParamFromClass(
      Class<T> concreteClass, Class<P> definingClass, int varIndex) {
    Map<TypeVariable<?>, Type> typeArguments =
        TypeUtils.getTypeArguments(concreteClass, definingClass);
    TypeVariable<Class<P>> typeVar = definingClass.getTypeParameters()[varIndex];
    return (C) typeArguments.get(typeVar);
  }
}
