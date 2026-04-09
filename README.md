# Methodical

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

```java
public class MyContextResolver implements ParameterResolver<JsonNode> {

    @Override
    public boolean supports(ParameterInfo info) {
        return MyContext.class.isAssignableFrom(info.resolvedType());
    }

    @Override
    public Object resolve(ParameterInfo info, JsonNode params) {
        return MyContext.current();  // however you obtain it
    }
}
```

Register as a Spring bean — it's automatically picked up by the factory. Use `@Order` to control priority (lower values = higher priority). JSON resolvers run at `Ordered.LOWEST_PRECEDENCE` as the fallback.

## Exception Handling

- **Runtime exceptions** from the invoked method are unwrapped and rethrown as-is.
- **Checked exceptions** and reflection failures are wrapped in `MethodInvocationException` (unchecked).

## Requirements

- Java 25+
- Spring Boot 4.x (for autoconfigure/starter) or standalone with any Java version
