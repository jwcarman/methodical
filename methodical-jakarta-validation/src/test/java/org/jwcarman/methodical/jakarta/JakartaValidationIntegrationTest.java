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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.groups.Default;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.Argument;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JakartaValidationIntegrationTest {

  static class Greeter {
    public String greet(@Argument @NotBlank String name) {
      return "hello " + name;
    }
  }

  private MethodInvoker<String> buildInvoker() throws Exception {
    JakartaMethodValidatorFactory vf =
        new JakartaMethodValidatorFactory(
            Validation.buildDefaultValidatorFactory().getValidator(),
            new DefaultValidationGroupResolver(new Class<?>[] {Default.class}));
    DefaultMethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of(), vf);
    Method m = Greeter.class.getDeclaredMethod("greet", String.class);
    return factory.create(m, new Greeter(), String.class);
  }

  @Test
  void valid_invocation_returns_result() throws Exception {
    assertThat(buildInvoker().invoke("world")).isEqualTo("hello world");
  }

  @Test
  void invalid_argument_surfaces_constraint_violation_exception() throws Exception {
    MethodInvoker<String> invoker = buildInvoker();
    assertThatThrownBy(() -> invoker.invoke("")).isInstanceOf(ConstraintViolationException.class);
  }
}
