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

/**
 * Intercepts a method invocation. Implementations decide whether and when to call {@link
 * MethodInvocation#proceed()} to continue the chain.
 *
 * <p>Interceptors registered on a {@link org.jwcarman.methodical.MethodInvokerConfig} run in
 * registration order: the first interceptor added is the outermost and runs first; the last
 * interceptor added runs closest to the reflective method invocation.
 */
@FunctionalInterface
public interface MethodInterceptor<A> {

  Object intercept(MethodInvocation<? extends A> invocation);
}
