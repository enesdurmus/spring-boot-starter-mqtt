package io.github.enesdurmus.mqtt;

import org.springframework.messaging.Message;

/**
 * Thrown when a subscription or unsubscription fails.
 */
public class MqttSubscriptionException extends MqttClientException {

    public MqttSubscriptionException(String description) {
        super(description);
    }

    public MqttSubscriptionException(String description, Throwable cause) {
        super(description, cause);
    }

    public MqttSubscriptionException(Message<?> message, String description, Throwable cause) {
        super(message, description, cause);
    }
}
