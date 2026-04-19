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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jwcarman.methodical.MethodInvokerConfig;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.param.ParameterResolver;

final class DefaultMethodInvokerConfig<A> implements MethodInvokerConfig<A> {

  private final List<ParameterResolver<? super A>> resolvers = new ArrayList<>();
  private final List<MethodInterceptor<? super A>> interceptors = new ArrayList<>();

  @Override
  public MethodInvokerConfig<A> resolver(ParameterResolver<? super A> resolver) {
    resolvers.add(Objects.requireNonNull(resolver, "resolver"));
    return this;
  }

  @Override
  public MethodInvokerConfig<A> interceptor(MethodInterceptor<? super A> interceptor) {
    interceptors.add(Objects.requireNonNull(interceptor, "interceptor"));
    return this;
  }

  List<ParameterResolver<? super A>> resolvers() {
    return List.copyOf(resolvers);
  }

  List<MethodInterceptor<? super A>> interceptors() {
    return List.copyOf(interceptors);
  }
}
