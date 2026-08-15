package io.github.enesdurmus.mqtt.autoconfigure;

import io.github.enesdurmus.mqtt.annotation.Topic;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the MQTT connection and its listeners.
 */
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    /**
     * Broker URL, for example tcp://localhost:1883, ssl://broker.example.com:8883 or
     * ws://localhost:8000/mqtt. Required; the starter backs off entirely when it is absent.
     */
    private String url;

    /**
     * Client identifier. Must be unique per broker connection. Defaults to spring-mqtt- followed
     * by a random token.
     */
    private String clientId;

    /** Username for broker authentication. */
    private String username;

    /** Password for broker authentication. */
    private String password;

    /**
     * Whether to start a fresh session, discarding any state the broker holds for this client id.
     * Set to false together with a session-expiry-interval for durable subscriptions.
     */
    private boolean cleanStart = true;

    /**
     * How long the broker keeps session state after disconnect. Only meaningful when
     * clean-start is false. Null leaves the broker default in place.
     */
    private Duration sessionExpiryInterval;

    /** Interval at which the client pings the broker to keep the connection alive. */
    private Duration keepAliveInterval = Duration.ofSeconds(60);

    /** How long to wait for the connect handshake to complete. */
    private Duration connectionTimeout = Duration.ofSeconds(30);

    /** How long to wait for a subscribe, unsubscribe or awaited publish to be acknowledged. */
    private Duration actionTimeout = Duration.ofSeconds(10);

    /** How long a graceful disconnect may take during shutdown. */
    private Duration disconnectTimeout = Duration.ofSeconds(5);

    /**
     * Delay between attempts when the initial connect fails. Only applies before the first
     * successful connect; afterwards Paho's own automatic reconnect takes over.
     */
    private Duration connectRetryInterval = Duration.ofSeconds(10);

    /**
     * Whether an unreachable broker fails application startup. When false the context starts and
     * the client keeps retrying in the background.
     */
    private boolean failFast = false;

    /** Whether the client reconnects automatically after losing an established connection. */
    private boolean automaticReconnect = true;

    /** Shortest backoff delay used by automatic reconnect. */
    private Duration reconnectMinDelay = Duration.ofSeconds(1);

    /** Longest backoff delay used by automatic reconnect. */
    private Duration reconnectMaxDelay = Duration.ofSeconds(120);

    /** Maximum number of QoS 1 and 2 messages the broker may have in flight towards this client. */
    private Integer receiveMaximum;

    /** Largest packet this client accepts, in bytes. */
    private Long maximumPacketSize;

    private final Listener listener = new Listener();
    private final Publisher publisher = new Publisher();
    private final Will will = new Will();

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Nullable
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Nullable
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isCleanStart() {
        return cleanStart;
    }

    public void setCleanStart(boolean cleanStart) {
        this.cleanStart = cleanStart;
    }

    @Nullable
    public Duration getSessionExpiryInterval() {
        return sessionExpiryInterval;
    }

    public void setSessionExpiryInterval(Duration sessionExpiryInterval) {
        this.sessionExpiryInterval = sessionExpiryInterval;
    }

    public Duration getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public void setKeepAliveInterval(Duration keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public Duration getActionTimeout() {
        return actionTimeout;
    }

    public void setActionTimeout(Duration actionTimeout) {
        this.actionTimeout = actionTimeout;
    }

    public Duration getDisconnectTimeout() {
        return disconnectTimeout;
    }

    public void setDisconnectTimeout(Duration disconnectTimeout) {
        this.disconnectTimeout = disconnectTimeout;
    }

    public Duration getConnectRetryInterval() {
        return connectRetryInterval;
    }

    public void setConnectRetryInterval(Duration connectRetryInterval) {
        this.connectRetryInterval = connectRetryInterval;
    }

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean isAutomaticReconnect() {
        return automaticReconnect;
    }

    public void setAutomaticReconnect(boolean automaticReconnect) {
        this.automaticReconnect = automaticReconnect;
    }

    public Duration getReconnectMinDelay() {
        return reconnectMinDelay;
    }

    public void setReconnectMinDelay(Duration reconnectMinDelay) {
        this.reconnectMinDelay = reconnectMinDelay;
    }

    public Duration getReconnectMaxDelay() {
        return reconnectMaxDelay;
    }

    public void setReconnectMaxDelay(Duration reconnectMaxDelay) {
        this.reconnectMaxDelay = reconnectMaxDelay;
    }

    @Nullable
    public Integer getReceiveMaximum() {
        return receiveMaximum;
    }

    public void setReceiveMaximum(Integer receiveMaximum) {
        this.receiveMaximum = receiveMaximum;
    }

    @Nullable
    public Long getMaximumPacketSize() {
        return maximumPacketSize;
    }

    public void setMaximumPacketSize(Long maximumPacketSize) {
        this.maximumPacketSize = maximumPacketSize;
    }

    public Listener getListener() {
        return listener;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public Will getWill() {
        return will;
    }

    /** Thread pool that listener invocations run on. */
    public static class Listener {

        /** Number of threads kept alive for message processing. */
        private int concurrency = 3;

        /** Upper bound on threads; the pool only grows past the core size once the queue is full. */
        private Integer maxConcurrency;

        /** How many messages may wait for a free thread. */
        private int queueCapacity = 100;

        /** How long shutdown waits for in-flight messages to finish. */
        private Duration shutdownTimeout = Duration.ofSeconds(30);

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        public int getMaxConcurrency() {
            return maxConcurrency != null ? maxConcurrency : concurrency * 2;
        }

        public void setMaxConcurrency(Integer maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }
    }

    public static class Publisher {

        /**
         * Whether publish calls block until the broker acknowledges. Leave enabled so a failed
         * publish surfaces as an exception rather than being lost silently.
         */
        private boolean awaitDelivery = true;

        public boolean isAwaitDelivery() {
            return awaitDelivery;
        }

        public void setAwaitDelivery(boolean awaitDelivery) {
            this.awaitDelivery = awaitDelivery;
        }
    }

    /** Last Will and Testament, published by the broker if this client disconnects ungracefully. */
    public static class Will {

        /** Topic to publish the will to. Leaving this empty disables the will. */
        private String topic;

        /** Will payload. Empty by default. */
        private String payload = "";

        private int qos = 0;

        private boolean retained = false;

        @Nullable
        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public int getQos() {
            return qos;
        }

        public void setQos(int qos) {
            this.qos = qos;
        }

        public boolean isRetained() {
            return retained;
        }

        public void setRetained(boolean retained) {
            this.retained = retained;
        }
    }

    List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (listener.getConcurrency() < 1) {
            errors.add("mqtt.listener.concurrency must be at least 1");
        }
        if (listener.getMaxConcurrency() < listener.getConcurrency()) {
            errors.add("mqtt.listener.max-concurrency must not be smaller than mqtt.listener.concurrency");
        }
        if (listener.getQueueCapacity() < 0) {
            errors.add("mqtt.listener.queue-capacity must not be negative");
        }
        requirePositive(connectionTimeout, "mqtt.connection-timeout", errors);
        requirePositive(actionTimeout, "mqtt.action-timeout", errors);
        requirePositive(disconnectTimeout, "mqtt.disconnect-timeout", errors);
        requirePositive(connectRetryInterval, "mqtt.connect-retry-interval", errors);
        if (will.getTopic() != null && (will.getQos() < 0 || will.getQos() > 2)) {
            errors.add("mqtt.will.qos must be 0, 1 or 2");
        }
        return errors;
    }

    private static void requirePositive(@Nullable Duration value, String name, List<String> errors) {
        if (value == null || value.isNegative() || value.isZero()) {
            errors.add(name + " must be positive");
        }
    }
}
