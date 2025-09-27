package io.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.MqttException;

public interface MqttTemplate {
    void publish(String topic, String payload) throws MqttException;

    void publish(String topic, String payload, int qos, boolean retained) throws MqttException;
}
