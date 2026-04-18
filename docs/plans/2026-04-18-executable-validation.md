# Executable Validation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add optional Jakarta Bean Validation support to Methodical via a small `MethodValidator` SPI in core (no-op by default) and a separate `methodical-jakarta-validation` module that wires Jakarta's `ExecutableValidator` into the invocation flow.

**Architecture:** Single `DefaultMethodInvokerFactory` gains an optional `MethodValidator` constructor arg (defaults to `NoOpMethodValidator`). `DefaultMethodInvoker` calls `validateParameters` after argument resolution and `validateReturnValue` after reflective invocation. The Jakarta module provides a `JakartaMethodValidator`, a `@MethodValidation` annotation (groups + return-value toggle), and a `ValidationGroupResolver` that walks the class/interface hierarchy (including bridge-method resolution) to find the annotation.

**Tech Stack:** Java 25, JUnit 5, AssertJ, Mockito, Maven multi-module, Jakarta Bean Validation 3.x API, Hibernate Validator 9.x (test-scope).

**Design decisions already settled:**
- One factory, not a wrapper. `ValidatingMethodInvokerFactory` is rejected.
- `@MethodValidation` lives in the Jakarta module, not core (groups field is Jakarta-specific).
- Annotation inheritance: walks superclass chain + interfaces; method-level resolution must handle `Method.isBridge()` by jumping to the real target.
- Resolver order: method annotation → class annotation → constructor-default groups. No silent `Default.class` fallback in the resolver — the constructor default carries that responsibility.
- Static methods: Jakarta's `ExecutableValidator` requires a non-null target. The Jakarta module's validator skips validation when `Modifier.isStatic(method.getModifiers())` — documented as a known limitation. (Core's `MethodValidator` contract permits a null target; impls decide.)
- Exception leakage: `ConstraintViolationException` propagates directly; opt-in is at the dependency level.

**Out of scope for this plan:** Spring Boot autoconfiguration for Jakarta (deferred — separate plan when needed).

---

## Conventions

- Every new `.java` file starts with the Apache 2.0 license header used in existing files (see `methodical-core/src/main/java/org/jwcarman/methodical/MethodInvoker.java`).
- Code formatting is enforced by spotless (Google Java Format). Run `./mvnw spotless:apply` before committing if needed.
- Tests use `@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)` and snake_case method names — match the existing convention in `DefaultMethodInvokerFactoryWrapTest`.
- No star imports, no `@SuppressWarnings`, no comments unless explaining a non-obvious *why*.
- Commit after each task. Use conventional-commit prefixes: `feat:`, `test:`, `refactor:`, `docs:`.

---

# Phase 1 — Core hook

## Task 1: Add `MethodValidator` interface to core

**Files:**
- Create: `methodical-core/src/main/java/org/jwcarman/methodical/MethodValidator.java`

**Step 1: Write the file**

```java
package org.jwcarman.methodical;

import java.lang.reflect.Method;

/**
 * Optional hook invoked around reflective method invocation to validate
 * parameters before the call and the return value after.
 *
 * <p>Implementations may throw any runtime exception when validation fails;
 * the exception propagates out of {@link MethodInvoker#invoke(Object)} unchanged.
 * The {@code target} argument may be {@code null} for static methods.
 */
public interface MethodValidator {

  void validateParameters(Object target, Method method, Object[] args);

  void validateReturnValue(Object target, Method method, Object returnValue);
}
```

**Step 2: Verify compile**

Run: `./mvnw -pl methodical-core compile -q`
Expected: success

**Step 3: Commit**

```bash
git add methodical-core/src/main/java/org/jwcarman/methodical/MethodValidator.java
git commit -m "feat(core): add MethodValidator SPI"
```

---

## Task 2: Add `NoOpMethodValidator` (TDD)

**Files:**
- Test: `methodical-core/src/test/java/org/jwcarman/methodical/NoOpMethodValidatorTest.java`
- Create: `methodical-core/src/main/java/org/jwcarman/methodical/NoOpMethodValidator.java`

**Step 1: Write the failing test**

```java
package org.jwcarman.methodical;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NoOpMethodValidatorTest {

  @Test
  void validate_parameters_does_nothing() throws Exception {
    MethodValidator v = new NoOpMethodValidator();
    Method m = String.class.getMethod("length");
    assertThatCode(() -> v.validateParameters("x", m, new Object[0])).doesNotThrowAnyException();
  }

  @Test
  void validate_return_value_does_nothing() throws Exception {
    MethodValidator v = new NoOpMethodValidator();
    Method m = String.class.getMethod("length");
    assertThatCode(() -> v.validateReturnValue("x", m, 1)).doesNotThrowAnyException();
  }

  @Test
  void accepts_null_target_for_static_methods() throws Exception {
    MethodValidator v = new NoOpMethodValidator();
    Method m = Integer.class.getMethod("parseInt", String.class);
    assertThatCode(() -> v.validateParameters(null, m, new Object[] {"1"}))
        .doesNotThrowAnyException();
  }
}
```

**Step 2: Run, expect FAIL (class missing)**

Run: `./mvnw -pl methodical-core test -Dtest=NoOpMethodValidatorTest -q`
Expected: compilation error referencing `NoOpMethodValidator`

**Step 3: Implement**

```java
package org.jwcarman.methodical;

import java.lang.reflect.Method;

/** No-op {@link MethodValidator}; the default when validation is not configured. */
public final class NoOpMethodValidator implements MethodValidator {

  @Override
  public void validateParameters(Object target, Method method, Object[] args) {
    // intentionally empty
  }

  @Override
  public void validateReturnValue(Object target, Method method, Object returnValue) {
    // intentionally empty
  }
}
```

**Step 4: Run, expect PASS**

Run: `./mvnw -pl methodical-core test -Dtest=NoOpMethodValidatorTest -q`

**Step 5: Commit**

```bash
git add methodical-core/src/main/java/org/jwcarman/methodical/NoOpMethodValidator.java \
        methodical-core/src/test/java/org/jwcarman/methodical/NoOpMethodValidatorTest.java
git commit -m "feat(core): add NoOpMethodValidator"
```

---

## Task 3: Wire `MethodValidator` into `DefaultMethodInvoker` (TDD)

**Files:**
- Modify: `methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvoker.java`
- Test: `methodical-core/src/test/java/org/jwcarman/methodical/def/DefaultMethodInvokerValidationTest.java`

**Step 1: Write the failing test**

```java
package org.jwcarman.methodical.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodValidator;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodInvokerValidationTest {

  static class Greeter {
    String greet(String name) {
      return "hello " + name;
    }
  }

  @Test
  void validator_sees_parameters_then_return_value_in_order() throws Exception {
    Greeter target = new Greeter();
    Method method = Greeter.class.getDeclaredMethod("greet", String.class);
    ParameterInfo info = ParameterInfo.of(method.getParameters()[0], 0,
        org.jwcarman.specular.TypeRef.of(String.class));
    ParameterResolver<String> resolver = (pi, arg) -> arg;
    StringBuilder log = new StringBuilder();
    MethodValidator validator = new MethodValidator() {
      @Override
      public void validateParameters(Object t, Method m, Object[] args) {
        log.append("params:").append(args[0]).append(';');
      }
      @Override
      public void validateReturnValue(Object t, Method m, Object returnValue) {
        log.append("return:").append(returnValue).append(';');
      }
    };

    MethodInvoker<String> invoker =
        new DefaultMethodInvoker<>(method, target, new ParameterInfo[] {info},
            List.of(resolver), validator);

    Object result = invoker.invoke("world");

    assertThat(result).isEqualTo("hello world");
    assertThat(log.toString()).isEqualTo("params:world;return:hello world;");
  }

  @Test
  void parameter_validation_failure_skips_invocation_and_return_validation() throws Exception {
    Greeter target = new Greeter();
    Method method = Greeter.class.getDeclaredMethod("greet", String.class);
    ParameterInfo info = ParameterInfo.of(method.getParameters()[0], 0,
        org.jwcarman.specular.TypeRef.of(String.class));
    ParameterResolver<String> resolver = (pi, arg) -> arg;
    boolean[] returnValidated = {false};
    MethodValidator validator = new MethodValidator() {
      @Override
      public void validateParameters(Object t, Method m, Object[] args) {
        throw new IllegalArgumentException("bad args");
      }
      @Override
      public void validateReturnValue(Object t, Method m, Object returnValue) {
        returnValidated[0] = true;
      }
    };

    MethodInvoker<String> invoker =
        new DefaultMethodInvoker<>(method, target, new ParameterInfo[] {info},
            List.of(resolver), validator);

    assertThatThrownBy(() -> invoker.invoke("world"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bad args");
    assertThat(returnValidated[0]).isFalse();
  }
}
```

**Step 2: Run, expect FAIL (constructor signature mismatch)**

Run: `./mvnw -pl methodical-core test -Dtest=DefaultMethodInvokerValidationTest -q`
Expected: compile error — `DefaultMethodInvoker` constructor doesn't accept a `MethodValidator`.

**Step 3: Modify `DefaultMethodInvoker`**

Replace the existing class body to add a `MethodValidator` field and call it around the reflective invoke. Full file content:

```java
package org.jwcarman.methodical.def;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.jwcarman.methodical.MethodInvocationException;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodValidator;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;

class DefaultMethodInvoker<A> implements MethodInvoker<A> {

  private final Method method;
  private final Object target;
  private final ParameterInfo[] paramInfos;
  private final List<ParameterResolver<? super A>> resolvers;
  private final MethodValidator validator;

  DefaultMethodInvoker(
      Method method,
      Object target,
      ParameterInfo[] paramInfos,
      List<ParameterResolver<? super A>> resolvers,
      MethodValidator validator) {
    this.method = method;
    this.target = target;
    this.paramInfos = paramInfos;
    this.resolvers = resolvers;
    this.validator = validator;
  }

  @Override
  public Object invoke(A argument) {
    Object[] args = resolveArguments(argument);
    validator.validateParameters(target, method, args);
    Object result;
    try {
      result = method.invoke(target, args);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) {
        throw re;
      }
      throw new MethodInvocationException("Method invocation failed: " + cause.getMessage(), cause);
    } catch (IllegalAccessException e) {
      throw new MethodInvocationException("Method invocation failed: " + e.getMessage(), e);
    }
    validator.validateReturnValue(target, method, result);
    return result;
  }

  private Object[] resolveArguments(A argument) {
    Object[] args = new Object[paramInfos.length];
    for (int i = 0; i < paramInfos.length; i++) {
      args[i] = resolvers.get(i).resolve(paramInfos[i], argument);
    }
    return args;
  }
}
```

**Step 4: Update `DefaultMethodInvokerFactory` to pass `NoOpMethodValidator` (temporary — Task 4 makes it injectable)**

In `methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactory.java`, change line 59 from:

```java
return new DefaultMethodInvoker<>(method, target, paramInfos, assigned);
```

to:

```java
return new DefaultMethodInvoker<>(
    method, target, paramInfos, assigned, new NoOpMethodValidator());
```

Add the import: `import org.jwcarman.methodical.NoOpMethodValidator;`

**Step 5: Run new test + full core suite**

Run: `./mvnw -pl methodical-core test -q`
Expected: PASS

**Step 6: Commit**

```bash
git add methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvoker.java \
        methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactory.java \
        methodical-core/src/test/java/org/jwcarman/methodical/def/DefaultMethodInvokerValidationTest.java
git commit -m "feat(core): invoke MethodValidator around reflective call"
```

---

## Task 4: Make `MethodValidator` injectable through `DefaultMethodInvokerFactory` (TDD)

**Files:**
- Modify: `methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactory.java`
- Test: `methodical-core/src/test/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactoryValidationTest.java`

**Step 1: Write the failing test**

```java
package org.jwcarman.methodical.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodValidator;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodInvokerFactoryValidationTest {

  static class Echo {
    String echo(String s) {
      return s;
    }
  }

  @Test
  void factory_threads_validator_into_created_invoker() throws Exception {
    boolean[] called = {false};
    MethodValidator v = new MethodValidator() {
      @Override
      public void validateParameters(Object t, Method m, Object[] args) {
        called[0] = true;
      }
      @Override
      public void validateReturnValue(Object t, Method m, Object r) {}
    };
    DefaultMethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of(), v);
    Method m = Echo.class.getDeclaredMethod("echo", String.class);
    MethodInvoker<String> invoker = factory.create(m, new Echo(), String.class);

    assertThat(invoker.invoke("hi")).isEqualTo("hi");
    assertThat(called[0]).isTrue();
  }

  @Test
  void single_arg_constructor_uses_no_op_validator() throws Exception {
    DefaultMethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of());
    Method m = Echo.class.getDeclaredMethod("echo", String.class);
    MethodInvoker<String> invoker = factory.create(m, new Echo(), String.class);
    // Behaves identically to the legacy code path; smoke test.
    assertThat(invoker.invoke("hi")).isEqualTo("hi");
  }

  @Test
  void rejects_null_validator() {
    assertThatThrownBy(() -> new DefaultMethodInvokerFactory(List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
```

**Step 2: Run, expect FAIL**

Run: `./mvnw -pl methodical-core test -Dtest=DefaultMethodInvokerFactoryValidationTest -q`

**Step 3: Modify factory**

Add a `MethodValidator validator` field and a second constructor; the existing constructor delegates with `NoOpMethodValidator`. Update `create()` to pass it through.

```java
public class DefaultMethodInvokerFactory implements MethodInvokerFactory {

  private final List<ResolvedParameterResolver<?>> resolvers;
  private final MethodValidator validator;

  public DefaultMethodInvokerFactory(List<ParameterResolver<?>> resolvers) {
    this(resolvers, new NoOpMethodValidator());
  }

  public DefaultMethodInvokerFactory(
      List<ParameterResolver<?>> resolvers, MethodValidator validator) {
    this.resolvers =
        resolvers.stream()
            .<ResolvedParameterResolver<?>>map(DefaultMethodInvokerFactory::wrap)
            .toList();
    this.validator = Objects.requireNonNull(validator, "validator");
  }

  // ... create() now passes `validator` instead of `new NoOpMethodValidator()`.
```

Add imports: `import java.util.Objects;`, `import org.jwcarman.methodical.MethodValidator;`. Keep the `NoOpMethodValidator` import.

**Step 4: Run, expect PASS**

Run: `./mvnw -pl methodical-core test -q`

**Step 5: Commit**

```bash
git add methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactory.java \
        methodical-core/src/test/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactoryValidationTest.java
git commit -m "feat(core): accept optional MethodValidator in DefaultMethodInvokerFactory"
```

---

# Phase 2 — Jakarta Validation module

## Task 5: Scaffold `methodical-jakarta-validation` module

**Files:**
- Create: `methodical-jakarta-validation/pom.xml`
- Modify: `pom.xml` (root) — add module + dependency-management entry
- Modify: `methodical-bom/pom.xml` — add the new artifact (if BOM lists modules; check first)

**Step 1: Create module pom**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.jwcarman.methodical</groupId>
        <artifactId>methodical-parent</artifactId>
        <version>0.5.0-SNAPSHOT</version>
    </parent>

    <artifactId>methodical-jakarta-validation</artifactId>

    <name>Methodical Jakarta Validation</name>
    <description>Jakarta Bean Validation integration for Methodical</description>

    <dependencies>
        <dependency>
            <groupId>org.jwcarman.methodical</groupId>
            <artifactId>methodical-core</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.hibernate.validator</groupId>
            <artifactId>hibernate-validator</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.glassfish.expressly</groupId>
            <artifactId>expressly</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Add the LICENSE header comment block at the top (copy from `methodical-core/pom.xml`).

**Step 2: Add to root pom**

In `/Users/jcarman/IdeaProjects/methodical/pom.xml`, add to `<modules>` (after `methodical-gson`):

```xml
<module>methodical-jakarta-validation</module>
```

Add to `<dependencyManagement>`:

```xml
<dependency>
    <groupId>org.jwcarman.methodical</groupId>
    <artifactId>methodical-jakarta-validation</artifactId>
    <version>${project.version}</version>
</dependency>
```

**Step 3: Add to BOM if present**

Read `methodical-bom/pom.xml` first. If it enumerates artifacts, add `methodical-jakarta-validation`.

**Step 4: Verify build**

Run: `./mvnw -pl methodical-jakarta-validation -am compile -q`
Expected: success (Spring Boot parent manages `jakarta.validation-api` and `hibernate-validator` versions — no version needed in module pom)

**Step 5: Commit**

```bash
git add pom.xml methodical-bom/pom.xml methodical-jakarta-validation/pom.xml
git commit -m "build: scaffold methodical-jakarta-validation module"
```

---

## Task 6: Add `@MethodValidation` annotation

**Files:**
- Create: `methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/MethodValidation.java`

**Step 1: Write the file**

```java
package org.jwcarman.methodical.jakarta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures Jakarta Bean Validation behavior for a Methodical-invoked method.
 *
 * <p>May be placed on the method itself or on the declaring class. Method-level
 * annotations override class-level annotations. Inherited from superclasses and
 * implemented interfaces.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MethodValidation {

  Class<?>[] groups() default {};

  boolean validateReturnValue() default true;
}
```

**Step 2: Verify compile**

Run: `./mvnw -pl methodical-jakarta-validation compile -q`

**Step 3: Commit**

```bash
git add methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/MethodValidation.java
git commit -m "feat(jakarta): add @MethodValidation annotation"
```

---

## Task 7: Add `AnnotationFinder` helper (TDD)

This helper walks the class/interface hierarchy and resolves bridge methods to their bridged target. It is package-private — internal to the Jakarta module.

**Files:**
- Test: `methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/AnnotationFinderTest.java`
- Create: `methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/AnnotationFinder.java`

**Step 1: Write the failing test**

Cover:
- annotation directly on method → found
- annotation on overridden method in superclass → found
- annotation on method in implemented interface → found
- annotation on neither → null
- bridge method delegates to the real method's annotation
- class-level: annotation on the declaring class → found
- class-level: annotation on superclass → found
- class-level: annotation on interface → found

```java
package org.jwcarman.methodical.jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AnnotationFinderTest {

  @Target({ElementType.TYPE, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @interface Marker {
    String value();
  }

  interface Iface {
    @Marker("iface-method")
    void hello();
  }

  @Marker("super-class")
  static class Base implements Iface {
    @Override
    @Marker("base-method")
    public void hello() {}
  }

  static class Child extends Base {
    @Override
    public void hello() {}
  }

  @Marker("annotated-class")
  static class AnnotatedOnly {
    public void hello() {}
  }

  static class Plain {
    public void hello() {}
  }

  @Test
  void method_annotation_found_via_overridden_chain() throws Exception {
    Method m = Child.class.getMethod("hello");
    Marker found = AnnotationFinder.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("base-method");
  }

  @Test
  void method_annotation_found_on_interface() throws Exception {
    interface OnlyIface {
      @Marker("only-iface")
      void run();
    }
    class Impl implements OnlyIface {
      @Override public void run() {}
    }
    Method m = Impl.class.getMethod("run");
    Marker found = AnnotationFinder.findOnMethod(m, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("only-iface");
  }

  @Test
  void method_annotation_returns_null_when_absent() throws Exception {
    Method m = Plain.class.getMethod("hello");
    assertThat(AnnotationFinder.findOnMethod(m, Marker.class)).isNull();
  }

  @Test
  void class_annotation_found_directly() {
    Marker found = AnnotationFinder.findOnClass(AnnotatedOnly.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("annotated-class");
  }

  @Test
  void class_annotation_found_via_superclass() {
    Marker found = AnnotationFinder.findOnClass(Child.class, Marker.class);
    assertThat(found).isNotNull();
    assertThat(found.value()).isEqualTo("super-class");
  }

  @Test
  void class_annotation_returns_null_when_absent() {
    assertThat(AnnotationFinder.findOnClass(Plain.class, Marker.class)).isNull();
  }
}
```

**Step 2: Run, expect FAIL**

Run: `./mvnw -pl methodical-jakarta-validation test -Dtest=AnnotationFinderTest -q`

**Step 3: Implement**

```java
package org.jwcarman.methodical.jakarta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

final class AnnotationFinder {

  private AnnotationFinder() {}

  static <A extends Annotation> A findOnMethod(Method method, Class<A> annotationType) {
    Method target = method.isBridge() ? resolveBridged(method) : method;
    A direct = target.getAnnotation(annotationType);
    if (direct != null) {
      return direct;
    }
    Class<?> declaring = target.getDeclaringClass();
    Class<?> superclass = declaring.getSuperclass();
    while (superclass != null && superclass != Object.class) {
      A found = lookup(superclass, target.getName(), target.getParameterTypes(), annotationType);
      if (found != null) {
        return found;
      }
      superclass = superclass.getSuperclass();
    }
    for (Class<?> iface : allInterfaces(declaring)) {
      A found = lookup(iface, target.getName(), target.getParameterTypes(), annotationType);
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
      Class<?> type, String name, Class<?>[] params, Class<A> annotationType) {
    try {
      Method m = type.getDeclaredMethod(name, params);
      return m.getAnnotation(annotationType);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private static java.util.Set<Class<?>> allInterfaces(Class<?> type) {
    java.util.Set<Class<?>> out = new java.util.LinkedHashSet<>();
    Class<?> current = type;
    while (current != null && current != Object.class) {
      collectInterfaces(current, out);
      current = current.getSuperclass();
    }
    return out;
  }

  private static void collectInterfaces(Class<?> type, java.util.Set<Class<?>> sink) {
    for (Class<?> iface : type.getInterfaces()) {
      if (sink.add(iface)) {
        collectInterfaces(iface, sink);
      }
    }
  }

  // Bridge methods: walk to the same-name, same-arity method whose erased parameter types match
  // and which is itself non-bridge.
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
```

**Step 4: Run, expect PASS**

Run: `./mvnw -pl methodical-jakarta-validation test -Dtest=AnnotationFinderTest -q`

**Step 5: Commit**

```bash
git add methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/AnnotationFinder.java \
        methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/AnnotationFinderTest.java
git commit -m "feat(jakarta): add AnnotationFinder helper for hierarchy walks"
```

---

## Task 8: Add `ValidationGroupResolver` interface + default impl (TDD)

**Files:**
- Create: `methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/ValidationGroupResolver.java`
- Create: `methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/DefaultValidationGroupResolver.java`
- Test: `methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/DefaultValidationGroupResolverTest.java`

**Step 1: Write interface**

```java
package org.jwcarman.methodical.jakarta;

import java.lang.reflect.Method;

public interface ValidationGroupResolver {

  Class<?>[] resolveGroups(Object target, Method method);

  boolean shouldValidateReturnValue(Object target, Method method);
}
```

**Step 2: Write the failing test for default impl**

```java
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
    @MethodValidation(groups = GroupB.class, validateReturnValue = false)
    public void other() {}
  }

  @Test
  void uses_constructor_default_when_nothing_annotated() throws Exception {
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {Default.class}, true);
    Method m = Plain.class.getMethod("run");
    assertThat(r.resolveGroups(new Plain(), m)).containsExactly(Default.class);
    assertThat(r.shouldValidateReturnValue(new Plain(), m)).isTrue();
  }

  @Test
  void class_annotation_overrides_constructor_default() throws Exception {
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {Default.class}, true);
    Method m = ClassAnnotated.class.getMethod("run");
    assertThat(r.resolveGroups(new ClassAnnotated(), m)).containsExactly(GroupA.class);
    assertThat(r.shouldValidateReturnValue(new ClassAnnotated(), m)).isTrue();
  }

  @Test
  void method_annotation_overrides_class_annotation() throws Exception {
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {Default.class}, true);
    Method m = ClassAnnotated.class.getMethod("other");
    assertThat(r.resolveGroups(new ClassAnnotated(), m)).containsExactly(GroupB.class);
    assertThat(r.shouldValidateReturnValue(new ClassAnnotated(), m)).isFalse();
  }

  @Test
  void empty_groups_on_annotation_normalize_to_constructor_default() throws Exception {
    @MethodValidation
    class EmptyGroups {
      public void run() {}
    }
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {GroupA.class}, true);
    Method m = EmptyGroups.class.getMethod("run");
    assertThat(r.resolveGroups(new EmptyGroups(), m)).containsExactly(GroupA.class);
  }

  @Test
  void constructor_default_for_return_value_used_when_no_annotation() throws Exception {
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {Default.class}, false);
    Method m = Plain.class.getMethod("run");
    assertThat(r.shouldValidateReturnValue(new Plain(), m)).isFalse();
  }

  @Test
  void null_target_uses_method_declaring_class_for_class_annotation() throws Exception {
    DefaultValidationGroupResolver r =
        new DefaultValidationGroupResolver(new Class<?>[] {Default.class}, true);
    Method m = ClassAnnotated.class.getMethod("run");
    assertThat(r.resolveGroups(null, m)).containsExactly(GroupA.class);
  }
}
```

**Step 3: Run, expect FAIL**

Run: `./mvnw -pl methodical-jakarta-validation test -Dtest=DefaultValidationGroupResolverTest -q`

**Step 4: Implement default resolver**

```java
package org.jwcarman.methodical.jakarta;

import java.lang.reflect.Method;
import java.util.Objects;

public final class DefaultValidationGroupResolver implements ValidationGroupResolver {

  private final Class<?>[] defaultGroups;
  private final boolean defaultValidateReturnValue;

  public DefaultValidationGroupResolver(
      Class<?>[] defaultGroups, boolean defaultValidateReturnValue) {
    this.defaultGroups = Objects.requireNonNull(defaultGroups, "defaultGroups").clone();
    this.defaultValidateReturnValue = defaultValidateReturnValue;
  }

  @Override
  public Class<?>[] resolveGroups(Object target, Method method) {
    MethodValidation annotation = findAnnotation(target, method);
    if (annotation != null && annotation.groups().length > 0) {
      return annotation.groups();
    }
    return defaultGroups.clone();
  }

  @Override
  public boolean shouldValidateReturnValue(Object target, Method method) {
    MethodValidation annotation = findAnnotation(target, method);
    if (annotation != null) {
      return annotation.validateReturnValue();
    }
    return defaultValidateReturnValue;
  }

  private MethodValidation findAnnotation(Object target, Method method) {
    MethodValidation onMethod = AnnotationFinder.findOnMethod(method, MethodValidation.class);
    if (onMethod != null) {
      return onMethod;
    }
    Class<?> type = target != null ? target.getClass() : method.getDeclaringClass();
    return AnnotationFinder.findOnClass(type, MethodValidation.class);
  }
}
```

**Step 5: Run, expect PASS**

Run: `./mvnw -pl methodical-jakarta-validation test -Dtest=DefaultValidationGroupResolverTest -q`

**Step 6: Commit**

```bash
git add methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/ValidationGroupResolver.java \
        methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/DefaultValidationGroupResolver.java \
        methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/DefaultValidationGroupResolverTest.java
git commit -m "feat(jakarta): add ValidationGroupResolver with hierarchy-aware defaults"
```

---

## Task 9: Add `JakartaMethodValidator` (TDD with Hibernate Validator)

**Files:**
- Create: `methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/JakartaMethodValidator.java`
- Test: `methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/JakartaMethodValidatorTest.java`

**Step 1: Write the failing test**

Cover: param violation throws `ConstraintViolationException`; valid call passes; return-value violation throws; `@MethodValidation(validateReturnValue=false)` skips return validation; static method skipped without throwing; null target on instance method skipped without throwing (defensive — Jakarta would throw `IllegalArgumentException`).

```java
package org.jwcarman.methodical.jakarta;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodValidator;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JakartaMethodValidatorTest {

  static class Service {
    @NotBlank
    public String greet(@NotBlank String name) {
      return "hello " + name;
    }

    @NotNull
    public String maybe(boolean ok) {
      return ok ? "value" : null;
    }

    @MethodValidation(validateReturnValue = false)
    @NotNull
    public String maybeNoReturnCheck(boolean ok) {
      return ok ? "value" : null;
    }

    public static String staticMethod(@NotBlank String s) {
      return s;
    }
  }

  private Validator newValidator() {
    return Validation.buildDefaultValidatorFactory().getValidator();
  }

  private MethodValidator newMethodValidator() {
    return new JakartaMethodValidator(
        newValidator(),
        new DefaultValidationGroupResolver(new Class<?>[] {Default.class}, true));
  }

  @Test
  void valid_parameters_pass() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    assertThatCode(() -> newMethodValidator().validateParameters(new Service(), m, new Object[] {"world"}))
        .doesNotThrowAnyException();
  }

  @Test
  void invalid_parameters_throw_ConstraintViolationException() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    assertThatThrownBy(() -> newMethodValidator().validateParameters(new Service(), m, new Object[] {""}))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void valid_return_value_passes() throws Exception {
    Method m = Service.class.getDeclaredMethod("maybe", boolean.class);
    assertThatCode(() -> newMethodValidator().validateReturnValue(new Service(), m, "value"))
        .doesNotThrowAnyException();
  }

  @Test
  void invalid_return_value_throws_ConstraintViolationException() throws Exception {
    Method m = Service.class.getDeclaredMethod("maybe", boolean.class);
    assertThatThrownBy(() -> newMethodValidator().validateReturnValue(new Service(), m, null))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void return_validation_skipped_when_annotation_disables_it() throws Exception {
    Method m = Service.class.getDeclaredMethod("maybeNoReturnCheck", boolean.class);
    assertThatCode(() -> newMethodValidator().validateReturnValue(new Service(), m, null))
        .doesNotThrowAnyException();
  }

  @Test
  void static_methods_are_skipped() throws Exception {
    Method m = Service.class.getDeclaredMethod("staticMethod", String.class);
    assertThatCode(() -> newMethodValidator().validateParameters(null, m, new Object[] {""}))
        .doesNotThrowAnyException();
    assertThatCode(() -> newMethodValidator().validateReturnValue(null, m, ""))
        .doesNotThrowAnyException();
  }

  @Test
  void null_target_on_instance_method_is_skipped() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    assertThatCode(() -> newMethodValidator().validateParameters(null, m, new Object[] {""}))
        .doesNotThrowAnyException();
  }
}
```

**Step 2: Run, expect FAIL**

Run: `./mvnw -pl methodical-jakarta-validation test -Dtest=JakartaMethodValidatorTest -q`

**Step 3: Implement**

`ValidationGroupResolver`'s output is a pure function of `(target.getClass(), method)`. Walking the class/interface hierarchy on every invocation would be wasteful, so cache the resolution per `(class, method)` pair. The cache is an internal optimization — SPI unchanged.

```java
package org.jwcarman.methodical.jakarta;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.executable.ExecutableValidator;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jwcarman.methodical.MethodValidator;

public final class JakartaMethodValidator implements MethodValidator {

  private final ExecutableValidator executableValidator;
  private final ValidationGroupResolver groupResolver;
  private final ConcurrentMap<CacheKey, ResolvedConfig> configCache = new ConcurrentHashMap<>();

  public JakartaMethodValidator(Validator validator, ValidationGroupResolver groupResolver) {
    this.executableValidator =
        Objects.requireNonNull(validator, "validator").forExecutables();
    this.groupResolver = Objects.requireNonNull(groupResolver, "groupResolver");
  }

  @Override
  public void validateParameters(Object target, Method method, Object[] args) {
    if (cannotValidate(target, method)) {
      return;
    }
    ResolvedConfig config = configFor(target, method);
    Set<ConstraintViolation<Object>> violations =
        executableValidator.validateParameters(target, method, args, config.groups());
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  @Override
  public void validateReturnValue(Object target, Method method, Object returnValue) {
    if (cannotValidate(target, method)) {
      return;
    }
    ResolvedConfig config = configFor(target, method);
    if (!config.validateReturnValue()) {
      return;
    }
    Set<ConstraintViolation<Object>> violations =
        executableValidator.validateReturnValue(target, method, returnValue, config.groups());
    if (!violations.isEmpty()) {
      throw new ConstraintViolationException(violations);
    }
  }

  // Jakarta's ExecutableValidator requires a non-null instance and does not handle static methods.
  // Skip silently in both cases — the alternative is throwing, which would surprise users
  // whose Methodical setup happens to include a static method.
  private boolean cannotValidate(Object target, Method method) {
    return target == null || Modifier.isStatic(method.getModifiers());
  }

  private ResolvedConfig configFor(Object target, Method method) {
    CacheKey key = new CacheKey(target.getClass(), method);
    return configCache.computeIfAbsent(
        key,
        k ->
            new ResolvedConfig(
                groupResolver.resolveGroups(target, method),
                groupResolver.shouldValidateReturnValue(target, method)));
  }

  private record CacheKey(Class<?> targetClass, Method method) {}

  private record ResolvedConfig(Class<?>[] groups, boolean validateReturnValue) {}
}
```

Add a test verifying the cache is populated lazily and the resolver is consulted only once per `(class, method)` pair:

```java
@Test
void resolver_is_invoked_once_per_class_method_pair() throws Exception {
  jakarta.validation.Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
  int[] callCount = {0};
  ValidationGroupResolver counting = new ValidationGroupResolver() {
    @Override
    public Class<?>[] resolveGroups(Object target, Method method) {
      callCount[0]++;
      return new Class<?>[] {Default.class};
    }
    @Override
    public boolean shouldValidateReturnValue(Object target, Method method) {
      return true;
    }
  };
  JakartaMethodValidator v = new JakartaMethodValidator(validator, counting);
  Method m = Service.class.getDeclaredMethod("greet", String.class);
  Service target = new Service();
  v.validateParameters(target, m, new Object[] {"a"});
  v.validateParameters(target, m, new Object[] {"b"});
  v.validateReturnValue(target, m, "x");
  assertThat(callCount[0]).isEqualTo(1);
}
```

**Step 4: Run, expect PASS**

Run: `./mvnw -pl methodical-jakarta-validation test -q`

**Step 5: Commit**

```bash
git add methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/JakartaMethodValidator.java \
        methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/JakartaMethodValidatorTest.java
git commit -m "feat(jakarta): add JakartaMethodValidator backed by ExecutableValidator"
```

---

## Task 10: End-to-end integration test (Jakarta validator wired through factory)

**Files:**
- Test: `methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/JakartaValidationIntegrationTest.java`

**Step 1: Write the test**

Wire `DefaultMethodInvokerFactory` with `JakartaMethodValidator` and exercise a real `MethodInvoker`. Verify the violation surfaces from `MethodInvoker.invoke(...)`.

```java
package org.jwcarman.methodical.jakarta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.groups.Default;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.Argument;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.def.DefaultMethodInvokerFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JakartaValidationIntegrationTest {

  static class Greeter {
    public String greet(@Argument @NotBlank String name) {
      return "hello " + name;
    }
  }

  private MethodInvoker<String> buildInvoker() throws Exception {
    JakartaMethodValidator validator =
        new JakartaMethodValidator(
            Validation.buildDefaultValidatorFactory().getValidator(),
            new DefaultValidationGroupResolver(new Class<?>[] {Default.class}, true));
    DefaultMethodInvokerFactory factory =
        new DefaultMethodInvokerFactory(List.of(), validator);
    Method m = Greeter.class.getDeclaredMethod("greet", String.class);
    return factory.create(m, new Greeter(), String.class);
  }

  @Test
  void valid_invocation_returns_result() throws Exception {
    assertThat(buildInvoker().invoke("world")).isEqualTo("hello world");
  }

  @Test
  void invalid_argument_surfaces_constraint_violation_exception() throws Exception {
    MethodInvoker<String> invoker = buildInvoker();
    assertThatThrownBy(() -> invoker.invoke(""))
        .isInstanceOf(ConstraintViolationException.class);
  }
}
```

**Step 2: Run**

Run: `./mvnw -pl methodical-jakarta-validation test -Dtest=JakartaValidationIntegrationTest -q`
Expected: PASS

**Step 3: Commit**

```bash
git add methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/JakartaValidationIntegrationTest.java
git commit -m "test(jakarta): end-to-end invocation through DefaultMethodInvokerFactory"
```

---

## Task 11: Final verification

**Step 1: Full build**

Run: `./mvnw clean verify -q`
Expected: all modules build and test successfully. License/spotless checks pass.

**Step 2: Sonar (if needed)**

If new Sonar findings appear after CI runs, address them per the project's standard workflow.

**Step 3: Update CHANGELOG**

Add an entry under `## [Unreleased]` in `CHANGELOG.md`:

```markdown
### Added
- New `MethodValidator` SPI in `methodical-core` for validating reflective method invocations; defaults to a no-op.
- New `methodical-jakarta-validation` module providing Jakarta Bean Validation integration via `JakartaMethodValidator`, `@MethodValidation` annotation, and `ValidationGroupResolver`.
```

**Step 4: Commit**

```bash
git add CHANGELOG.md
git commit -m "docs: changelog for executable validation"
```

---

## Open follow-ups (not in this plan)

- Spring Boot autoconfiguration in `methodical-autoconfigure`: conditionally register `JakartaMethodValidator` when both `jakarta.validation.Validator` and `methodical-jakarta-validation` are on the classpath. Add `methodical.validation.enabled` and `methodical.validation.validate-return-value` properties.
- README documentation for the new module.
- Native-image hints for reflection on `@MethodValidation`-annotated methods/classes if `methodical-jakarta-validation` is used with GraalVM native-image (likely a `RuntimeHints` registrar).
