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
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/** Exercises resolvers registered via the per-invoker customizer. */
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
  void customizer_resolver_used() throws Exception {
    var factory = new DefaultMethodInvokerFactory();
    Method method = Greeter.class.getMethod("greet", String.class);
    var invoker =
        factory.create(
            method,
            new Greeter(),
            TypeRef.of(String.class),
            cfg -> cfg.resolver(new ConstantResolver("only")));
    assertThat(invoker.invoke("ignored")).isEqualTo("Hello, only!");
  }

  @Test
  void earlier_registered_resolver_wins() throws Exception {
    var factory = new DefaultMethodInvokerFactory();
    Method method = Greeter.class.getMethod("greet", String.class);
    var invoker =
        factory.create(
            method,
            new Greeter(),
            TypeRef.of(String.class),
            cfg ->
                cfg.resolver(new ConstantResolver("first"))
                    .resolver(new ConstantResolver("second")));
    assertThat(invoker.invoke("ignored")).isEqualTo("Hello, first!");
  }
}
