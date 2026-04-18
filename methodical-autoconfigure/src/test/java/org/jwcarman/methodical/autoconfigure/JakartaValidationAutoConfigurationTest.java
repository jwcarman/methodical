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
package org.jwcarman.methodical.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.Argument;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodInvokerFactory;
import org.jwcarman.methodical.MethodValidatorFactory;
import org.jwcarman.methodical.NoOpMethodValidatorFactory;
import org.jwcarman.methodical.jakarta.JakartaMethodValidatorFactory;
import org.jwcarman.methodical.jakarta.ValidationGroupResolver;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JakartaValidationAutoConfigurationTest {

  static class Greeter {
    public String greet(@Argument @NotBlank String name) {
      return "hello " + name;
    }
  }

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JakartaValidationAutoConfiguration.class, MethodicalAutoConfiguration.class));

  @Test
  void registers_jakarta_validator_factory_when_validator_bean_present() {
    contextRunner
        .withBean(Validator.class, () -> Validation.buildDefaultValidatorFactory().getValidator())
        .run(
            context -> {
              assertThat(context).hasSingleBean(JakartaMethodValidatorFactory.class);
              assertThat(context).hasSingleBean(ValidationGroupResolver.class);
              assertThat(context.getBean(MethodValidatorFactory.class))
                  .isInstanceOf(JakartaMethodValidatorFactory.class);
            });
  }

  @Test
  void method_invoker_factory_uses_jakarta_validator_when_wired() {
    contextRunner
        .withBean(Validator.class, () -> Validation.buildDefaultValidatorFactory().getValidator())
        .run(
            context -> {
              MethodInvokerFactory factory = context.getBean(MethodInvokerFactory.class);
              Method m = Greeter.class.getDeclaredMethod("greet", String.class);
              MethodInvoker<String> invoker = factory.create(m, new Greeter(), String.class);
              assertThat(invoker.invoke("world")).isEqualTo("hello world");
              assertThatThrownBy(() -> invoker.invoke(""))
                  .isInstanceOf(ConstraintViolationException.class);
            });
  }

  @Test
  void falls_back_to_no_op_validator_when_no_validator_bean() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(JakartaMethodValidatorFactory.class);
          assertThat(context.getBean(MethodValidatorFactory.class))
              .isInstanceOf(NoOpMethodValidatorFactory.class);
        });
  }
}
