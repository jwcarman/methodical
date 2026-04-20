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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jwcarman.specular.TypeRef;

/**
 * Default implementation of {@link MethodInvoker.Builder}. Obtained via {@link
 * MethodInvoker#builder(Method, Object, Class)} / {@link MethodInvoker#builder(Method, Object,
 * TypeRef)}.
 */
final class DefaultMethodInvokerBuilder<A> implements MethodInvoker.Builder<A> {

  private final Method method;
  private final Object target;
  private final TypeRef<A> argumentType;
  private final List<ParameterResolver<? super A>> resolvers = new ArrayList<>();
  private final List<MethodInterceptor<? super A>> interceptors = new ArrayList<>();

  DefaultMethodInvokerBuilder(Method method, Object target, TypeRef<A> argumentType) {
    this.method = Objects.requireNonNull(method, "method");
    this.target = Objects.requireNonNull(target, "target");
    this.argumentType = Objects.requireNonNull(argumentType, "argumentType");
  }

  @Override
  public MethodInvoker.Builder<A> resolver(ParameterResolver<? super A> resolver) {
    resolvers.add(Objects.requireNonNull(resolver, "resolver"));
    return this;
  }

  @Override
  public MethodInvoker.Builder<A> interceptor(MethodInterceptor<? super A> interceptor) {
    interceptors.add(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  @Override
  public MethodInvoker<A> build() {
    List<ParameterResolver<? super A>> effective = new ArrayList<>(resolvers);
    effective.add(new ArgumentParameterResolver<>(argumentType));

    Parameter[] parameters = method.getParameters();
    List<ParameterResolver.Binding<? super A>> bindings = new ArrayList<>(parameters.length);

    for (int i = 0; i < parameters.length; i++) {
      TypeRef<?> type = TypeRef.parameterType(parameters[i], target.getClass());
      ParameterInfo info = ParameterInfo.of(parameters[i], i, type);
      bindings.add(bindParameter(effective, info));
    }

    return new DefaultMethodInvoker<>(method, target, bindings, List.copyOf(interceptors));
  }

  private ParameterResolver.Binding<? super A> bindParameter(
      List<ParameterResolver<? super A>> effective, ParameterInfo info) {
    for (ParameterResolver<? super A> resolver : effective) {
      Optional<? extends ParameterResolver.Binding<? super A>> bound = resolver.bind(info);
      if (bound.isPresent()) {
        return bound.get();
      }
    }
    throw new ParameterResolutionException(
        String.format(
            "No resolver found for parameter \"%s\" (type %s) on %s. "
                + "Argument type: %s. Tried: [%s]. "
                + "Hint: annotate with @Argument or add a matching ParameterResolver.",
            info.name(),
            info.genericType().getTypeName(),
            method.getDeclaringClass().getSimpleName() + "." + method.getName(),
            argumentType.getType().getTypeName(),
            effective.stream()
                .map(r -> r.getClass().getSimpleName())
                .collect(Collectors.joining(", "))));
  }
}
