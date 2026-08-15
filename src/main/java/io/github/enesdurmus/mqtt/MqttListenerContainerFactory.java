package io.github.enesdurmus.mqtt;

/**
 * Creates a {@link MqttListenerContainer} for an endpoint. Declare a bean of this type to
 * replace the container implementation or its defaults.
 */
@FunctionalInterface
public interface MqttListenerContainerFactory {

    MqttListenerContainer createContainer(MqttListenerEndpoint endpoint);
}
