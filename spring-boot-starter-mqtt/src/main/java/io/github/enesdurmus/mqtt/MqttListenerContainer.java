package io.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class MqttListenerContainer {

    private final MqttClient mqttClient;
    private final MqttListenerRegistry registry;
    private final ThreadPoolTaskExecutor executor;

    MqttListenerContainer(MqttClient mqttClient,
                          MqttListenerRegistry registry,
                          ThreadPoolTaskExecutor executor) {
        this.mqttClient = mqttClient;
        this.registry = registry;
        this.executor = executor;
    }

    public void start() {
        for (MqttListenerEndpoint endpoint : registry.getAllEndpoints()) {
            try {
                for (String topic : endpoint.getTopics()) {
                    MqttMessageListener messageListener = new MqttMessageListener(endpoint, executor);
                    mqttClient.subscribe(topic, endpoint.getQos(), messageListener);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
