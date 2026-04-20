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
package org.jwcarman.methodical.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvokerConfig;
import org.jwcarman.methodical.intercept.MethodInterceptor;
import org.jwcarman.methodical.intercept.MethodInvocation;
import org.jwcarman.methodical.param.ParameterResolver;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodInvokerConfigTest {

  @Test
  void resolver_null_rejected() {
    DefaultMethodInvokerConfig<String> cfg = new DefaultMethodInvokerConfig<>();
    assertThatThrownBy(() -> cfg.resolver(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void interceptor_null_rejected() {
    DefaultMethodInvokerConfig<String> cfg = new DefaultMethodInvokerConfig<>();
    assertThatThrownBy(() -> cfg.interceptor(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void resolver_returns_self_for_chaining() {
    DefaultMethodInvokerConfig<String> cfg = new DefaultMethodInvokerConfig<>();
    ParameterResolver<String> r = info -> java.util.Optional.of(arg -> arg);
    MethodInvokerConfig<String> returned = cfg.resolver(r);
    assertThat(returned).isSameAs(cfg);
  }

  @Test
  void interceptor_returns_self_for_chaining() {
    DefaultMethodInvokerConfig<String> cfg = new DefaultMethodInvokerConfig<>();
    MethodInterceptor<String> i = MethodInvocation::proceed;
    MethodInvokerConfig<String> returned = cfg.interceptor(i);
    assertThat(returned).isSameAs(cfg);
  }

  @Test
  void interceptors_exposed_list_preserves_registration_order() {
    DefaultMethodInvokerConfig<String> cfg = new DefaultMethodInvokerConfig<>();
    MethodInterceptor<String> a = MethodInvocation::proceed;
    MethodInterceptor<String> b = MethodInvocation::proceed;
    MethodInterceptor<String> c = MethodInvocation::proceed;
    cfg.interceptor(a).interceptor(b).interceptor(c);
    assertThat(cfg.interceptors()).containsExactly(a, b, c);
  }

  @Test
  void interceptors_exposed_list_is_immutable() {
    DefaultMethodInvokerConfig<String> cfg = new DefaultMethodInvokerConfig<>();
    cfg.interceptor(MethodInvocation::proceed);
    var list = cfg.interceptors();
    assertThatThrownBy(() -> list.add(MethodInvocation::proceed))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
