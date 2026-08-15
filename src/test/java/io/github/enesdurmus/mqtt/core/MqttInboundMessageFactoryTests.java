package io.github.enesdurmus.mqtt.core;


import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MqttInboundMessageFactoryTests {

    @Test
    void mapsCoreHeaders() {
        org.eclipse.paho.mqttv5.common.MqttMessage source =
                new org.eclipse.paho.mqttv5.common.MqttMessage("sıcaklık".getBytes(StandardCharsets.UTF_8));
        source.setQos(2);
        source.setRetained(true);
        source.setId(42);

        Message<byte[]> message = MqttInboundMessageFactory.create("sensor/tr/temp", source);
        MqttMessageHeaderAccessor headers = MqttMessageHeaderAccessor.wrap(message);

        assertThat(headers.getReceivedTopic()).isEqualTo("sensor/tr/temp");
        assertThat(headers.getQos()).isEqualTo(2);
        assertThat(headers.isRetained()).isTrue();
        assertThat(headers.getMessageId()).isEqualTo(42);
        assertThat(new String(message.getPayload(), StandardCharsets.UTF_8)).isEqualTo("sıcaklık");
    }

    @Test
    void mapsMqtt5Properties() {
        MqttProperties properties = new MqttProperties();
        properties.setContentType("application/json");
        properties.setResponseTopic("reply/1");
        properties.setCorrelationData(new byte[]{1, 2});
        properties.setMessageExpiryInterval(60L);
        properties.setUserProperties(List.of(new UserProperty("tenant", "acme")));

        org.eclipse.paho.mqttv5.common.MqttMessage source = new org.eclipse.paho.mqttv5.common.MqttMessage();
        source.setProperties(properties);

        MqttMessageHeaderAccessor headers =
                MqttMessageHeaderAccessor.wrap(MqttInboundMessageFactory.create("t", source));

        assertThat(headers.getMqttContentType()).isEqualTo("application/json");
        assertThat(headers.getResponseTopic()).isEqualTo("reply/1");
        assertThat(headers.getCorrelationData()).containsExactly(1, 2);
        assertThat(headers.getMessageExpiryInterval()).isEqualTo(60L);
        assertThat(headers.getUserProperties()).containsEntry("tenant", "acme");
    }

    @Test
    void toleratesNullPayloadAndProperties() {
        org.eclipse.paho.mqttv5.common.MqttMessage source = new org.eclipse.paho.mqttv5.common.MqttMessage();

        Message<byte[]> message = MqttInboundMessageFactory.create("t", source);

        assertThat(message.getPayload()).isEmpty();
        assertThat(MqttMessageHeaderAccessor.wrap(message).getUserProperties()).isEmpty();
    }
}
