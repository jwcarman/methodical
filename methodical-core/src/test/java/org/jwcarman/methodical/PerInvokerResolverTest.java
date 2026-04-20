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
import org.jwcarman.specular.TypeRef;

/** Exercises resolvers registered on the builder. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PerInvokerResolverTest {

  static class ConstantResolver implements ParameterResolver<String> {
    private final String value;

    ConstantResolver(String value) {
      this.value = value;
    }

    @Override
    public java.util.Optional<Binding<String>> bind(ParameterInfo info) {
      if (!info.accepts(String.class)) {
        return java.util.Optional.empty();
      }
      final String v = value;
      return java.util.Optional.of(argument -> v);
    }
  }

  public static class Greeter {
    public String greet(String name) {
      return "Hello, " + name + "!";
    }
  }

  @Test
  void resolver_registered_on_builder_is_used() throws Exception {
    Method method = Greeter.class.getMethod("greet", String.class);
    var invoker =
        MethodInvoker.builder(method, new Greeter(), TypeRef.of(String.class))
            .resolver(new ConstantResolver("only"))
            .build();
    assertThat(invoker.invoke("ignored")).isEqualTo("Hello, only!");
  }

  @Test
  void earlier_registered_resolver_wins() throws Exception {
    Method method = Greeter.class.getMethod("greet", String.class);
    var invoker =
        MethodInvoker.builder(method, new Greeter(), TypeRef.of(String.class))
            .resolver(new ConstantResolver("first"))
            .resolver(new ConstantResolver("second"))
            .build();
    assertThat(invoker.invoke("ignored")).isEqualTo("Hello, first!");
  }
}
