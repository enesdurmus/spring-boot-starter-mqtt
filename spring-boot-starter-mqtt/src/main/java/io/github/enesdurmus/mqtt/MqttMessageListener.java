package io.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class MqttMessageListener implements IMqttMessageListener {

    private final MqttListenerEndpoint endpoint;
    private final ThreadPoolTaskExecutor executor;

    MqttMessageListener(MqttListenerEndpoint endpoint, ThreadPoolTaskExecutor executor) {
        this.endpoint = endpoint;
        this.executor = executor;
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());

        executor.execute(() -> {
            try {
                endpoint.invoke(payload, message);
            } catch (Exception e) {
                handleException(topic, payload, e);
            }
        });
    }

    protected void handleException(String topic, String payload, Exception e) {
        throw new IllegalStateException("Failed to process message from topic: " + topic, e);
    }

    public MqttListenerEndpoint getEndpoint() {
        return endpoint;
    }
}
