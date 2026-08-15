package io.github.enesdurmus.mqtt;

import org.springframework.messaging.Message;

/**
 * Thrown when the broker connection cannot be established or is lost unrecoverably.
 */
public class MqttConnectionException extends MqttClientException {

    private static final long serialVersionUID = 1L;

    public MqttConnectionException(String description) {
        super(description);
    }

    public MqttConnectionException(String description, Throwable cause) {
        super(description, cause);
    }

    public MqttConnectionException(Message<?> message, String description, Throwable cause) {
        super(message, description, cause);
    }
}
