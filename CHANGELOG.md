# Changelog

## 0.1.0

Initial release — pluggable reflection-based method invocation for Java.

### Features
- `MethodInvokerFactory` for creating pre-built method invokers
- `ParameterResolver<A>` SPI for pluggable argument resolution
- Jackson 3 parameter resolver (`methodical-jackson3`)
- Jackson 2 parameter resolver (`methodical-jackson2`)
- Gson parameter resolver (`methodical-gson`)
- Spring Boot auto-configuration with classpath-based resolver detection
