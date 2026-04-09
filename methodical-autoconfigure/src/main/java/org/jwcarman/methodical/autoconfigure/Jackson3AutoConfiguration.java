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
package org.jwcarman.methodical.autoconfigure;

import org.jwcarman.methodical.jackson3.Jackson3ParameterResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.ObjectMapper;

@AutoConfiguration(before = MethodicalAutoConfiguration.class)
@ConditionalOnClass(name = "tools.jackson.databind.ObjectMapper")
@ConditionalOnBean(ObjectMapper.class)
public class Jackson3AutoConfiguration {

  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE)
  public Jackson3ParameterResolver jackson3ParameterResolver(ObjectMapper mapper) {
    return new Jackson3ParameterResolver(mapper);
  }
}
