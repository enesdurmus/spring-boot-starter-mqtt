package io.github.enesdurmus.mqtt;

import org.springframework.messaging.Message;

/**
 * Thrown when a payload cannot be converted.
 */
public class MqttConversionException extends MqttClientException {

    public MqttConversionException(String description) {
        super(description);
    }

    public MqttConversionException(String description, Throwable cause) {
        super(description, cause);
    }

    public MqttConversionException(Message<?> message, String description, Throwable cause) {
        super(message, description, cause);
    }
}
