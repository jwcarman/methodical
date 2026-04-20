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

import java.util.Optional;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

/**
 * Built-in {@link ParameterResolver} for parameters annotated with {@link Argument}. Rejects
 * parameters whose declared type is not assignable from the invoker's argument type at bind time
 * (rather than letting a {@link ClassCastException} fire at invocation time). Produces a binding
 * that returns the invoker's argument unchanged.
 */
final class ArgumentParameterResolver<A> implements ParameterResolver<A> {

  private final TypeRef<?> argumentType;

  ArgumentParameterResolver(TypeRef<?> argumentType) {
    this.argumentType = argumentType;
  }

  @Override
  public Optional<Binding<A>> bind(ParameterInfo info) {
    if (!info.hasAnnotation(Argument.class)) {
      return Optional.empty();
    }
    if (!info.accepts(argumentType)) {
      throw new ParameterResolutionException(
          String.format(
              "@Argument parameter \"%s\" (type %s) is not assignable from argument type %s",
              info.name(), info.genericType().getTypeName(), argumentType.getType().getTypeName()));
    }
    return Optional.of(argument -> argument);
  }
}
