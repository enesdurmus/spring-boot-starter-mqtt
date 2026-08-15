package io.github.enesdurmus.mqtt;

/**
 * Message header names used by this starter. Names prefixed {@code mqtt_received} are inbound
 * only; the remaining names are honoured on outbound messages sent through {@link MqttOperations}.
 *
 * @see MqttMessageHeaderAccessor
 */
public final class MqttHeaders {

    private static final String PREFIX = "mqtt_";

    public static final String TOPIC = PREFIX + "topic";
    public static final String QOS = PREFIX + "qos";
    public static final String RETAINED = PREFIX + "retained";

    public static final String RECEIVED_TOPIC = PREFIX + "receivedTopic";
    public static final String RECEIVED_QOS = PREFIX + "receivedQos";
    public static final String RECEIVED_RETAINED = PREFIX + "receivedRetained";
    public static final String DUPLICATE = PREFIX + "duplicate";
    public static final String ID = PREFIX + "id";

    public static final String CONTENT_TYPE = PREFIX + "contentType";
    public static final String RESPONSE_TOPIC = PREFIX + "responseTopic";
    public static final String CORRELATION_DATA = PREFIX + "correlationData";
    public static final String MESSAGE_EXPIRY_INTERVAL = PREFIX + "messageExpiryInterval";
    public static final String USER_PROPERTIES = PREFIX + "userProperties";

    /** Id of the listener that received the message. */
    public static final String LISTENER_ID = PREFIX + "listenerId";

    private MqttHeaders() {
    }
}
