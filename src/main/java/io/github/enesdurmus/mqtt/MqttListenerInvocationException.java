package io.github.enesdurmus.mqtt;

import org.springframework.messaging.Message;

/**
 * Thrown when a listener method fails or cannot be invoked.
 */
public class MqttListenerInvocationException extends MqttClientException {

    public MqttListenerInvocationException(String description) {
        super(description);
    }

    public MqttListenerInvocationException(String description, Throwable cause) {
        super(description, cause);
    }

    public MqttListenerInvocationException(Message<?> message, String description, Throwable cause) {
        super(message, description, cause);
    }
}
