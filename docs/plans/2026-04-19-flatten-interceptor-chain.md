# Flatten the Interceptor Chain — Allocation Reduction

**Date:** 2026-04-19
**Target version:** 0.7.0

## Goal

Eliminate per-invocation lambda capture and per-interceptor wrapper allocation
inside `DefaultMethodInvoker`'s chain execution. Turn the linked-list
`Call` chain into a flat indexed array walked by a single shared object,
so the per-invocation allocation footprint drops from *(N interceptors)
× (wrapper + lambda)* plus one `MethodInvocation` per step, down to a
single `MethodInvocation` instance plus the pre-existing `Object[]`
argument array.

## Motivation

A CPU + allocation profile of a Spring-Boot application using Methodical
under sustained load (565 req/s, ~15ms p50) showed Methodical accounting
for **1.8 % of CPU time** and a meaningful share of the allocation rate.
The allocation breakdown per dispatched call is:

- 1 × `Object[]` for resolved arguments (unavoidable — escapes to
  `Method.invoke`).
- **N × `InterceptorCall` instance** (per chain element; already
  constructed at invoker-construction time, so not a per-call cost —
  this is the good part).
- **N × `MethodInvocation` instance** created inside
  `InterceptorCall.invoke` on every invocation (line 120).
- **N × captured lambda closure** for the `proceed()` continuation
  (line 122: `() -> next.invoke(argument, parameters)`).

At 565 req/s with three interceptors per call (typical for a real MCP
deployment: MDC, o11y, audit), that's ~3,400 `MethodInvocation`
allocations per second and ~3,400 captured lambdas per second. Young-gen
pressure dominated by those two sites.

The reflection path itself (`Method.invoke`) is roughly **0.18 %** of
total CPU in the same profile — modern JVM (Java 9+) inflates it to
bytecode after ~15 invocations, so it's already effectively free.
Replacing reflection with ByteBuddy / CGLIB / LambdaMetafactory would
not move the needle. The allocation churn is the real lever.

## Current shape

```java
// DefaultMethodInvoker.java — abbreviated

@Override
public Object invoke(A argument) {
    Object[] parameters = resolveArguments(argument);
    return chain.invoke(argument, parameters);
}

private final class InterceptorCall implements Call<A> {
    private final MethodInterceptor<? super A> interceptor;
    private final Call<A> next;
    @Override
    public Object invoke(A argument, Object[] parameters) {
        MethodInvocation<A> invocation =
                MethodInvocation.of(method, target, argument, parameters,
                        () -> next.invoke(argument, parameters));
        return interceptor.intercept(invocation);
    }
}

private final class MethodCall implements Call<A> {
    @Override
    public Object invoke(A argument, Object[] parameters) {
        return method.invoke(target, parameters);
    }
}
```

Per-call sequence at runtime:
1. `InterceptorCall#0.invoke` — allocate `MethodInvocation` #0 + lambda
   #0 → `interceptor[0].intercept(inv0)`.
2. Interceptor #0's `intercept` calls `inv0.proceed()` → the lambda runs
   → `InterceptorCall#1.invoke` — allocate `MethodInvocation` #1 +
   lambda #1 → `interceptor[1].intercept(inv1)`.
3. ... repeats for each chain element ...
4. Terminal `MethodCall.invoke` — no new allocation, does the reflective
   invoke.

One `MethodInvocation` + one lambda per interceptor per call. The
lambdas close over `argument`, `parameters`, and `next` from the
enclosing method.

## Proposed shape

Treat the chain as an indexed array walked by a single mutable cursor
object that *is* the `MethodInvocation`. `proceed()` advances the index
and dispatches to the next interceptor — no lambda, no new invocation
instance, no new closure.

### Skeleton

```java
class DefaultMethodInvoker<A> implements MethodInvoker<A> {

    private final Method method;
    private final Object target;
    private final ParameterInfo[] paramInfos;
    private final List<ParameterResolver<? super A>> resolvers;
    private final MethodInterceptor<? super A>[] interceptors;  // frozen array

    @Override
    public Object invoke(A argument) {
        Object[] parameters = resolveArguments(argument);
        // One cursor per call. Passed to every interceptor as the
        // MethodInvocation; proceed() bumps the index and recurses.
        Cursor<A> cursor = new Cursor<>(this, argument, parameters);
        return cursor.proceed();
    }

    // Package-private for the cursor's terminal step.
    Object invokeMethod(Object[] parameters) {
        try {
            return method.invoke(target, parameters);
        } catch (InvocationTargetException e) { ... }
        catch (IllegalAccessException e) { ... }
    }

    static final class Cursor<A> implements MethodInvocation<A> {
        private final DefaultMethodInvoker<A> owner;
        private final A argument;
        private final Object[] parameters;
        private int index = -1;         // -1 = haven't entered chain yet

        Cursor(DefaultMethodInvoker<A> owner, A argument, Object[] parameters) {
            this.owner = owner;
            this.argument = argument;
            this.parameters = parameters;
        }

        @Override
        public Object proceed() {
            index++;
            if (index < owner.interceptors.length) {
                return owner.interceptors[index].intercept(this);
            }
            return owner.invokeMethod(parameters);
        }

        @Override public Method method()           { return owner.method; }
        @Override public Object target()           { return owner.target; }
        @Override public A argument()              { return argument; }
        @Override public Object[] parameters()     { return parameters; }
    }
}
```

Per-call allocations drop to:
- 1 × `Object[]` for arguments (unchanged).
- **1 × `Cursor`** (plays the `MethodInvocation` role for every
  interceptor in the chain — one instance for the whole call, not one
  per interceptor).

