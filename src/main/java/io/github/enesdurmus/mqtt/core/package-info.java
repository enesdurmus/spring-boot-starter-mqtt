/**
 * Core publishing and connection API. {@link io.github.enesdurmus.mqtt.core.MqttOperations} and
 * {@link io.github.enesdurmus.mqtt.core.MqttTemplate} for sending,
 * {@link io.github.enesdurmus.mqtt.core.MqttConnection} for the broker connection, and
 * {@link io.github.enesdurmus.mqtt.core.MqttHeaders} /
 * {@link io.github.enesdurmus.mqtt.core.MqttMessageHeaderAccessor} for message metadata.
 *
 * <p>This is the only package that touches Paho types directly. It does not depend on
 * {@code autoconfigure}: what the connection needs from configuration is passed in as
 * {@link io.github.enesdurmus.mqtt.core.MqttConnectionSettings}.
 *
 * <p>Depend on the interfaces — {@link io.github.enesdurmus.mqtt.core.MqttOperations},
 * {@link io.github.enesdurmus.mqtt.core.MqttSubscriptionManager} — rather than on the
 * {@code Default}-prefixed classes implementing them. Those are infrastructure: public only
 * because the auto-configuration creates them from another package, and replaceable by declaring
 * a bean of the corresponding interface.
 */
@NullMarked
package io.github.enesdurmus.mqtt.core;

import org.jspecify.annotations.NullMarked;
