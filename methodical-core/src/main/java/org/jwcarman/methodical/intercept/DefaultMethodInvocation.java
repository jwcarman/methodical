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
}
