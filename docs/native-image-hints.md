# GraalVM native-image hints for methodical

## Context

Exercised against `cowork-connector-example` (Spring Boot 4.0.5, Java 25) using the GraalVM tracing agent (`-agentlib:native-image-agent`). Methodical 0.3.0 – 0.5.0-SNAPSHOT currently ships **zero** `META-INF/native-image/` metadata and **no** `RuntimeHintsRegistrar` / `BeanRegistrationAotProcessor`.

Pattern reference: mocapi 0.4.0-SNAPSHOT (`mocapi-server/src/main/java/com/callibrity/mocapi/server/autoconfigure/aot/MocapiServicesAotProcessor.java`) shows the per-bean AOT processor pattern that registers `INVOKE` hints on annotated methods.

## Agent-captured surface (6 entries)

- `methodical-core` — `MethodInvokerFactory` (iface), `DefaultMethodInvokerFactory`, `ParameterResolver` (iface)
- `methodical-autoconfigure` — `MethodicalAutoConfiguration`, `Jackson3AutoConfiguration`
- `methodical-jackson3` — `Jackson3ParameterResolver`

The capture pre-dates `methodical-jackson2` and `methodical-gson`. Both ship parallel resolvers (`Jackson2ParameterResolver`, `GsonParameterResolver`) and autoconfigs (`Jackson2AutoConfiguration`, `GsonAutoConfiguration`); the analysis below applies to them unchanged — they're `@Bean` singletons handled by Spring AOT.

## Coverage analysis

| Category | Handled by |
|---|---|
| Auto-config classes | ✅ Spring AOT |
| Spring beans (`DefaultMethodInvokerFactory`, `Jackson3ParameterResolver`, interfaces) | ✅ Spring AOT |
| `INVOKE` hints on the annotated user methods that `MethodInvokerFactory` ultimately dispatches to | ✅ Consumer responsibility |
| Binding hints on user method parameter / return types | ✅ Consumer responsibility |

## What methodical needs to ship

**Most likely: nothing.**

Methodical is a reflective-method-invocation library. Its job is to invoke methods on user-supplied beans that its callers (mocapi, ripcurl, and similar frameworks) have already surfaced via their own `@ToolService` / `@JsonRpcService` / `@PromptService`-style annotations. Those consumer frameworks are responsible for registering:

- `ExecutableMode.INVOKE` hints on each annotated method (so the reflective invocation is legal in a native image).
- `BindingReflectionHints` on every parameter type and the return type (so Jackson — or whichever codec — can bind arguments and results).

This is already done correctly in ripcurl's `JsonRpcServiceBeanAotProcessor` and mocapi's `MocapiServicesAotProcessor`. The agent's observation of `MethodInvokerFactory` / `ParameterResolver` / `Jackson3ParameterResolver` is satisfied by Spring AOT's normal bean processing (these are `@Bean`-registered singletons with no reflective lookups beyond what AOT pre-resolves).

## When to reconsider

Ship hints from methodical itself only if one of these changes:

1. **methodical introduces its own `@Method`-style annotation that users apply directly.** In that case methodical would ship a `BeanRegistrationAotProcessor` analogous to `MocapiServicesAotProcessor` that walks each annotated method and registers `INVOKE` + binding hints.
2. **methodical-jackson3 starts using reflection on internal wire types** (e.g., a parameter-binding context record that Jackson serializes). That type would need a `BindingReflectionHints` entry.
3. **methodical adds SPI discovery via `ServiceLoader`.** Every SPI implementation class would need a reflection + `NativeImageServiceLoader`-equivalent hint.

None of these apply today.

## Verification

Bump the cowork-connector-example to a candidate methodical release, build with `mvn -Pnative spring-boot:build-image -DBP_NATIVE_IMAGE=true`, and invoke at least one tool-call through the full Jackson-parameter-resolver path. If the call succeeds natively, methodical's contribution to the chain is correct with no hints needed.

If a gap surfaces, the error will identify the specific class / method that lacks a hint, and that points back at one of the three "when to reconsider" cases above.
