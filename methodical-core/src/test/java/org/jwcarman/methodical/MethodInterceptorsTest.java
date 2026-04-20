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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MethodInterceptorsTest {

  static class Target {
    public String m() {
      return "impl";
    }
  }

  private MethodInvocation<String> invocation(String result) throws Exception {
    Method m = Target.class.getMethod("m");
    return MethodInvocation.of(m, new Target(), "arg", new Object[0], () -> result);
  }

  // --- before ---

  @Test
  void before_runs_action_then_proceeds() throws Exception {
    AtomicReference<String> seen = new AtomicReference<>();
    MethodInterceptor<String> interceptor =
        MethodInterceptors.before(inv -> seen.set(inv.argument()));
    Object result = interceptor.intercept(invocation("ok"));
    assertThat(seen.get()).isEqualTo("arg");
    assertThat(result).isEqualTo("ok");
  }

  @Test
  void before_short_circuits_when_action_throws() throws Exception {
    AtomicInteger proceeded = new AtomicInteger();
    Method m = Target.class.getMethod("m");
    MethodInvocation<String> inv =
        MethodInvocation.of(
            m,
            new Target(),
            "a",
            new Object[0],
            () -> {
              proceeded.incrementAndGet();
              return null;
            });
    MethodInterceptor<String> interceptor =
        MethodInterceptors.before(
            i -> {
              throw new IllegalStateException("stop");
            });
    assertThatThrownBy(() -> interceptor.intercept(inv))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("stop");
    assertThat(proceeded.get()).isZero();
  }

  @Test
  void before_rejects_null_action() {
    Consumer<MethodInvocation<? extends String>> nullAction = null;
    assertThatThrownBy(() -> MethodInterceptors.<String>before(nullAction))
        .isInstanceOf(NullPointerException.class);
  }

  // --- onSuccess ---

  @Test
  void onSuccess_runs_action_on_normal_return() throws Exception {
    AtomicReference<Object> seenResult = new AtomicReference<>();
    MethodInterceptor<String> interceptor =
        MethodInterceptors.onSuccess((inv, r) -> seenResult.set(r));
    Object result = interceptor.intercept(invocation("ok"));
    assertThat(seenResult.get()).isEqualTo("ok");
    assertThat(result).isEqualTo("ok");
  }

  @Test
  void onSuccess_skipped_when_chain_throws() throws Exception {
    AtomicInteger actionCalls = new AtomicInteger();
    Method m = Target.class.getMethod("m");
    MethodInvocation<String> inv =
        MethodInvocation.of(
            m,
            new Target(),
            "a",
            new Object[0],
            () -> {
              throw new RuntimeException("boom");
            });
    MethodInterceptor<String> interceptor =
        MethodInterceptors.onSuccess((i, r) -> actionCalls.incrementAndGet());
    assertThatThrownBy(() -> interceptor.intercept(inv))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("boom");
    assertThat(actionCalls.get()).isZero();
  }

  @Test
  void onSuccess_rejects_null_action() {
    BiConsumer<MethodInvocation<? extends String>, Object> nullAction = null;
    assertThatThrownBy(() -> MethodInterceptors.<String>onSuccess(nullAction))
        .isInstanceOf(NullPointerException.class);
  }

  // --- scopedValue ---

  @Test
  void scopedValue_binds_when_supplier_returns_value() throws Exception {
    ScopedValue<String> sv = ScopedValue.newInstance();
    AtomicReference<String> observed = new AtomicReference<>();
    Method m = Target.class.getMethod("m");
    MethodInvocation<String> inv =
        MethodInvocation.of(
            m,
            new Target(),
            "a",
            new Object[0],
            () -> {
              observed.set(sv.isBound() ? sv.get() : null);
              return "ok";
            });
    MethodInterceptor<String> interceptor =
        MethodInterceptors.scopedValue(sv, i -> Optional.of("bound-value"));
    interceptor.intercept(inv);
    assertThat(observed.get()).isEqualTo("bound-value");
  }

  @Test
  void scopedValue_skips_binding_when_supplier_empty() throws Exception {
    ScopedValue<String> sv = ScopedValue.newInstance();
    AtomicReference<Boolean> wasBound = new AtomicReference<>();
    Method m = Target.class.getMethod("m");
    MethodInvocation<String> inv =
        MethodInvocation.of(
            m,
            new Target(),
            "a",
            new Object[0],
            () -> {
              wasBound.set(sv.isBound());
              return "ok";
            });
    MethodInterceptor<String> interceptor =
        MethodInterceptors.scopedValue(sv, i -> Optional.empty());
    interceptor.intercept(inv);
    assertThat(wasBound.get()).isFalse();
  }

  @Test
  void scopedValue_rejects_null_scoped_value() {
    Function<MethodInvocation<? extends String>, Optional<String>> supplier = i -> Optional.empty();
    assertThatThrownBy(() -> MethodInterceptors.<String, String>scopedValue(null, supplier))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void scopedValue_rejects_null_supplier() {
    ScopedValue<String> sv = ScopedValue.newInstance();
    assertThatThrownBy(() -> MethodInterceptors.<String, String>scopedValue(sv, null))
        .isInstanceOf(NullPointerException.class);
  }
}
