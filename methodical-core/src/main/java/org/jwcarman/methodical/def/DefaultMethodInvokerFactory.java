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
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerConfig;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.ParameterResolutionException;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/**
 * Default {@link MethodInvokerFactory}. Holds no ambient state — all configuration flows through
 * the per-invoker {@code customizer}. The only resolver the factory itself supplies is the built-in
 * {@link ArgumentParameterResolver} for parameters annotated with {@link
 * org.jwcarman.methodical.Argument}.
 */
public class DefaultMethodInvokerFactory implements MethodInvokerFactory {

  @Override
  public <A> MethodInvoker<A> create(
      Method method,
      Object target,
      TypeRef<A> argumentType,
      Consumer<MethodInvokerConfig<A>> customizer) {
    DefaultMethodInvokerConfig<A> config = new DefaultMethodInvokerConfig<>();
    customizer.accept(config);

    List<ParameterResolver<? super A>> resolvers = new ArrayList<>(config.resolvers());
    resolvers.add(new ArgumentParameterResolver<>(argumentType));

    Parameter[] parameters = method.getParameters();
    ParameterInfo[] paramInfos = new ParameterInfo[parameters.length];
    List<ParameterResolver.Binding<? super A>> bindings = new ArrayList<>(parameters.length);

    for (int i = 0; i < parameters.length; i++) {
      TypeRef<?> type = TypeRef.parameterType(parameters[i], target.getClass());
      paramInfos[i] = ParameterInfo.of(parameters[i], i, type);
      bindings.add(bindParameter(method, argumentType, resolvers, paramInfos[i]));
    }

    return new DefaultMethodInvoker<>(method, target, bindings, config.interceptors());
  }

  private <A> ParameterResolver.Binding<? super A> bindParameter(
      Method method,
      TypeRef<A> argumentType,
      List<ParameterResolver<? super A>> resolvers,
      ParameterInfo paramInfo) {
    for (ParameterResolver<? super A> resolver : resolvers) {
      Optional<? extends ParameterResolver.Binding<? super A>> bound = resolver.bind(paramInfo);
      if (bound.isPresent()) {
        return bound.get();
      }
    }
    throw new ParameterResolutionException(
        String.format(
            "No resolver found for parameter \"%s\" (type %s) on %s. "
                + "Argument type: %s. Tried: [%s]. "
                + "Hint: annotate with @Argument or add a matching ParameterResolver via the customizer.",
            paramInfo.name(),
            paramInfo.genericType().getTypeName(),
            describe(method),
            argumentType.getType().getTypeName(),
            resolvers.stream()
                .map(r -> r.getClass().getSimpleName())
                .collect(Collectors.joining(", "))));
  }

  private static String describe(Method method) {
    return method.getDeclaringClass().getSimpleName() + "." + method.getName();
  }
}
