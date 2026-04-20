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
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/** Covers the two {@link MethodInvokerBuilder} constructor overloads as call surfaces. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MethodInvokerBuilderOverloadTest {

  static class StringResolver implements ParameterResolver<String> {
    @Override
    public java.util.Optional<Binding<String>> bind(ParameterInfo info) {
      return info.accepts(String.class)
          ? java.util.Optional.of(argument -> argument)
          : java.util.Optional.empty();
    }
  }

  public static class Greeter {
    public String greet(String name) {
      return "Hello, " + name + "!";
    }
  }

  private Method greetMethod() throws Exception {
    return Greeter.class.getMethod("greet", String.class);
  }

  @Test
  void constructor_accepts_class_argument_type() throws Exception {
    var invoker =
        MethodInvoker.builder(greetMethod(), new Greeter(), String.class)
            .resolver(new StringResolver())
            .build();
    assertThat(invoker.invoke("world")).isEqualTo("Hello, world!");
  }

  @Test
  void constructor_accepts_typeref_argument_type() throws Exception {
    var invoker =
        MethodInvoker.builder(greetMethod(), new Greeter(), TypeRef.of(String.class))
            .resolver(new StringResolver())
            .build();
    assertThat(invoker.invoke("world")).isEqualTo("Hello, world!");
  }

  @Test
  void builder_with_argument_fallback_needs_no_resolver() throws Exception {
    Method m = ArgMethod.class.getMethod("m", String.class);
    var invoker = MethodInvoker.builder(m, new ArgMethod(), String.class).build();
    assertThat(invoker.invoke("raw")).isEqualTo("got raw");
  }

  public static class ArgMethod {
    public String m(@Argument String s) {
      return "got " + s;
    }
  }
}
