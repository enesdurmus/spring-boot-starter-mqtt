package io.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultMqttTemplateTest {

    @Mock
    private MqttClient mqttClient;

    @Mock
    private MessageConverter messageConverter;

    private DefaultMqttTemplate sut;

    @BeforeEach
    void setUp() {
        sut = new DefaultMqttTemplate(mqttClient, messageConverter);
    }

    @Test
    void publish_withoutTopicAndPayload_callsMqttClientPublish() throws Exception {
        // given
        String topic = "test/topic";
        String payload = "Hello, MQTT!";

        when(messageConverter.write(payload)).thenReturn(payload.getBytes());

        // when
        sut.publish(topic, payload);

        // then
        verify(mqttClient).publish(topic, payload.getBytes(), 0, false);
    }

    @Test
    void publish_withTopicPayloadQosAndRetained_callsMqttClientPublish() throws Exception {
        // given
        String topic = "test/topic";
        String payload = "Hello, MQTT!";
        int qos = 1;
        boolean retained = true;

        when(messageConverter.write(payload)).thenReturn(payload.getBytes());

        // when
        sut.publish(topic, payload, qos, retained);

        // then
        verify(mqttClient).publish(topic, payload.getBytes(), qos, retained);
    }
}