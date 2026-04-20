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
// Create an invoker for a specific method with whatever resolvers and interceptors it needs.
Method method = MyService.class.getMethod("greet", String.class);
MethodInvoker<JsonNode> invoker =
    MethodInvoker.builder(method, myService, JsonNode.class)
        .resolver(new Jackson3ParameterResolver(objectMapper))
        .build();

// Invoke with JSON arguments
JsonNode params = objectMapper.readTree("{\"name\": \"World\"}");
Object result = invoker.invoke(params);  // "Hello, World!"
```

## How It Works

1. **`ParameterResolver<A>`** — Resolves method parameters from an argument of type `A`. Multiple resolvers are consulted in order; the first that supports a parameter wins.

2. **`MethodInvoker.Builder<A>`** — Inspects a method's parameters at `build()` time, assigns per-parameter `ParameterResolver.Binding`s, and returns a pre-built `MethodInvoker<A>` with zero per-call reflection overhead. Obtained via `MethodInvoker.builder(method, target, argumentType)`.

3. **`MethodInvoker<A>`** — A lightweight, reusable handle that resolves arguments and invokes the method.

Generic types are carried at runtime via [specular](https://github.com/jwcarman/specular)'s `TypeRef<T>` — a small super-type-token helper that methodical depends on for parameter type resolution and generic-aware assignability.

## Generic Argument Types

When the argument type is parameterized (e.g. `Map<String, Object>` for MCP-style tool calls), use a `TypeRef` so resolvers can match generically:

```java
MethodInvoker<Map<String, Object>> invoker =
    MethodInvoker.builder(method, target, new TypeRef<Map<String, Object>>() {})
        .build();
```

Methodical checks assignability with Java's normal generic rules — `Map<String, String>` is *not* assignable to `Map<String, Object>` (invariance), but `HashMap<String, String>` *is* assignable to `Map<String, String>`.

## @Argument Pass-Through

To receive the raw argument without any resolver, annotate the parameter with `@Argument`:

```java
public String process(@Argument Map<String, Object> raw) { ... }
```

The factory validates at `create(...)` time that the parameter type is assignable from the argument type, throwing `ParameterResolutionException` if not. Generic-aware: `@Argument Map<String, String>` will reject an argument type of `Map<String, Object>`.

## Per-Invoker Configuration

Every invoker is built fluently. Resolvers and interceptors are added in registration order — the first matching resolver wins, and interceptors run outermost-first around the reflective call:

```java
MethodInvoker<Request> invoker =
    MethodInvoker.builder(method, target, TypeRef.of(Request.class))
        .resolver(new SessionResolver())
        .resolver(new AuthResolver())
        .interceptor(new AuditInterceptor())
        .build();
```

For helper APIs that want to accept configuration contributions, take a `Consumer<MethodInvoker.Builder<A>>`:

```java
static <A> void registerCommonInterceptors(MethodInvoker.Builder<A> builder) {
  builder.interceptor(new TracingInterceptor()).interceptor(new MdcInterceptor());
}

MethodInvoker.Builder<Request> builder = MethodInvoker.builder(method, target, Request.class)
    .resolver(new SessionResolver());
registerCommonInterceptors(builder);
MethodInvoker<Request> invoker = builder.build();
```

Methodical ships as a plain library with no DI-framework integration built in. Callers wire resolvers and interceptors however they like.

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
| `methodical-core` | Core API: `MethodInvoker<A>`, `MethodInvoker.Builder<A>`, `ParameterResolver<A>`, `MethodInterceptor<A>`, `@Named`, `@Argument` |
| `methodical-jackson3` | Jackson 3 (`tools.jackson`) parameter resolver |
| `methodical-jackson2` | Jackson 2 (`com.fasterxml.jackson`) parameter resolver |
| `methodical-gson` | Gson parameter resolver |
| `methodical-jakarta-validation` | Jakarta Bean Validation interceptor (`@NotNull`, `@NotBlank`, etc. on invoked-method parameters and return values) |
| `methodical-bom` | Bill of materials for dependency management |

All modules are plain Java libraries — no Spring, no DI-framework dependency. In a Spring Boot app, register the pieces you want as beans yourself (see the Jakarta section below for an example) and compose them into invokers via the customizer.

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

MethodInvoker<Request> invoker =
    MethodInvoker.builder(method, target, Request.class)
        .interceptor(timing)
        .build();
```

