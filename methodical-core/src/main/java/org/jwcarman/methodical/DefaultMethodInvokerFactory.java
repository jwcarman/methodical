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
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.reflect.TypeUtils;

/** Default implementation of {@link MethodInvokerFactory}. */
public class DefaultMethodInvokerFactory implements MethodInvokerFactory {

  private final List<ResolvedParameterResolver> resolvers;

  public DefaultMethodInvokerFactory(List<ParameterResolver<?>> resolvers) {
    this.resolvers = resolvers.stream().map(ResolvedParameterResolver::new).toList();
  }

  @Override
  public <A> MethodInvoker<A> create(Method method, Object target, Class<A> argumentType) {
    Parameter[] parameters = method.getParameters();
    ParameterInfo[] paramInfos = new ParameterInfo[parameters.length];
    DefaultMethodInvoker.ParameterResolverAssignment[] assignments =
        new DefaultMethodInvoker.ParameterResolverAssignment[parameters.length];

    for (int i = 0; i < parameters.length; i++) {
      Type genericType = parameters[i].getParameterizedType();
      Class<?> resolvedType =
          TypeUtils.getRawType(genericType, target.getClass()) != null
              ? TypeUtils.getRawType(genericType, target.getClass())
              : parameters[i].getType();
      paramInfos[i] = ParameterInfo.of(parameters[i], i, resolvedType, genericType);

      for (ResolvedParameterResolver resolver : resolvers) {
        if (resolver.argumentType().isAssignableFrom(argumentType)
            && resolver.supports(paramInfos[i])) {
          assignments[i] =
              new DefaultMethodInvoker.ParameterResolverAssignment(resolver.delegate());
          break;
        }
      }
    }

    return new DefaultMethodInvoker<>(method, target, paramInfos, assignments);
  }

  private static class ResolvedParameterResolver {
    private final ParameterResolver<?> resolver;
    private final Class<?> argumentType;

    ResolvedParameterResolver(ParameterResolver<?> resolver) {
      this.resolver = resolver;
      this.argumentType = resolveArgumentType(resolver);
    }

    ParameterResolver<?> delegate() {
      return resolver;
    }

    Class<?> argumentType() {
      return argumentType;
    }

    boolean supports(ParameterInfo info) {
      return resolver.supports(info);
    }

    private static Class<?> resolveArgumentType(ParameterResolver<?> resolver) {
      Map<java.lang.reflect.TypeVariable<?>, Type> typeArgs =
          TypeUtils.getTypeArguments(resolver.getClass(), ParameterResolver.class);
      if (typeArgs != null && !typeArgs.isEmpty()) {
        Type argType = typeArgs.values().iterator().next();
        Class<?> raw = TypeUtils.getRawType(argType, null);
        if (raw != null) {
          return raw;
        }
      }
      return Object.class;
    }
  }
}
