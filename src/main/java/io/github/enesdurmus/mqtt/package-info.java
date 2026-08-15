/**
 * Exceptions raised by the MQTT starter. Everything thrown across the public API is an unchecked
 * {@link io.github.enesdurmus.mqtt.MqttClientException}; Paho's checked {@code MqttException}
 * never leaks out.
 *
 * <p>The functional API lives in the sub-packages: {@code annotation} for
 * {@link io.github.enesdurmus.mqtt.annotation.MqttListener @MqttListener}, {@code core} for
 * publishing and connection management, {@code listener} for the listener container
 * infrastructure, {@code support} for extension points, and {@code autoconfigure} for the
 * Spring Boot auto-configuration.
 */
@NullMarked
package io.github.enesdurmus.mqtt;


import org.jspecify.annotations.NullMarked;
