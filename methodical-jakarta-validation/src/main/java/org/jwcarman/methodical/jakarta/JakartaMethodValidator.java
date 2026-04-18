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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import org.jwcarman.methodical.MethodValidator;

/**
 * Bound Jakarta Bean Validation {@link MethodValidator} for a single {@code (target, method)} pair.
 *
 * <p>Captures the validation groups and return-value flag at construction so the per-invocation
 * path is just an {@link ExecutableValidator} dispatch.
 */
public final class JakartaMethodValidator implements MethodValidator {

  private final ExecutableValidator executableValidator;
  private final Object target;
  private final Method method;
  private final Class<?>[] groups;
  private final boolean validateReturnValue;

  public JakartaMethodValidator(
      ExecutableValidator executableValidator,
      Object target,
      Method method,
      Class<?>[] groups,
      boolean validateReturnValue) {
    this.executableValidator = Objects.requireNonNull(executableValidator, "executableValidator");
    this.target = Objects.requireNonNull(target, "target");
    this.method = Objects.requireNonNull(method, "method");
    this.groups = Objects.requireNonNull(groups, "groups").clone();
    this.validateReturnValue = validateReturnValue;
  }

  @Override
  public void validateParameters(Object[] args) {
    Set<ConstraintViolation<Object>> violations =
        executableValidator.validateParameters(target, method, args, groups);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  @Override
  public void validateReturnValue(Object returnValue) {
    if (!validateReturnValue) {
      return;
    }
    Set<ConstraintViolation<Object>> violations =
        executableValidator.validateReturnValue(target, method, returnValue, groups);
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }
}
