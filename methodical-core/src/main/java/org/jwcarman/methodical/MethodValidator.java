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

/**
 * Validates the parameters and return value of a single bound method invocation.
 *
 * <p>Obtained from {@link MethodValidatorFactory#create(Object, java.lang.reflect.Method)} once per
 * {@link MethodInvoker} at construction time. The bound validator captures any per-method
 * configuration (e.g., validation groups) so the per-invocation path stays cheap.
 *
 * <p>Implementations may throw any runtime exception when validation fails; the exception
 * propagates out of {@link MethodInvoker#invoke(Object)} unchanged.
 */
public interface MethodValidator {

  /** Canonical no-op {@link MethodValidator}; thread-safe singleton. */
  MethodValidator NO_OP =
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

  void validateParameters(Object[] args);

  void validateReturnValue(Object returnValue);
}
