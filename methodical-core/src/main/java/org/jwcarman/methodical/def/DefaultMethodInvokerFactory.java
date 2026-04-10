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
package org.jwcarman.methodical.def;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.jwcarman.methodical.Argument;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.methodical.reflect.Types;

/** Default implementation of {@link MethodInvokerFactory}. */
public class DefaultMethodInvokerFactory implements MethodInvokerFactory {

  private final List<ResolvedParameterResolver<?>> resolvers;

  public DefaultMethodInvokerFactory(List<ParameterResolver<?>> resolvers) {
    this.resolvers =
        resolvers.stream()
            .<ResolvedParameterResolver<?>>map(DefaultMethodInvokerFactory::wrap)
            .toList();
  }

  @Override
  public <A> MethodInvoker<A> create(Method method, Object target, Class<A> argumentType) {
    Parameter[] parameters = method.getParameters();
    ParameterInfo[] paramInfos = new ParameterInfo[parameters.length];
    List<ParameterResolver<? super A>> assigned = new ArrayList<>(parameters.length);

    for (int i = 0; i < parameters.length; i++) {
      Class<?> resolvedType = Types.resolveParameterType(parameters[i], target.getClass());
      Type genericType = parameters[i].getParameterizedType();
      paramInfos[i] = ParameterInfo.of(parameters[i], i, resolvedType, genericType);
      assigned.add(findResolver(argumentType, paramInfos[i]));
    }

    return new DefaultMethodInvoker<>(method, target, paramInfos, assigned);
  }

  @SuppressWarnings("unchecked")
  private <A> ParameterResolver<? super A> findResolver(
      Class<A> argumentType, ParameterInfo paramInfo) {
    if (paramInfo.parameter().isAnnotationPresent(Argument.class)) {
      if (!paramInfo.resolvedType().isAssignableFrom(argumentType)) {
        throw new IllegalArgumentException(
            String.format(
                "@Argument parameter \"%s\" has type %s which is not assignable from argument type %s",
                paramInfo.name(), paramInfo.resolvedType().getName(), argumentType.getName()));
      }
      return new ParameterResolver<>() {
        @Override
        public boolean supports(ParameterInfo info) {
          return true;
        }

        @Override
        public Object resolve(ParameterInfo info, A argument) {
          return argument;
        }
      };
    }
    return (ParameterResolver<? super A>)
        resolvers.stream()
            .filter(r -> r.argumentType().isAssignableFrom(argumentType))
            .map(ResolvedParameterResolver::resolver)
            .filter(r -> r.supports(paramInfo))
            .findFirst()
            .orElse(null);
  }

  private static <A> ResolvedParameterResolver<A> wrap(ParameterResolver<A> resolver) {
    Class<A> argumentType =
        Types.typeParamFromClass(resolver.getClass(), ParameterResolver.class, 0);
    return new ResolvedParameterResolver<>(resolver, argumentType);
  }

  private record ResolvedParameterResolver<A>(
      ParameterResolver<A> resolver, Class<A> argumentType) {}
}
