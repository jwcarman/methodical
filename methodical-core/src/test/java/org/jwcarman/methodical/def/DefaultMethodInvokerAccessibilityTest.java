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

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodValidator;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodInvokerAccessibilityTest {

  private static class PrivateService {
    private String secret(String name) {
      return "got: " + name;
    }
  }

  @Test
  void can_invoke_private_method_on_private_nested_class() throws Exception {
    PrivateService target = new PrivateService();
    Method method = PrivateService.class.getDeclaredMethod("secret", String.class);
    ParameterInfo info = ParameterInfo.of(method.getParameters()[0], 0, TypeRef.of(String.class));
    ParameterResolver<String> resolver =
        new ParameterResolver<>() {
          @Override
          public boolean supports(ParameterInfo paramInfo) {
            return true;
          }

          @Override
          public Object resolve(ParameterInfo paramInfo, String argument) {
            return argument;
          }
        };

    MethodInvoker<String> invoker =
        new DefaultMethodInvoker<>(
            method, target, new ParameterInfo[] {info}, List.of(resolver), MethodValidator.NO_OP);

    assertThat(invoker.invoke("hello")).isEqualTo("got: hello");
  }
}
