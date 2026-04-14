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
// Create a factory with resolvers
var factory = new DefaultMethodInvokerFactory(List.of(
    new Jackson3ParameterResolver(objectMapper)
));

// Create an invoker for a specific method
Method method = MyService.class.getMethod("greet", String.class);
MethodInvoker<JsonNode> invoker = factory.create(method, myService, JsonNode.class);

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

## Per-Invoker Resolvers

Register resolvers for a single invoker without mutating the factory. These are tried *before* factory-level resolvers:

```java
MethodInvoker<Request> invoker = factory.create(
    method, target, TypeRef.of(Request.class),
    List.of(new SessionResolver(), new AuthResolver()));
```

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
| `methodical-core` | Core API: `MethodInvokerFactory`, `ParameterResolver<A>`, `@Named` |
| `methodical-jackson3` | Jackson 3 (`tools.jackson`) parameter resolver |
| `methodical-jackson2` | Jackson 2 (`com.fasterxml.jackson`) parameter resolver |
| `methodical-gson` | Gson parameter resolver |
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

Auto-configuration detects which JSON library is on the classpath and registers the appropriate resolver at lowest priority (catch-all). Custom resolvers registered as Spring beans take precedence.

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

**Dispatch order:** per-invoker resolvers → factory-level resolvers. Within each group, the first resolver whose `supports()` returns `true` wins. The type parameter on `ParameterResolver<A>` is the *argument* type (what's passed to `MethodInvoker.invoke(A)`), and the factory matches resolvers against the invoker's `TypeRef<A>` — so a `ParameterResolver<Map<String,String>>` only matches invokers whose argument type is assignable to `Map<String,String>`.

**Fail-fast:** if no resolver matches a parameter, the factory throws `ParameterResolutionException` at `create(...)` time with a message listing what was tried. No silent nulls at invoke time.

Register as a Spring bean — it's automatically picked up by the factory. Use `@Order` to control priority (lower values = higher priority). JSON resolvers run at `Ordered.LOWEST_PRECEDENCE` as the fallback.

## Exception Handling

All Methodical exceptions extend `MethodicalException` (abstract, unchecked):

- **`ParameterResolutionException`** — a resolver failed to deserialize a parameter (e.g., invalid JSON), *or* the factory couldn't find a resolver for a parameter at `create(...)` time. Catch this to distinguish bad input / misconfiguration from other failures.
- **`MethodInvocationException`** — reflection failure (private method, inaccessible) or checked exception from the invoked method.
- **Runtime exceptions** from the invoked method are unwrapped and rethrown as-is — not wrapped.

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
