package io.github.enesdurmus.mqtt;

import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;

/**
 * Callback for tuning the Paho connection options beyond what {@link MqttProperties} exposes —
 * TLS socket factories, custom WebSocket headers, authentication data, and so on.
 *
 * <p>All beans of this type are applied in {@link org.springframework.core.Ordered} order.
 */
@FunctionalInterface
public interface MqttConnectionOptionsCustomizer {

    void customize(MqttConnectionOptions options);
}
