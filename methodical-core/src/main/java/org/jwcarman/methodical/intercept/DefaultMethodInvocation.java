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

  // equals/hashCode/toString overridden so the Object[] component participates by deep value
  // rather than by reference identity (the default record behavior).

  @Override
  public boolean equals(Object other) {
    return other instanceof DefaultMethodInvocation<?> that
        && Objects.equals(method, that.method)
        && Objects.equals(target, that.target)
        && Objects.equals(argument, that.argument)
        && Arrays.equals(resolvedParameters, that.resolvedParameters)
        && Objects.equals(continuation, that.continuation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        method, target, argument, Arrays.hashCode(resolvedParameters), continuation);
  }

  @Override
  public String toString() {
    return "DefaultMethodInvocation[method="
        + method
        + ", target="
        + target
        + ", argument="
        + argument
        + ", resolvedParameters="
        + Arrays.toString(resolvedParameters)
        + ", continuation="
        + continuation
        + "]";
  }
}
