package io.github.enesdurmus.mqtt.support;

import io.github.enesdurmus.mqtt.annotation.Topic;
import io.github.enesdurmus.mqtt.core.MqttHeaders;

import org.springframework.core.MethodParameter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;

/**
 * Resolves {@link Topic}-annotated parameters to the concrete topic the message was published to.
 */
public class TopicMethodArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Topic.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, Message<?> message) {
        if (!String.class.isAssignableFrom(parameter.getParameterType())) {
            throw new IllegalStateException("@Topic parameter must be of type String: " + parameter.getExecutable());
        }
        return message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC, String.class);
    }
}
