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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jwcarman.methodical.Argument;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

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
  public <A> MethodInvoker<A> create(
      Method method,
      Object target,
      TypeRef<A> argumentType,
      List<ParameterResolver<? super A>> extraResolvers) {
    Parameter[] parameters = method.getParameters();
    ParameterInfo[] paramInfos = new ParameterInfo[parameters.length];
    List<ParameterResolver<? super A>> assigned = new ArrayList<>(parameters.length);

    for (int i = 0; i < parameters.length; i++) {
      TypeRef<?> type = TypeRef.parameterType(parameters[i], target.getClass());
      paramInfos[i] = ParameterInfo.of(parameters[i], i, type);
      assigned.add(findResolver(method, argumentType, extraResolvers, paramInfos[i]));
    }

    return new DefaultMethodInvoker<>(method, target, paramInfos, assigned);
  }

  private <A> ParameterResolver<? super A> findResolver(
      Method method,
      TypeRef<A> argumentType,
      List<ParameterResolver<? super A>> extraResolvers,
      ParameterInfo paramInfo) {
    if (paramInfo.hasAnnotation(Argument.class)) {
      if (!paramInfo.accepts(argumentType)) {
        throw new ParameterResolutionException(
            String.format(
                "@Argument parameter \"%s\" (type %s) on %s is not assignable from argument type %s",
                paramInfo.name(),
                paramInfo.genericType().getTypeName(),
                describe(method),
                argumentType.getType().getTypeName()));
      }
      return new ArgumentParameterResolver<>(argumentType);
    }

    ParameterResolver<? super A> extra =
        extraResolvers.stream().filter(r -> r.supports(paramInfo)).findFirst().orElse(null);
    if (extra != null) {
      return extra;
    }

    ParameterResolver<? super A> factoryResolver = findFactoryResolver(argumentType, paramInfo);
    if (factoryResolver != null) {
      return factoryResolver;
    }

    throw new ParameterResolutionException(
        String.format(
            "No resolver found for parameter \"%s\" (type %s) on %s. "
                + "Argument type: %s. Tried: extras=[%s], factory=[%s]. "
                + "Hint: annotate with @Argument, add a matching ParameterResolver, "
                + "or pass one via the per-invoker resolvers list.",
            paramInfo.name(),
            paramInfo.genericType().getTypeName(),
            describe(method),
            argumentType.getType().getTypeName(),
            extraResolvers.stream()
                .map(r -> r.getClass().getSimpleName())
                .collect(Collectors.joining(", ")),
            resolvers.stream()
                .map(r -> r.resolver().getClass().getSimpleName())
                .collect(Collectors.joining(", "))));
  }

  @SuppressWarnings("unchecked")
  private <A> ParameterResolver<? super A> findFactoryResolver(
      TypeRef<A> argumentType, ParameterInfo paramInfo) {
    return (ParameterResolver<? super A>)
        resolvers.stream()
            .filter(r -> r.argumentType().isAssignableFrom(argumentType))
            .map(ResolvedParameterResolver::resolver)
            .filter(r -> r.supports(paramInfo))
            .findFirst()
            .orElse(null);
  }

  private static String describe(Method method) {
    return method.getDeclaringClass().getSimpleName() + "." + method.getName();
  }

  private static <A> ResolvedParameterResolver<A> wrap(ParameterResolver<A> resolver) {
    TypeRef<?> argumentType =
        TypeRef.of(resolver.getClass())
            .typeArgument(ParameterResolver.class, 0)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unable to determine argument type for ParameterResolver: "
                            + resolver.getClass().getName()
                            + ". Declare the type parameter on a concrete subclass."));
    return new ResolvedParameterResolver<>(resolver, argumentType);
  }

  private record ResolvedParameterResolver<A>(
      ParameterResolver<A> resolver, TypeRef<?> argumentType) {}
}
