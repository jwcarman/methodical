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
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/** Exercises the per-invoker {@code extraResolvers} list passed to {@code create(...)}. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PerInvokerResolverTest {

  static class ConstantResolver implements ParameterResolver<String> {
    private final String value;

    ConstantResolver(String value) {
      this.value = value;
    }

    @Override
    public boolean supports(ParameterInfo info) {
      return info.accepts(String.class);
    }

    @Override
    public Object resolve(ParameterInfo info, String argument) {
      return value;
    }
  }

  public static class Greeter {
    public String greet(String name) {
      return "Hello, " + name + "!";
    }
  }

  @Test
  void extra_resolver_wins_over_factory_resolver() throws Exception {
    var factory = new DefaultMethodInvokerFactory(List.of(new ConstantResolver("factory")));
    Method method = Greeter.class.getMethod("greet", String.class);
    var invoker =
        factory.create(
            method,
            new Greeter(),
            TypeRef.of(String.class),
            List.of(new ConstantResolver("invoker")));
    assertThat(invoker.invoke("ignored")).isEqualTo("Hello, invoker!");
  }

  @Test
  void extra_resolver_used_when_no_factory_resolver_registered() throws Exception {
    var factory = new DefaultMethodInvokerFactory(List.of());
    Method method = Greeter.class.getMethod("greet", String.class);
    var invoker =
        factory.create(
            method, new Greeter(), TypeRef.of(String.class), List.of(new ConstantResolver("only")));
    assertThat(invoker.invoke("ignored")).isEqualTo("Hello, only!");
  }
}
