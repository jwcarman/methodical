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
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

record DefaultMethodInvocation<A>(
    Method method,
    Object target,
    A argument,
    Object[] resolvedParameters,
    Supplier<Object> continuation)
    implements MethodInvocation<A> {

  DefaultMethodInvocation {
    resolvedParameters = resolvedParameters.clone();
  }

  /** Defensive copy on every access; callers may mutate the returned array with no side effects. */
  @Override
  public Object[] resolvedParameters() {
    return resolvedParameters.clone();
  }

  @Override
  public Object proceed() {
    return continuation.get();
  }

  /**
   * Identity is defined by the call data: method, target, argument, resolvedParameters. The
   * continuation is an opaque chain-control lambda — not part of what the invocation represents —
   * so it is excluded from equals/hashCode and from toString.
   */
  @Override
  public boolean equals(Object other) {
    return other
            instanceof
            DefaultMethodInvocation<?>(Method m, Object t, Object arg, Object[] params, var _)
        && Objects.equals(method, m)
        && Objects.equals(target, t)
        && Objects.equals(argument, arg)
        && Arrays.equals(resolvedParameters, params);
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, target, argument, Arrays.hashCode(resolvedParameters));
  }

  @Override
  public String toString() {
    // Render the invoked method only. Argument and resolved parameters are deliberately
    // excluded because they may contain secrets, tokens, or PII — toString is often
    // written to logs unintentionally.
    return "MethodInvocation["
        + method.getDeclaringClass().getSimpleName()
        + "."
        + method.getName()
        + "]";
  }
}
