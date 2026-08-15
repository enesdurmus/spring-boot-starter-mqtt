package io.github.enesdurmus.mqtt;

import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Multiplexes many listeners onto one broker subscription per topic filter.
 *
 * <p>MQTT allows a client a single set of options per topic filter, and Paho keeps one callback
 * per filter. Registering a second listener for a filter directly against the client would
 * therefore silently displace the first. This class keeps one broker subscription per filter,
 * fans messages out to every registered listener, merges their {@link MqttSubscriptionOptions},
 * and only unsubscribes once the last listener for a filter is gone. Subscriptions are restored
 * after a reconnect.
 */
class MqttSubscriptionManager implements MqttConnectionListener {

    private static final Logger log = LoggerFactory.getLogger(MqttSubscriptionManager.class);

    private final MqttConnection connection;
    private final Map<String, TopicSubscription> subscriptions = new HashMap<>();
    private final Object monitor = new Object();

    MqttSubscriptionManager(MqttConnection connection) {
        this.connection = connection;
        this.connection.addListener(this);
    }

    SubscriptionHandle subscribe(String topicFilter, MqttSubscriptionOptions options, MessageHandler handler) {
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

            connection.subscribe(subscription, this::dispatch);
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
            MqttSubscriptionManager.this.cancel(this);
        }
    }

    interface SubscriptionHandle {
        void cancel();
    }
}
