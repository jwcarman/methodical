# Executable Validation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add optional Jakarta Bean Validation support to Methodical via a small `MethodValidatorFactory`/`MethodValidator` SPI in core (no-op by default) and a separate `methodical-jakarta-validation` module that wires Jakarta's `ExecutableValidator` into the invocation flow.

**Architecture:** Factory SPI mirrors the existing `MethodInvokerFactory`/`MethodInvoker` pair: `MethodValidatorFactory.create(target, method)` is called once at invoker construction and returns a pre-bound `MethodValidator`. The bound validator's per-invocation API is just `validateParameters(args)` / `validateReturnValue(result)` — no `target` or `Method` re-passed on the hot path, no internal cache. `NoOpMethodValidatorFactory` returns a singleton no-op `MethodValidator`. The Jakarta module's factory resolves groups once at bind time.

**Tech Stack:** Java 25, JUnit 5, AssertJ, Mockito, Maven multi-module, Jakarta Bean Validation 3.x API, Hibernate Validator 9.x (test-scope).

**Design decisions already settled:**
- Factory SPI in core — no internal cache, no per-invocation hierarchy walk. Resolution happens once at bind time.
- One invoker factory, not a wrapper. `ValidatingMethodInvokerFactory` is rejected.
- `@MethodValidation` lives in the Jakarta module, not core (groups field is Jakarta-specific).
- Annotation inheritance: walks superclass chain + interfaces; method-level resolution must handle `Method.isBridge()` by jumping to the real target.
- Resolver order: method annotation → class annotation → constructor-default groups. No silent `Default.class` fallback in the resolver — the constructor default carries that responsibility.
- Static methods: Jakarta's `ExecutableValidator` requires a non-null target. The Jakarta module's factory returns the singleton no-op validator when `target == null` or `Modifier.isStatic(method.getModifiers())` — documented as a known limitation.
- Exception leakage: `ConstraintViolationException` propagates directly; opt-in is at the dependency level.

**Out of scope for this plan:** Spring Boot autoconfiguration for Jakarta (deferred — separate plan when needed).

---

## Conventions

- Every new `.java` file starts with the Apache 2.0 license header used in existing files (see `methodical-core/src/main/java/org/jwcarman/methodical/MethodInvoker.java`).
- Code formatting is enforced by spotless (Google Java Format). Run `mvn spotless:apply` before committing if needed. (No `./mvnw` wrapper in this repo — use the system `mvn`.)
- Tests use `@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)` and snake_case method names.
- No star imports, no `@SuppressWarnings`, no comments unless explaining a non-obvious *why*.
- Commit after each task. Use conventional-commit prefixes: `feat:`, `test:`, `refactor:`, `docs:`.

---

# Phase 1 — Core SPI

## Task 1+2 (combined refactor): Factory SPI + no-op factory

This task replaces the earlier per-invocation `MethodValidator` SPI (commits `43c1566`, `bc2d6f4`) with the factory shape. The earlier SPI was a per-call `validateParameters(target, method, args)`; we're collapsing the (target, method) into a one-time bind.

**Files:**
- Replace: `methodical-core/src/main/java/org/jwcarman/methodical/MethodValidator.java` (now the bound, per-invoker validator)
- Create: `methodical-core/src/main/java/org/jwcarman/methodical/MethodValidatorFactory.java`
- Replace: `methodical-core/src/main/java/org/jwcarman/methodical/NoOpMethodValidator.java` → delete; replaced by `NoOpMethodValidatorFactory`
- Create: `methodical-core/src/main/java/org/jwcarman/methodical/NoOpMethodValidatorFactory.java`
- Delete: `methodical-core/src/test/java/org/jwcarman/methodical/NoOpMethodValidatorTest.java`
- Create: `methodical-core/src/test/java/org/jwcarman/methodical/NoOpMethodValidatorFactoryTest.java`

### `MethodValidator.java` (after license header)

```java
package org.jwcarman.methodical;

/**
 * Validates the parameters and return value of a single bound method invocation.
 *
 * <p>Obtained from {@link MethodValidatorFactory#create(Object, java.lang.reflect.Method)}
 * once per {@link MethodInvoker} at construction time. The bound validator captures any
 * per-method configuration (e.g., validation groups) so the per-invocation path stays cheap.
 *
 * <p>Implementations may throw any runtime exception when validation fails;
 * the exception propagates out of {@link MethodInvoker#invoke(Object)} unchanged.
 */
public interface MethodValidator {

  void validateParameters(Object[] args);

  void validateReturnValue(Object returnValue);
}
```

