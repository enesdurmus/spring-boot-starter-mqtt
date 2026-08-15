package io.github.enesdurmus.mqtt.core;

import io.github.enesdurmus.mqtt.MqttSubscriptionException;

import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.util.MqttTopicValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Multiplexes many handlers onto one broker subscription per topic filter, merging their
 * {@link MqttSubscriptionOptions} and unsubscribing only once the last handler for a filter is
 * gone. Subscriptions are re-applied after a reconnect, since a broker may have dropped the
 * session state in the meantime.
 *
 * <p>The auto-configuration contributes one of these; declare an {@link MqttSubscriptionManager}
 * bean to replace it.
 */
public final class DefaultMqttSubscriptionManager implements MqttSubscriptionManager, MqttConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(DefaultMqttSubscriptionManager.class);

    private final MqttConnection connection;
    private final Map<String, TopicSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Object monitor = new Object();

    private DefaultMqttSubscriptionManager(MqttConnection connection) {
        this.connection = connection;
    }

    /**
     * Creates a manager, attaches it to {@code connection} as the receiver of inbound messages and
     * as the listener that restores subscriptions after a reconnect. A factory method rather than
     * a constructor, so the connection never sees a half-constructed listener.
     */
    public static DefaultMqttSubscriptionManager create(MqttConnection connection) {
        Assert.notNull(connection, "connection must not be null");
        DefaultMqttSubscriptionManager manager = new DefaultMqttSubscriptionManager(connection);
        connection.addListener(manager);
        connection.setMessageListener(manager::route);
        return manager;
    }

    /**
     * Hands a message to every filter it matches. A message may match more than one filter, and
     * each of them gets it.
     */
    private void route(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) {
        boolean delivered = false;
        for (Map.Entry<String, TopicSubscription> entry : subscriptions.entrySet()) {
            if (MqttTopicValidator.isMatched(entry.getKey(), topic)) {
                delivered = true;
                entry.getValue().dispatch(topic, message);
            }
        }
        if (!delivered) {
            log.debug("Received a message on [{}] matching no subscription", topic);
        }
    }

    @Override
    public SubscriptionHandle subscribe(String topicFilter, MqttSubscriptionOptions options, MessageHandler handler) {
        Assert.hasText(topicFilter, "topicFilter must not be empty");
        Assert.notNull(options, "options must not be null");
        Assert.notNull(handler, "handler must not be null");

        Registration registration = new Registration(topicFilter, options, handler);
        synchronized (monitor) {
            TopicSubscription subscription = subscriptions.computeIfAbsent(topicFilter, TopicSubscription::new);
            subscription.add(registration);
            subscription.applyTo(connection);
        }
        return registration;
    }

    @Override
    public void onConnected(boolean reconnect) {
        if (!reconnect) {
            return;
        }
        List<TopicSubscription> current;
        synchronized (monitor) {
            current = new ArrayList<>(subscriptions.values());
        }
        for (TopicSubscription subscription : current) {
            try {
                subscription.forceApplyTo(connection);
            } catch (RuntimeException e) {
                log.error("Failed to restore subscription [{}] after reconnect", subscription.topicFilter, e);
            }
        }
        log.info("Restored {} MQTT subscription(s) after reconnect", current.size());
    }

    private void cancel(Registration registration) {
        synchronized (monitor) {
            TopicSubscription subscription = subscriptions.get(registration.topicFilter);
            if (subscription == null) {
                return;
            }
            if (subscription.remove(registration)) {
                subscriptions.remove(registration.topicFilter);
                try {
                    connection.unsubscribe(registration.topicFilter);
                } catch (MqttSubscriptionException e) {
                    log.warn("Failed to unsubscribe from [{}]: {}", registration.topicFilter, e.getMessage());
                }
            } else {
                subscription.applyTo(connection);
            }
        }
    }

    private static final class TopicSubscription {

        private final String topicFilter;
        private final List<Registration> registrations = new CopyOnWriteArrayList<>();
        private volatile MqttSubscriptionOptions applied;

        private TopicSubscription(String topicFilter) {
            this.topicFilter = topicFilter;
        }

        private void add(Registration registration) {
            registrations.add(registration);
        }

        /** @return {@code true} when the last registration was removed */
        private boolean remove(Registration registration) {
            registrations.remove(registration);
            return registrations.isEmpty();
        }

        private void applyTo(MqttConnection connection) {
            MqttSubscriptionOptions merged = merge();
            if (merged.equals(applied)) {
                return;
            }
            doApply(connection, merged);
        }

        private void forceApplyTo(MqttConnection connection) {
            doApply(connection, merge());
        }

        private void doApply(MqttConnection connection, MqttSubscriptionOptions merged) {
            MqttSubscription subscription = new MqttSubscription(topicFilter, merged.getQos());
            subscription.setRetainHandling(merged.getRetainHandling().value());
            subscription.setRetainAsPublished(merged.isRetainAsPublished());
            subscription.setNoLocal(merged.isNoLocal());

            connection.subscribe(subscription);
            applied = merged;
            log.debug("Subscribed to [{}] with {} for {} listener(s)", topicFilter, merged, registrations.size());
        }

        private MqttSubscriptionOptions merge() {
            MqttSubscriptionOptions merged = registrations.get(0).options;
            for (int i = 1; i < registrations.size(); i++) {
                merged = merged.mergeWith(registrations.get(i).options);
            }
            return merged;
        }

        private void dispatch(String topic, org.eclipse.paho.mqttv5.common.MqttMessage source) {
            Message<byte[]> message = MqttInboundMessageFactory.create(topic, source);
            for (Registration registration : registrations) {
                try {
                    registration.handler.handleMessage(message);
                } catch (Exception e) {
                    log.error("Listener for [{}] rejected message from [{}]", topicFilter, topic, e);
                }
            }
        }
    }

    private final class Registration implements SubscriptionHandle {

        private final String topicFilter;
        private final MqttSubscriptionOptions options;
        private final MessageHandler handler;
        private volatile boolean cancelled;

        private Registration(String topicFilter, MqttSubscriptionOptions options, MessageHandler handler) {
            this.topicFilter = topicFilter;
            this.options = options;
            this.handler = handler;
        }

        @Override
        public void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            DefaultMqttSubscriptionManager.this.cancel(this);
        }
    }
}
