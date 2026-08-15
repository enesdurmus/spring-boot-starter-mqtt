package io.github.enesdurmus.mqtt.core;

import io.github.enesdurmus.mqtt.MqttConnectionException;
import io.github.enesdurmus.mqtt.MqttPublishException;
import io.github.enesdurmus.mqtt.MqttSubscriptionException;

import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns a single Paho MQTT 5 client and its lifecycle. This is the only place where Paho types
 * are touched; everything above it works with {@code org.springframework.messaging} types.
 *
 * <p>Connecting happens on {@link #start()} rather than at bean creation, so an unreachable broker
 * does not prevent the application context from starting unless {@code mqtt.fail-fast} is set.
 */
public class MqttConnection implements SmartLifecycle, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MqttConnection.class);

    private final String clientId;
    private final MqttAsyncClient client;
    private final MqttConnectionOptions options;
    private final MqttConnectionSettings settings;
    private final TaskScheduler scheduler;
    private final CopyOnWriteArrayList<MqttConnectionListener> listeners = new CopyOnWriteArrayList<>();

    private volatile @Nullable IMqttMessageListener messageListener;

    private final Object lifecycleMonitor = new Object();
    private volatile boolean running;
    private volatile boolean everConnected;

    public MqttConnection(String clientId,
                          MqttAsyncClient client,
                          MqttConnectionOptions options,
                          MqttConnectionSettings settings,
                          TaskScheduler scheduler) {
        Assert.hasText(clientId, "clientId must not be empty");
        Assert.notNull(client, "client must not be null");
        Assert.notNull(options, "options must not be null");
        Assert.notNull(settings, "settings must not be null");
        Assert.notNull(scheduler, "scheduler must not be null");
        this.clientId = clientId;
        this.client = client;
        this.options = options;
        this.settings = settings;
        this.scheduler = scheduler;
        this.client.setCallback(new CallbackAdapter());
    }

    /** Registering the same listener twice has no effect. */
    public void addListener(MqttConnectionListener listener) {
        Assert.notNull(listener, "listener must not be null");
        listeners.addIfAbsent(listener);
    }

    /** @return {@code true} if the listener was registered */
    public boolean removeListener(MqttConnectionListener listener) {
        return listeners.remove(listener);
    }

    public String getClientId() {
        return clientId;
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (running) {
                return;
            }
            running = true;
        }
        connect(true);
    }

    @Override
    public void stop() {
        synchronized (lifecycleMonitor) {
            if (!running) {
                return;
            }
            running = false;
        }
        try {
            if (client.isConnected()) {
                client.disconnect(settings.getDisconnectTimeout().toMillis())
                        .waitForCompletion(settings.getDisconnectTimeout().toMillis());
            }
            log.info("Disconnected MQTT client [{}]", clientId);
        } catch (MqttException e) {
            log.warn("Graceful disconnect of MQTT client [{}] failed, forcing close: {}", clientId, e.getMessage());
            forceDisconnect();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return MqttPhases.CONNECTION;
    }

    @Override
    public void destroy() {
        stop();
        try {
            client.close(true);
        } catch (MqttException e) {
            log.warn("Failed to close MQTT client [{}]: {}", clientId, e.getMessage());
        }
    }

    /**
     * Sets the single listener every inbound message is handed to, replacing any previous one.
     *
     * <p>Deliberately one listener for the whole connection rather than one per topic filter:
     * Paho keeps per-filter listeners in plain {@code HashMap}s that its callback thread iterates
     * while delivering, so subscribing or unsubscribing under traffic corrupts them and takes the
     * connection down. Which subscriber a message belongs to is decided by
     * {@link MqttSubscriptionManager}, which already knows the filters.
     */
    public void setMessageListener(@Nullable IMqttMessageListener messageListener) {
        this.messageListener = messageListener;
    }

    public IMqttToken subscribe(MqttSubscription subscription) {
        Assert.notNull(subscription, "subscription must not be null");
        try {
            IMqttToken token = client.subscribe(new MqttSubscription[]{subscription}, null, null, null);
            token.waitForCompletion(settings.getActionTimeout().toMillis());
            verifyGranted(subscription, token);
            return token;
        } catch (MqttException e) {
            throw new MqttSubscriptionException(
                    "Failed to subscribe client [" + clientId + "] to [" + subscription.getTopic() + "]", e);
        }
    }

    public void unsubscribe(String topicFilter) {
        try {
            client.unsubscribe(topicFilter).waitForCompletion(settings.getActionTimeout().toMillis());
        } catch (MqttException e) {
            throw new MqttSubscriptionException(
                    "Failed to unsubscribe client [" + clientId + "] from [" + topicFilter + "]", e);
        }
    }

    public void publish(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message, boolean awaitDelivery) {
        try {
            IMqttToken token = client.publish(topic, message);
            if (awaitDelivery) {
                token.waitForCompletion(settings.getActionTimeout().toMillis());
            }
        } catch (MqttException e) {
            throw new MqttPublishException("Failed to publish to topic [" + topic + "]", e);
        }
    }

    private void verifyGranted(MqttSubscription subscription, IMqttToken token) {
        int[] reasonCodes = token.getReasonCodes();
        if (reasonCodes == null || reasonCodes.length == 0) {
            return;
        }
        int granted = reasonCodes[0];
        if (granted >= 0x80) {
            throw new MqttSubscriptionException("Broker rejected subscription to ["
                    + subscription.getTopic() + "] with reason code 0x" + Integer.toHexString(granted));
        }
        if (granted < subscription.getQos()) {
            log.warn("Broker downgraded QoS for [{}] from {} to {}",
                    subscription.getTopic(), subscription.getQos(), granted);
        }
    }

    private void connect(boolean initial) {
        try {
            client.connect(options).waitForCompletion(settings.getConnectionTimeout().toMillis());
        } catch (MqttException e) {
            if (initial && settings.isFailFast()) {
                throw new MqttConnectionException(
                        "Failed to connect MQTT client [" + clientId + "] to " + settings.getUrl(), e);
            }
            log.warn("MQTT client [{}] could not connect to {}: {}. Retrying in {}.",
                    clientId, settings.getUrl(), e.getMessage(), settings.getConnectRetryInterval());
            scheduleReconnect();
        }
    }

    /**
     * Paho's own automatic reconnect only engages after a first successful connect, so the
     * initial handshake is retried here.
     */
    private void scheduleReconnect() {
        if (!running) {
            return;
        }
        Duration interval = settings.getConnectRetryInterval();
        scheduler.schedule(() -> {
            if (running && !client.isConnected()) {
                connect(false);
            }
        }, Instant.now().plus(interval));
    }

    private void forceDisconnect() {
        try {
            client.disconnectForcibly(settings.getDisconnectTimeout().toMillis());
        } catch (MqttException ignored) {
            // discarding the client either way
        }
    }

    private class CallbackAdapter implements MqttCallback {

        @Override
        public void connectComplete(boolean reconnect, String serverUri) {
            everConnected = true;
            log.info("MQTT client [{}] {} to [{}]", clientId, reconnect ? "reconnected" : "connected", serverUri);
            for (MqttConnectionListener listener : listeners) {
                try {
                    listener.onConnected(reconnect);
                } catch (Exception e) {
                    log.error("Connection listener failed for client [{}]", clientId, e);
                }
            }
        }

        @Override
        public void disconnected(MqttDisconnectResponse response) {
            Throwable cause = response.getException();
            log.warn("MQTT client [{}] disconnected: {}", clientId,
                    response.getReasonString() != null ? response.getReasonString() : String.valueOf(cause));
            for (MqttConnectionListener listener : listeners) {
                try {
                    listener.onDisconnected(cause);
                } catch (Exception e) {
                    log.error("Connection listener failed for client [{}]", clientId, e);
                }
            }
            if (running && !everConnected) {
                scheduleReconnect();
            }
        }

        @Override
        public void mqttErrorOccurred(MqttException exception) {
            log.error("MQTT client [{}] error: {}", clientId, exception.getMessage(), exception);
        }

        @Override
        public void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) {
            IMqttMessageListener listener = messageListener;
            if (listener == null) {
                log.debug("Client [{}] received a message on [{}] with no registered listener", clientId, topic);
                return;
            }
            try {
                listener.messageArrived(topic, message);
            } catch (Exception e) {
                log.error("Message listener failed on a message from [{}]", topic, e);
            }
        }

        @Override
        public void deliveryComplete(IMqttToken token) {
        }

        @Override
        public void authPacketArrived(int reasonCode, org.eclipse.paho.mqttv5.common.packet.MqttProperties props) {
        }
    }
}
