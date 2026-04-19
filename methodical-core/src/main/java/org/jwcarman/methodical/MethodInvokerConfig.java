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

import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.param.ParameterResolver;

/**
 * Per-invoker configuration presented to the customizer passed to {@link
 * MethodInvokerFactory#create}.
 *
 * <p>Both {@link #resolver} and {@link #interceptor} return {@code this} for chaining. Resolvers
 * are consulted in registration order; the first matching resolver wins, falling through to the
 * built-in {@code @Argument} resolver as a last resort. Interceptors run in registration order: the
 * first interceptor added is outermost and runs first; the last interceptor added runs closest to
 * the reflective method invocation.
 */
public interface MethodInvokerConfig<A> {

  MethodInvokerConfig<A> resolver(ParameterResolver<? super A> resolver);

  MethodInvokerConfig<A> interceptor(MethodInterceptor<? super A> interceptor);
}
