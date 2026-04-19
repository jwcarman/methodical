# Interceptors Design

**Date:** 2026-04-19
**Target version:** 0.6.0

## Goal

Introduce a first-class interceptor SPI for Methodical, replacing the
special-cased `MethodValidator` construct in `methodical-core`. Keep the core
framework-neutral: the factory ships no built-in interceptors, and validation
moves out to `methodical-jakarta-validation` as just another interceptor.

## Motivation

Today `DefaultMethodInvoker` hard-codes a single cross-cutting concern —
Jakarta Bean Validation — via `MethodValidator`. That choice couples the core
module to a specific use case and gives no extension point for others
(timing, logging, auth, retry, scoped-value binding, tracing). A generic
interceptor SPI unifies every such concern behind one abstraction and lets
`methodical-core` stay neutral.

## Core SPI

### `MethodInvocation<A>`

Public interface. Immutable view of a single invocation, plus the
`proceed()` continuation into the rest of the chain.

```java
public interface MethodInvocation<A> {
  Method method();
  Object target();
  A argument();
  List<Object> resolvedParameters();
  Object proceed();

  static <A> MethodInvocation<A> of(
      Method method, Object target, A argument,
      List<Object> resolvedParameters, Supplier<Object> continuation);
}
```

- `resolvedParameters()` returns an unmodifiable `List<Object>`. `List`
  rather than `Object[]` to prevent an interceptor from mutating the
  parameter array that the reflective call will use.
- `proceed()` may be called zero, one, or many times. Each call re-runs
  downstream interceptors and, ultimately, the reflective method call.
  Most interceptors call it exactly once. Retry interceptors can call it
  in a loop.
- `of(...)` is a public factory so tests can construct invocations
  directly without mocks.

### `MethodInvocationInterceptor<A>`

```java
public interface MethodInvocationInterceptor<A> {
  Object intercept(MethodInvocation<A> invocation);
}
```

Single-parameter signature. The interceptor decides whether and when to
call `invocation.proceed()`, and may observe, wrap, short-circuit, or
transform the result.

### Internal record impl

Package-private record in `org.jwcarman.methodical.intercept`:

```java
record DefaultMethodInvocation<A>(
    Method method, Object target, A argument,
    List<Object> resolvedParameters, Supplier<Object> continuation)
    implements MethodInvocation<A> {

  DefaultMethodInvocation {
    resolvedParameters = Collections.unmodifiableList(new ArrayList<>(resolvedParameters));
  }

  @Override public Object proceed() { return continuation.get(); }
}
```

The defensive copy + `unmodifiableList` uses `ArrayList` rather than
`List.copyOf` because resolved parameters may legitimately be `null`,
and `List.copyOf` rejects null elements.

### `MethodInterceptors` — helper factory class

Guava-style plural naming (matches project convention). Public static
factory methods for common patterns.

```java
public final class MethodInterceptors {
  private MethodInterceptors() {}

  public static <A> MethodInvocationInterceptor<A> before(
      Consumer<MethodInvocation<A>> action);

  public static <A> MethodInvocationInterceptor<A> onSuccess(
      BiConsumer<MethodInvocation<A>, Object> action);

  public static <A, T> MethodInvocationInterceptor<A> scopedValue(
      ScopedValue<T> scopedValue,
      Function<MethodInvocation<A>, Optional<T>> supplier);
}
```

Semantics:

- `before` runs its `Consumer` before `proceed()`. If the consumer throws,
  the chain short-circuits; the exception propagates unchanged.
- `onSuccess` runs its `BiConsumer` only on normal return. On exception
  from `proceed()`, the consumer is skipped and the exception propagates.
  (Named `onSuccess` to make this explicit, vs. the ambiguous `after`.)
- `scopedValue` binds the supplied `ScopedValue<T>` around the invocation
  when the supplier returns a non-empty `Optional`. On `Optional.empty()`,
  the binding is skipped — the chain proceeds without modification. This
  lets interceptors opt out per-invocation.

If users need more complex semantics (e.g., `afterThrowing`, arbitrary
`around`), they implement `MethodInvocationInterceptor` directly. The
helpers cover the common cases; we're not chasing completeness.

## Factory and config

### `MethodInvokerConfig<A>`

Per-invoker configuration passed to the customizer.

