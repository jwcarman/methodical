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

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MethodInvocationTest {

  static class Target {
    public String greet(String n) {
      return "hi " + n;
    }
  }

  private Method method() throws Exception {
    return Target.class.getMethod("greet", String.class);
  }

  @Test
  void of_exposes_method_target_argument() throws Exception {
    Object target = new Target();
    MethodInvocation<String> inv =
        MethodInvocation.of(method(), target, "world", new Object[] {"world"}, () -> "x");
    assertThat(inv.method()).isEqualTo(method());
    assertThat(inv.target()).isSameAs(target);
    assertThat(inv.argument()).isEqualTo("world");
  }

  @Test
  void resolved_parameters_returns_fresh_copy_each_call() throws Exception {
    Object[] seed = {"a", 1};
    MethodInvocation<String> inv =
        MethodInvocation.of(method(), new Target(), "s", seed, () -> null);
    Object[] first = inv.resolvedParameters();
    Object[] second = inv.resolvedParameters();
    assertThat(first).isNotSameAs(second).containsExactly("a", 1);
    assertThat(second).containsExactly("a", 1);
  }

  @Test
  void mutating_returned_array_does_not_affect_subsequent_calls() throws Exception {
    MethodInvocation<String> inv =
        MethodInvocation.of(method(), new Target(), "s", new Object[] {"a", 1}, () -> null);
    Object[] first = inv.resolvedParameters();
    first[0] = "mutated";
    first[1] = 999;
    assertThat(inv.resolvedParameters()).containsExactly("a", 1);
  }

  @Test
  void mutating_constructor_input_does_not_affect_invocation() throws Exception {
    Object[] input = {"a", 1};
    MethodInvocation<String> inv =
        MethodInvocation.of(method(), new Target(), "s", input, () -> null);
    input[0] = "mutated";
    input[1] = 999;
    assertThat(inv.resolvedParameters()).containsExactly("a", 1);
  }

  @Test
  void resolved_parameters_supports_null_elements() throws Exception {
    MethodInvocation<String> inv =
        MethodInvocation.of(
            method(), new Target(), "s", new Object[] {null, "x", null}, () -> null);
    assertThat(inv.resolvedParameters()).containsExactly(null, "x", null);
  }

  @Test
  void proceed_delegates_to_continuation_supplier() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    MethodInvocation<String> inv =
        MethodInvocation.of(
            method(),
            new Target(),
            "s",
            new Object[0],
            () -> {
              calls.incrementAndGet();
              return "result";
            });
    assertThat(inv.proceed()).isEqualTo("result");
    assertThat(calls.get()).isEqualTo(1);
  }

  @Test
  void equals_and_hashCode_are_value_based_over_method_target_argument_parameters()
      throws Exception {
    Object target = new Target();
    MethodInvocation<String> a =
        MethodInvocation.of(method(), target, "x", new Object[] {"x", 1}, () -> "A");
    MethodInvocation<String> b =
        MethodInvocation.of(method(), target, "x", new Object[] {"x", 1}, () -> "B-different");
    assertThat(a).isEqualTo(b);
    assertThat(a).hasSameHashCodeAs(b);
  }

  @Test
  void equals_is_false_when_argument_differs() throws Exception {
    Object target = new Target();
    MethodInvocation<String> a =
        MethodInvocation.of(method(), target, "x", new Object[] {"x"}, () -> null);
    MethodInvocation<String> b =
        MethodInvocation.of(method(), target, "y", new Object[] {"x"}, () -> null);
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_is_false_when_parameters_differ() throws Exception {
    Object target = new Target();
    MethodInvocation<String> a =
        MethodInvocation.of(method(), target, "x", new Object[] {"a"}, () -> null);
    MethodInvocation<String> b =
        MethodInvocation.of(method(), target, "x", new Object[] {"b"}, () -> null);
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void equals_is_false_when_target_differs() throws Exception {
    MethodInvocation<String> a =
        MethodInvocation.of(method(), new Target(), "x", new Object[0], () -> null);
    MethodInvocation<String> b =
        MethodInvocation.of(method(), new Target(), "x", new Object[0], () -> null);
    assertThat(a).isNotEqualTo(b); // distinct target instances
  }

  @Test
  void equals_is_false_for_non_method_invocation() throws Exception {
    MethodInvocation<String> a =
        MethodInvocation.of(method(), new Target(), "x", new Object[0], () -> null);
    assertThat(a).isNotEqualTo("not an invocation");
    assertThat(a).isNotEqualTo(null);
  }

  @Test
  void toString_renders_only_method_identity_and_does_not_leak_sensitive_state() throws Exception {
    MethodInvocation<String> inv =
        MethodInvocation.of(
            method(),
            new Target(),
            "super-secret-password",
            new Object[] {"super-secret-password", "another-secret"},
            () -> {
              throw new AssertionError("continuation should not be invoked by toString");
            });
    String s = inv.toString();
    assertThat(s).contains("Target").contains("greet");
    assertThat(s)
        .doesNotContain("super-secret-password")
        .doesNotContain("another-secret")
        .doesNotContain("Lambda")
        .doesNotContain("continuation");
  }

  @Test
  void proceed_can_be_called_multiple_times() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    MethodInvocation<String> inv =
        MethodInvocation.of(
            method(), new Target(), "s", new Object[0], () -> "call#" + calls.incrementAndGet());
    assertThat(inv.proceed()).isEqualTo("call#1");
    assertThat(inv.proceed()).isEqualTo("call#2");
    assertThat(inv.proceed()).isEqualTo("call#3");
  }
}
