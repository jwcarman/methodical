# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.9.1] - 2026-04-21

### Fixed

- **`MethodInvoker` is a `@FunctionalInterface` again.** 0.9.0 dropped the annotation to make room for an abstract `describe()` method, which broke lambda-based implementations. `describe()` is now a `default` method (returning the invoker's runtime class name, `"invoke"`, and an empty interceptor list), so lambdas satisfy the interface once more. Implementations that already override `describe()` — notably the builder-produced `DefaultMethodInvoker` — are unaffected.

## [0.9.0] - 2026-04-21

### Breaking changes

- **`MethodInvoker` is no longer a `@FunctionalInterface`.** A new abstract method `describe()` returning a `MethodInvoker.Descriptor` has been added, so lambda expressions no longer satisfy the interface. Obtain invokers via `MethodInvoker.builder(...)` — which has always been the documented entry point — and the descriptor is populated automatically.

### Added

- **`MethodInvoker.describe()`** returns an inert, human-readable snapshot of what the invoker wraps, intended for observability surfaces (actuator endpoints, logs, diagnostics). The snapshot is precomputed at construction and contains:
  - `declaringClassName` — fully-qualified name of the class declaring the method (matches `Method#getDeclaringClass`).
  - `methodName` — the method's simple name.
  - `interceptors` — `toString()` of each interceptor in registration order (outermost first).
- **`JakartaValidationInterceptor.toString()`** now returns `"Jakarta Bean Validation"` so descriptors read naturally instead of `ClassName@hashcode`.

## [0.8.0] - 2026-04-19

### Breaking changes

- **`MethodInvokerFactory` interface and `DefaultMethodInvokerFactory` class removed.** Replaced by a nested public interface `MethodInvoker.Builder<A>` with `.resolver(...)` / `.interceptor(...)` / `.build()`. The concrete implementation (`DefaultMethodInvokerBuilder`) is package-private; users obtain builders via `MethodInvoker.builder(method, target, argumentType)`. The old factory interface + implementation provided no value over a direct builder for a stateless, single-implementation construction operation.
- **`MethodInvokerConfig<A>` interface removed.** The separate "config surface without `build()`" interface is absorbed into `MethodInvoker.Builder<A>`. Helpers that want to accept configuration contributions take `Consumer<MethodInvoker.Builder<A>>` or the builder itself; convention ("don't call `build()` from inside the contributor") replaces the type-system signal.
- **Package flattened.** The `org.jwcarman.methodical.def`, `org.jwcarman.methodical.intercept`, and `org.jwcarman.methodical.param` subpackages are gone. All public and package-private types now live in a single `org.jwcarman.methodical` package. Imports like `org.jwcarman.methodical.intercept.MethodInterceptor` become `org.jwcarman.methodical.MethodInterceptor`; likewise for `ParameterInfo`, `ParameterResolver`, `MethodInvocation`, `MethodInterceptors`.
- **`methodical-autoconfigure` and `methodical-spring-boot-starter` modules removed.** Methodical now ships as a plain Java library with no Spring Boot coupling. The Maven coordinates `org.jwcarman.methodical:methodical-autoconfigure` and `org.jwcarman.methodical:methodical-spring-boot-starter` no longer exist. The BOM (`methodical-bom`) no longer declares those artifacts. Spring Boot consumers now wire beans themselves — see migration below.

### Migration

Construction (before):
```java
MethodInvokerFactory factory = new DefaultMethodInvokerFactory();
MethodInvoker<Req> invoker = factory.create(
    method, target, Req.class,
    cfg -> cfg.resolver(r).interceptor(i));
```

Construction (after):
```java
MethodInvoker<Req> invoker =
    MethodInvoker.builder(method, target, Req.class)
        .resolver(r)
        .interceptor(i)
        .build();
```

Spring Boot consumers (before — Spring Boot starter):
```xml
<dependency>
  <groupId>org.jwcarman.methodical</groupId>
  <artifactId>methodical-spring-boot-starter</artifactId>
  <version>0.7.0</version>
</dependency>
```

Spring Boot consumers (after — plain libraries + one `@Configuration` class you write yourself):
```xml
<dependency>
  <groupId>org.jwcarman.methodical</groupId>
  <artifactId>methodical-core</artifactId>
  <version>0.8.0</version>
</dependency>
<dependency>
  <groupId>org.jwcarman.methodical</groupId>
  <artifactId>methodical-jackson3</artifactId>
  <version>0.8.0</version>
</dependency>
<!-- optional -->
<dependency>
  <groupId>org.jwcarman.methodical</groupId>
  <artifactId>methodical-jakarta-validation</artifactId>
  <version>0.8.0</version>
</dependency>
```

```java
@Configuration
class MethodicalConfig {
  @Bean Jackson3ParameterResolver jsonResolver(ObjectMapper mapper) {
    return new Jackson3ParameterResolver(mapper);
  }

  @Bean @ConditionalOnBean(Validator.class)
  JakartaValidationInterceptor jakartaValidation(Validator validator) {
    return new JakartaValidationInterceptor(validator);
  }
}

// In the class that builds invokers, inject the beans above and call
//   MethodInvoker.builder(method, target, argType)
//       .resolver(jsonResolver)
//       .interceptor(jakartaValidation)
//       .build();
```

## [0.7.0] - 2026-04-19

### Breaking changes

- **`ParameterResolver<A>` SPI reshaped around per-parameter binding.** The old two-method interface (`boolean supports(ParameterInfo)` + `Object resolve(ParameterInfo, A)`) is replaced by a single method `Optional<Binding<A>> bind(ParameterInfo info)` that returns a pre-bound `ParameterResolver.Binding<A>` (nested type with signature `Object resolve(A argument)`). The resolver decides once, at invoker-build time, whether it applies to a given parameter and — if so — captures all per-parameter state (name, index, resolved type, annotations, reader/parser) into the returned `Binding`. Per-invocation dispatch calls `binding.resolve(argument)` directly, with no `ParameterInfo` traversal.
- **Jackson 2 / Jackson 3 / Gson resolvers updated to the new SPI.** `Jackson2ParameterResolver`, `Jackson3ParameterResolver`, and `GsonParameterResolver` now pre-construct their reader/type at `bind` time. Public constructors unchanged — users constructing these resolvers directly see no difference; users who implemented the interface themselves must rewrite against `bind(...) -> Optional<Binding<A>>`.
- **`ArgumentParameterResolver` now checks the `@Argument` annotation inside `bind(...)`.** The built-in `@Argument` resolver is no longer wired as a wildcard type-check — it only produces a `Binding` when the parameter carries `@Argument`. Behavior visible to callers is identical; internal dispatch order simplified.

### Changed

- **`DefaultMethodInvoker` holds a `List<ParameterResolver.Binding<? super A>>`** keyed by parameter index, rather than a list of resolvers paired with `ParameterInfo`. `resolveArguments(A)` walks the bindings and calls each with the invoker's argument — no `ParameterInfo` lookup, no resolver supports-check at call time.

### Migration

Before:
```java
public class HeaderResolver implements ParameterResolver<HttpRequest> {
  public boolean supports(ParameterInfo info) {
    return info.hasAnnotation(Header.class) && info.accepts(String.class);
  }
  public Object resolve(ParameterInfo info, HttpRequest req) {
    String name = info.annotation(Header.class).orElseThrow().value();
    return req.getHeader(name);
  }
}
```

After:
```java
public class HeaderResolver implements ParameterResolver<HttpRequest> {
  public Optional<Binding<HttpRequest>> bind(ParameterInfo info) {
    if (!info.hasAnnotation(Header.class) || !info.accepts(String.class)) {
      return Optional.empty();
    }
    final String name = info.annotation(Header.class).orElseThrow().value();
    return Optional.of(req -> req.getHeader(name));
  }
}
```

## [0.6.1] - 2026-04-19

### Changed

- **Interceptor chain execution flattened.** `DefaultMethodInvoker` now dispatches through a single per-call cursor object that plays the `MethodInvocation` role for every interceptor in the chain, replacing the previous per-step `MethodInvocation` + continuation-lambda allocations. Per-invocation object allocation drops from `1 + N` (one `MethodInvocation` and one captured lambda per interceptor) to `1` (the cursor), plus the unavoidable `Object[]` handed to `Method.invoke`. Retry semantics preserved — an interceptor calling `proceed()` more than once still re-runs the entire remaining chain on each call, via a saved/restored cursor index. When the interceptor list is empty, the cursor is skipped entirely and the reflective call fires directly. Public API surface unchanged.
- **`MethodInvocation.proceed()` javadoc** now documents the thread-affinity constraint: `proceed()` must be called on the same thread that received `intercept(...)`. Cross-thread continuation (handing the invocation to a worker pool and calling `proceed()` there) is not supported.

## [0.6.0] - 2026-04-19

### Breaking changes

- **`MethodValidator` / `MethodValidatorFactory` SPI removed from `methodical-core`.** Validation is no longer a first-class concern in the core module; it is now one of many `MethodInterceptor`s. Users who depended on `MethodValidator` directly must migrate to the interceptor SPI.
- **`MethodInvokerFactory.create(...)` overloads replaced.** The `List<ParameterResolver<? super A>> extraResolvers` parameter is gone; every `create(...)` now takes a `Consumer<MethodInvokerConfig<A>>` customizer that registers resolvers and interceptors. Migrate `List.of(r1, r2)` call sites to `cfg -> cfg.resolver(r1).resolver(r2)`.
- **`DefaultMethodInvokerFactory` is stateless.** Its two constructors (`DefaultMethodInvokerFactory(List<ParameterResolver<?>>)` and `DefaultMethodInvokerFactory(List<ParameterResolver<?>>, MethodValidatorFactory)`) have been removed in favor of a single no-arg constructor. Any resolver previously registered at factory construction must now be registered per-invoker via the customizer. The factory no longer holds any ambient state beyond the built-in `@Argument` fallback.
- **`JakartaMethodValidator` / `JakartaMethodValidatorFactory` removed.** Replaced by a single `JakartaValidationInterceptor` class in `methodical-jakarta-validation`. Wire it via `cfg.interceptor(new JakartaValidationInterceptor(validator))` on each invoker.
- **Spring Boot auto-configuration simplified.** `MethodicalAutoConfiguration` now provides a bare `MethodInvokerFactory` bean (no factory-level resolver wiring). `JakartaValidationAutoConfiguration` provides a `JakartaValidationInterceptor` bean when `jakarta.validation.Validator` is present. Callers attach context beans to individual invokers via the customizer — nothing is auto-wired into every invoker.

### Added

- **`MethodInterceptor<A>` SPI** in `methodical-core` — a single-method functional interface `Object intercept(MethodInvocation<? extends A> invocation)`. Interceptors observe the `MethodInvocation` and call `invocation.proceed()` to continue the chain; they may short-circuit, throw, wrap, retry (via repeated `proceed()` calls), or transform the return value.
- **`MethodInvocation<A>`** — view of a single invocation passed to each interceptor: `method()`, `target()`, `argument()`, `resolvedParameters()` (defensive copy on each call), and `proceed()`.
- **`MethodInvokerConfig<A>`** — per-invoker configuration surface with `resolver(ParameterResolver<? super A>)` and `interceptor(MethodInterceptor<? super A>)` builder-style methods. Passed to the customizer lambda in `MethodInvokerFactory.create(...)`.
- **`MethodInterceptors` helpers** — static factory methods for common patterns: `before(Consumer)`, `onSuccess(BiConsumer)`, and `scopedValue(ScopedValue<T>, Function<..., Optional<T>>)` for binding a `ScopedValue` around the chain.
- **`MethodInvocation.of(...)`** — public static factory for constructing invocations directly in tests or custom dispatch code.
- **`JakartaValidationInterceptor`** — direct replacement for the removed Jakarta validator SPI. Implements `MethodInterceptor<Object>` so it can be registered against any `MethodInvokerConfig<A>`. Performs parameter and return-value validation; skips static methods and null targets; honors `@ValidationGroups` from method or class.

### Changed

- **Interceptor chain order is strictly registration order.** The first interceptor added via `cfg.interceptor(...)` is the outermost (runs first); the last added runs closest to the reflective method call. No priority, no reordering.
- **`DefaultMethodInvoker` precomputes the chain at construction time.** Interceptor wrapping happens once per invoker, not per invocation. Per-call cost is one `MethodInvocation` allocation per interceptor.

### Documentation

- README rewritten around the customizer pattern, with a new "Interceptors" section covering the SPI and the `MethodInterceptors` helpers.
- `docs/plans/2026-04-19-interceptors-design.md` — design document captured before implementation.

### Requirements

- Java 25+ (unchanged). The `MethodInterceptors.scopedValue` helper uses finalized `ScopedValue` APIs.

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

[Unreleased]: https://github.com/jwcarman/methodical/compare/0.9.1...HEAD
[0.9.1]: https://github.com/jwcarman/methodical/releases/tag/0.9.1
[0.9.0]: https://github.com/jwcarman/methodical/releases/tag/0.9.0
[0.8.0]: https://github.com/jwcarman/methodical/releases/tag/0.8.0
[0.7.0]: https://github.com/jwcarman/methodical/releases/tag/0.7.0
[0.6.1]: https://github.com/jwcarman/methodical/releases/tag/0.6.1
[0.6.0]: https://github.com/jwcarman/methodical/releases/tag/0.6.0
[0.5.0]: https://github.com/jwcarman/methodical/releases/tag/0.5.0
[0.4.0]: https://github.com/jwcarman/methodical/releases/tag/0.4.0
[0.3.0]: https://github.com/jwcarman/methodical/releases/tag/0.3.0
[0.2.1]: https://github.com/jwcarman/methodical/releases/tag/0.2.1
[0.2.0]: https://github.com/jwcarman/methodical/releases/tag/0.2.0
[0.1.0]: https://github.com/jwcarman/methodical/releases/tag/0.1.0