Net allocation reduction per call:
- Before: `N + 1` objects (N InterceptorCall wrappers already
  constructed at build-time, N × MethodInvocation + N × lambda per
  call, plus 1 × Object[]).
- After: `2` objects (1 × Cursor + 1 × Object[]). N × MethodInterceptor
  references stay in the invoker's field, no wrappers, no lambdas.

At 565 req/s × 3 interceptors: **~6,800 fewer per-second allocations**
just from this change. On a mocapi-style workload the Methodical share
of the GC rate should drop materially.

### Reentrance safety

The `Cursor` is single-threaded by construction — one per call,
contained within a single `invoke(A)` execution. It carries the index
as mutable state, which is fine as long as nothing re-enters `proceed()`
on the same cursor from a different stack frame. Interceptors that call
`invocation.proceed()` in the usual "run next step" pattern are fine.
Interceptors that call `proceed()` twice on the same invocation were
already wrong under the existing shape (each call would re-run the rest
of the chain), so nothing new to forbid.

If a future interceptor needs to hold the invocation beyond its
`intercept` call — e.g., for retries — it should `proceed()` before
returning and not store the reference. Document this in the
`MethodInvocation` Javadoc; today the contract is implicit.

### Async interceptors

The `lambda$invoke$0` capture in today's code permits an interceptor to
invoke `proceed()` on a different thread (the lambda carries its closure).
Under the flattened design, `proceed()` mutates the cursor index and
requires the same thread — async calls into `proceed()` from a pool
executor would race.

Realistic assessment: none of the existing Methodical-consumer
interceptors (Jakarta Validation, MDC, Micrometer Observation, MCP
Guards, audit loggers) call `proceed()` asynchronously. They all wrap
sync. If a future async-interceptor use case appears, the fix is to
make the cursor snapshot its index before recursing — but until
there's a real demand, don't pay that complexity.

**Document the constraint** in the `MethodInvocation` javadoc:
"`proceed()` must be called on the same thread that invoked
`intercept(...)`. Cross-thread continuations are not supported."

### Fallback when no interceptors

If `interceptors.length == 0`, the cursor allocates only to be used
once before immediately reaching the terminal branch. Could be worth a
micro-optimization: skip the Cursor and call `invokeMethod(parameters)`
directly from `invoke(A)`. Trivial branch at the entry point, saves
one allocation for interceptor-free calls. Clean win.

## Non-goals

- **Replacing reflection with bytecode generation** (ByteBuddy / CGLIB /
  LambdaMetafactory). `Method.invoke` is a 0.18 % CPU line item after
  JIT inflation; the complexity isn't worth the micro-optimization.
- **Pooling Cursor instances across calls.** Young-gen GC collects
  these for free in < 1 ms per pause; pooling would introduce
  thread-local / reset concerns with worse ergonomics than the GC cost.
- **Pooling the `Object[] parameters` array.** It escapes to
  `Method.invoke` which may retain references beyond the call, and
  interceptors might mutate entries. Pool semantics would be fragile
  for the same marginal benefit.
- **Removing the per-call `Object[]`** — `Method.invoke` requires it;
  varargs would allocate regardless. Fine as-is.
- **A new public API.** `MethodInvocation`, `MethodInterceptor`,
  `MethodInvoker` stay identical. Consumers don't change.

## Acceptance criteria

- [ ] `DefaultMethodInvoker` no longer instantiates `InterceptorCall`
      per chain element; the interceptor list is held as a frozen
      array field.
- [ ] A single `Cursor` (or similarly named) class implements
      `MethodInvocation<A>` and walks the interceptor array via an
      internal index.
- [ ] Per-invocation allocation count (objects beyond `Object[]` args)
      drops from *1 + N* (one `MethodInvocation` + N lambdas) to **1**
      (the cursor) — verified via a microbenchmark or JFR allocation
      profile.
- [ ] `invoke(A)` short-circuits to direct method invocation when the
      interceptor chain is empty (no cursor allocation).
- [ ] Existing unit tests pass unchanged.
- [ ] A new JMH microbenchmark in `methodical-core/src/test/jmh`
      compares throughput + GC rate under a 3-interceptor chain
      between the old and new shape. Target: ≥ 10 % throughput
      improvement, ≥ 50 % allocation reduction.
- [ ] `MethodInvocation` javadoc documents the single-thread
      constraint on `proceed()`.
- [ ] `CHANGELOG.md` entry under `[Unreleased] / ### Changed`.
- [ ] `mvn verify` green.

## Implementation notes

- The interceptor list → array conversion is trivial:
  `@SuppressWarnings("unchecked") MethodInterceptor<? super A>[] arr =
  interceptors.toArray(new MethodInterceptor[0]);`. Store as
  `private final MethodInterceptor<? super A>[] interceptors;`.
- `Cursor` implements `MethodInvocation<A>`. Today's
  `MethodInvocation.of(...)` factory can stay for test purposes but
  `DefaultMethodInvoker` stops using it.
- The `Call` inner interface and its two implementations can be
  deleted outright. The only trace left is the terminal reflective
  call, which moves inline onto `DefaultMethodInvoker.invokeMethod`.
- Exception handling currently lives in `MethodCall.invoke`; it moves
  verbatim to `invokeMethod`.
- Keep the `setAccessible(true)` call where it is (constructor).

## Downstream impact

None. The public API surface is unchanged. Consumers (Methodical Jakarta
Validation, mocapi's MDC / o11y / audit interceptors, user-written
interceptors) continue to implement `MethodInterceptor<A>` and call
`invocation.proceed()` exactly as before.
