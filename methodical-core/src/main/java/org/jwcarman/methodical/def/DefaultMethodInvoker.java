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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.jwcarman.methodical.MethodInvocationException;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.intercept.MethodInvocation;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;

/**
 * Default {@link MethodInvoker} implementation. Resolves arguments via assigned {@link
 * ParameterResolver}s, then dispatches through the configured interceptor chain, terminating in a
 * reflective method invocation.
 *
 * <p>The chain executes via a single {@link Cursor} allocated per call that plays the {@link
 * MethodInvocation} role for every interceptor. {@code proceed()} advances an internal index to the
 * next step and restores it on return, so interceptors that call {@code proceed()} more than once
 * (retry) re-run the entire remaining chain on each call. With no interceptors, the cursor is
 * bypassed and the reflective call fires directly.
 */
class DefaultMethodInvoker<A> implements MethodInvoker<A> {

  private final Method method;
  private final Object target;
  private final ParameterInfo[] paramInfos;
  private final List<ParameterResolver<? super A>> resolvers;
  private final List<MethodInterceptor<? super A>> interceptors;

  @SuppressWarnings(
      "java:S3011") // setAccessible is load-bearing for a reflective invocation library
  DefaultMethodInvoker(
      Method method,
      Object target,
      ParameterInfo[] paramInfos,
      List<ParameterResolver<? super A>> resolvers,
      List<MethodInterceptor<? super A>> interceptors) {
    this.method = method;
    this.target = target;
    this.paramInfos = paramInfos;
    this.resolvers = resolvers;
    this.interceptors = interceptors;
    method.setAccessible(true);
  }

  @Override
  public Object invoke(A argument) {
    Object[] parameters = resolveArguments(argument);
    if (interceptors.isEmpty()) {
      return invokeMethod(parameters);
    }
    return new Cursor(argument, parameters).proceed();
  }

  private Object invokeMethod(Object[] parameters) {
    try {
      return method.invoke(target, parameters);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      throw new MethodInvocationException("Method invocation failed: " + cause.getMessage(), cause);
    } catch (IllegalAccessException e) {
      throw new MethodInvocationException("Method invocation failed: " + e.getMessage(), e);
    }
  }

  private Object[] resolveArguments(A argument) {
    Object[] args = new Object[paramInfos.length];
    for (int i = 0; i < paramInfos.length; i++) {
      args[i] = resolvers.get(i).resolve(paramInfos[i], argument);
    }
    return args;
  }

  /**
   * Shared {@link MethodInvocation} instance for the duration of a single {@code invoke(A)} call.
   *
   * <p>The interceptor chain is walked via a single integer cursor. {@link #proceed()} saves the
   * current index, advances, dispatches to the next step, and restores the index on return. That
   * save/restore preserves existing behavior for interceptors that call {@code proceed()} more than
   * once: each call re-enters the chain at the caller's position and re-runs the remaining
   * interceptors plus the reflective invocation. The same cursor instance is passed to every
   * interceptor in the chain — confirming this is the key observable property of the allocation
   * reduction.
   *
   * <p>Thread affinity: {@code proceed()} must be called on the same thread that received {@code
   * intercept()}. Cross-thread continuation is not supported.
   */
  private final class Cursor implements MethodInvocation<A> {

    private final A argument;
    private final Object[] parameters;
    private int index = -1;

    Cursor(A argument, Object[] parameters) {
      this.argument = argument;
      this.parameters = parameters;
    }

    @Override
    public Method method() {
      return method;
    }

    @Override
    public Object target() {
      return target;
    }

    @Override
    public A argument() {
      return argument;
    }

    @Override
    public Object[] resolvedParameters() {
      return parameters.clone();
    }

    @Override
    public Object proceed() {
      int saved = index;
      index = saved + 1;
      try {
        if (index < interceptors.size()) {
          return interceptors.get(index).intercept(this);
        }
        return invokeMethod(parameters);
      } finally {
        index = saved;
      }
    }

    @Override
    public String toString() {
      // Method identity only — see DefaultMethodInvocation for the rationale (log-safety).
      return "MethodInvocation["
          + method.getDeclaringClass().getSimpleName()
          + "."
          + method.getName()
          + "]";
    }
  }
}
