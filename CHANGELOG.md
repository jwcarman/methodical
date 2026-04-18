# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-04-18

### Added

- **`MethodValidatorFactory` / `MethodValidator` SPI** in `methodical-core` — optional hook called around every reflective method invocation. `MethodValidatorFactory.create(target, method)` is called once at `MethodInvoker` construction and returns a bound `MethodValidator` whose hot-path `validateParameters(args)` / `validateReturnValue(result)` methods have no per-call resolver overhead. `MethodValidatorFactory.NO_OP` is the canonical no-op singleton.
- **`methodical-jakarta-validation` module** — Jakarta Bean Validation integration. `JakartaMethodValidatorFactory` wraps a `jakarta.validation.Validator`; the resulting bound `JakartaMethodValidator` enforces constraint annotations (`@NotNull`, `@NotBlank`, `@Size`, `@Valid` cascades, etc.) on parameters and return values at every `MethodInvoker.invoke(...)`. Constraint violations surface as `jakarta.validation.ConstraintViolationException`.
- **`@ValidationGroups` annotation** — declare Jakarta validation groups for a method or class. Method-level wins over class-level; inherited through superclass chain and implemented interfaces (matching Spring's `@Validated` and Hibernate Validator conventions). Defaults to `{Default.class}` when absent.
- **Spring Boot auto-configuration** for validation. `MethodicalAutoConfiguration` gains a default `MethodValidatorFactory` bean (`NO_OP`). When `jakarta.validation.Validator` and a `Validator` bean are present, `JakartaValidationAutoConfiguration` registers a `JakartaMethodValidatorFactory` — adding `spring-boot-starter-validation` + `methodical-jakarta-validation` to a Spring Boot app is the full setup; no manual wiring.

### Changed

- **`DefaultMethodInvoker` now calls `method.setAccessible(true)` at construction.** Methodical can now invoke private and package-private methods, and public methods on non-public classes from outside the declaring package. Previously these threw `MethodInvocationException` wrapping `IllegalAccessException`. The `IllegalAccessException` catch block is retained for defense-in-depth (e.g., `SecurityManager` rejections) but is no longer reachable via normal access restrictions.

### Requirements

- `methodical-jakarta-validation` requires a Jakarta Bean Validation 3.x provider at runtime (e.g., Hibernate Validator 9.x, typically brought in via `spring-boot-starter-validation`).
- Modules consuming Java Platform Module System (`module-info.java`) must `opens` their target packages to `methodical-core`; without this, `setAccessible(true)` throws `InaccessibleObjectException` at `MethodInvoker` construction. Non-module-path consumers are unaffected.

## [0.4.0] - 2026-04-14

### Breaking changes

- **`ParameterInfo` record signature changed** from `(Parameter, int, String name, Class<?> resolvedType, Type genericType)` to `(Parameter, int, String name, TypeRef<?> type)`. The `resolvedType()` and `genericType()` accessors are preserved as derived methods, but destructuring patterns or direct component-order calls will break.
- **`@Argument` type mismatch now throws `ParameterResolutionException`** instead of `IllegalArgumentException`. Code catching `IllegalArgumentException` specifically must update.
- **Factory fails fast at `create(...)` time** with `ParameterResolutionException` when no resolver matches a parameter. Previously the parameter was silently passed as `null` at invoke time.
- **`ParameterResolver` dispatch is generic-aware.** A resolver declared for `ParameterResolver<Map<String,String>>` no longer matches an invoker with argument type `Map<String,Integer>`. Dispatch now uses full `TypeRef<?>` matching rather than raw-class assignability.

### Added

- **`TypeRef<A>`-based factory API.** `MethodInvokerFactory.create(...)` now has four overloads:
  - `create(Method, Object, Class<A>)`
  - `create(Method, Object, Class<A>, List<ParameterResolver<? super A>>)`
  - `create(Method, Object, TypeRef<A>)`
  - `create(Method, Object, TypeRef<A>, List<ParameterResolver<? super A>>)` *(primary)*
- **Per-invoker resolvers** — pass an `extraResolvers` list to `create(...)` to add resolvers that are tried ahead of the factory-level ones, scoped to a single invoker.
- **`ParameterInfo` ergonomics:**
  - `accepts(Class<?>)`, `accepts(TypeRef<?>)`, `accepts(Type)` — generic-aware assignability check against the parameter's declared type.
  - `annotation(Class<T>)` returns `Optional<T>`.
  - `hasAnnotation(Class<?>)` returns `boolean`.

### Changed

- **Consolidated reflection helpers on [specular](https://github.com/jwcarman/specular).** `org.jwcarman.methodical.reflect.Types` has been removed; methodical now depends on `org.jwcarman:specular` for `TypeRef` and type-variable resolution.
- **Detailed error messages** when the factory can't find a resolver: parameter name, declared type, method, argument type, and the list of resolvers tried.
- README overhauled with sections for `TypeRef`-based argument types, `@Argument` pass-through, per-invoker resolvers, and an expanded "Writing a custom resolver" guide.

### Removed

- `org.jwcarman.methodical.reflect.Types` — use specular's `TypeRef` helpers (`TypeRef.parameterType(...)`, `TypeRef.of(cls).typeArgument(...)`).

## [0.3.0] - 2026-04-10

### Added

- `@Argument` annotation — parameters annotated with `@Argument` receive the raw `invoke()` argument directly, bypassing parameter resolution. The argument type must be assignable to the parameter type.

## [0.2.1] - 2026-04-09

### Fixed

- Auto-configuration ordering — `Jackson3AutoConfiguration`, `Jackson2AutoConfiguration`, and `GsonAutoConfiguration` now declare `after=` ordering on their corresponding Spring Boot auto-configs. Without this, `@ConditionalOnBean(ObjectMapper.class)` could evaluate before the `ObjectMapper` bean existed, causing the parameter resolver to never be created.

### Changed

- Add `spring-boot-jackson` and `spring-boot-jackson2` as optional dependencies for type-safe `after` references.

## [0.2.0] - 2026-04-09

### Added

- `MethodicalException` — abstract base exception for all Methodical errors.
- `ParameterResolutionException` — thrown when a resolver fails to deserialize a parameter value.
- Exception hierarchy: `MethodicalException` ← `MethodInvocationException` (reflection/checked), `ParameterResolutionException` (deserialization).

### Changed

- `MethodInvocationException` now extends `MethodicalException` (was `RuntimeException`).
- All JSON resolvers (Jackson 2, Jackson 3, Gson) throw `ParameterResolutionException` on deserialization failure.

## [0.1.0] - 2026-04-09

### Added

- Initial release — pluggable reflection-based method invocation for Java.
- `MethodInvokerFactory` interface with `DefaultMethodInvokerFactory` implementation.
- `MethodInvoker<A>` functional interface — pre-built, zero per-call reflection overhead.
- `ParameterResolver<A>` SPI for pluggable argument resolution from any source type.
- `@Named` annotation for overriding parameter names used during resolution.
- `ParameterInfo` record with resolved generic types and parameter metadata.
- Jackson 3 parameter resolver (`methodical-jackson3`) — `tools.jackson.databind.JsonNode`.
- Jackson 2 parameter resolver (`methodical-jackson2`) — `com.fasterxml.jackson.databind.JsonNode`.
- Gson parameter resolver (`methodical-gson`) — `com.google.gson.JsonElement`.
- Spring Boot auto-configuration with classpath-based resolver detection.
- JSON resolvers registered at `Ordered.LOWEST_PRECEDENCE` as catch-all fallback.
- Runtime exceptions from invoked methods unwrapped and rethrown.
- Checked exceptions and reflection failures wrapped in `MethodInvocationException`.

[Unreleased]: https://github.com/jwcarman/methodical/compare/0.5.0...HEAD
[0.5.0]: https://github.com/jwcarman/methodical/releases/tag/0.5.0
[0.4.0]: https://github.com/jwcarman/methodical/releases/tag/0.4.0
[0.3.0]: https://github.com/jwcarman/methodical/releases/tag/0.3.0
[0.2.1]: https://github.com/jwcarman/methodical/releases/tag/0.2.1
[0.2.0]: https://github.com/jwcarman/methodical/releases/tag/0.2.0
[0.1.0]: https://github.com/jwcarman/methodical/releases/tag/0.1.0