```java
public interface MethodInvokerConfig<A> {
  MethodInvokerConfig<A> resolver(ParameterResolver<? super A> resolver);
  MethodInvokerConfig<A> interceptor(MethodInvocationInterceptor<? super A> interceptor);
}
```

Both methods return `this` for chaining. Variance (`? super A`) matches
today's `ParameterResolver` usage and lets generic interceptors (e.g.,
`MethodInvocationInterceptor<Object>`) apply to any `A`.

### `MethodInvokerFactory`

```java
public interface MethodInvokerFactory {

  default <A> MethodInvoker<A> create(Method method, Object target, Class<A> argumentType) {
    return create(method, target, TypeRef.of(argumentType), cfg -> {});
  }

  default <A> MethodInvoker<A> create(Method method, Object target, TypeRef<A> argumentType) {
    return create(method, target, argumentType, cfg -> {});
  }

  default <A> MethodInvoker<A> create(
      Method method, Object target, Class<A> argumentType,
      Consumer<MethodInvokerConfig<A>> customizer) {
    return create(method, target, TypeRef.of(argumentType), customizer);
  }

  <A> MethodInvoker<A> create(
      Method method, Object target, TypeRef<A> argumentType,
      Consumer<MethodInvokerConfig<A>> customizer);
}
```

The existing `List<ParameterResolver<? super A>> extraResolvers`
overloads are removed. Callers migrate via
`cfg -> extras.forEach(cfg::resolver)`.

### Factory internals

The factory holds **no** ambient configuration — no factory-level
resolvers, no factory-level interceptors. The only resolver the factory
adds itself is the `@Argument` fallback, which is inherent to the
argument type rather than a pluggable resolver.

```java
@Override
public <A> MethodInvoker<A> create(Method m, Object target, TypeRef<A> argType,
                                   Consumer<MethodInvokerConfig<A>> customizer) {
  var config = new DefaultMethodInvokerConfig<A>();
  customizer.accept(config);

  List<ParameterResolver<? super A>> resolvers = new ArrayList<>(config.resolvers());
  resolvers.add(new ArgumentParameterResolver<>(argType));

  var paramInfos = ParameterInfo.forMethod(m);
  var assigned = assignResolvers(paramInfos, resolvers);

  return new DefaultMethodInvoker<>(m, target, paramInfos, assigned, config.interceptors());
}
```

**Resolver precedence:** customizer-added resolvers run before the
`@Argument` fallback; first matching resolver wins.

**Interceptor order:** registration order. The first interceptor added
is the outermost; the last interceptor added runs closest to the
reflective call. No `priority()` method, no reordering. Callers who need
a specific order add in that order.

### On connascence of position

Registration-order semantics introduce connascence of position:
interceptors must agree on their relative order, and that agreement is
implicit in who calls `.interceptor()` first. This is a deliberate
trade-off.

The alternatives don't remove the coupling — they relocate it, usually
into a stronger form. Priority numbers introduce connascence of value
(two interceptors must agree on numeric magnitudes). Named phases
introduce connascence of name plus baked-in framework opinions about
what phases exist, which we explicitly don't want in a
framework-neutral core. Explicit before/after constraints introduce
connascence of algorithm (graph topology), which is stronger still.

Positional connascence is acceptable here because it is **local**.
The customizer pattern forces all interceptors to be registered inside
a single lambda at a single call site, so the positional agreement
spans a few adjacent lines rather than files or modules. The one place
this locality breaks down is composition across boundaries — e.g., a
Spring Boot starter's `starterDefaults` composed with a user's
customizer via `Consumer.andThen`. That boundary is handled by
documenting the starter's ordering contract (starter-added interceptors
wrap user-added ones), which is connascence of convention between two
well-known parties rather than N arbitrary modules.

## Invocation flow

`DefaultMethodInvoker`:

```java
@Override
public Object invoke(A argument) {
  Object[] args = resolveArguments(argument);
  return dispatch(argument, args, 0);
}

private Object dispatch(A argument, Object[] args, int index) {
  if (index == interceptors.size()) {
    return reflectInvoke(args);
  }
  MethodInvocation<A> inv = MethodInvocation.of(
      method, target, argument, Arrays.asList(args),
      () -> dispatch(argument, args, index + 1));
  return interceptors.get(index).intercept(inv);
}

private Object reflectInvoke(Object[] args) {
  try {
    return method.invoke(target, args);
  } catch (InvocationTargetException e) {
    Throwable cause = e.getCause();
    if (cause instanceof RuntimeException re) throw re;
    throw new MethodInvocationException("Method invocation failed: " + cause.getMessage(), cause);
  } catch (IllegalAccessException e) {
    throw new MethodInvocationException("Method invocation failed: " + e.getMessage(), e);
  }
}
```

