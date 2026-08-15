package io.github.enesdurmus.mqtt.listener;

import io.github.enesdurmus.mqtt.core.MqttHeaders;
import io.github.enesdurmus.mqtt.core.MqttMessageHeaderAccessor;

import org.springframework.messaging.Message;

/**
 * Handles failures raised while processing an inbound message. Exceptions thrown from an
 * implementation are logged and swallowed.
 *
 * <p>The failing message carries {@link MqttHeaders#LISTENER_ID} and the inbound
 * {@code mqtt_received*} headers; wrap it with {@link MqttMessageHeaderAccessor} for typed access.
 */
@FunctionalInterface
public interface MqttListenerErrorHandler {

    void handleError(Message<?> message, Exception exception);
}