Interceptors run in **registration order** — first added is outermost, last added runs closest to the reflective call. There are no built-in interceptors; every cross-cutting concern is opt-in.

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

You also need a Jakarta Validation provider at runtime (e.g., Hibernate Validator).

Wire the interceptor and attach it to an invoker:

```java
public User createUser(@NotBlank String name, @Valid @NotNull Address address) {
    // ...
}

Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
JakartaValidationInterceptor validation = new JakartaValidationInterceptor(validator);

MethodInvoker<Request> invoker =
    MethodInvoker.builder(method, target, Request.class)
        .interceptor(validation)
        .build();
```

Invalid input throws `jakarta.validation.ConstraintViolationException` from `MethodInvoker.invoke(...)`.

In a Spring Boot app: add `spring-boot-starter-validation` to get Hibernate Validator + a `Validator` bean in the context. Then register `JakartaValidationInterceptor` as your own `@Bean` fed by that `Validator`, and inject it wherever you build invokers. Methodical does not ship Spring Boot auto-configuration — the wiring is one `@Bean` method.

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

## Writing a Custom Resolver

A resolver's single job is to produce a `Binding` for a given parameter at invoker-build time. The `Binding` is called once per parameter per invocation; it's where the hot-path logic lives. Any per-parameter setup (annotation reading, type lookups, reader construction) should happen inside `bind` so the `Binding.resolve(A)` implementation stays as cheap as possible.

```java
public class HeaderResolver implements ParameterResolver<HttpRequest> {

    @Override
    public Optional<Binding<HttpRequest>> bind(ParameterInfo info) {
        if (!info.hasAnnotation(Header.class) || !info.accepts(String.class)) {
            return Optional.empty();
        }
        final String headerName = info.annotation(Header.class).orElseThrow().value();
        return Optional.of(req -> req.getHeader(headerName));
    }
}
```

The lambda captures `headerName` once. The hot path is a single `req.getHeader(name)` call — no `ParameterInfo` traversal, no annotation lookup, no string comparison per invocation.

Key `ParameterInfo` helpers (consulted at bind time):

- `accepts(Class<?>)` / `accepts(TypeRef<?>)` / `accepts(Type)` — generic-aware assignability check against the parameter's declared type.
- `hasAnnotation(Class<? extends Annotation>)` — quick check.
- `annotation(Class<T>)` — returns `Optional<T>`.
- `name()` — resolved name (honors `@Named`).
- `index()` — positional index.
- `resolvedType()` / `genericType()` — the parameter's type with generics preserved.

**Dispatch order:** at `build()` time, the builder iterates the registered resolvers in registration order. The first whose `bind(info)` returns a non-empty `Optional` wins; its `Binding` is stored for that parameter. After the builder-added resolvers, a built-in `@Argument` resolver is consulted as a fallback. The type parameter on `ParameterResolver<A>` is the *argument* type passed to `MethodInvoker.invoke(A)`; the compile-time variance `? super A` on `MethodInvoker.Builder.resolver(...)` lets generic resolvers apply to narrower argument types.

**Fail-fast:** if no resolver produces a `Binding` for a parameter, the factory throws `ParameterResolutionException` at `create(...)` time with a message listing what was tried. No silent nulls at invoke time.

**In a Spring Boot app:** register your resolver as a `@Bean` and inject it wherever you build invokers; methodical ships no Spring Boot auto-configuration, and composition into invokers is always explicit via the customizer.

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

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
