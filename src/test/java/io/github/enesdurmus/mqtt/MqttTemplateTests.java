package io.github.enesdurmus.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MqttTemplateTests {

    private MqttConnection connection;
    private MqttTemplate template;

    @BeforeEach
    void setUp() {
        connection = mock(MqttConnection.class);
        template = new MqttTemplate(connection, true);
        template.setMessageConverter(new CompositeMessageConverter(List.of(
                new ByteArrayMessageConverter(),
                new StringMessageConverter(StandardCharsets.UTF_8),
                JacksonMqttMessageConverterFactory.create(new ObjectMapper()))));
    }

    private org.eclipse.paho.mqttv5.common.MqttMessage capture(String topic) {
        ArgumentCaptor<org.eclipse.paho.mqttv5.common.MqttMessage> captor =
                ArgumentCaptor.forClass(org.eclipse.paho.mqttv5.common.MqttMessage.class);
        verify(connection).publish(eq(topic), captor.capture(), anyBoolean());
        return captor.getValue();
    }

    @Test
    void stringPayloadIsPublishedVerbatimWithoutJsonQuoting() {
        template.publish("t", "merhaba");

        assertThat(capture("t").getPayload()).isEqualTo("merhaba".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void stringPayloadUsesUtf8() {
        template.publish("t", "sıcaklık ölçümü");

        assertThat(new String(capture("t").getPayload(), StandardCharsets.UTF_8)).isEqualTo("sıcaklık ölçümü");
    }

    @Test
    void byteArrayPayloadIsPublishedVerbatim() {
        template.publish("t", new byte[]{1, 2, 3});

        assertThat(capture("t").getPayload()).containsExactly(1, 2, 3);
    }

    @Test
    void objectPayloadIsSerializedAsJson() {
        template.publish("t", new Reading("kitchen", 21.5));

        assertThat(new String(capture("t").getPayload(), StandardCharsets.UTF_8))
                .isEqualTo("{\"sensor\":\"kitchen\",\"value\":21.5}");
    }

    @Test
    void qosAndRetainedFlagsArePropagated() {
        template.publish("t", "x", 2, true);

        org.eclipse.paho.mqttv5.common.MqttMessage message = capture("t");
        assertThat(message.getQos()).isEqualTo(2);
        assertThat(message.isRetained()).isTrue();
    }

    @Test
    void retainPublishesWithTheRetainFlagSet() {
        template.retain("state/door", "open", 1);

        org.eclipse.paho.mqttv5.common.MqttMessage message = capture("state/door");
        assertThat(message.isRetained()).isTrue();
        assertThat(message.getQos()).isEqualTo(1);
        assertThat(message.getPayload()).isEqualTo("open".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void clearRetainedPublishesAnEmptyRetainedMessage() {
        template.clearRetained("state/door", 1);

        org.eclipse.paho.mqttv5.common.MqttMessage message = capture("state/door");
        assertThat(message.isRetained()).isTrue();
        assertThat(message.getPayload()).isEmpty();
        assertThat(message.getQos()).isEqualTo(1);
    }

    @Test
    void mqtt5PropertyHeadersArePropagated() {
        template.publish("t", "x", 0, false, Map.of(
                MqttHeaders.CONTENT_TYPE, "text/plain",
                MqttHeaders.RESPONSE_TOPIC, "reply/1",
                MqttHeaders.MESSAGE_EXPIRY_INTERVAL, 30L,
                MqttHeaders.USER_PROPERTIES, Map.of("tenant", "acme")));

        org.eclipse.paho.mqttv5.common.packet.MqttProperties properties = capture("t").getProperties();
        assertThat(properties.getContentType()).isEqualTo("text/plain");
        assertThat(properties.getResponseTopic()).isEqualTo("reply/1");
        assertThat(properties.getMessageExpiryInterval()).isEqualTo(30L);
        assertThat(properties.getUserProperties()).extracting(UserProperty::getKey).containsExactly("tenant");
    }

    @Test
    void rejectsInvalidQos() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> template.publish("t", "x", 3));
    }

    record Reading(String sensor, double value) {
    }
}
