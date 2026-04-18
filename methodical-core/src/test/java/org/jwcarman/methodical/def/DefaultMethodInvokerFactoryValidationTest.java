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
package org.jwcarman.methodical.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.Argument;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodValidator;
import org.jwcarman.methodical.MethodValidatorFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodInvokerFactoryValidationTest {

  static class Echo {
    String echo(@Argument String s) {
      return s;
    }
  }

  @Test
  void factory_calls_validator_factory_once_at_invoker_creation() throws Exception {
    int[] bindCalls = {0};
    int[] validateCalls = {0};
    MethodValidatorFactory vf =
        (target, method) -> {
          bindCalls[0]++;
          return new MethodValidator() {
            @Override
            public void validateParameters(Object[] args) {
              validateCalls[0]++;
            }

            @Override
            public void validateReturnValue(Object returnValue) {}
          };
        };

    DefaultMethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of(), vf);
    Method m = Echo.class.getDeclaredMethod("echo", String.class);
    MethodInvoker<String> invoker = factory.create(m, new Echo(), String.class);

    assertThat(bindCalls[0]).isEqualTo(1);
    assertThat(validateCalls[0]).isZero();

    invoker.invoke("a");
    invoker.invoke("b");

    assertThat(bindCalls[0]).isEqualTo(1);
    assertThat(validateCalls[0]).isEqualTo(2);
  }

  @Test
  void single_arg_constructor_uses_no_op_factory() throws Exception {
    DefaultMethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of());
    Method m = Echo.class.getDeclaredMethod("echo", String.class);
    MethodInvoker<String> invoker = factory.create(m, new Echo(), String.class);
    assertThat(invoker.invoke("hi")).isEqualTo("hi");
  }

  @Test
  void rejects_null_validator_factory() {
    assertThatThrownBy(() -> new DefaultMethodInvokerFactory(List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
