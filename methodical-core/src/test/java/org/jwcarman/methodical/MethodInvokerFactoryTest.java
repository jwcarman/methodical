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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class MethodInvokerFactoryTest {

  @Test
  void shouldInvokeMethodWithResolverProvidedParams() throws Exception {
    var resolver = new StringResolver();
    var factory = new DefaultMethodInvokerFactory(List.of(resolver));
    Method method = Target.class.getMethod("greet", String.class);
    var target = new Target();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    Object result = invoker.invoke("world");
    assertThat(result).isEqualTo("Hello, world!");
  }

  @Test
  void shouldInvokeMethodWithNoParams() throws Exception {
    var factory = new DefaultMethodInvokerFactory(List.of());
    Method method = Target.class.getMethod("noArgs");
    var target = new Target();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    Object result = invoker.invoke("anything");
    assertThat(result).isEqualTo("no args");
  }

  @Test
  void shouldReturnNullForVoidReturnType() throws Exception {
    var resolver = new StringResolver();
    var factory = new DefaultMethodInvokerFactory(List.of(resolver));
    Method method = Target.class.getMethod("voidMethod", String.class);
    var target = new Target();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    Object result = invoker.invoke("test");
    assertThat(result).isNull();
  }

  @Test
  void shouldReturnNullForBoxedVoidReturnType() throws Exception {
    var resolver = new StringResolver();
    var factory = new DefaultMethodInvokerFactory(List.of(resolver));
    Method method = Target.class.getMethod("boxedVoidMethod", String.class);
    var target = new Target();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    Object result = invoker.invoke("test");
    assertThat(result).isNull();
  }

  @Test
  void shouldUnwrapAndRethrowRuntimeException() throws Exception {
    var factory = new DefaultMethodInvokerFactory(List.of());
    Method method = Target.class.getMethod("throwsRuntime");
    var target = new Target();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    assertThatThrownBy(() -> invoker.invoke("test"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("runtime error");
  }

  @Test
  void shouldWrapCheckedExceptionInMethodInvocationException() throws Exception {
    var factory = new DefaultMethodInvokerFactory(List.of());
    Method method = Target.class.getMethod("throwsChecked");
    var target = new Target();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    assertThatThrownBy(() -> invoker.invoke("test"))
        .isInstanceOf(MethodInvocationException.class)
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldWrapIllegalAccessInMethodInvocationException() throws Exception {
    var factory = new DefaultMethodInvokerFactory(List.of());
    Method method = PrivateTarget.class.getDeclaredMethod("privateMethod");
    var target = new PrivateTarget();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    assertThatThrownBy(() -> invoker.invoke("test")).isInstanceOf(MethodInvocationException.class);
  }

  @Test
  void shouldUseFirstSupportingResolver() throws Exception {
    var first = new StringResolver();
    var second = new AlternateStringResolver();
    var factory = new DefaultMethodInvokerFactory(List.of(first, second));
    Method method = Target.class.getMethod("greet", String.class);
    var target = new Target();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    Object result = invoker.invoke("world");
    assertThat(result).isEqualTo("Hello, world!");
  }

  @Test
  void shouldSkipNonSupportingResolver() throws Exception {
    var nonSupporting = new NonSupportingResolver();
    var supporting = new StringResolver();
    var factory = new DefaultMethodInvokerFactory(List.of(nonSupporting, supporting));
    Method method = Target.class.getMethod("greet", String.class);
    var target = new Target();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    Object result = invoker.invoke("world");
    assertThat(result).isEqualTo("Hello, world!");
  }

  @Test
  void shouldPassNullWhenNoResolverMatches() throws Exception {
    var factory = new DefaultMethodInvokerFactory(List.of());
    Method method = Target.class.getMethod("greet", String.class);
    var target = new Target();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    Object result = invoker.invoke("world");
    assertThat(result).isEqualTo("Hello, null!");
  }

  @Test
  void shouldResolveGenericTypes() throws Exception {
    var resolver = new StringResolver();
    var factory = new DefaultMethodInvokerFactory(List.of(resolver));
    Method method = ConcreteService.class.getMethod("process", Object.class);
    var target = new ConcreteService();
    MethodInvoker<String> invoker = factory.create(method, target, String.class);
    Object result = invoker.invoke("test");
    assertThat(result).isEqualTo("processed: test");
  }

  // --- Test support classes ---

  public static class Target {
    public String greet(String name) {
      return "Hello, " + name + "!";
    }

    public String noArgs() {
      return "no args";
    }

    public void voidMethod(String arg) {
      // do nothing
    }

    public Void boxedVoidMethod(String arg) {
      return null;
    }

    public String throwsRuntime() {
      throw new IllegalStateException("runtime error");
    }

    public String throwsChecked() throws IOException {
      throw new IOException("checked error");
    }
  }

  static class PrivateTarget {
    private String privateMethod() {
      return "private";
    }
  }

  public abstract static class AbstractService<T> {
    public abstract String process(T input);
  }

  public static class ConcreteService extends AbstractService<String> {
    @Override
    public String process(String input) {
      return "processed: " + input;
    }
  }

  static class StringResolver implements ParameterResolver<String> {
    @Override
    public boolean supports(ParameterInfo info) {
      return true;
    }

    @Override
    public Object resolve(ParameterInfo info, String argument) {
      return argument;
    }
  }

  static class AlternateStringResolver implements ParameterResolver<String> {
    @Override
    public boolean supports(ParameterInfo info) {
      return true;
    }

    @Override
    public Object resolve(ParameterInfo info, String argument) {
      return "alternate: " + argument;
    }
  }

  static class NonSupportingResolver implements ParameterResolver<String> {
    @Override
    public boolean supports(ParameterInfo info) {
      return false;
    }

    @Override
    public Object resolve(ParameterInfo info, String argument) {
      return "should not be called";
    }
  }
}
