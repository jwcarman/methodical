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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodValidator;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JakartaMethodValidatorFactoryTest {

  static class Service {
    @NotBlank
    public String greet(@NotBlank String name) {
      return "hello " + name;
    }

    @NotNull
    public String maybe(boolean ok) {
      return ok ? "value" : null;
    }

    public static String staticMethod(@NotBlank String s) {
      return s;
    }
  }

  private Validator validator() {
    return Validation.buildDefaultValidatorFactory().getValidator();
  }

  private JakartaMethodValidatorFactory newFactory() {
    return new JakartaMethodValidatorFactory(
        validator(), new DefaultValidationGroupResolver(new Class<?>[] {Default.class}));
  }

  @Test
  void valid_parameters_pass() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    MethodValidator v = newFactory().create(new Service(), m);
    assertThatCode(() -> v.validateParameters(new Object[] {"world"})).doesNotThrowAnyException();
  }

  @Test
  void invalid_parameters_throw_ConstraintViolationException() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    MethodValidator v = newFactory().create(new Service(), m);
    assertThatThrownBy(() -> v.validateParameters(new Object[] {""}))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void valid_return_value_passes() throws Exception {
    Method m = Service.class.getDeclaredMethod("maybe", boolean.class);
    MethodValidator v = newFactory().create(new Service(), m);
    assertThatCode(() -> v.validateReturnValue("value")).doesNotThrowAnyException();
  }

  @Test
  void invalid_return_value_throws_ConstraintViolationException() throws Exception {
    Method m = Service.class.getDeclaredMethod("maybe", boolean.class);
    MethodValidator v = newFactory().create(new Service(), m);
    assertThatThrownBy(() -> v.validateReturnValue(null))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void static_methods_return_no_op() throws Exception {
    Method m = Service.class.getDeclaredMethod("staticMethod", String.class);
    MethodValidator v = newFactory().create(null, m);
    assertThatCode(() -> v.validateParameters(new Object[] {""})).doesNotThrowAnyException();
    assertThatCode(() -> v.validateReturnValue("")).doesNotThrowAnyException();
  }

  @Test
  void null_target_returns_no_op() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    MethodValidator v = newFactory().create(null, m);
    assertThatCode(() -> v.validateParameters(new Object[] {""})).doesNotThrowAnyException();
  }

  @Test
  void resolver_invoked_at_bind_time_not_per_call() throws Exception {
    int[] calls = {0};
    ValidationGroupResolver counting =
        (target, method) -> {
          calls[0]++;
          return new Class<?>[] {Default.class};
        };
    JakartaMethodValidatorFactory factory =
        new JakartaMethodValidatorFactory(validator(), counting);
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    Service target = new Service();

    MethodValidator v = factory.create(target, m);

    assertThat(calls[0]).isEqualTo(1);

    v.validateParameters(new Object[] {"a"});
    v.validateParameters(new Object[] {"b"});
    v.validateReturnValue("x");
    v.validateReturnValue("y");

    assertThat(calls[0]).isEqualTo(1);
  }
}
