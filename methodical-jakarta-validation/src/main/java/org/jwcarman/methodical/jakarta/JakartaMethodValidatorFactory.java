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

import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import org.jwcarman.methodical.MethodValidator;
import org.jwcarman.methodical.MethodValidatorFactory;

public final class JakartaMethodValidatorFactory implements MethodValidatorFactory {

  private final ExecutableValidator executableValidator;
  private final ValidationGroupResolver groupResolver;

  public JakartaMethodValidatorFactory(Validator validator, ValidationGroupResolver groupResolver) {
    this.executableValidator = Objects.requireNonNull(validator, "validator").forExecutables();
    this.groupResolver = Objects.requireNonNull(groupResolver, "groupResolver");
  }

  @Override
  public MethodValidator create(Object target, Method method) {
    if (target == null || Modifier.isStatic(method.getModifiers())) {
      return MethodValidator.NO_OP;
    }
    Class<?>[] groups = groupResolver.resolveGroups(target, method);
    return new JakartaMethodValidator(executableValidator, target, method, groups);
  }
}
