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
import jakarta.validation.groups.Default;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Set;
import org.jwcarman.methodical.MethodInterceptor;
import org.jwcarman.methodical.MethodInvocation;

/**
 * A {@link MethodInterceptor} that performs Jakarta Bean Validation on parameters before the chain
 * proceeds and on the return value after it returns normally. Throws {@link
 * ConstraintViolationException} on any violation.
 *
 * <p>Validation groups are resolved per-invocation from a {@link ValidationGroups} annotation on
 * the invoked method (or on the target class), defaulting to {@link Default} when absent. Static
 * methods and invocations with a {@code null} target are skipped.
 *
 * <p>This interceptor does not depend on the caller-supplied argument type, so it is declared as
 * {@code MethodInterceptor<Object>} and can be registered against any {@code
 * MethodInvokerConfig<A>}.
 */
public final class JakartaValidationInterceptor implements MethodInterceptor<Object> {

  private static final Class<?>[] DEFAULT_GROUPS = {Default.class};

  private final ExecutableValidator executableValidator;

  public JakartaValidationInterceptor(Validator validator) {
    this.executableValidator = Objects.requireNonNull(validator, "validator").forExecutables();
  }

  @Override
  public Object intercept(MethodInvocation<?> invocation) {
    Object target = invocation.target();
    Method method = invocation.method();
    if (target == null || Modifier.isStatic(method.getModifiers())) {
      return invocation.proceed();
    }
    Class<?>[] groups = resolveGroups(target, method);

    Set<ConstraintViolation<Object>> paramViolations =
        executableValidator.validateParameters(
            target, method, invocation.resolvedParameters(), groups);
    if (!paramViolations.isEmpty()) {
      throw new ConstraintViolationException(paramViolations);
    }

    Object result = invocation.proceed();

    Set<ConstraintViolation<Object>> returnViolations =
        executableValidator.validateReturnValue(target, method, result, groups);
    if (!returnViolations.isEmpty()) {
      throw new ConstraintViolationException(returnViolations);
    }
    return result;
  }

  private static Class<?>[] resolveGroups(Object target, Method method) {
    ValidationGroups annotation = Annotations.findOnMethod(method, ValidationGroups.class);
    if (annotation == null) {
      annotation = Annotations.findOnClass(target.getClass(), ValidationGroups.class);
    }
    return annotation != null ? annotation.value() : DEFAULT_GROUPS;
  }
}
