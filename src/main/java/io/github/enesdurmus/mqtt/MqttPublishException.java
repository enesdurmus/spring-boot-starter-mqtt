package io.github.enesdurmus.mqtt;

import org.springframework.messaging.Message;

/**
 * Thrown when a message cannot be published.
 */
public class MqttPublishException extends MqttClientException {

    public MqttPublishException(String description) {
        super(description);
    }

    public MqttPublishException(String description, Throwable cause) {
        super(description, cause);
    }

    public MqttPublishException(Message<?> message, String description, Throwable cause) {
        super(message, description, cause);
    }
}
