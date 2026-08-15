package io.github.enesdurmus.mqtt;

import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.core.AbstractMessageSendingTemplate;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link MqttOperations} implementation.
 *
 * <p>Payload conversion is delegated to the configured
 * {@link org.springframework.messaging.converter.MessageConverter}, so {@code String} and
 * {@code byte[]} go out verbatim while other types are serialized as JSON. Outbound
 * {@link MqttHeaders} — {@code mqtt_qos}, {@code mqtt_retained}, {@code mqtt_contentType},
 * {@code mqtt_responseTopic}, {@code mqtt_correlationData}, {@code mqtt_messageExpiryInterval}
 * and {@code mqtt_userProperties} — are honoured on any message sent through this template.
 */
public class MqttTemplate extends AbstractMessageSendingTemplate<String> implements MqttOperations {

    private final MqttConnection connection;
    private final boolean awaitDelivery;

    MqttTemplate(MqttConnection connection, boolean awaitDelivery) {
        this.connection = connection;
        this.awaitDelivery = awaitDelivery;
    }

    @Override
    public void publish(String topic, Object payload) {
        publish(topic, payload, 0, false);
    }

    @Override
    public void publish(String topic, Object payload, int qos) {
        publish(topic, payload, qos, false);
    }

    @Override
    public void publish(String topic, Object payload, int qos, boolean retained) {
        publish(topic, payload, qos, retained, Map.of());
    }

    @Override
    public void publish(String topic, Object payload, int qos, boolean retained, Map<String, Object> headers) {
        Map<String, Object> merged = new HashMap<>(headers);
        merged.put(MqttHeaders.QOS, Qos.validate(qos));
        merged.put(MqttHeaders.RETAINED, retained);
        convertAndSend(topic, payload, merged);
    }

    @Override
    public void retain(String topic, Object payload) {
        publish(topic, payload, 0, true);
    }

    @Override
    public void retain(String topic, Object payload, int qos) {
        publish(topic, payload, qos, true);
    }

    @Override
    public void clearRetained(String topic) {
        clearRetained(topic, 0);
    }

    @Override
    public void clearRetained(String topic, int qos) {
        Assert.hasText(topic, "topic must not be empty");
        org.eclipse.paho.mqttv5.common.MqttMessage message = new org.eclipse.paho.mqttv5.common.MqttMessage();
        message.setPayload(new byte[0]);
        message.setQos(Qos.validate(qos));
        message.setRetained(true);
        connection.publish(topic, message, awaitDelivery);
    }

    @Override
    protected void doSend(String topic, Message<?> message) {
        Assert.hasText(topic, "topic must not be empty");
        connection.publish(topic, toPahoMessage(message), awaitDelivery);
    }

    private org.eclipse.paho.mqttv5.common.MqttMessage toPahoMessage(Message<?> message) {
        MqttMessageHeaderAccessor headers = MqttMessageHeaderAccessor.wrap(message);

        org.eclipse.paho.mqttv5.common.MqttMessage target = new org.eclipse.paho.mqttv5.common.MqttMessage();
        target.setPayload(toBytes(message.getPayload()));
        target.setQos(Qos.validate(intHeader(message, MqttHeaders.QOS)));
        target.setRetained(Boolean.TRUE.equals(message.getHeaders().get(MqttHeaders.RETAINED, Boolean.class)));

        org.eclipse.paho.mqttv5.common.packet.MqttProperties properties =
                new org.eclipse.paho.mqttv5.common.packet.MqttProperties();
        properties.setContentType(headers.getMqttContentType());
        properties.setResponseTopic(headers.getResponseTopic());
        properties.setCorrelationData(headers.getCorrelationData());
        properties.setMessageExpiryInterval(headers.getMessageExpiryInterval());
        properties.setUserProperties(toUserProperties(headers.getUserProperties()));
        target.setProperties(properties);
        return target;
    }

    private byte[] toBytes(Object payload) {
        if (payload instanceof byte[] bytes) {
            return bytes;
        }
        if (payload instanceof String text) {
            return text.getBytes(StandardCharsets.UTF_8);
        }
        throw new MqttConversionException("Message converter produced an unsupported payload type: "
                + payload.getClass().getName() + ". Expected byte[] or String.");
    }

    private int intHeader(Message<?> message, String name) {
        Object value = message.getHeaders().get(name);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private List<UserProperty> toUserProperties(Map<String, String> source) {
        List<UserProperty> properties = new ArrayList<>(source.size());
        source.forEach((key, value) -> properties.add(new UserProperty(key, value)));
        return properties;
    }
}
