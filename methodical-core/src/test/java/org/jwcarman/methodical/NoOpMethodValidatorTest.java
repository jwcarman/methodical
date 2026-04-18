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

import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NoOpMethodValidatorTest {

  @Test
  void validate_parameters_does_nothing() throws Exception {
    MethodValidator v = new NoOpMethodValidator();
    Method m = String.class.getMethod("length");
    assertThatCode(() -> v.validateParameters("x", m, new Object[0])).doesNotThrowAnyException();
  }

  @Test
  void validate_return_value_does_nothing() throws Exception {
    MethodValidator v = new NoOpMethodValidator();
    Method m = String.class.getMethod("length");
    assertThatCode(() -> v.validateReturnValue("x", m, 1)).doesNotThrowAnyException();
  }

  @Test
  void accepts_null_target_for_static_methods() throws Exception {
    MethodValidator v = new NoOpMethodValidator();
    Method m = Integer.class.getMethod("parseInt", String.class);
    assertThatCode(() -> v.validateParameters(null, m, new Object[] {"1"}))
        .doesNotThrowAnyException();
  }
}
