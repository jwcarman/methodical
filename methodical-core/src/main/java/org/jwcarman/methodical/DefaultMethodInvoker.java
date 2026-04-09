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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Default {@link MethodInvoker} implementation that resolves arguments via assigned {@link
 * ParameterResolver}s and invokes the method reflectively.
 */
class DefaultMethodInvoker<A> implements MethodInvoker<A> {

  private final Method method;
  private final Object target;
  private final ParameterInfo[] paramInfos;
  private final ParameterResolver<?>[] resolvers;
  private final boolean isVoid;

  DefaultMethodInvoker(
      Method method, Object target, ParameterInfo[] paramInfos, ParameterResolver<?>[] resolvers) {
    this.method = method;
    this.target = target;
    this.paramInfos = paramInfos;
    this.resolvers = resolvers;
    this.isVoid = method.getReturnType() == void.class || method.getReturnType() == Void.class;
  }

  @Override
  public Object invoke(A argument) {
    Object[] args = resolveArguments(argument);
    try {
      Object result = method.invoke(target, args);
      return isVoid ? null : result;
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

  @SuppressWarnings("unchecked")
  private Object[] resolveArguments(A argument) {
    Object[] args = new Object[paramInfos.length];
    for (int i = 0; i < paramInfos.length; i++) {
      if (resolvers[i] != null) {
        args[i] = ((ParameterResolver<Object>) resolvers[i]).resolve(paramInfos[i], argument);
      }
    }
    return args;
  }
}
