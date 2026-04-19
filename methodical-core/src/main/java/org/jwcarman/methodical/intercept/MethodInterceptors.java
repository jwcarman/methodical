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

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Factory methods for common {@link MethodInterceptor} patterns. */
public final class MethodInterceptors {

  private MethodInterceptors() {}

  /**
   * Runs {@code action} before {@link MethodInvocation#proceed()}. If {@code action} throws, the
   * chain short-circuits and the exception propagates unchanged.
   */
  public static <A> MethodInterceptor<A> before(
      Consumer<? super MethodInvocation<? extends A>> action) {
    Objects.requireNonNull(action, "action");
    return invocation -> {
      action.accept(invocation);
      return invocation.proceed();
    };
  }

  /**
   * Runs {@code action} only on normal return from {@link MethodInvocation#proceed()}. If the chain
   * throws, the action is skipped and the exception propagates unchanged.
   */
  public static <A> MethodInterceptor<A> onSuccess(
      BiConsumer<? super MethodInvocation<? extends A>, Object> action) {
    Objects.requireNonNull(action, "action");
    return invocation -> {
      Object result = invocation.proceed();
      action.accept(invocation, result);
      return result;
    };
  }

  /**
   * Binds the given {@link ScopedValue} around {@link MethodInvocation#proceed()} when {@code
   * supplier} returns a non-empty {@link Optional}; otherwise invokes the chain unchanged.
   */
  public static <A, T> MethodInterceptor<A> scopedValue(
      ScopedValue<T> scopedValue,
      Function<? super MethodInvocation<? extends A>, Optional<T>> supplier) {
    Objects.requireNonNull(scopedValue, "scopedValue");
    Objects.requireNonNull(supplier, "supplier");
    return invocation -> {
      Optional<T> value = supplier.apply(invocation);
      if (value.isEmpty()) {
        return invocation.proceed();
      }
      return ScopedValue.where(scopedValue, value.get()).call(invocation::proceed);
    };
  }
}
