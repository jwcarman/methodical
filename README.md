# Methodical

[![CI](https://github.com/jwcarman/methodical/actions/workflows/maven.yml/badge.svg)](https://github.com/jwcarman/methodical/actions/workflows/maven.yml)
[![CodeQL](https://github.com/jwcarman/methodical/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/jwcarman/methodical/actions/workflows/github-code-scanning/codeql)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/dynamic/xml?url=https://raw.githubusercontent.com/jwcarman/methodical/main/pom.xml&query=//*[local-name()='java.version']/text()&label=Java&color=orange)](https://openjdk.org/)
[![Maven Central](https://img.shields.io/maven-central/v/org.jwcarman.methodical/methodical-core)](https://central.sonatype.com/artifact/org.jwcarman.methodical/methodical-core)

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_methodical&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_methodical)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_methodical&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_methodical)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_methodical&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=jwcarman_methodical)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_methodical&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=jwcarman_methodical)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_methodical&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=jwcarman_methodical)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=jwcarman_methodical&metric=coverage)](https://sonarcloud.io/summary/new_code?id=jwcarman_methodical)

Pluggable reflection-based method invocation for Java. Resolve method arguments from any source — JSON, maps, dependency injection contexts — through a simple `ParameterResolver<A>` SPI.

## Quick Start

```java
// Factory is stateless — all per-invoker configuration flows through the customizer.
var factory = new DefaultMethodInvokerFactory();

// Create an invoker for a specific method, registering a resolver inline.
Method method = MyService.class.getMethod("greet", String.class);
MethodInvoker<JsonNode> invoker = factory.create(
    method, myService, JsonNode.class,
    cfg -> cfg.resolver(new Jackson3ParameterResolver(objectMapper)));

// Invoke with JSON arguments
JsonNode params = objectMapper.readTree("{\"name\": \"World\"}");
Object result = invoker.invoke(params);  // "Hello, World!"
```

## How It Works

1. **`ParameterResolver<A>`** — Resolves method parameters from an argument of type `A`. Multiple resolvers are consulted in order; the first that supports a parameter wins.

2. **`MethodInvokerFactory`** — Inspects a method's parameters at creation time, assigns resolvers, and returns a pre-built `MethodInvoker<A>` with zero per-call reflection overhead.

3. **`MethodInvoker<A>`** — A lightweight, reusable handle that resolves arguments and invokes the method.

Generic types are carried at runtime via [specular](https://github.com/jwcarman/specular)'s `TypeRef<T>` — a small super-type-token helper that methodical depends on for parameter type resolution and generic-aware assignability.

## Generic Argument Types

When the argument type is parameterized (e.g. `Map<String, Object>` for MCP-style tool calls), use a `TypeRef` so the factory can match resolvers generically:

```java
MethodInvoker<Map<String, Object>> invoker =
    factory.create(method, target, new TypeRef<Map<String, Object>>() {});
```

Methodical checks assignability with Java's normal generic rules — `Map<String, String>` is *not* assignable to `Map<String, Object>` (invariance), but `HashMap<String, String>` *is* assignable to `Map<String, String>`.

## @Argument Pass-Through

To receive the raw argument without any resolver, annotate the parameter with `@Argument`:

```java
public String process(@Argument Map<String, Object> raw) { ... }
```

The factory validates at `create(...)` time that the parameter type is assignable from the argument type, throwing `ParameterResolutionException` if not. Generic-aware: `@Argument Map<String, String>` will reject an argument type of `Map<String, Object>`.

## Per-Invoker Configuration

Every invoker is configured through a `Consumer<MethodInvokerConfig<A>>` customizer. Resolvers and interceptors are added in registration order — the first matching resolver wins, and interceptors run outermost-first around the reflective call:

```java
MethodInvoker<Request> invoker = factory.create(
    method, target, TypeRef.of(Request.class),
    cfg -> cfg
        .resolver(new SessionResolver())
        .resolver(new AuthResolver())
        .interceptor(new AuditInterceptor()));
```

The factory itself holds no ambient resolvers or interceptors — everything you want applied to an invoker goes through the customizer. This keeps the core framework-neutral; the Spring Boot starter provides beans (see below), and callers compose customizers however they like.

## Parameter Name Override

Use `@Named` to override the parameter name used for resolution:

```java
public String greet(@Named("user_name") String name) {
    return "Hello, " + name + "!";
}
```

The resolver sees `"user_name"` instead of `"name"` when looking up the value.

## Modules

| Module | Description |
|--------|-------------|
| `methodical-core` | Core API: `MethodInvokerFactory`, `ParameterResolver<A>`, `MethodInterceptor<A>`, `MethodInvokerConfig<A>`, `@Named`, `@Argument` |
| `methodical-jackson3` | Jackson 3 (`tools.jackson`) parameter resolver |
| `methodical-jackson2` | Jackson 2 (`com.fasterxml.jackson`) parameter resolver |
| `methodical-gson` | Gson parameter resolver |
| `methodical-jakarta-validation` | Jakarta Bean Validation interceptor (`@NotNull`, `@NotBlank`, etc. on invoked-method parameters and return values) |
| `methodical-autoconfigure` | Spring Boot auto-configuration |
| `methodical-spring-boot-starter` | Starter pulling in core + autoconfigure |
| `methodical-bom` | Bill of materials for dependency management |

## Spring Boot

Add the starter:

```xml
<dependency>
    <groupId>org.jwcarman.methodical</groupId>
    <artifactId>methodical-spring-boot-starter</artifactId>
    <version>${methodical.version}</version>
</dependency>
```

And a JSON module (whichever matches your Spring Boot version):

```xml
<!-- Spring Boot 4.x (Jackson 3) -->
<dependency>
    <groupId>org.jwcarman.methodical</groupId>
    <artifactId>methodical-jackson3</artifactId>
    <version>${methodical.version}</version>
</dependency>

<!-- Spring Boot 3.x (Jackson 2) -->
<dependency>
    <groupId>org.jwcarman.methodical</groupId>
    <artifactId>methodical-jackson2</artifactId>
    <version>${methodical.version}</version>
</dependency>
```

Auto-configuration registers a `MethodInvokerFactory` bean along with a `ParameterResolver` bean for whichever JSON module is on the classpath. Attach resolvers and interceptors to individual invokers via the customizer — the Spring Boot starter does not auto-wire context beans into every invoker; explicit composition is the pattern:

```java
@Bean
MethodInvoker<JsonNode> greetInvoker(
    MethodInvokerFactory factory,
    Jackson3ParameterResolver jsonResolver,
    JakartaValidationInterceptor validation,
    MyService target) throws Exception {
  Method m = MyService.class.getMethod("greet", String.class);
  return factory.create(m, target, JsonNode.class,
      cfg -> cfg.resolver(jsonResolver).interceptor(validation));
}
```

## Interceptors

`MethodInterceptor<A>` wraps the reflective invocation. An interceptor observes the `MethodInvocation`, decides whether and when to call `proceed()`, and may short-circuit, transform, retry, or wrap the call with cross-cutting behavior (timing, auth, tracing, scoped-value binding, validation).

```java
MethodInterceptor<Object> timing = invocation -> {
  long start = System.nanoTime();
  try {
    return invocation.proceed();
  } finally {
    metrics.record(invocation.method(), System.nanoTime() - start);
  }
};

MethodInvoker<Request> invoker = factory.create(
    method, target, Request.class,
    cfg -> cfg.interceptor(timing));
```

Interceptors run in **registration order** — first added is outermost, last added runs closest to the reflective call. There are no built-in interceptors; every cross-cutting concern is opt-in via the customizer.

The `MethodInterceptors` helper class provides common patterns:

- `MethodInterceptors.before(Consumer<MethodInvocation<? extends A>>)` — run an action before `proceed()`.
- `MethodInterceptors.onSuccess(BiConsumer<MethodInvocation<? extends A>, Object>)` — observe the return value on normal completion; skipped if the chain throws.
- `MethodInterceptors.scopedValue(ScopedValue<T>, Function<..., Optional<T>>)` — bind a `ScopedValue` around the invocation when the supplier returns a value; skip binding on `Optional.empty()`.

## Jakarta Bean Validation

Add `methodical-jakarta-validation` to validate method parameters and return values with standard Jakarta constraints (`@NotNull`, `@NotBlank`, `@Size`, `@Valid` cascades, etc.). Validation is a `MethodInterceptor` — parameters are validated before `proceed()`; return value after.

```xml
<dependency>
    <groupId>org.jwcarman.methodical</groupId>
    <artifactId>methodical-jakarta-validation</artifactId>
    <version>${methodical.version}</version>
</dependency>
```

You also need a Jakarta Validation provider at runtime. In a Spring Boot app, `spring-boot-starter-validation` brings Hibernate Validator and auto-configures a `Validator` bean:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Annotate the invoked method and register the interceptor on the invoker:

```java
public User createUser(@NotBlank String name, @Valid @NotNull Address address) {
    // ...
}

MethodInvoker<JsonNode> invoker = factory.create(
    method, userService, JsonNode.class,
    cfg -> cfg
        .resolver(jsonResolver)
        .interceptor(jakartaValidationInterceptor));
```

Invalid input throws `jakarta.validation.ConstraintViolationException` from `MethodInvoker.invoke(...)`.

### Validation Groups

For per-method/per-class group activation, annotate with `@ValidationGroups`:

```java
interface OnCreate {}
interface OnUpdate {}

@ValidationGroups(OnCreate.class)
public User createUser(@NotBlank(groups = OnCreate.class) String email) { ... }

@ValidationGroups(OnUpdate.class)
public User updateUser(@Null(groups = OnCreate.class) @NotNull(groups = OnUpdate.class) Long id) { ... }
```

Method-level overrides class-level. Both are inherited from supertypes (including bridge-methods from generic interfaces). When absent, `jakarta.validation.groups.Default` is used — matching stock Jakarta behavior.

### Standalone (no Spring)

Wire it manually:

```java
Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
JakartaValidationInterceptor validation = new JakartaValidationInterceptor(validator);

var factory = new DefaultMethodInvokerFactory();
MethodInvoker<Request> invoker = factory.create(
    method, target, Request.class,
    cfg -> cfg.interceptor(validation));
```

In a Spring Boot app, `JakartaValidationAutoConfiguration` registers a `JakartaValidationInterceptor` bean whenever `jakarta.validation.Validator` is on the classpath and a `Validator` bean is present. Attach it to invokers via the customizer; nothing is wired automatically into every invoker.

## Writing a Custom Resolver

A resolver is a two-method SPI: `supports(ParameterInfo)` decides whether it handles a given parameter, and `resolve(ParameterInfo, A)` produces the value.

```java
public class HeaderResolver implements ParameterResolver<HttpRequest> {

    @Override
    public boolean supports(ParameterInfo info) {
        return info.hasAnnotation(Header.class) && info.accepts(String.class);
    }

    @Override
    public Object resolve(ParameterInfo info, HttpRequest req) {
        String name = info.annotation(Header.class).orElseThrow().value();
        return req.getHeader(name);
    }
}
```

Key `ParameterInfo` helpers:

- `accepts(Class<?>)` / `accepts(TypeRef<?>)` / `accepts(Type)` — generic-aware assignability check against the parameter's declared type.
- `hasAnnotation(Class<? extends Annotation>)` — quick check.
- `annotation(Class<T>)` — returns `Optional<T>`.
- `name()` — resolved name (honors `@Named`).
- `index()` — positional index.

**Dispatch order:** resolvers are tried in registration order — the first whose `supports()` returns `true` wins. The factory appends a built-in `@Argument` fallback after any customizer-added resolvers. The type parameter on `ParameterResolver<A>` is the *argument* type (what's passed to `MethodInvoker.invoke(A)`); the compile-time variance `? super A` on `MethodInvokerConfig.resolver(...)` lets generic resolvers (e.g., `ParameterResolver<Object>`) apply to narrower argument types.

**Fail-fast:** if no resolver matches a parameter, the factory throws `ParameterResolutionException` at `create(...)` time with a message listing what was tried. No silent nulls at invoke time.

**Spring Boot:** register your resolver as a Spring bean to make it available in the context. Compose it into invokers via the customizer — Spring Boot does not wire resolver beans into every invoker automatically.

## Exception Handling

All Methodical exceptions extend `MethodicalException` (abstract, unchecked):

- **`ParameterResolutionException`** — a resolver failed to deserialize a parameter (e.g., invalid JSON), *or* the factory couldn't find a resolver for a parameter at `create(...)` time. Catch this to distinguish bad input / misconfiguration from other failures.
- **`MethodInvocationException`** — reflection failure (private method, inaccessible) or checked exception from the invoked method.
- **Runtime exceptions** from the invoked method are unwrapped and rethrown as-is — not wrapped.
- **`jakarta.validation.ConstraintViolationException`** — thrown by `methodical-jakarta-validation` when a parameter or return value fails Jakarta Bean Validation. Not a `MethodicalException` subclass; it propagates directly so consumers can handle it using standard Jakarta patterns.

```java
try {
    Object result = invoker.invoke(params);
} catch (ParameterResolutionException e) {
    // Bad input — parameter couldn't be deserialized
} catch (MethodicalException e) {
    // Reflection failure or checked exception
}
// RuntimeExceptions from the method propagate directly
```

## Requirements

- Java 25+
- [specular](https://github.com/jwcarman/specular) (transitive, for `TypeRef` and generic type helpers)
- Spring Boot 4.x (for autoconfigure/starter) or standalone with any Java version

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
