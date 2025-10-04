package io.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.MqttException;

public interface MqttTemplate {
    void publish(String topic, Object payload) throws MqttException;

    void publish(String topic, Object payload, int qos, boolean retained) throws MqttException;
}
