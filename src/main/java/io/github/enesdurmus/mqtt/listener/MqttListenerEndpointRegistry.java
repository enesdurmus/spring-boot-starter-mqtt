package io.github.enesdurmus.mqtt.listener;

import io.github.enesdurmus.mqtt.annotation.MqttListener;
import io.github.enesdurmus.mqtt.core.MqttPhases;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry of every {@link MqttListenerContainer} in the application, whether declared with
 * {@link MqttListener} or registered through {@link MqttListenerConfigurer}.
 *
 * <p>Containers can be started and stopped by id at runtime, which is the supported way to pause
 * a listener without tearing down the broker connection:
 * <pre>{@code
 * registry.getListenerContainer("device-events").stop();
 * }</pre>
 */
public class MqttListenerEndpointRegistry implements SmartLifecycle, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MqttListenerEndpointRegistry.class);

    private final Map<String, MqttListenerContainer> containers = new LinkedHashMap<>();
    private final Object monitor = new Object();
    private volatile boolean running;

    /**
     * @throws IllegalStateException if another endpoint is already registered under the same id
     */
    public void registerEndpoint(MqttListenerEndpoint endpoint, MqttListenerContainerFactory factory) {
        Assert.notNull(endpoint, "endpoint must not be null");
        Assert.hasText(endpoint.getId(), "endpoint id must not be empty");
        Assert.notNull(factory, "factory must not be null");

        MqttListenerContainer container = factory.createContainer(endpoint);
        synchronized (monitor) {
            MqttListenerContainer existing = containers.putIfAbsent(endpoint.getId(), container);
            if (existing != null) {
                throw new IllegalStateException("Another MQTT listener is already registered with id ["
                        + endpoint.getId() + "]. Give one of them an explicit, unique id.");
            }
            if (running && container.isAutoStartup()) {
                container.start();
            }
        }
    }

    @Nullable
    public MqttListenerContainer getListenerContainer(String id) {
        synchronized (monitor) {
            return containers.get(id);
        }
    }

    public Optional<MqttListenerContainer> findListenerContainer(String id) {
        return Optional.ofNullable(getListenerContainer(id));
    }

    public Set<String> getListenerContainerIds() {
        synchronized (monitor) {
            return Set.copyOf(containers.keySet());
        }
    }

    public Collection<MqttListenerContainer> getListenerContainers() {
        synchronized (monitor) {
            return Collections.unmodifiableCollection(new LinkedHashMap<>(containers).values());
        }
    }

    @Override
    public void start() {
        synchronized (monitor) {
            if (running) {
                return;
            }
            containers.values().stream()
                    .filter(MqttListenerContainer::isAutoStartup)
                    .forEach(this::startQuietly);
            running = true;
        }
    }

    @Override
    public void stop() {
        synchronized (monitor) {
            if (!running) {
                return;
            }
            containers.values().forEach(this::stopQuietly);
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return MqttPhases.SUBSCRIPTIONS;
    }

    @Override
    public void destroy() {
        stop();
        synchronized (monitor) {
            containers.clear();
        }
    }

    private void startQuietly(MqttListenerContainer container) {
        try {
            container.start();
        } catch (RuntimeException e) {
            log.error("Failed to start MQTT listener [{}]", container.getListenerId(), e);
            throw e;
        }
    }

    private void stopQuietly(MqttListenerContainer container) {
        try {
            container.stop();
        } catch (RuntimeException e) {
            log.warn("Failed to stop MQTT listener [{}]: {}", container.getListenerId(), e.getMessage());
        }
    }
}
