package io.github.enesdurmus.mqtt.listener;

import io.github.enesdurmus.mqtt.annotation.MqttListener;

/**
 * Implemented by configuration classes that register MQTT listeners programmatically, or that
 * customise how {@link MqttListener}-annotated methods are turned into endpoints.
 *
 * <pre>{@code
 * @Configuration
 * class DeviceListeners implements MqttListenerConfigurer {
 *
 *     @Override
 *     public void configureMqttListeners(MqttListenerEndpointRegistrar registrar) {
 *         devices.forEach(device -> registrar.registerEndpoint(
 *                 SimpleMqttListenerEndpoint.builder("devices/" + device.id() + "/events")
 *                         .id("device-" + device.id())
 *                         .qos(1)
 *                         .messageHandler(message -> handle(device, message))
 *                         .build()));
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface MqttListenerConfigurer {

    void configureMqttListeners(MqttListenerEndpointRegistrar registrar);
}
