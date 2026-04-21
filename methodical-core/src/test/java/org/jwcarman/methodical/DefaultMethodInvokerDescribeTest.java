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

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** Exercises {@link MethodInvoker#describe()}. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodInvokerDescribeTest {

  public static class Greeter {
    public String greet(@Argument String name) {
      return "hello " + name;
    }
  }

  private static final class NamedInterceptor implements MethodInterceptor<String> {
    private final String name;

    NamedInterceptor(String name) {
      this.name = name;
    }

    @Override
    public Object intercept(MethodInvocation<? extends String> invocation) {
      return invocation.proceed();
    }

    @Override
    public String toString() {
      return name;
    }
  }

  @Test
  void descriptor_reports_declaring_class_and_method_name() throws Exception {
    Method m = Greeter.class.getMethod("greet", String.class);
    MethodInvoker<String> invoker = MethodInvoker.builder(m, new Greeter(), String.class).build();

    MethodInvoker.Descriptor descriptor = invoker.describe();

    assertThat(descriptor.declaringClassName()).isEqualTo(Greeter.class.getName());
    assertThat(descriptor.methodName()).isEqualTo("greet");
    assertThat(descriptor.interceptors()).isEmpty();
  }

  @Test
  void descriptor_lists_interceptor_toString_in_registration_order() throws Exception {
    Method m = Greeter.class.getMethod("greet", String.class);
    MethodInvoker<String> invoker =
        MethodInvoker.builder(m, new Greeter(), String.class)
            .interceptor(new NamedInterceptor("outer"))
            .interceptor(new NamedInterceptor("middle"))
            .interceptor(new NamedInterceptor("inner"))
            .build();

    assertThat(invoker.describe().interceptors()).containsExactly("outer", "middle", "inner");
  }
}
