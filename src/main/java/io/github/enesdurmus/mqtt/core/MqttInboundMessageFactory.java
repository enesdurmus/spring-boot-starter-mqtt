package io.github.enesdurmus.mqtt.core;

import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MqttInboundMessageFactory {

    private MqttInboundMessageFactory() {
    }

    static Message<byte[]> create(String topic, org.eclipse.paho.mqttv5.common.MqttMessage source) {
        byte[] payload = source.getPayload();
        MessageBuilder<byte[]> builder = MessageBuilder.withPayload(payload == null ? new byte[0] : payload)
                .setHeader(MqttHeaders.RECEIVED_TOPIC, topic)
                .setHeader(MqttHeaders.RECEIVED_QOS, source.getQos())
                .setHeader(MqttHeaders.RECEIVED_RETAINED, source.isRetained())
                .setHeader(MqttHeaders.DUPLICATE, source.isDuplicate())
                .setHeader(MqttHeaders.ID, source.getId());

        org.eclipse.paho.mqttv5.common.packet.MqttProperties props = source.getProperties();
        if (props != null) {
            setIfPresent(builder, MqttHeaders.CONTENT_TYPE, props.getContentType());
            setIfPresent(builder, MqttHeaders.RESPONSE_TOPIC, props.getResponseTopic());
            setIfPresent(builder, MqttHeaders.CORRELATION_DATA, props.getCorrelationData());
            setIfPresent(builder, MqttHeaders.MESSAGE_EXPIRY_INTERVAL, props.getMessageExpiryInterval());
            builder.setHeader(MqttHeaders.USER_PROPERTIES, toMap(props.getUserProperties()));
        } else {
            builder.setHeader(MqttHeaders.USER_PROPERTIES, Map.of());
        }
        return builder.build();
    }

    private static void setIfPresent(MessageBuilder<byte[]> builder, String name, Object value) {
        if (value != null) {
            builder.setHeader(name, value);
        }
    }

    private static Map<String, String> toMap(List<UserProperty> properties) {
        if (properties == null || properties.isEmpty()) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>(properties.size());
        for (UserProperty property : properties) {
            map.put(property.getKey(), property.getValue());
        }
        return Map.copyOf(map);
    }
}
