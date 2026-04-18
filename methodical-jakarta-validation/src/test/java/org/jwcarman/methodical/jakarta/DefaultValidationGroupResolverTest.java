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
package org.jwcarman.methodical.jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.groups.Default;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultValidationGroupResolverTest {

  interface GroupA {}

  interface GroupB {}

  static class Plain {
    public void run() {}
  }

  @MethodValidation(groups = GroupA.class)
  static class ClassAnnotated {
    public void run() {}

    @MethodValidation(groups = GroupB.class)
    public void other() {}
  }

  @MethodValidation
  static class EmptyGroups {
    public void run() {}
  }

  @Test
  void uses_constructor_default_when_nothing_annotated() throws Exception {
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {Default.class});
    Method m = Plain.class.getMethod("run");
    assertThat(r.resolveGroups(new Plain(), m)).containsExactly(Default.class);
  }

  @Test
  void class_annotation_overrides_constructor_default() throws Exception {
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {Default.class});
    Method m = ClassAnnotated.class.getMethod("run");
    assertThat(r.resolveGroups(new ClassAnnotated(), m)).containsExactly(GroupA.class);
  }

  @Test
  void method_annotation_overrides_class_annotation() throws Exception {
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {Default.class});
    Method m = ClassAnnotated.class.getMethod("other");
    assertThat(r.resolveGroups(new ClassAnnotated(), m)).containsExactly(GroupB.class);
  }

  @Test
  void empty_groups_on_annotation_normalize_to_constructor_default() throws Exception {
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {GroupA.class});
    Method m = EmptyGroups.class.getMethod("run");
    assertThat(r.resolveGroups(new EmptyGroups(), m)).containsExactly(GroupA.class);
  }

  @Test
  void null_target_uses_method_declaring_class_for_class_annotation() throws Exception {
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {Default.class});
    Method m = ClassAnnotated.class.getMethod("run");
    assertThat(r.resolveGroups(null, m)).containsExactly(GroupA.class);
  }
}
