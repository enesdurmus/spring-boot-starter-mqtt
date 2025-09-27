package io.github.enesdurmus.mqtt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MqttListenerRegistry {

    private final Map<String, List<MqttListenerEndpoint>> registry = new HashMap<>();

    void register(MqttListenerEndpoint endpoint) {
        for (String topic : endpoint.getTopics()) {
            registry.computeIfAbsent(topic, k -> new ArrayList<>()).add(endpoint);
        }
    }

    public List<MqttListenerEndpoint> getAllEndpoints() {
        return registry.values().stream()
                .flatMap(List::stream)
                .toList();
    }
}