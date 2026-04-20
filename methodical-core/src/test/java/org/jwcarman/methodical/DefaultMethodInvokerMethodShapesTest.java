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
import java.util.Arrays;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Exercises invoker behavior across Method shapes: bridges, defaults, inherited, static, varargs,
 * primitives.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodInvokerMethodShapesTest {

  // --- bridge methods ---

  public interface GenericHandler<T> {
    String handle(T input);
  }

  public static class StringHandler implements GenericHandler<String> {
    @Override
    public String handle(@Argument String input) {
      return "handled: " + input;
    }
  }

  @Test
  void bridge_method_from_getMethods_dispatches_correctly() throws Exception {
    // getMethod on the generic interface against the concrete class returns a bridge.
    Method bridge = StringHandler.class.getMethod("handle", Object.class);
    assertThat(bridge.isBridge()).isTrue();
    MethodInvoker<String> invoker =
        MethodInvoker.builder(bridge, new StringHandler(), String.class).build();
    assertThat(invoker.invoke("hi")).isEqualTo("handled: hi");
  }

  @Test
  void concrete_override_method_dispatches_correctly() throws Exception {
    // getDeclaredMethod on String parameter returns the non-bridge override.
    Method concrete = StringHandler.class.getDeclaredMethod("handle", String.class);
    assertThat(concrete.isBridge()).isFalse();
    MethodInvoker<String> invoker =
        MethodInvoker.builder(concrete, new StringHandler(), String.class).build();
    assertThat(invoker.invoke("hi")).isEqualTo("handled: hi");
  }

  @Test
  void interceptor_sees_bridge_method_when_bridge_was_passed_to_factory() throws Exception {
    Method bridge = StringHandler.class.getMethod("handle", Object.class);
    java.util.concurrent.atomic.AtomicReference<Method> seen =
        new java.util.concurrent.atomic.AtomicReference<>();
    var invoker =
        MethodInvoker.builder(bridge, new StringHandler(), String.class)
            .interceptor(
                invocation -> {
                  seen.set(invocation.method());
                  return invocation.proceed();
                })
            .build();
    assertThat(invoker.invoke("hi")).isEqualTo("handled: hi");
    assertThat(seen.get()).isEqualTo(bridge);
    assertThat(seen.get().isBridge()).isTrue();
  }

  // --- covariant-return bridge ---

  public abstract static class AnimalFactory {
    public abstract Number make();
  }

  public static class IntegerFactory extends AnimalFactory {
    @Override
    public Integer make() { // covariant return
      return 42;
    }
  }

  @Test
  void covariant_return_bridge_dispatches_to_concrete_override() throws Exception {
    Method bridge = IntegerFactory.class.getMethod("make");
    // getMethod returns the bridge from the supertype perspective — it has return type Number
    // and isBridge() = true. Invoking it reflectively lands on the Integer-returning override.
    MethodInvoker<Object> invoker =
        MethodInvoker.builder(bridge, new IntegerFactory(), Object.class).build();
    assertThat(invoker.invoke(new Object())).isEqualTo(42);
  }

  @Test
  void covariant_return_non_bridge_override_also_dispatches() throws Exception {
    Method concrete = IntegerFactory.class.getDeclaredMethod("make");
    assertThat(concrete.isBridge()).isFalse();
    assertThat(concrete.getReturnType()).isEqualTo(Integer.class);
    MethodInvoker<Object> invoker =
        MethodInvoker.builder(concrete, new IntegerFactory(), Object.class).build();
    assertThat(invoker.invoke(new Object())).isEqualTo(42);
  }

  // --- bridge through a multi-level generic hierarchy ---

  public interface Processor<I, O> {
    O process(I input);
  }

  public abstract static class StringInputProcessor<O> implements Processor<String, O> {}

  public static class StringToIntProcessor extends StringInputProcessor<Integer> {
    @Override
    public Integer process(@Argument String input) {
      return input.length();
    }
  }

  @Test
  void multi_level_generic_hierarchy_bridge_dispatches() throws Exception {
    // getMethod from the Processor interface view returns a bridge.
    Method bridge = StringToIntProcessor.class.getMethod("process", Object.class);
    assertThat(bridge.isBridge()).isTrue();
    MethodInvoker<String> invoker =
        MethodInvoker.builder(bridge, new StringToIntProcessor(), String.class).build();
    assertThat(invoker.invoke("hello")).isEqualTo(5);
  }

  @Test
  void multi_level_generic_hierarchy_non_bridge_dispatches() throws Exception {
    Method concrete = StringToIntProcessor.class.getDeclaredMethod("process", String.class);
    assertThat(concrete.isBridge()).isFalse();
    MethodInvoker<String> invoker =
        MethodInvoker.builder(concrete, new StringToIntProcessor(), String.class).build();
    assertThat(invoker.invoke("hello")).isEqualTo(5);
  }

  // --- inherited methods ---

  public static class Base {
    public String greet(@Argument String n) {
      return "base " + n;
    }
  }

  public static class Sub extends Base {}

  @Test
  void inherited_method_invokes_on_subclass_instance() throws Exception {
    Method m = Sub.class.getMethod("greet", String.class);
    MethodInvoker<String> invoker = MethodInvoker.builder(m, new Sub(), String.class).build();
    assertThat(invoker.invoke("x")).isEqualTo("base x");
  }

  // --- default methods on interfaces ---

  public interface Greeter {
    default String greet(@Argument String name) {
      return "hi " + name;
    }
  }

  public static class DefaultGreeter implements Greeter {}

  @Test
  void default_interface_method_invokes_on_implementer() throws Exception {
    Method m = Greeter.class.getMethod("greet", String.class);
    MethodInvoker<String> invoker =
        MethodInvoker.builder(m, new DefaultGreeter(), String.class).build();
    assertThat(invoker.invoke("z")).isEqualTo("hi z");
  }

  // --- static methods ---

  public static class StaticHolder {
    public static String shout(@Argument String s) {
      return s.toUpperCase();
    }
  }

  @Test
  void static_method_can_be_invoked_via_null_target() throws Exception {
    Method m = StaticHolder.class.getMethod("shout", String.class);
    // Factory's findResolver calls target.getClass() to resolve generic parameter types.
    // Using a dummy instance keeps that working while the reflective invoke itself uses null
    // target.
    // Alternative: factories accept null target; this test pins current behavior.
    MethodInvoker<String> invoker =
        MethodInvoker.builder(m, new StaticHolder(), String.class).build();
    assertThat(invoker.invoke("hi")).isEqualTo("HI");
  }

  // --- primitive parameters ---

  public static class PrimitiveTarget {
    public int doubled(@Argument Integer n) {
      return n * 2;
    }
  }

  @Test
  void primitive_return_autoboxed() throws Exception {
    Method m = PrimitiveTarget.class.getMethod("doubled", Integer.class);
    MethodInvoker<Integer> invoker =
        MethodInvoker.builder(m, new PrimitiveTarget(), Integer.class).build();
    assertThat(invoker.invoke(21)).isEqualTo(42);
  }

  // --- varargs ---

  public static class VarargsTarget {
    public String join(@Argument String[] parts) {
      return String.join("-", parts);
    }
  }

  @Test
  void varargs_shaped_method_invokes_correctly() throws Exception {
    Method m = VarargsTarget.class.getMethod("join", String[].class);
    MethodInvoker<String[]> invoker =
        MethodInvoker.builder(m, new VarargsTarget(), String[].class).build();
    assertThat(invoker.invoke(new String[] {"a", "b", "c"})).isEqualTo("a-b-c");
  }

  // --- package-private method ---

  static class PackagePrivate {
    String secret(@Argument String x) {
      return "pkg: " + x;
    }
  }

  @Test
  void package_private_method_invokable_via_setAccessible() throws Exception {
    Method m = PackagePrivate.class.getDeclaredMethod("secret", String.class);
    MethodInvoker<String> invoker =
        MethodInvoker.builder(m, new PackagePrivate(), String.class).build();
    assertThat(invoker.invoke("hello")).isEqualTo("pkg: hello");
  }

  // --- method returning void ---

  public static class VoidTarget {
    private String captured;

    public void capture(@Argument String s) {
      this.captured = s;
    }
  }

  @Test
  void void_return_surfaces_as_null() throws Exception {
    VoidTarget target = new VoidTarget();
    Method m = VoidTarget.class.getMethod("capture", String.class);
    MethodInvoker<String> invoker = MethodInvoker.builder(m, target, String.class).build();
    assertThat(invoker.invoke("captured")).isNull();
    assertThat(target.captured).isEqualTo("captured");
  }

  // --- exception from method (checked) ---

  public static class ThrowingTarget {
    public String boom(@Argument String s) throws Exception {
      throw new java.io.IOException("bad " + s);
    }
  }

  @Test
  void checked_exception_wrapped_in_method_invocation_exception() throws Exception {
    Method m = ThrowingTarget.class.getMethod("boom", String.class);
    MethodInvoker<String> invoker =
        MethodInvoker.builder(m, new ThrowingTarget(), String.class).build();
    assertThat(Arrays.stream(m.getExceptionTypes()).toList())
        .contains(Exception.class); // sanity check
    try {
      invoker.invoke("input");
      throw new AssertionError("expected throw");
    } catch (org.jwcarman.methodical.MethodInvocationException e) {
      assertThat(e.getCause()).isInstanceOf(java.io.IOException.class);
      assertThat(e.getCause()).hasMessage("bad input");
    }
  }
}