### `MethodValidatorFactory.java` (after license header)

```java
package org.jwcarman.methodical;

import java.lang.reflect.Method;

/**
 * Creates a {@link MethodValidator} bound to a specific {@code (target, method)} pair.
 *
 * <p>Called once per {@link MethodInvoker} at construction time. Implementations should
 * pre-resolve any per-method configuration here so the bound validator's hot path is cheap.
 * The {@code target} argument may be {@code null} for static methods.
 */
public interface MethodValidatorFactory {

  MethodValidator create(Object target, Method method);
}
```

### `NoOpMethodValidatorFactory.java` (after license header)

```java
package org.jwcarman.methodical;

import java.lang.reflect.Method;

/** No-op {@link MethodValidatorFactory}; the default when validation is not configured. */
public final class NoOpMethodValidatorFactory implements MethodValidatorFactory {

  private static final MethodValidator NO_OP =
      new MethodValidator() {
        @Override
        public void validateParameters(Object[] args) {
          // intentionally empty
        }

        @Override
        public void validateReturnValue(Object returnValue) {
          // intentionally empty
        }
      };

  @Override
  public MethodValidator create(Object target, Method method) {
    return NO_OP;
  }
}
```

### `NoOpMethodValidatorFactoryTest.java` (after license header)

```java
package org.jwcarman.methodical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class NoOpMethodValidatorFactoryTest {

  @Test
  void created_validator_does_nothing_for_parameters() throws Exception {
    Method m = String.class.getMethod("length");
    MethodValidator v = new NoOpMethodValidatorFactory().create("x", m);
    assertThatCode(() -> v.validateParameters(new Object[0])).doesNotThrowAnyException();
  }

  @Test
  void created_validator_does_nothing_for_return_value() throws Exception {
    Method m = String.class.getMethod("length");
    MethodValidator v = new NoOpMethodValidatorFactory().create("x", m);
    assertThatCode(() -> v.validateReturnValue(1)).doesNotThrowAnyException();
  }

  @Test
  void accepts_null_target_for_static_methods() throws Exception {
    Method m = Integer.class.getMethod("parseInt", String.class);
    MethodValidator v = new NoOpMethodValidatorFactory().create(null, m);
    assertThatCode(() -> v.validateParameters(new Object[] {"1"})).doesNotThrowAnyException();
  }

  @Test
  void returns_singleton_validator_instance() throws Exception {
    NoOpMethodValidatorFactory factory = new NoOpMethodValidatorFactory();
    Method m1 = String.class.getMethod("length");
    Method m2 = Integer.class.getMethod("parseInt", String.class);
    assertThat(factory.create("x", m1)).isSameAs(factory.create(123, m2));
  }
}
```

### Steps

1. Delete: `methodical-core/src/main/java/org/jwcarman/methodical/NoOpMethodValidator.java` and `methodical-core/src/test/java/org/jwcarman/methodical/NoOpMethodValidatorTest.java`.
2. Overwrite `MethodValidator.java` with the new bound-validator content above.
3. Create `MethodValidatorFactory.java`.
4. Create `NoOpMethodValidatorFactory.java` and `NoOpMethodValidatorFactoryTest.java`.
5. `mvn -pl methodical-core test spotless:check -q` — green.
6. Commit:
   ```
   git add -A methodical-core/src
   git commit -m "refactor(core): switch MethodValidator SPI to factory shape"
   ```

---

## Task 3: Wire factory through `DefaultMethodInvoker` (TDD)

**Files:**
- Modify: `methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvoker.java`
- Test: `methodical-core/src/test/java/org/jwcarman/methodical/def/DefaultMethodInvokerValidationTest.java`

