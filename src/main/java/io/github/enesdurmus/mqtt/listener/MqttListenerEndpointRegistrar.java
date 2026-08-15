package io.github.enesdurmus.mqtt.listener;

import io.github.enesdurmus.mqtt.annotation.MqttListener;

import org.springframework.beans.factory.InitializingBean;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects endpoints during startup and flushes them into the {@link MqttListenerEndpointRegistry}.
 * Handed to every {@link MqttListenerConfigurer}.
 */
public class MqttListenerEndpointRegistrar implements InitializingBean {

    private final List<Registration> registrations = new ArrayList<>();

    private MqttListenerEndpointRegistry registry;
    private MqttListenerContainerFactory containerFactory;
    private MessageHandlerMethodFactory messageHandlerMethodFactory;

    /** Registers an endpoint with the default container factory. */
    public void registerEndpoint(MqttListenerEndpoint endpoint) {
        registerEndpoint(endpoint, null);
    }

    public void registerEndpoint(MqttListenerEndpoint endpoint, @Nullable MqttListenerContainerFactory factory) {
        Assert.notNull(endpoint, "endpoint must not be null");
        Assert.hasText(endpoint.getId(), "endpoint id must not be empty");
        registrations.add(new Registration(endpoint, factory));
    }

    public void setRegistry(MqttListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    public MqttListenerEndpointRegistry getRegistry() {
        return registry;
    }

    public void setContainerFactory(MqttListenerContainerFactory containerFactory) {
        this.containerFactory = containerFactory;
    }

    public MqttListenerContainerFactory getContainerFactory() {
        return containerFactory;
    }

    /** Replaces the factory used to bind {@link MqttListener} method arguments. */
    public void setMessageHandlerMethodFactory(MessageHandlerMethodFactory messageHandlerMethodFactory) {
        this.messageHandlerMethodFactory = messageHandlerMethodFactory;
    }

    @Nullable
    public MessageHandlerMethodFactory getMessageHandlerMethodFactory() {
        return messageHandlerMethodFactory;
    }

    @Override
    public void afterPropertiesSet() {
        Assert.state(registry != null, "registry must be set");
        Assert.state(containerFactory != null, "containerFactory must be set");
        for (Registration registration : registrations) {
            registry.registerEndpoint(registration.endpoint,
                    registration.factory != null ? registration.factory : containerFactory);
        }
        registrations.clear();
    }

    private record Registration(MqttListenerEndpoint endpoint, @Nullable MqttListenerContainerFactory factory) {
    }
}
