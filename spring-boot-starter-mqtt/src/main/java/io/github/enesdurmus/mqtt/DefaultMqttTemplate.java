package io.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

class DefaultMqttTemplate implements MqttTemplate {

    private final MqttClient client;
    private final MessageConverter converter;

    DefaultMqttTemplate(MqttClient client,
                        MessageConverter converter) {
        this.client = client;
        this.converter = converter;
    }

    public void publish(String topic, Object payload) throws MqttException {
        try {
            byte[] convertedPayload = converter.write(payload);
            client.publish(topic, convertedPayload, 0, false);
        } catch (Exception e) {
            throw new MqttException(e);
        }
    }

    public void publish(String topic, Object payload, int qos, boolean retained) throws MqttException {
        try {
            byte[] convertedPayload = converter.write(payload);
            client.publish(topic, convertedPayload, qos, retained);
        } catch (Exception e) {
            throw new MqttException(e);
        }
    }
}
