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

import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/**
 * A {@link ParameterResolver} that passes the invoker's argument through to a parameter annotated
 * with {@link org.jwcarman.methodical.Argument}. Generic-aware: {@link #supports(ParameterInfo)}
 * defers to {@link ParameterInfo#accepts(TypeRef)} for assignability against the parameter's
 * generic type.
 */
final class ArgumentParameterResolver<A> implements ParameterResolver<A> {

  private final TypeRef<?> argumentType;

  ArgumentParameterResolver(TypeRef<?> argumentType) {
    this.argumentType = argumentType;
  }

  @Override
  public boolean supports(ParameterInfo info) {
    return info.accepts(argumentType);
  }

  @Override
  public Object resolve(ParameterInfo info, A argument) {
    return argument;
  }
}
