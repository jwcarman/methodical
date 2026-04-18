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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.groups.Default;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JakartaMethodValidatorTest {

  interface OtherGroup {}

  static class Service {
    @NotNull
    public String greet(@NotBlank String name) {
      return name == null ? null : "hello " + name;
    }

    public String onlyValidatedInOtherGroup(@NotBlank(groups = OtherGroup.class) String name) {
      return name;
    }
  }

  private static final ExecutableValidator EXECUTABLE_VALIDATOR = newExecutableValidator();

  private static ExecutableValidator newExecutableValidator() {
    Validator v = Validation.buildDefaultValidatorFactory().getValidator();
    return v.forExecutables();
  }

  private JakartaMethodValidator newValidator(
      Method method, Class<?>[] groups, boolean validateReturnValue) {
    return new JakartaMethodValidator(
        EXECUTABLE_VALIDATOR, new Service(), method, groups, validateReturnValue);
  }

  @Test
  void valid_parameters_pass() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    JakartaMethodValidator v = newValidator(m, new Class<?>[] {Default.class}, true);
    assertThatCode(() -> v.validateParameters(new Object[] {"world"})).doesNotThrowAnyException();
  }

  @Test
  void invalid_parameters_throw_ConstraintViolationException() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    JakartaMethodValidator v = newValidator(m, new Class<?>[] {Default.class}, true);
    assertThatThrownBy(() -> v.validateParameters(new Object[] {""}))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void invalid_return_value_throws_ConstraintViolationException() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    JakartaMethodValidator v = newValidator(m, new Class<?>[] {Default.class}, true);
    assertThatThrownBy(() -> v.validateReturnValue(null))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void return_value_validation_skipped_when_flag_is_false() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    JakartaMethodValidator v = newValidator(m, new Class<?>[] {Default.class}, false);
    assertThatCode(() -> v.validateReturnValue(null)).doesNotThrowAnyException();
  }

  @Test
  void groups_filter_constraints() throws Exception {
    Method m = Service.class.getDeclaredMethod("onlyValidatedInOtherGroup", String.class);
    JakartaMethodValidator defaultGroupOnly = newValidator(m, new Class<?>[] {Default.class}, true);
    assertThatCode(() -> defaultGroupOnly.validateParameters(new Object[] {""}))
        .doesNotThrowAnyException();

    JakartaMethodValidator otherGroup = newValidator(m, new Class<?>[] {OtherGroup.class}, true);
    assertThatThrownBy(() -> otherGroup.validateParameters(new Object[] {""}))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void groups_array_is_defensively_copied() throws Exception {
    Method m = Service.class.getDeclaredMethod("onlyValidatedInOtherGroup", String.class);
    Class<?>[] groups = new Class<?>[] {OtherGroup.class};
    JakartaMethodValidator v = newValidator(m, groups, true);

    groups[0] = Default.class;

    assertThatThrownBy(() -> v.validateParameters(new Object[] {""}))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void rejects_null_target() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    assertThatThrownBy(
            () ->
                new JakartaMethodValidator(
                    EXECUTABLE_VALIDATOR, null, m, new Class<?>[] {Default.class}, true))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejects_null_method() {
    assertThatThrownBy(
            () ->
                new JakartaMethodValidator(
                    EXECUTABLE_VALIDATOR,
                    new Service(),
                    null,
                    new Class<?>[] {Default.class},
                    true))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void rejects_null_groups() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    assertThatThrownBy(
            () -> new JakartaMethodValidator(EXECUTABLE_VALIDATOR, new Service(), m, null, true))
        .isInstanceOf(NullPointerException.class);
  }
}
