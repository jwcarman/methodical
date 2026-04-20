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
package org.jwcarman.methodical.intercept;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * View of a single method invocation passed through the interceptor chain.
 *
 * <p>Interceptors receive a {@code MethodInvocation} and decide whether and when to call {@link
 * #proceed()} to continue down the chain. An interceptor may short-circuit (return without calling
 * {@code proceed}), observe, wrap, or retry the invocation.
 *
 * <p>{@link #resolvedParameters()} returns a defensive copy on each call; mutating the returned
 * array is permitted and has no effect on subsequent invocations or on the reflective call itself.
 */
public interface MethodInvocation<A> {

  /** The method being invoked. */
  Method method();

  /** The target instance on which the method is invoked; may be {@code null} for static methods. */
  Object target();

  /** The raw argument passed to {@link org.jwcarman.methodical.MethodInvoker#invoke(Object)}. */
  A argument();

  /**
   * Resolved parameter values, in declaration order. A fresh copy of the internal array is returned
   * on every call — callers may freely mutate the result.
   */
  Object[] resolvedParameters();

  /**
   * Continues the interceptor chain.
   *
   * <p>May be called zero, one, or many times. Each call re-runs any remaining interceptors and,
   * ultimately, the reflective method invocation. Most interceptors call this exactly once; retry
   * interceptors may call it in a loop.
   *
   * <p>Thread affinity: implementations may require {@code proceed()} to be called on the same
   * thread that received {@code intercept(...)}. Cross-thread continuations — e.g. handing the
   * invocation to a pool executor and calling {@code proceed()} from the worker thread — are not
   * supported.
   */
  Object proceed();

  /**
   * Constructs a {@code MethodInvocation} for testing or for custom uses.
   *
   * <p>The supplied {@code resolvedParameters} array is defensively copied on construction and on
   * every call to {@link #resolvedParameters()}. {@code continuation} is invoked each time {@link
   * #proceed()} is called.
   */
  static <A> MethodInvocation<A> of(
      Method method,
      Object target,
      A argument,
      Object[] resolvedParameters,
      Supplier<Object> continuation) {
    return new DefaultMethodInvocation<>(
        method, target, argument, resolvedParameters, continuation);
  }
}
