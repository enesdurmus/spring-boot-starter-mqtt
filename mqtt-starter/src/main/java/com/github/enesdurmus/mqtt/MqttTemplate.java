package com.github.enesdurmus.mqtt;

public interface MqttTemplate {
    void publish(String topic, String payload);

    void publish(String topic, String payload, int qos);
}
