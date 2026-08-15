package io.github.enesdurmus.mqtt;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;

/**
 * Base class for all unchecked exceptions raised by this starter. Paho's checked
 * {@code MqttException} never crosses the public API.
 */
public class MqttClientException extends MessagingException {

    private static final long serialVersionUID = 1L;

    public MqttClientException(String description) {
        super(description);
    }

    public MqttClientException(String description, Throwable cause) {
        super(description, cause);
    }

    public MqttClientException(Message<?> message, String description) {
        super(message, description);
    }

    public MqttClientException(Message<?> message, String description, Throwable cause) {
        super(message, description, cause);
    }
}
