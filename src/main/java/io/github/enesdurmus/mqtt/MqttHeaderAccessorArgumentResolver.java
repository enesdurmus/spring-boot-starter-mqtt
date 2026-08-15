package io.github.enesdurmus.mqtt;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;

/**
 * Resolves {@link MqttMessageHeaderAccessor} parameters, giving listener methods typed access to
 * the MQTT headers without declaring one parameter per header.
 */
public class MqttHeaderAccessorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return MqttMessageHeaderAccessor.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, Message<?> message) {
        return MqttMessageHeaderAccessor.wrap(message);
    }
}
