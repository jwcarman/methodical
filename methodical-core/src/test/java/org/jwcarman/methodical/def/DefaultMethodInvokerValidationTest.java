package org.jwcarman.methodical.def;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.jwcarman.methodical.MethodInvoker;
import org.jwcarman.methodical.MethodValidator;
import org.jwcarman.methodical.param.ParameterInfo;
import org.jwcarman.methodical.param.ParameterResolver;
import org.jwcarman.specular.TypeRef;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class DefaultMethodInvokerValidationTest {

  static class Greeter {
    String greet(String name) {
      return "hello " + name;
    }
  }

  @Test
  void validator_sees_parameters_then_return_value_in_order() throws Exception {
    Greeter target = new Greeter();
    Method method = Greeter.class.getDeclaredMethod("greet", String.class);
    ParameterInfo info = ParameterInfo.of(method.getParameters()[0], 0, TypeRef.of(String.class));
    ParameterResolver<String> resolver =
        new ParameterResolver<>() {
          @Override
          public boolean supports(ParameterInfo paramInfo) {
            return true;
          }

          @Override
          public Object resolve(ParameterInfo paramInfo, String arg) {
            return arg;
          }
        };
    StringBuilder log = new StringBuilder();
    MethodValidator validator =
        new MethodValidator() {
          @Override
          public void validateParameters(Object[] args) {
            log.append("params:").append(args[0]).append(';');
          }

          @Override
          public void validateReturnValue(Object returnValue) {
            log.append("return:").append(returnValue).append(';');
          }
        };

    MethodInvoker<String> invoker =
        new DefaultMethodInvoker<>(
            method, target, new ParameterInfo[] {info}, List.of(resolver), validator);

    Object result = invoker.invoke("world");

    assertThat(result).isEqualTo("hello world");
    assertThat(log.toString()).isEqualTo("params:world;return:hello world;");
  }

  @Test
  void parameter_validation_failure_skips_invocation_and_return_validation() throws Exception {
    Greeter target = new Greeter();
    Method method = Greeter.class.getDeclaredMethod("greet", String.class);
    ParameterInfo info = ParameterInfo.of(method.getParameters()[0], 0, TypeRef.of(String.class));
    ParameterResolver<String> resolver =
        new ParameterResolver<>() {
          @Override
          public boolean supports(ParameterInfo paramInfo) {
            return true;
          }

          @Override
          public Object resolve(ParameterInfo paramInfo, String arg) {
            return arg;
          }
        };
    boolean[] returnValidated = {false};
    MethodValidator validator =
        new MethodValidator() {
          @Override
          public void validateParameters(Object[] args) {
            throw new IllegalArgumentException("bad args");
          }

          @Override
          public void validateReturnValue(Object returnValue) {
            returnValidated[0] = true;
          }
        };

    MethodInvoker<String> invoker =
        new DefaultMethodInvoker<>(
            method, target, new ParameterInfo[] {info}, List.of(resolver), validator);

    assertThatThrownBy(() -> invoker.invoke("world"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bad args");
    assertThat(returnValidated[0]).isFalse();
  }
}
