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
package org.jwcarman.methodical.jakarta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvocation;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JakartaValidationInterceptorTest {

  interface GroupA {}

  interface GroupB {}

  public static class Service {
    public String echo(@NotBlank String s) {
      return s;
    }

    @NotNull
    public String blankReturn() {
      return "";
    }

    @NotNull
    public String nullReturn() {
      return null;
    }

    public static String staticEcho(@NotBlank String s) {
      return s;
    }

    @ValidationGroups({GroupA.class})
    public String groupedEcho(@NotBlank(groups = GroupA.class) String s) {
      return s;
    }

    @ValidationGroups({GroupB.class})
    public String otherGroupEcho(@NotBlank(groups = GroupA.class) String s) {
      return s;
    }
  }

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  private final JakartaValidationInterceptor interceptor =
      new JakartaValidationInterceptor(validator);

  private <A> MethodInvocation<A> invocationOf(
      Method method, Object target, A argument, Object[] params, Object result) {
    return MethodInvocation.of(method, target, argument, params, () -> result);
  }

  @Test
  void toString_describes_what_the_interceptor_does() {
    assertThat(interceptor).hasToString("Jakarta Bean Validation");
  }

  @Test
  void constructor_rejects_null_validator() {
    assertThatThrownBy(() -> new JakartaValidationInterceptor(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void valid_parameters_pass_through() throws Exception {
    Method m = Service.class.getMethod("echo", String.class);
    Object result =
        interceptor.intercept(invocationOf(m, new Service(), "arg", new Object[] {"hi"}, "hi"));
    assertThat(result).isEqualTo("hi");
  }

  @Test
  void invalid_parameters_throw_constraint_violation() throws Exception {
    Method m = Service.class.getMethod("echo", String.class);
    MethodInvocation<String> inv =
        invocationOf(m, new Service(), "arg", new Object[] {""}, "unreached");
    assertThatThrownBy(() -> interceptor.intercept(inv))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void invalid_parameters_prevents_method_invocation() throws Exception {
    AtomicInteger proceedCalls = new AtomicInteger();
    Method m = Service.class.getMethod("echo", String.class);
    MethodInvocation<String> inv =
        MethodInvocation.of(
            m,
            new Service(),
            "arg",
            new Object[] {""},
            () -> {
              proceedCalls.incrementAndGet();
              return null;
            });
    assertThatThrownBy(() -> interceptor.intercept(inv))
        .isInstanceOf(ConstraintViolationException.class);
    assertThat(proceedCalls.get()).isZero();
  }

  @Test
  void invalid_return_value_throws_constraint_violation() throws Exception {
    Method m = Service.class.getMethod("nullReturn");
    MethodInvocation<Object> inv = invocationOf(m, new Service(), null, new Object[0], null);
    assertThatThrownBy(() -> interceptor.intercept(inv))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void valid_return_value_passes() throws Exception {
    Method m = Service.class.getMethod("blankReturn");
    // @NotNull allows empty string; should pass
    Object result = interceptor.intercept(invocationOf(m, new Service(), null, new Object[0], ""));
    assertThat(result).isEqualTo("");
  }

  @Test
  void null_target_skips_validation() throws Exception {
    Method m = Service.class.getMethod("echo", String.class);
    // null target skips validation; blank param would normally fail
    Object result = interceptor.intercept(invocationOf(m, null, "arg", new Object[] {""}, "ok"));
    assertThat(result).isEqualTo("ok");
  }

  @Test
  void static_method_skips_validation() throws Exception {
    Method m = Service.class.getMethod("staticEcho", String.class);
    Object result =
        interceptor.intercept(invocationOf(m, new Service(), "arg", new Object[] {""}, "ok"));
    assertThat(result).isEqualTo("ok");
  }

  @Test
  void method_level_validation_groups_applied() throws Exception {
    Method m = Service.class.getMethod("groupedEcho", String.class);
    // GroupA active → blank fails
    MethodInvocation<String> inv =
        invocationOf(m, new Service(), "arg", new Object[] {""}, "unreached");
    assertThatThrownBy(() -> interceptor.intercept(inv))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void method_level_validation_groups_exclude_others() throws Exception {
    Method m = Service.class.getMethod("otherGroupEcho", String.class);
    // GroupB active; constraint is on GroupA → passes
    Object result =
        interceptor.intercept(invocationOf(m, new Service(), "arg", new Object[] {""}, "ok"));
    assertThat(result).isEqualTo("ok");
  }
}
