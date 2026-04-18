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
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Set;
import org.jwcarman.methodical.MethodValidator;
import org.jwcarman.methodical.MethodValidatorFactory;

public final class JakartaMethodValidatorFactory implements MethodValidatorFactory {

  private static final MethodValidator NO_OP =
      new MethodValidator() {
        @Override
        public void validateParameters(Object[] args) {
          // intentionally empty
        }

        @Override
        public void validateReturnValue(Object returnValue) {
          // intentionally empty
        }
      };

  private final ExecutableValidator executableValidator;
  private final ValidationGroupResolver groupResolver;

  public JakartaMethodValidatorFactory(Validator validator, ValidationGroupResolver groupResolver) {
    this.executableValidator = Objects.requireNonNull(validator, "validator").forExecutables();
    this.groupResolver = Objects.requireNonNull(groupResolver, "groupResolver");
  }

  @Override
  public MethodValidator create(Object target, Method method) {
    if (target == null || Modifier.isStatic(method.getModifiers())) {
      return NO_OP;
    }
    Class<?>[] groups = groupResolver.resolveGroups(target, method);
    boolean validateReturn = groupResolver.shouldValidateReturnValue(target, method);
    return new BoundJakartaValidator(executableValidator, target, method, groups, validateReturn);
  }

  private static final class BoundJakartaValidator implements MethodValidator {
    private final ExecutableValidator executableValidator;
    private final Object target;
    private final Method method;
    private final Class<?>[] groups;
    private final boolean validateReturn;

    BoundJakartaValidator(
        ExecutableValidator executableValidator,
        Object target,
        Method method,
        Class<?>[] groups,
        boolean validateReturn) {
      this.executableValidator = executableValidator;
      this.target = target;
      this.method = method;
      this.groups = groups;
      this.validateReturn = validateReturn;
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
      if (!validateReturn) {
        return;
      }
      Set<ConstraintViolation<Object>> violations =
          executableValidator.validateReturnValue(target, method, returnValue, groups);
      if (!violations.isEmpty()) {
        throw new ConstraintViolationException(violations);
      }
    }
  }
}
