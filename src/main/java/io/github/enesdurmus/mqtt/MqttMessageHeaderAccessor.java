package io.github.enesdurmus.mqtt;

import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.util.Collections;
import java.util.Map;

/**
 * Typed access to {@link MqttHeaders} on a received message.
 *
 * <pre>{@code
 * MqttMessageHeaderAccessor mqtt = MqttMessageHeaderAccessor.wrap(message);
 * if (mqtt.isRetained()) { ... }
 * }</pre>
 */
public class MqttMessageHeaderAccessor extends MessageHeaderAccessor {

    public MqttMessageHeaderAccessor(Message<?> message) {
        super(message);
    }

    public static MqttMessageHeaderAccessor wrap(Message<?> message) {
        return new MqttMessageHeaderAccessor(message);
    }

    public String getReceivedTopic() {
        return getHeader(MqttHeaders.RECEIVED_TOPIC, String.class);
    }

    public int getQos() {
        Integer qos = getHeader(MqttHeaders.RECEIVED_QOS, Integer.class);
        return qos != null ? qos : 0;
    }

    public boolean isRetained() {
        return Boolean.TRUE.equals(getHeader(MqttHeaders.RECEIVED_RETAINED, Boolean.class));
    }

    public boolean isDuplicate() {
        return Boolean.TRUE.equals(getHeader(MqttHeaders.DUPLICATE, Boolean.class));
    }

    public int getMessageId() {
        Integer id = getHeader(MqttHeaders.ID, Integer.class);
        return id != null ? id : 0;
    }

    /** The MQTT 5 content-type property, distinct from the Spring {@code contentType} header. */
    @Nullable
    public String getMqttContentType() {
        return getHeader(MqttHeaders.CONTENT_TYPE, String.class);
    }

    @Nullable
    public String getResponseTopic() {
        return getHeader(MqttHeaders.RESPONSE_TOPIC, String.class);
    }

    @Nullable
    public byte[] getCorrelationData() {
        return getHeader(MqttHeaders.CORRELATION_DATA, byte[].class);
    }

    @Nullable
    public Long getMessageExpiryInterval() {
        return getHeader(MqttHeaders.MESSAGE_EXPIRY_INTERVAL, Long.class);
    }

    @Nullable
    public String getListenerId() {
        return getHeader(MqttHeaders.LISTENER_ID, String.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getUserProperties() {
        Object value = getHeader(MqttHeaders.USER_PROPERTIES);
        return value instanceof Map ? (Map<String, String>) value : Collections.emptyMap();
    }

    @Nullable
    private <T> T getHeader(String name, Class<T> type) {
        Object value = getHeader(name);
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
