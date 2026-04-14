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
import java.util.List;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/** Factory for creating pre-built {@link MethodInvoker} instances. */
public interface MethodInvokerFactory {

  /**
   * Creates a {@link MethodInvoker} using only the factory-level resolvers.
   *
   * @param method the method to invoke
   * @param target the target object to invoke the method on
   * @param argumentType the type of argument passed at call time
   * @param <A> the argument type
   * @return a pre-built invoker
   */
  default <A> MethodInvoker<A> create(Method method, Object target, Class<A> argumentType) {
    return create(method, target, TypeRef.of(argumentType), List.of());
  }

  /**
   * Creates a {@link MethodInvoker} with additional per-invoker resolvers that are tried ahead of
   * the factory-level resolvers.
   *
   * @param method the method to invoke
   * @param target the target object to invoke the method on
   * @param argumentType the type of argument passed at call time
   * @param extraResolvers per-invoker resolvers, tried before factory-level resolvers
   * @param <A> the argument type
   * @return a pre-built invoker
   */
  default <A> MethodInvoker<A> create(
      Method method,
      Object target,
      Class<A> argumentType,
      List<ParameterResolver<? super A>> extraResolvers) {
    return create(method, target, TypeRef.of(argumentType), extraResolvers);
  }

  /**
   * Creates a {@link MethodInvoker} with a possibly parameterized argument type, using only the
   * factory-level resolvers.
   *
   * @param method the method to invoke
   * @param target the target object to invoke the method on
   * @param argumentType the (possibly parameterized) type of argument passed at call time
   * @param <A> the argument type
   * @return a pre-built invoker
   */
  default <A> MethodInvoker<A> create(Method method, Object target, TypeRef<A> argumentType) {
    return create(method, target, argumentType, List.of());
  }

  /**
   * Creates a {@link MethodInvoker} with a possibly parameterized argument type and additional
   * per-invoker resolvers that are tried ahead of the factory-level resolvers. This is the primary
   * method; all other overloads delegate here.
   *
   * @param method the method to invoke
   * @param target the target object to invoke the method on
   * @param argumentType the (possibly parameterized) type of argument passed at call time
   * @param extraResolvers per-invoker resolvers, tried before factory-level resolvers
   * @param <A> the argument type
   * @return a pre-built invoker
   */
  <A> MethodInvoker<A> create(
      Method method,
      Object target,
      TypeRef<A> argumentType,
      List<ParameterResolver<? super A>> extraResolvers);
}
