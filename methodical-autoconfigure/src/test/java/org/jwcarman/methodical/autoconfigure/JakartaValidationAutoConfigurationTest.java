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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.jakarta.JakartaValidationInterceptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class JakartaValidationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JakartaValidationAutoConfiguration.class, MethodicalAutoConfiguration.class));

  @Test
  void registers_jakarta_validation_interceptor_when_validator_present() {
    contextRunner
        .withBean(Validator.class, () -> Validation.buildDefaultValidatorFactory().getValidator())
        .run(context -> assertThat(context).hasSingleBean(JakartaValidationInterceptor.class));
  }

  @Test
  void no_interceptor_when_validator_missing() {
    contextRunner.run(
        context -> assertThat(context).doesNotHaveBean(JakartaValidationInterceptor.class));
  }
}
