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
 * ParameterResolver}s and dispatches through a pre-built chain of {@link Call}s.
 *
 * <p>The chain is assembled once at construction time by folding from the last interceptor back to
 * the first: the reflective call ({@link MethodCall}) is wrapped by the last interceptor, that
 * result is wrapped by the previous, and so on. With no interceptors, the chain is just a {@link
 * MethodCall}. {@link MethodCall} and {@link InterceptorCall} are non-static inner classes so they
 * can read {@code method} and {@code target} directly from the enclosing invoker.
 */
class DefaultMethodInvoker<A> implements MethodInvoker<A> {

  private final Method method;
  private final Object target;
  private final ParameterInfo[] paramInfos;
  private final List<ParameterResolver<? super A>> resolvers;
  private final Call<A> chain;

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
    method.setAccessible(true);
    Call<A> call = new MethodCall();
    for (MethodInterceptor<? super A> interceptor : interceptors.reversed()) {
      call = new InterceptorCall(interceptor, call);
    }
    this.chain = call;
  }

  @Override
  public Object invoke(A argument) {
    Object[] parameters = resolveArguments(argument);
    return chain.invoke(argument, parameters);
  }

  private Object[] resolveArguments(A argument) {
    Object[] args = new Object[paramInfos.length];
    for (int i = 0; i < paramInfos.length; i++) {
      args[i] = resolvers.get(i).resolve(paramInfos[i], argument);
    }
    return args;
  }

  /** A step in the pre-built chain — either {@link MethodCall} or {@link InterceptorCall}. */
  @FunctionalInterface
  private interface Call<T> {
    Object invoke(T argument, Object[] parameters);
  }

  /** Terminal {@link Call} that invokes the underlying method reflectively. */
  private final class MethodCall implements Call<A> {
    @Override
    public Object invoke(A argument, Object[] parameters) {
      try {
        return method.invoke(target, parameters);
      } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException re) {
          throw re;
        }
        throw new MethodInvocationException(
            "Method invocation failed: " + cause.getMessage(), cause);
      } catch (IllegalAccessException e) {
        throw new MethodInvocationException("Method invocation failed: " + e.getMessage(), e);
      }
    }
  }

  /**
   * {@link Call} that applies an interceptor around a next step. Builds a public {@link
   * MethodInvocation} whose {@code proceed()} drives the next step.
   */
  private final class InterceptorCall implements Call<A> {
    private final MethodInterceptor<? super A> interceptor;
    private final Call<A> next;

    InterceptorCall(MethodInterceptor<? super A> interceptor, Call<A> next) {
      this.interceptor = interceptor;
      this.next = next;
    }

    @Override
    public Object invoke(A argument, Object[] parameters) {
      MethodInvocation<A> invocation =
          MethodInvocation.of(
              method, target, argument, parameters, () -> next.invoke(argument, parameters));
      return interceptor.intercept(invocation);
    }
  }
}
