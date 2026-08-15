package io.github.enesdurmus.mqtt.core;

import org.springframework.messaging.MessageHandler;

/**
 * Registers message handlers against topic filters.
 *
 * <p>MQTT allows a client a single set of options per topic filter, and Paho keeps a single
 * callback per filter, so subscribing twice for the same filter directly against the client would
 * silently displace the first subscriber. Implementations hide that constraint: any number of
 * handlers may be registered for the same filter, and each one receives every matching message.
 *
 * <p>Registering is independent of the connection state — a handler registered while the client
 * is down starts receiving messages once it is up again.
 *
 * @see DefaultMqttSubscriptionManager
 */
public interface MqttSubscriptionManager {

    /**
     * Registers {@code handler} for {@code topicFilter}, subscribing at the broker if needed.
     *
     * @param options the options this handler needs; they are combined with those of the other
     *                handlers on the same filter so that no handler receives less than it asked for
     * @return a handle that deregisters this handler, and only this handler
     * @throws io.github.enesdurmus.mqtt.MqttSubscriptionException if the broker rejects the
     *                                                             subscription
     */
    SubscriptionHandle subscribe(String topicFilter, MqttSubscriptionOptions options, MessageHandler handler);

    /**
     * Deregisters a single handler. The broker subscription is dropped once the last handler for
     * the filter is gone. Cancelling twice does nothing the second time.
     */
    interface SubscriptionHandle {

        void cancel();
    }
}