Key points:

- The `Object[] args` is the invoker's private array, used directly for
  the reflective call. Interceptors see an unmodifiable `List<Object>`
  view constructed fresh for each `MethodInvocation` they receive.
- With zero interceptors, `dispatch` takes one call to land on
  `reflectInvoke` — no measurable overhead vs. today.
- Allocation per call: one `DefaultMethodInvocation` per interceptor per
  call, one unmodifiable-`List` wrapper per `MethodInvocation`. Retry
  interceptors that call `proceed()` multiple times pay this cost per
  call, which is correct and expected.

## Module impact

### `methodical-core`

**Add:**
- Package `org.jwcarman.methodical.intercept`.
- `MethodInvocation<A>` (interface, static `of(...)` factory).
- `MethodInvocationInterceptor<A>` (interface).
- `MethodInterceptors` (helper factory class).
- `MethodInvokerConfig<A>` (interface).

**Change:**
- `MethodInvokerFactory` — replace `List<ParameterResolver<? super A>>`
  overloads with `Consumer<MethodInvokerConfig<A>>` overloads.
- `DefaultMethodInvoker` — drop `MethodValidator` field; hold
  `List<MethodInvocationInterceptor<? super A>>` and use the `dispatch`
  flow above.
- `DefaultMethodInvokerFactory` — drop factory-level resolvers;
  factory becomes nearly stateless.

**Remove:**
- `MethodValidator`.
- `MethodValidatorFactory`.

**Keep unchanged:**
- `Argument`, `Named`, `MethodInvoker`, `MethodInvocationException`,
  `ParameterResolver`, `ParameterInfo`, `ArgumentParameterResolver`.

### `methodical-jakarta-validation`

**Add:**
- `JakartaValidationInterceptor<A> implements MethodInvocationInterceptor<A>`.
  Performs parameter validation before `proceed()` and return-value
  validation after, via `ExecutableValidator`. Throws
  `ConstraintViolationException` on any violation.

**Remove:**
- Any existing `MethodValidator` / `MethodValidatorFactory` impl (moved
  concept is now the interceptor).

**Wiring:** users add it explicitly via
`cfg.interceptor(new JakartaValidationInterceptor<>(validator))`.
No classpath magic.

### `methodical-spring-boot-starter` + `methodical-autoconfigure`

Expose a shared `Consumer<MethodInvokerConfig<?>>` default-configuration
bean that wires:
- Spring-aware `ParameterResolver`s.
- `JakartaValidationInterceptor` if Jakarta validation is on the
  classpath.

Users compose:
```java
factory.create(m, t, argType, starterDefaults.andThen(userCustomizer));
```

### `methodical-gson`, `methodical-jackson2`, `methodical-jackson3`

Expected to be unaffected — these modules supply argument-deserialization
`ParameterResolver`s, not validators. Confirm via grep during
implementation.

### `methodical-bom`

No changes.

## Migration for library consumers

Before:
```java
MethodInvoker<Req> invoker = factory.create(m, target, Req.class,
    List.of(new MyResolver()));
```

After:
```java
MethodInvoker<Req> invoker = factory.create(m, target, Req.class,
    cfg -> cfg.resolver(new MyResolver())
              .interceptor(new JakartaValidationInterceptor<>(validator)));
```

## Versioning

Breaking SPI change — `MethodValidator` removal, factory signature
change, factory-level resolver removal. Appropriate for **0.6.0** (the
current SNAPSHOT version).

## Non-goals

- **Argument rewriting via interceptors.** Deliberately excluded.
  Argument transformation belongs in `ParameterResolver`. If real
  demand appears later, a non-breaking overload
  `proceed(List<Object> overrideArgs)` can be added.
- **Priority-based interceptor ordering.** Registration order is
  explicit and sufficient. If needed later, a sort-by-priority wrapper
  can be layered on top without changing the core SPI.
- **Automatic `ServiceLoader` discovery** of interceptors. Explicit
  wiring only — auto-registration is easy to get wrong under GraalVM
  native-image and makes behavior depend on classpath in ways that are
  hard to debug.
