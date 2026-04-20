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
import org.jwcarman.methodical.param.ParameterResolver;

/**
 * Default {@link MethodInvoker} implementation. Consults per-parameter {@link
 * ParameterResolver.Binding}s to produce arguments, then dispatches through the configured
 * interceptor chain, terminating in a reflective method invocation.
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
  private final List<ParameterResolver.Binding<? super A>> bindings;
  private final List<MethodInterceptor<? super A>> interceptors;

  @SuppressWarnings(
      "java:S3011") // setAccessible is load-bearing for a reflective invocation library
  DefaultMethodInvoker(
      Method method,
      Object target,
      List<ParameterResolver.Binding<? super A>> bindings,
      List<MethodInterceptor<? super A>> interceptors) {
    this.method = method;
    this.target = target;
    this.bindings = bindings;
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
    Object[] args = new Object[bindings.size()];
    for (int i = 0; i < bindings.size(); i++) {
      args[i] = bindings.get(i).resolve(argument);
    }
    return args;
  }

  /**
   * Shared {@link MethodInvocation} instance for the duration of a single {@code invoke(A)} call.
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
      final int next = index + 1;
      if (next == interceptors.size()) {
        // Terminal: no state to restore; the reflective call doesn't call back into proceed().
        return invokeMethod(parameters);
      }
      // Advance the cursor for the duration of the nested interceptor's intercept() call, then
      // restore. The restore is what keeps retry semantics working: if the caller invokes
      // proceed() again, we re-enter the chain from the caller's position and re-run the
      // remaining steps.
      final int resumePoint = index;
      index = next;
      try {
        return interceptors.get(next).intercept(this);
      } finally {
        index = resumePoint;
      }
    }

    @Override
    public String toString() {
      return "MethodInvocation["
          + method.getDeclaringClass().getSimpleName()
          + "."
          + method.getName()
          + "]";
    }
  }
}
