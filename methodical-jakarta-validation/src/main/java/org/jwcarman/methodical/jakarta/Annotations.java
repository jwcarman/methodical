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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

final class Annotations {

  private Annotations() {}

  static <A extends Annotation> A findOnMethod(Method method, Class<A> annotationType) {
    Method target = method.isBridge() ? resolveBridged(method) : method;
    A direct = target.getAnnotation(annotationType);
    if (direct != null) {
      return direct;
    }
    Class<?> declaring = target.getDeclaringClass();
    Class<?> superclass = declaring.getSuperclass();
    while (superclass != null && superclass != Object.class) {
      A found = lookup(superclass, target.getName(), target.getParameterCount(), annotationType);
      if (found != null) {
        return found;
      }
      superclass = superclass.getSuperclass();
    }
    for (Class<?> iface : allInterfaces(declaring)) {
      A found = lookup(iface, target.getName(), target.getParameterCount(), annotationType);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  static <A extends Annotation> A findOnClass(Class<?> type, Class<A> annotationType) {
    Class<?> current = type;
    while (current != null && current != Object.class) {
      A direct = current.getAnnotation(annotationType);
      if (direct != null) {
        return direct;
      }
      current = current.getSuperclass();
    }
    for (Class<?> iface : allInterfaces(type)) {
      A direct = iface.getAnnotation(annotationType);
      if (direct != null) {
        return direct;
      }
    }
    return null;
  }

  private static <A extends Annotation> A lookup(
      Class<?> type, String name, int parameterCount, Class<A> annotationType) {
    for (Method candidate : type.getDeclaredMethods()) {
      if (!candidate.isBridge()
          && candidate.getName().equals(name)
          && candidate.getParameterCount() == parameterCount) {
        A annotation = candidate.getAnnotation(annotationType);
        if (annotation != null) {
          return annotation;
        }
      }
    }
    return null;
  }

  private static Set<Class<?>> allInterfaces(Class<?> type) {
    Set<Class<?>> out = new LinkedHashSet<>();
    Class<?> current = type;
    while (current != null && current != Object.class) {
      collectInterfaces(current, out);
      current = current.getSuperclass();
    }
    return out;
  }

  private static void collectInterfaces(Class<?> type, Set<Class<?>> sink) {
    for (Class<?> iface : type.getInterfaces()) {
      if (sink.add(iface)) {
        collectInterfaces(iface, sink);
      }
    }
  }

  private static Method resolveBridged(Method bridge) {
    Class<?> declaring = bridge.getDeclaringClass();
    for (Method candidate : declaring.getDeclaredMethods()) {
      if (!candidate.isBridge()
          && candidate.getName().equals(bridge.getName())
          && candidate.getParameterCount() == bridge.getParameterCount()) {
        return candidate;
      }
    }
    return bridge;
  }
}
