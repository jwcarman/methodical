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

import java.lang.reflect.Method;

/** Factory for creating pre-built {@link MethodInvoker} instances. */
public interface MethodInvokerFactory {

  /**
   * Creates a {@link MethodInvoker} for the given method and target object. The invoker resolves
   * arguments from the given argument type at invocation time.
   *
   * @param method the method to invoke
   * @param target the target object to invoke the method on
   * @param argumentType the type of argument passed to the invoker at call time
   * @param <A> the argument type
   * @return a pre-built invoker
   */
  <A> MethodInvoker<A> create(Method method, Object target, Class<A> argumentType);
}
