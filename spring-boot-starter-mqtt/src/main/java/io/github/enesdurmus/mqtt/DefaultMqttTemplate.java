package io.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;

class DefaultMqttTemplate implements MqttTemplate {

    private final MqttClient client;

    DefaultMqttTemplate(MqttClient client) {
        this.client = client;
    }

    public void publish(String topic, String payload) throws MqttException {
        client.publish(topic, payload.getBytes(), 0, false);
    }

    public void publish(String topic, String payload, int qos, boolean retained) throws MqttException {
        client.publish(topic, payload.getBytes(), qos, retained);
    }
}
