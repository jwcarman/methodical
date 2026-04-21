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
import org.jwcarman.specular.TypeRef;

/**
 * A pre-built, reusable handle that invokes a {@link Method} on a target, resolving the method's
 * parameters from a single call-time argument of type {@code A}.
 *
 * <p>Obtain instances via {@link #builder(Method, Object, Class)} or {@link #builder(Method,
 * Object, TypeRef)}. The returned {@link Builder} lets you attach resolvers and interceptors
 * fluently before finalizing with {@link Builder#build()}.
 */
public interface MethodInvoker<A> {

  /**
   * Resolves parameter values from {@code argument} and invokes the underlying method, returning
   * the method's return value (or {@code null} for {@code void} returns).
   */
  Object invoke(A argument);

  /**
   * Returns an inert, human-readable snapshot of what this invoker wraps. Intended for
   * observability surfaces (actuator endpoints, logs, diagnostics) — not for invocation.
   */
  Descriptor describe();

  /**
   * Snapshot of the method an invoker wraps, plus a {@code toString()} view of its interceptor
   * chain in registration order (outermost first).
   */
  record Descriptor(String declaringClassName, String methodName, List<String> interceptors) {}

  /** Starts a fluent build of a {@code MethodInvoker<A>} for {@code method} on {@code target}. */
  static <A> Builder<A> builder(Method method, Object target, Class<A> argumentType) {
    return new DefaultMethodInvokerBuilder<>(method, target, TypeRef.of(argumentType));
  }

  /** Starts a fluent build with a parameterized argument type carried by a {@link TypeRef}. */
  static <A> Builder<A> builder(Method method, Object target, TypeRef<A> argumentType) {
    return new DefaultMethodInvokerBuilder<>(method, target, argumentType);
  }

  /**
   * Fluent builder for a {@link MethodInvoker}.
   *
   * <p>Resolvers and interceptors are added in registration order — the first matching resolver
   * wins, and interceptors run outermost-first around the reflective call. {@link #build()} is the
   * terminal step; it produces a new invoker each call, so the builder can be reused if needed.
   */
  interface Builder<A> {

    /**
     * Register a {@link ParameterResolver}. Later-registered resolvers are tried after earlier
     * ones.
     */
    Builder<A> resolver(ParameterResolver<? super A> resolver);

    /**
     * Register a {@link MethodInterceptor}. The first interceptor added is the outermost (runs
     * first); the last is innermost (runs closest to the reflective call).
     */
    Builder<A> interceptor(MethodInterceptor<? super A> interceptor);

    /** Builds the {@link MethodInvoker}. Each call produces an independent invoker. */
    MethodInvoker<A> build();
  }
}
