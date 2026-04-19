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
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;

class MethodInvokerFactoryTest {

  private final MethodInvokerFactory factory = new DefaultMethodInvokerFactory();

  @Test
  void shouldInvokeMethodWithResolverProvidedParams() throws Exception {
    Method method = Target.class.getMethod("greet", String.class);
    MethodInvoker<String> invoker =
        factory.create(
            method, new Target(), String.class, cfg -> cfg.resolver(new StringResolver()));
    assertThat(invoker.invoke("world")).isEqualTo("Hello, world!");
  }

  @Test
  void shouldInvokeMethodWithNoParams() throws Exception {
    Method method = Target.class.getMethod("noArgs");
    MethodInvoker<String> invoker = factory.create(method, new Target(), String.class);
    assertThat(invoker.invoke("anything")).isEqualTo("no args");
  }

  @Test
  void shouldReturnNullForVoidReturnType() throws Exception {
    Method method = Target.class.getMethod("voidMethod", String.class);
    MethodInvoker<String> invoker =
        factory.create(
            method, new Target(), String.class, cfg -> cfg.resolver(new StringResolver()));
    assertThat(invoker.invoke("test")).isNull();
  }

  @Test
  void shouldReturnNullForBoxedVoidReturnType() throws Exception {
    Method method = Target.class.getMethod("boxedVoidMethod", String.class);
    MethodInvoker<String> invoker =
        factory.create(
            method, new Target(), String.class, cfg -> cfg.resolver(new StringResolver()));
    assertThat(invoker.invoke("test")).isNull();
  }

  @Test
  void shouldUnwrapAndRethrowRuntimeException() throws Exception {
    Method method = Target.class.getMethod("throwsRuntime");
    MethodInvoker<String> invoker = factory.create(method, new Target(), String.class);
    assertThatThrownBy(() -> invoker.invoke("test"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("runtime error");
  }

  @Test
  void shouldWrapCheckedExceptionInMethodInvocationException() throws Exception {
    Method method = Target.class.getMethod("throwsChecked");
    MethodInvoker<String> invoker = factory.create(method, new Target(), String.class);
    assertThatThrownBy(() -> invoker.invoke("test"))
        .isInstanceOf(MethodInvocationException.class)
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldUseFirstSupportingResolver() throws Exception {
    Method method = Target.class.getMethod("greet", String.class);
    MethodInvoker<String> invoker =
        factory.create(
            method,
            new Target(),
            String.class,
            cfg -> cfg.resolver(new StringResolver()).resolver(new AlternateStringResolver()));
    assertThat(invoker.invoke("world")).isEqualTo("Hello, world!");
  }

  @Test
  void shouldSkipNonSupportingResolver() throws Exception {
    Method method = Target.class.getMethod("greet", String.class);
    MethodInvoker<String> invoker =
        factory.create(
            method,
            new Target(),
            String.class,
            cfg -> cfg.resolver(new NonSupportingResolver()).resolver(new StringResolver()));
    assertThat(invoker.invoke("world")).isEqualTo("Hello, world!");
  }

  @Test
  void shouldFailFastWhenNoResolverMatches() throws Exception {
    Method method = Target.class.getMethod("greet", String.class);
    var target = new Target();
    assertThatThrownBy(() -> factory.create(method, target, String.class))
        .isInstanceOf(ParameterResolutionException.class)
        .hasMessageContaining("No resolver found")
        .hasMessageContaining("name")
        .hasMessageContaining("Target.greet")
        .hasMessageContaining("@Argument");
  }

  @Test
  void shouldResolveGenericTypes() throws Exception {
    Method method = ConcreteService.class.getMethod("process", Object.class);
    MethodInvoker<String> invoker =
        factory.create(
            method, new ConcreteService(), String.class, cfg -> cfg.resolver(new StringResolver()));
    assertThat(invoker.invoke("test")).isEqualTo("processed: test");
  }

  @Test
  void shouldHandleRawParameterResolver() throws Exception {
    Method method = Target.class.getMethod("greet", String.class);
    MethodInvoker<String> invoker =
        factory.create(
            method, new Target(), String.class, cfg -> cfg.resolver(new WildcardResolver()));
    assertThat(invoker.invoke("world")).isEqualTo("Hello, raw!");
  }

  @Test
  void shouldPropagateParameterResolutionException() throws Exception {
    ParameterResolver<String> failing =
        new ParameterResolver<>() {
          @Override
          public boolean supports(ParameterInfo info) {
            return true;
          }

          @Override
          public Object resolve(ParameterInfo info, String argument) {
            throw new ParameterResolutionException("bad param", new RuntimeException("cause"));
          }
        };
    Method method = Target.class.getMethod("greet", String.class);
    MethodInvoker<String> invoker =
        factory.create(method, new Target(), String.class, cfg -> cfg.resolver(failing));
    assertThatThrownBy(() -> invoker.invoke("test"))
        .isInstanceOf(ParameterResolutionException.class)
        .hasMessage("bad param");
  }

  @Test
  void shouldPassRawArgumentWhenAnnotatedWithArgument() throws Exception {
    Method method = ArgumentTarget.class.getMethod("process", String.class);
    MethodInvoker<String> invoker = factory.create(method, new ArgumentTarget(), String.class);
    assertThat(invoker.invoke("raw-value")).isEqualTo("got: raw-value");
  }

  @Test
  void argumentAnnotationBypassesResolvers() throws Exception {
    Method method = ArgumentTarget.class.getMethod("process", String.class);
    MethodInvoker<String> invoker =
        factory.create(
            method,
            new ArgumentTarget(),
            String.class,
            cfg -> cfg.resolver(new NonSupportingResolver()));
    assertThat(invoker.invoke("raw-value")).isEqualTo("got: raw-value");
  }

  @Test
  void argumentAnnotationThrowsWhenTypeIncompatible() throws Exception {
    Method method = IncompatibleArgumentTarget.class.getMethod("process", Integer.class);
    var target = new IncompatibleArgumentTarget();
    assertThatThrownBy(() -> factory.create(method, target, String.class))
        .isInstanceOf(ParameterResolutionException.class)
        .hasMessageContaining("@Argument")
        .hasMessageContaining("Integer")
        .hasMessageContaining("String");
  }

  @Test
  void shouldUseNamedAnnotationForParameterName() throws Exception {
    Method method = NamedTarget.class.getMethod("greet", String.class);
    MethodInvoker<String> invoker =
        factory.create(
            method, new NamedTarget(), String.class, cfg -> cfg.resolver(new StringResolver()));
    assertThat(invoker.invoke("test")).isEqualTo("Hello, test!");
  }

  // --- Test support classes ---

  public static class ArgumentTarget {
    public String process(@Argument String raw) {
      return "got: " + raw;
    }
  }

  public static class IncompatibleArgumentTarget {
    public String process(@Argument Integer value) {
      return "got: " + value;
    }
  }

  public static class NamedTarget {
    public String greet(@Named("alias") String name) {
      return "Hello, " + name + "!";
    }
  }

  public static class Target {
    public String greet(String name) {
      return "Hello, " + name + "!";
    }

    public String noArgs() {
      return "no args";
    }

    public void voidMethod(String arg) {
      // no-op
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

  static class WildcardResolver implements ParameterResolver<Object> {
    @Override
    public boolean supports(ParameterInfo info) {
      return true;
    }

    @Override
    public Object resolve(ParameterInfo info, Object argument) {
      return "raw";
    }
  }
}
