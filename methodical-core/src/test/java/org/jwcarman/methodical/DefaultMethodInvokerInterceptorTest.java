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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * Exercises interceptor semantics at the {@code MethodInvokerFactory.create(..., customizer)}
 * boundary.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodInvokerInterceptorTest {

  public static class Greeter {
    public String greet(@Argument String name) {
      return "hello " + name;
    }
  }

  private MethodInvoker<String> build(
      java.util.function.Consumer<MethodInvoker.Builder<String>> customizer) throws Exception {
    Method m = Greeter.class.getMethod("greet", String.class);
    MethodInvoker.Builder<String> builder = MethodInvoker.builder(m, new Greeter(), String.class);
    customizer.accept(builder);
    return builder.build();
  }

  @Test
  void no_interceptors_invokes_method_directly() throws Exception {
    MethodInvoker<String> invoker = build(cfg -> {});
    assertThat(invoker.invoke("world")).isEqualTo("hello world");
  }

  @Test
  void single_interceptor_sees_method_target_argument_and_parameters() throws Exception {
    AtomicReference<Method> seenMethod = new AtomicReference<>();
    AtomicReference<Object> seenTarget = new AtomicReference<>();
    AtomicReference<String> seenArgument = new AtomicReference<>();
    AtomicReference<Object[]> seenParameters = new AtomicReference<>();

    MethodInterceptor<String> interceptor =
        invocation -> {
          seenMethod.set(invocation.method());
          seenTarget.set(invocation.target());
          seenArgument.set(invocation.argument());
          seenParameters.set(invocation.resolvedParameters());
          return invocation.proceed();
        };

    MethodInvoker<String> invoker = build(cfg -> cfg.interceptor(interceptor));
    assertThat(invoker.invoke("world")).isEqualTo("hello world");
    assertThat(seenMethod.get()).isEqualTo(Greeter.class.getMethod("greet", String.class));
    assertThat(seenTarget.get()).isInstanceOf(Greeter.class);
    assertThat(seenArgument.get()).isEqualTo("world");
    assertThat(seenParameters.get()).containsExactly("world");
  }

  @Test
  void multiple_interceptors_run_in_registration_order_outermost_first() throws Exception {
    List<String> log = new ArrayList<>();
    MethodInterceptor<String> first = named("first", log);
    MethodInterceptor<String> second = named("second", log);
    MethodInterceptor<String> third = named("third", log);

    MethodInvoker<String> invoker =
        build(cfg -> cfg.interceptor(first).interceptor(second).interceptor(third));
    invoker.invoke("w");

    assertThat(log)
        .containsExactly(
            "first:before",
            "second:before",
            "third:before",
            "third:after",
            "second:after",
            "first:after");
  }

  @Test
  void interceptor_short_circuit_skips_method_and_downstream() throws Exception {
    AtomicInteger innerCalls = new AtomicInteger();
    MethodInterceptor<String> inner =
        invocation -> {
          innerCalls.incrementAndGet();
          return invocation.proceed();
        };
    MethodInterceptor<String> outer = invocation -> "short-circuited";

    MethodInvoker<String> invoker = build(cfg -> cfg.interceptor(outer).interceptor(inner));
    assertThat(invoker.invoke("world")).isEqualTo("short-circuited");
    assertThat(innerCalls.get()).isZero();
  }

  @Test
  void interceptor_throwing_propagates_and_skips_downstream() throws Exception {
    AtomicInteger innerCalls = new AtomicInteger();
    MethodInterceptor<String> inner =
        invocation -> {
          innerCalls.incrementAndGet();
          return invocation.proceed();
        };
    MethodInterceptor<String> outer =
        invocation -> {
          throw new IllegalStateException("reject");
        };

    MethodInvoker<String> invoker = build(cfg -> cfg.interceptor(outer).interceptor(inner));
    assertThatThrownBy(() -> invoker.invoke("w"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("reject");
    assertThat(innerCalls.get()).isZero();
  }

  @Test
  void interceptor_can_call_proceed_multiple_times_for_retry() throws Exception {
    List<String> log = new ArrayList<>();
    MethodInterceptor<String> retry =
        invocation -> {
          log.add("attempt");
          invocation.proceed();
          log.add("attempt");
          Object last = invocation.proceed();
          log.add("attempt");
          return invocation.proceed() + "|" + last;
        };

    MethodInvoker<String> invoker = build(cfg -> cfg.interceptor(retry));
    Object result = invoker.invoke("w");
    assertThat(log).containsExactly("attempt", "attempt", "attempt");
    assertThat(result).isEqualTo("hello w|hello w");
  }

  @Test
  void interceptor_can_transform_return_value() throws Exception {
    MethodInterceptor<String> upper = invocation -> ((String) invocation.proceed()).toUpperCase();
    MethodInvoker<String> invoker = build(cfg -> cfg.interceptor(upper));
    assertThat(invoker.invoke("world")).isEqualTo("HELLO WORLD");
  }

  @Test
  void parameters_mutation_in_interceptor_does_not_affect_method_call() throws Exception {
    MethodInterceptor<String> mutator =
        invocation -> {
          Object[] params = invocation.resolvedParameters();
          params[0] = "TAMPERED";
          return invocation.proceed();
        };
    MethodInvoker<String> invoker = build(cfg -> cfg.interceptor(mutator));
    assertThat(invoker.invoke("world")).isEqualTo("hello world");
  }

  @Test
  void super_typed_interceptor_accepted_for_narrow_argument_type() throws Exception {
    AtomicInteger hits = new AtomicInteger();
    MethodInterceptor<Object> generic =
        invocation -> {
          hits.incrementAndGet();
          return invocation.proceed();
        };
    MethodInvoker<String> invoker = build(cfg -> cfg.interceptor(generic));
    invoker.invoke("x");
    assertThat(hits.get()).isEqualTo(1);
  }

  @Test
  void single_MethodInvocation_instance_is_shared_across_all_interceptors() throws Exception {
    AtomicReference<Object> seenByFirst = new AtomicReference<>();
    AtomicReference<Object> seenBySecond = new AtomicReference<>();
    AtomicReference<Object> seenByThird = new AtomicReference<>();

    MethodInvoker<String> invoker =
        build(
            cfg ->
                cfg.interceptor(
                        invocation -> {
                          seenByFirst.set(invocation);
                          return invocation.proceed();
                        })
                    .interceptor(
                        invocation -> {
                          seenBySecond.set(invocation);
                          return invocation.proceed();
                        })
                    .interceptor(
                        invocation -> {
                          seenByThird.set(invocation);
                          return invocation.proceed();
                        }));
    invoker.invoke("x");

    // Every interceptor must see the exact same MethodInvocation instance. This is the observable
    // property of the single-cursor allocation strategy; regressing to per-step invocations would
    // make these distinct objects.
    assertThat(seenByFirst.get()).isSameAs(seenBySecond.get()).isSameAs(seenByThird.get());
  }

  @Test
  void multi_interceptor_retry_reruns_entire_remaining_chain_each_time() throws Exception {
    List<String> log = new ArrayList<>();
    AtomicInteger innerFires = new AtomicInteger();

    MethodInterceptor<String> retryer =
        invocation -> {
          log.add("retry:start");
          Object a = invocation.proceed();
          log.add("retry:after1=" + a);
          Object b = invocation.proceed();
          log.add("retry:after2=" + b);
          return a + "|" + b;
        };
    MethodInterceptor<String> inner =
        invocation -> {
          innerFires.incrementAndGet();
          log.add("inner:fire");
          return invocation.proceed();
        };

    MethodInvoker<String> invoker = build(cfg -> cfg.interceptor(retryer).interceptor(inner));
    Object result = invoker.invoke("w");

    // Inner must fire TWICE — once for each proceed() from retryer. The chain must re-run from the
    // caller's position on every proceed() call, not skip forward past already-consumed steps.
    assertThat(innerFires.get()).isEqualTo(2);
    assertThat(result).isEqualTo("hello w|hello w");
    assertThat(log)
        .containsExactly(
            "retry:start",
            "inner:fire",
            "retry:after1=hello w",
            "inner:fire",
            "retry:after2=hello w");
  }

  private static MethodInterceptor<String> named(String name, List<String> log) {
    return invocation -> {
      log.add(name + ":before");
      Object result = invocation.proceed();
      log.add(name + ":after");
      return result;
    };
  }
}
