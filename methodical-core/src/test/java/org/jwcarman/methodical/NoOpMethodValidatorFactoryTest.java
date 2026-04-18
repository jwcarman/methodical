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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NoOpMethodValidatorFactoryTest {

  @Test
  void created_validator_does_nothing_for_parameters() throws Exception {
    Method m = String.class.getMethod("length");
    MethodValidator v = new NoOpMethodValidatorFactory().create("x", m);
    assertThatCode(() -> v.validateParameters(new Object[0])).doesNotThrowAnyException();
  }

  @Test
  void created_validator_does_nothing_for_return_value() throws Exception {
    Method m = String.class.getMethod("length");
    MethodValidator v = new NoOpMethodValidatorFactory().create("x", m);
    assertThatCode(() -> v.validateReturnValue(1)).doesNotThrowAnyException();
  }

  @Test
  void accepts_null_target_for_static_methods() throws Exception {
    Method m = Integer.class.getMethod("parseInt", String.class);
    MethodValidator v = new NoOpMethodValidatorFactory().create(null, m);
    assertThatCode(() -> v.validateParameters(new Object[] {"1"})).doesNotThrowAnyException();
  }

  @Test
  void returns_singleton_validator_instance() throws Exception {
    NoOpMethodValidatorFactory factory = new NoOpMethodValidatorFactory();
    Method m1 = String.class.getMethod("length");
    Method m2 = Integer.class.getMethod("parseInt", String.class);
    assertThat(factory.create("x", m1)).isSameAs(factory.create(123, m2));
  }
}
