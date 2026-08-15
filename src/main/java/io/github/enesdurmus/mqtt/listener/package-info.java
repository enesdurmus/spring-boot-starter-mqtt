/**
 * Listener container infrastructure: endpoints describe what to subscribe to, containers own the
 * subscriptions, and {@link io.github.enesdurmus.mqtt.listener.MqttListenerEndpointRegistry}
 * starts and stops them by id.
 *
 * <p>Register listeners programmatically by implementing
 * {@link io.github.enesdurmus.mqtt.listener.MqttListenerConfigurer}.
 */
@NullMarked
package io.github.enesdurmus.mqtt.listener;

import org.jspecify.annotations.NullMarked;
