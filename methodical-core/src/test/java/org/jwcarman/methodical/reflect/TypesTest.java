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
package org.jwcarman.methodical.reflect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TypesTest {

  public interface GenericSuperType<T> {
    T someMethod();
  }

  public static class ConcreteType implements GenericSuperType<String> {
    @Override
    public String someMethod() {
      return null;
    }
  }

  public interface TwoTypeParams<K, V> {}

  public static class ConcreteTwo implements TwoTypeParams<String, Integer> {}

  @Test
  void shouldResolveFirstTypeParam() {
    Class<?> typeParam = Types.typeParamFromClass(ConcreteType.class, GenericSuperType.class, 0);
    assertThat(typeParam).isEqualTo(String.class);
  }

  @Test
  void shouldResolveFirstOfTwoTypeParams() {
    Class<?> typeParam = Types.typeParamFromClass(ConcreteTwo.class, TwoTypeParams.class, 0);
    assertThat(typeParam).isEqualTo(String.class);
  }

  @Test
  void shouldResolveSecondOfTwoTypeParams() {
    Class<?> typeParam = Types.typeParamFromClass(ConcreteTwo.class, TwoTypeParams.class, 1);
    assertThat(typeParam).isEqualTo(Integer.class);
  }
}
