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

import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.jwcarman.specular.TypeRef;

/** Factory for creating pre-built {@link MethodInvoker} instances. */
public interface MethodInvokerFactory {

  /** Creates a {@link MethodInvoker} with no per-invoker configuration. */
  default <A> MethodInvoker<A> create(Method method, Object target, Class<A> argumentType) {
    return create(method, target, TypeRef.of(argumentType), cfg -> {});
  }

  /** Creates a {@link MethodInvoker} with a parameterized argument type and no configuration. */
  default <A> MethodInvoker<A> create(Method method, Object target, TypeRef<A> argumentType) {
    return create(method, target, argumentType, cfg -> {});
  }

  /**
   * Creates a {@link MethodInvoker} with per-invoker configuration applied via {@code customizer}.
   */
  default <A> MethodInvoker<A> create(
      Method method,
      Object target,
      Class<A> argumentType,
      Consumer<MethodInvokerConfig<A>> customizer) {
    return create(method, target, TypeRef.of(argumentType), customizer);
  }

  /**
   * Primary overload. Creates a {@link MethodInvoker} with a parameterized argument type and
   * per-invoker configuration applied via {@code customizer}.
   */
  <A> MethodInvoker<A> create(
      Method method,
      Object target,
      TypeRef<A> argumentType,
      Consumer<MethodInvokerConfig<A>> customizer);
}
