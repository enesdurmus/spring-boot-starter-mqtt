package io.github.enesdurmus.mqtt.autoconfigure;

import io.github.enesdurmus.mqtt.core.MqttConnection;
import io.github.enesdurmus.mqtt.listener.MqttListenerContainer;
import io.github.enesdurmus.mqtt.listener.MqttListenerEndpointRegistry;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reports the broker connection and the state of every registered listener.
 */
public class MqttHealthIndicator implements HealthIndicator {

    private final MqttConnection connection;
    private final MqttListenerEndpointRegistry registry;

    MqttHealthIndicator(MqttConnection connection, MqttListenerEndpointRegistry registry) {
        this.connection = connection;
        this.registry = registry;
    }

    @Override
    public Health health() {
        boolean connected = connection.isConnected();
        Map<String, Object> listeners = registry.getListenerContainers().stream()
                .collect(Collectors.toMap(
                        MqttListenerContainer::getListenerId,
                        container -> Map.of(
                                "running", container.isRunning(),
                                "topics", container.getTopics())));

        Health.Builder builder = connected ? Health.up() : Health.down();
        return builder
                .withDetail("clientId", connection.getClientId())
                .withDetail("connected", connected)
                .withDetail("listeners", listeners)
                .build();
    }
}
