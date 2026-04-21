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

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** Exercises the default implementation of {@link MethodInvoker#describe()}. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MethodInvokerDefaultDescribeTest {

  @Test
  void lambda_invoker_uses_default_describe() {
    MethodInvoker<String> invoker = arg -> null;

    MethodInvoker.Descriptor descriptor = invoker.describe();

    assertThat(descriptor.declaringClassName()).isEqualTo(invoker.getClass().getName());
    assertThat(descriptor.methodName()).isEqualTo("invoke");
    assertThat(descriptor.interceptors()).isEmpty();
  }

  @Test
  void lambda_invoker_satisfies_functional_interface() {
    MethodInvoker<String> invoker = arg -> "result:" + arg;
    assertThat(invoker.invoke("x")).isEqualTo("result:x");
  }
}
