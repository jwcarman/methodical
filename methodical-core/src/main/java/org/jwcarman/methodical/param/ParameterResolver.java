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
package org.jwcarman.methodical.param;

import java.util.Optional;

/**
 * Produces a per-parameter {@link Binding} at invoker-build time.
 *
 * <p>A resolver's {@link #bind(ParameterInfo)} is called once per parameter when the {@link
 * org.jwcarman.methodical.MethodInvoker} is constructed. Returning {@link Optional#empty()} means
 * "I don't apply to this parameter" (the factory continues down the resolver list). Returning a
 * non-empty {@link Optional} supplies a {@link Binding} that is invoked once per parameter per
 * invocation. Expensive per-parameter setup (reader construction, annotation lookup, name
 * resolution) belongs inside {@code bind} so the binding's {@link Binding#resolve} is as cheap as
 * possible.
 */
@FunctionalInterface
public interface ParameterResolver<A> {

  /**
   * Returns a {@link Binding} for {@code info} if this resolver applies to that parameter, or
   * {@link Optional#empty()} otherwise.
   */
  Optional<Binding<A>> bind(ParameterInfo info);

  /**
   * A pre-bound resolver for a specific parameter. Holds all per-parameter state needed to produce
   * the value at invocation time — {@link #resolve(Object)} is the hot path and should avoid
   * per-call work beyond consulting the supplied argument.
   */
  @FunctionalInterface
  interface Binding<A> {
    Object resolve(A argument);
  }
}
