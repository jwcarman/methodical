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

/** Covers the four {@link MethodInvokerFactory#create create(...)} overloads as call surfaces. */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MethodInvokerFactoryOverloadTest {

  static class StringResolver implements ParameterResolver<String> {
    @Override
    public boolean supports(ParameterInfo info) {
      return info.accepts(String.class);
    }

    @Override
    public Object resolve(ParameterInfo info, String argument) {
      return argument;
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
  void create_with_class_only() throws Exception {
    MethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of(new StringResolver()));
    var invoker = factory.create(greetMethod(), new Greeter(), String.class);
    assertThat(invoker.invoke("world")).isEqualTo("Hello, world!");
  }

  @Test
  void create_with_class_and_extras() throws Exception {
    MethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of());
    var invoker =
        factory.create(greetMethod(), new Greeter(), String.class, List.of(new StringResolver()));
    assertThat(invoker.invoke("world")).isEqualTo("Hello, world!");
  }

  @Test
  void create_with_type_ref_only() throws Exception {
    MethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of(new StringResolver()));
    var invoker = factory.create(greetMethod(), new Greeter(), TypeRef.of(String.class));
    assertThat(invoker.invoke("world")).isEqualTo("Hello, world!");
  }

  @Test
  void create_with_type_ref_and_extras() throws Exception {
    MethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of());
    var invoker =
        factory.create(
            greetMethod(), new Greeter(), TypeRef.of(String.class), List.of(new StringResolver()));
    assertThat(invoker.invoke("world")).isEqualTo("Hello, world!");
  }
}
