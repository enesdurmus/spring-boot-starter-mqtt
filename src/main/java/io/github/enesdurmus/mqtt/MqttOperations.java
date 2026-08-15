package io.github.enesdurmus.mqtt;

import org.springframework.messaging.core.MessageSendingOperations;

import java.util.Map;

/**
 * Publishing contract. Extends Spring's {@link MessageSendingOperations} with the MQTT-specific
 * QoS and RETAIN controls, so both {@code convertAndSend(...)} and {@code publish(...)} styles
 * are available.
 *
 * <p>Every method throws {@link MqttPublishException} on failure; Paho's checked exceptions are
 * never propagated.
 */
public interface MqttOperations extends MessageSendingOperations<String> {

    /** Publishes at QoS 0 without retaining. */
    void publish(String topic, Object payload);

    void publish(String topic, Object payload, int qos);

    void publish(String topic, Object payload, int qos, boolean retained);

    void publish(String topic, Object payload, int qos, boolean retained, Map<String, Object> headers);

    /**
     * Publishes a retained message, so the broker keeps it as the last known value for the topic
     * and delivers it to every future subscriber.
     */
    void retain(String topic, Object payload);

    void retain(String topic, Object payload, int qos);

    /**
     * Removes the retained message for a topic by publishing a zero-length retained payload,
     * as required by the MQTT specification. Subscribers connecting afterwards receive nothing
     * until a new message is published.
     */
    void clearRetained(String topic);

    void clearRetained(String topic, int qos);
}