### Step 1: Test (write first)

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
import org.jwcarman.specular.TypeRef;

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
    ParameterInfo info =
        ParameterInfo.of(method.getParameters()[0], 0, TypeRef.of(String.class));
    ParameterResolver<String> resolver = (pi, arg) -> arg;
    StringBuilder log = new StringBuilder();
    MethodValidator validator =
        new MethodValidator() {
          @Override
          public void validateParameters(Object[] args) {
            log.append("params:").append(args[0]).append(';');
          }
          @Override
          public void validateReturnValue(Object returnValue) {
            log.append("return:").append(returnValue).append(';');
          }
        };

    MethodInvoker<String> invoker =
        new DefaultMethodInvoker<>(
            method, target, new ParameterInfo[] {info}, List.of(resolver), validator);

    Object result = invoker.invoke("world");

    assertThat(result).isEqualTo("hello world");
    assertThat(log.toString()).isEqualTo("params:world;return:hello world;");
  }

  @Test
  void parameter_validation_failure_skips_invocation_and_return_validation() throws Exception {
    Greeter target = new Greeter();
    Method method = Greeter.class.getDeclaredMethod("greet", String.class);
    ParameterInfo info =
        ParameterInfo.of(method.getParameters()[0], 0, TypeRef.of(String.class));
    ParameterResolver<String> resolver = (pi, arg) -> arg;
    boolean[] returnValidated = {false};
    MethodValidator validator =
        new MethodValidator() {
          @Override
          public void validateParameters(Object[] args) {
            throw new IllegalArgumentException("bad args");
          }
          @Override
          public void validateReturnValue(Object returnValue) {
            returnValidated[0] = true;
          }
        };

    MethodInvoker<String> invoker =
        new DefaultMethodInvoker<>(
            method, target, new ParameterInfo[] {info}, List.of(resolver), validator);

    assertThatThrownBy(() -> invoker.invoke("world"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bad args");
    assertThat(returnValidated[0]).isFalse();
  }
}
```

### Step 2: Modify `DefaultMethodInvoker`

Add a `MethodValidator validator` field. Change `invoke(...)` to call `validator.validateParameters(args)` after resolution and `validator.validateReturnValue(result)` after invocation. Full file:

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
    validator.validateParameters(args);
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
    validator.validateReturnValue(result);
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

### Step 3: Update `DefaultMethodInvokerFactory`

In `methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactory.java`, change line 59 (the `new DefaultMethodInvoker<>(...)` call) to pass a no-op validator:

```java
return new DefaultMethodInvoker<>(
    method, target, paramInfos, assigned, new NoOpMethodValidatorFactory().create(target, method));
```

Add the import: `import org.jwcarman.methodical.NoOpMethodValidatorFactory;`. (Task 4 will inject the factory cleanly; this is a temporary intermediate.)

### Step 4: Verify and commit

`mvn -pl methodical-core test spotless:check -q` — green.

```
git add methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvoker.java \
        methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactory.java \
        methodical-core/src/test/java/org/jwcarman/methodical/def/DefaultMethodInvokerValidationTest.java
git commit -m "feat(core): invoke MethodValidator around reflective call"
```

---

## Task 4: Inject `MethodValidatorFactory` through `DefaultMethodInvokerFactory` (TDD)

**Files:**
- Modify: `methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactory.java`
- Test: `methodical-core/src/test/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactoryValidationTest.java`

### Step 1: Test

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
import org.jwcarman.methodical.MethodValidatorFactory;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodInvokerFactoryValidationTest {

  static class Echo {
    String echo(String s) {
      return s;
    }
  }

  @Test
  void factory_calls_validator_factory_once_at_invoker_creation() throws Exception {
    int[] bindCalls = {0};
    int[] validateCalls = {0};
    MethodValidatorFactory vf =
        (target, method) -> {
          bindCalls[0]++;
          return new MethodValidator() {
            @Override
            public void validateParameters(Object[] args) {
              validateCalls[0]++;
            }
            @Override
            public void validateReturnValue(Object returnValue) {}
          };
        };

    DefaultMethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of(), vf);
    Method m = Echo.class.getDeclaredMethod("echo", String.class);
    MethodInvoker<String> invoker = factory.create(m, new Echo(), String.class);

    assertThat(bindCalls[0]).isEqualTo(1);
    assertThat(validateCalls[0]).isZero();

    invoker.invoke("a");
    invoker.invoke("b");

    assertThat(bindCalls[0]).isEqualTo(1);
    assertThat(validateCalls[0]).isEqualTo(2);
  }

  @Test
  void single_arg_constructor_uses_no_op_factory() throws Exception {
    DefaultMethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of());
    Method m = Echo.class.getDeclaredMethod("echo", String.class);
    MethodInvoker<String> invoker = factory.create(m, new Echo(), String.class);
    assertThat(invoker.invoke("hi")).isEqualTo("hi");
  }

  @Test
  void rejects_null_validator_factory() {
    assertThatThrownBy(() -> new DefaultMethodInvokerFactory(List.of(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
```

### Step 2: Modify factory

```java
public class DefaultMethodInvokerFactory implements MethodInvokerFactory {

  private final List<ResolvedParameterResolver<?>> resolvers;
  private final MethodValidatorFactory validatorFactory;

  public DefaultMethodInvokerFactory(List<ParameterResolver<?>> resolvers) {
    this(resolvers, new NoOpMethodValidatorFactory());
  }

  public DefaultMethodInvokerFactory(
      List<ParameterResolver<?>> resolvers, MethodValidatorFactory validatorFactory) {
    this.resolvers =
        resolvers.stream()
            .<ResolvedParameterResolver<?>>map(DefaultMethodInvokerFactory::wrap)
            .toList();
    this.validatorFactory = Objects.requireNonNull(validatorFactory, "validatorFactory");
  }

  @Override
  public <A> MethodInvoker<A> create(...) {
    // ... existing argument-resolution logic ...
    return new DefaultMethodInvoker<>(
        method, target, paramInfos, assigned, validatorFactory.create(target, method));
  }
```

Replace the `NoOpMethodValidatorFactory` import + hard-coded call site from Task 3 with the field-based call. Keep all other imports.

### Step 3: Verify and commit

`mvn -pl methodical-core test spotless:check -q` — green.

```
git add methodical-core/src/main/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactory.java \
        methodical-core/src/test/java/org/jwcarman/methodical/def/DefaultMethodInvokerFactoryValidationTest.java
git commit -m "feat(core): accept optional MethodValidatorFactory in DefaultMethodInvokerFactory"
```

---

# Phase 2 — Jakarta Validation module

## Task 5: Scaffold `methodical-jakarta-validation` module

**Files:**
- Create: `methodical-jakarta-validation/pom.xml`
- Modify: root `pom.xml` (add module + dependencyManagement entry)
- Modify: `methodical-bom/pom.xml` if it enumerates artifacts (read first)

### `methodical-jakarta-validation/pom.xml`

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

Apache 2.0 XML license header at the top — copy from `methodical-core/pom.xml`.

### Root `pom.xml`

Add to `<modules>` (after `methodical-gson`):

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

### Verify and commit

`mvn -pl methodical-jakarta-validation -am compile -q` — success.

```
git add pom.xml methodical-bom/pom.xml methodical-jakarta-validation/pom.xml
git commit -m "build: scaffold methodical-jakarta-validation module"
```

---

## Task 6: `@MethodValidation` annotation

**File:** `methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/MethodValidation.java`

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

Verify and commit:

```
mvn -pl methodical-jakarta-validation compile spotless:check -q
git add methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/MethodValidation.java
git commit -m "feat(jakarta): add @MethodValidation annotation"
```

---

## Task 7: `AnnotationFinder` helper (TDD)

Package-private helper that walks the class/interface hierarchy and resolves bridge methods.

**Files:**
- Test: `methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/AnnotationFinderTest.java`
- Create: `methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/AnnotationFinder.java`

### Test

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

  interface OnlyIface {
    @Marker("only-iface")
    void run();
  }

  static class OnlyIfaceImpl implements OnlyIface {
    @Override
    public void run() {}
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
    Method m = OnlyIfaceImpl.class.getMethod("run");
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

### Implementation

```java
package org.jwcarman.methodical.jakarta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

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
```

Verify and commit:

```
mvn -pl methodical-jakarta-validation test spotless:check -q
git add methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/AnnotationFinder.java \
        methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/AnnotationFinderTest.java
git commit -m "feat(jakarta): add AnnotationFinder helper for hierarchy walks"
```

---

## Task 8: `ValidationGroupResolver` interface + default impl (TDD)

**Files:**
- Create: `methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/ValidationGroupResolver.java`
- Create: `methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/DefaultValidationGroupResolver.java`
- Test: `methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/DefaultValidationGroupResolverTest.java`

### Interface

```java
package org.jwcarman.methodical.jakarta;

import java.lang.reflect.Method;

public interface ValidationGroupResolver {

  Class<?>[] resolveGroups(Object target, Method method);

  boolean shouldValidateReturnValue(Object target, Method method);
}
```

### Test (cover precedence: method-annotation > class-annotation > constructor-default)

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

  @MethodValidation
  static class EmptyGroups {
    public void run() {}
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

### Default impl

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

Verify and commit:

```
mvn -pl methodical-jakarta-validation test spotless:check -q
git add methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/ValidationGroupResolver.java \
        methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/DefaultValidationGroupResolver.java \
        methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/DefaultValidationGroupResolverTest.java
git commit -m "feat(jakarta): add ValidationGroupResolver with hierarchy-aware defaults"
```

---

## Task 9: `JakartaMethodValidator` + `JakartaMethodValidatorFactory` (TDD)

The factory does the resolution once at bind time and returns a bound validator with pre-resolved groups. For static methods or null targets, it returns a singleton no-op (Jakarta's `ExecutableValidator` requires a non-null instance).

**Files:**
- Create: `methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/JakartaMethodValidatorFactory.java`
- Test: `methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/JakartaMethodValidatorFactoryTest.java`

### Implementation

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
import org.jwcarman.methodical.MethodValidator;
import org.jwcarman.methodical.MethodValidatorFactory;

public final class JakartaMethodValidatorFactory implements MethodValidatorFactory {

  private static final MethodValidator NO_OP =
      new MethodValidator() {
        @Override
        public void validateParameters(Object[] args) {}
        @Override
        public void validateReturnValue(Object returnValue) {}
      };

  private final ExecutableValidator executableValidator;
  private final ValidationGroupResolver groupResolver;

  public JakartaMethodValidatorFactory(Validator validator, ValidationGroupResolver groupResolver) {
    this.executableValidator =
        Objects.requireNonNull(validator, "validator").forExecutables();
    this.groupResolver = Objects.requireNonNull(groupResolver, "groupResolver");
  }

  @Override
  public MethodValidator create(Object target, Method method) {
    if (target == null || Modifier.isStatic(method.getModifiers())) {
      return NO_OP;
    }
    Class<?>[] groups = groupResolver.resolveGroups(target, method);
    boolean validateReturn = groupResolver.shouldValidateReturnValue(target, method);
    return new BoundJakartaValidator(executableValidator, target, method, groups, validateReturn);
  }

  private static final class BoundJakartaValidator implements MethodValidator {
    private final ExecutableValidator executableValidator;
    private final Object target;
    private final Method method;
    private final Class<?>[] groups;
    private final boolean validateReturn;

    BoundJakartaValidator(
        ExecutableValidator executableValidator,
        Object target,
        Method method,
        Class<?>[] groups,
        boolean validateReturn) {
      this.executableValidator = executableValidator;
      this.target = target;
      this.method = method;
      this.groups = groups;
      this.validateReturn = validateReturn;
    }

    @Override
    public void validateParameters(Object[] args) {
      Set<ConstraintViolation<Object>> violations =
          executableValidator.validateParameters(target, method, args, groups);
      if (!violations.isEmpty()) {
        throw new ConstraintViolationException(violations);
      }
    }

    @Override
    public void validateReturnValue(Object returnValue) {
      if (!validateReturn) {
        return;
      }
      Set<ConstraintViolation<Object>> violations =
          executableValidator.validateReturnValue(target, method, returnValue, groups);
      if (!violations.isEmpty()) {
        throw new ConstraintViolationException(violations);
      }
    }
  }
}
```

### Test

Cover: param violation throws; valid call passes; return violation throws; `@MethodValidation(validateReturnValue=false)` skips; static methods return the no-op singleton; null target returns the no-op singleton; **resolver invoked exactly once at `create(...)` time, not on each invocation**.

```java
package org.jwcarman.methodical.jakarta;

import static org.assertj.core.api.Assertions.assertThat;
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
class JakartaMethodValidatorFactoryTest {

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

  private Validator validator() {
    return Validation.buildDefaultValidatorFactory().getValidator();
  }

  private JakartaMethodValidatorFactory newFactory() {
    return new JakartaMethodValidatorFactory(
        validator(), new DefaultValidationGroupResolver(new Class<?>[] {Default.class}, true));
  }

  @Test
  void valid_parameters_pass() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    MethodValidator v = newFactory().create(new Service(), m);
    assertThatCode(() -> v.validateParameters(new Object[] {"world"})).doesNotThrowAnyException();
  }

  @Test
  void invalid_parameters_throw_ConstraintViolationException() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    MethodValidator v = newFactory().create(new Service(), m);
    assertThatThrownBy(() -> v.validateParameters(new Object[] {""}))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void valid_return_value_passes() throws Exception {
    Method m = Service.class.getDeclaredMethod("maybe", boolean.class);
    MethodValidator v = newFactory().create(new Service(), m);
    assertThatCode(() -> v.validateReturnValue("value")).doesNotThrowAnyException();
  }

  @Test
  void invalid_return_value_throws_ConstraintViolationException() throws Exception {
    Method m = Service.class.getDeclaredMethod("maybe", boolean.class);
    MethodValidator v = newFactory().create(new Service(), m);
    assertThatThrownBy(() -> v.validateReturnValue(null))
        .isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  void return_validation_skipped_when_annotation_disables_it() throws Exception {
    Method m = Service.class.getDeclaredMethod("maybeNoReturnCheck", boolean.class);
    MethodValidator v = newFactory().create(new Service(), m);
    assertThatCode(() -> v.validateReturnValue(null)).doesNotThrowAnyException();
  }

  @Test
  void static_methods_return_no_op() throws Exception {
    Method m = Service.class.getDeclaredMethod("staticMethod", String.class);
    MethodValidator v = newFactory().create(null, m);
    assertThatCode(() -> v.validateParameters(new Object[] {""})).doesNotThrowAnyException();
    assertThatCode(() -> v.validateReturnValue("")).doesNotThrowAnyException();
  }

  @Test
  void null_target_returns_no_op() throws Exception {
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    MethodValidator v = newFactory().create(null, m);
    assertThatCode(() -> v.validateParameters(new Object[] {""})).doesNotThrowAnyException();
  }

  @Test
  void resolver_invoked_once_at_bind_time_not_per_call() throws Exception {
    int[] calls = {0};
    ValidationGroupResolver counting =
        new ValidationGroupResolver() {
          @Override
          public Class<?>[] resolveGroups(Object target, Method method) {
            calls[0]++;
            return new Class<?>[] {Default.class};
          }
          @Override
          public boolean shouldValidateReturnValue(Object target, Method method) {
            return true;
          }
        };
    JakartaMethodValidatorFactory factory =
        new JakartaMethodValidatorFactory(validator(), counting);
    Method m = Service.class.getDeclaredMethod("greet", String.class);
    Service target = new Service();

    MethodValidator v = factory.create(target, m);
    int afterCreate = calls[0];
    v.validateParameters(new Object[] {"a"});
    v.validateParameters(new Object[] {"b"});
    v.validateReturnValue("x");

    assertThat(afterCreate).isGreaterThanOrEqualTo(1);
    assertThat(calls[0]).isEqualTo(afterCreate);
  }
}
```

Verify and commit:

```
mvn -pl methodical-jakarta-validation test spotless:check -q
git add methodical-jakarta-validation/src/main/java/org/jwcarman/methodical/jakarta/JakartaMethodValidatorFactory.java \
        methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/JakartaMethodValidatorFactoryTest.java
git commit -m "feat(jakarta): add JakartaMethodValidatorFactory backed by ExecutableValidator"
```

---

## Task 10: End-to-end integration test

**File:** `methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/JakartaValidationIntegrationTest.java`

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
    JakartaMethodValidatorFactory vf =
        new JakartaMethodValidatorFactory(
            Validation.buildDefaultValidatorFactory().getValidator(),
            new DefaultValidationGroupResolver(new Class<?>[] {Default.class}, true));
    DefaultMethodInvokerFactory factory = new DefaultMethodInvokerFactory(List.of(), vf);
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

Verify and commit:

```
mvn -pl methodical-jakarta-validation test -Dtest=JakartaValidationIntegrationTest -q
git add methodical-jakarta-validation/src/test/java/org/jwcarman/methodical/jakarta/JakartaValidationIntegrationTest.java
git commit -m "test(jakarta): end-to-end invocation through DefaultMethodInvokerFactory"
```

---

## Task 11: Final verification + CHANGELOG

1. Full build: `mvn clean verify -q`. All modules green.
2. Add to `CHANGELOG.md` under `## [Unreleased]`:
   ```markdown
   ### Added
   - New `MethodValidatorFactory`/`MethodValidator` SPI in `methodical-core` for validating reflective method invocations; defaults to a no-op factory.
   - New `methodical-jakarta-validation` module providing Jakarta Bean Validation integration via `JakartaMethodValidatorFactory`, `@MethodValidation` annotation, and `ValidationGroupResolver`.
   ```
3. Commit:
   ```
   git add CHANGELOG.md
   git commit -m "docs: changelog for executable validation"
   ```

---

## Open follow-ups (not in this plan)

- Spring Boot autoconfiguration in `methodical-autoconfigure`: conditionally register `JakartaMethodValidatorFactory` when both `jakarta.validation.Validator` and `methodical-jakarta-validation` are on the classpath.
- README documentation for the new module.
- Native-image hints for reflection on `@MethodValidation`-annotated methods/classes.
