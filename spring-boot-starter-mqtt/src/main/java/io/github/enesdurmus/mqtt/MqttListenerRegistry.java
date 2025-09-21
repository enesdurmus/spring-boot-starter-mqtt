package io.github.enesdurmus.mqtt;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MqttListenerRegistry {

    private final Map<String, List<ListenerDefinition>> listeners = new ConcurrentHashMap<>();

    public void register(String topic, Method method, Object bean, int qos) {
        listeners.computeIfAbsent(topic, t -> new ArrayList<>())
                .add(new ListenerDefinition(bean, method, qos));
    }

    public Map<String, List<ListenerDefinition>> getListeners() {
        return listeners;
    }

    public record ListenerDefinition(Object bean, Method method, int qos) {
    }
}