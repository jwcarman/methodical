# Changelog

## 0.2.0

### New Types
- `MethodicalException` — abstract base exception for all Methodical errors
- `ParameterResolutionException` — thrown when a resolver fails to deserialize a parameter value
- Exception hierarchy: `MethodicalException` ← `MethodInvocationException` (reflection/checked), `ParameterResolutionException` (deserialization)

### Changes
- `MethodInvocationException` now extends `MethodicalException` (was `RuntimeException`)
- All JSON resolvers (Jackson 2, Jackson 3, Gson) throw `ParameterResolutionException` on deserialization failure
- Callers can catch `MethodicalException` for broad handling, or specific subtypes for granular control

## 0.1.0

Initial release — pluggable reflection-based method invocation for Java.

### Features
- `MethodInvokerFactory` interface with `DefaultMethodInvokerFactory` implementation
- `MethodInvoker<A>` functional interface — pre-built, zero per-call reflection overhead
- `ParameterResolver<A>` SPI for pluggable argument resolution from any source type
- `@Named` annotation for overriding parameter names used during resolution
- `ParameterInfo` record with resolved generic types and parameter metadata
- `Types` utility for generic type parameter resolution
- Jackson 3 parameter resolver (`methodical-jackson3`) — `tools.jackson.databind.JsonNode`
- Jackson 2 parameter resolver (`methodical-jackson2`) — `com.fasterxml.jackson.databind.JsonNode`
- Gson parameter resolver (`methodical-gson`) — `com.google.gson.JsonElement`
- Spring Boot auto-configuration with classpath-based resolver detection
- JSON resolvers registered at `Ordered.LOWEST_PRECEDENCE` as catch-all fallback
- Runtime exceptions from invoked methods unwrapped and rethrown
- Checked exceptions and reflection failures wrapped in `MethodInvocationException`
