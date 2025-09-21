package io.github.enesdurmus.mqtt;

import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;

class DefaultMqttTemplate implements MqttTemplate {

    private final MqttPahoMessageHandler handler;

    DefaultMqttTemplate(MqttPahoMessageHandler handler) {
        this.handler = handler;
    }

    public void publish(String topic, String payload) {
        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader("mqtt_topic", topic)
                .build();
        handler.handleMessage(message);
    }

    public void publish(String topic, String payload, int qos) {
        Message<String> message = MessageBuilder.withPayload(payload)
                .setHeader("mqtt_topic", topic)
                .setHeader("mqtt_qos", qos)
                .build();
        handler.handleMessage(message);
    }
}
