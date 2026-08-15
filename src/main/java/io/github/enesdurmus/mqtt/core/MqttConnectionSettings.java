package io.github.enesdurmus.mqtt.core;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable timing and failure-handling settings for an {@link MqttConnection}. Everything the
 * connection needs beyond the Paho client itself, so that {@code core} stays independent of how
 * the values were sourced — the auto-configuration builds these from the {@code mqtt.*}
 * properties, tests and programmatic setups build them directly.
 *
 * <p>The defaults match the {@code mqtt.*} defaults.
 */
public final class MqttConnectionSettings {

    private final String url;
    private final Duration connectionTimeout;
    private final Duration actionTimeout;
    private final Duration disconnectTimeout;
    private final Duration connectRetryInterval;
    private final boolean failFast;

    private MqttConnectionSettings(Builder builder) {
        this.url = builder.url;
        this.connectionTimeout = builder.connectionTimeout;
        this.actionTimeout = builder.actionTimeout;
        this.disconnectTimeout = builder.disconnectTimeout;
        this.connectRetryInterval = builder.connectRetryInterval;
        this.failFast = builder.failFast;
    }

    public static Builder builder(String url) {
        return new Builder(url);
    }

    /** Broker URL, used for logging and exception messages. */
    public String getUrl() {
        return url;
    }

    /** How long to wait for the connect handshake to complete. */
    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    /** How long to wait for a subscribe, unsubscribe or awaited publish to complete. */
    public Duration getActionTimeout() {
        return actionTimeout;
    }

    /** How long to wait for a graceful disconnect before forcing one. */
    public Duration getDisconnectTimeout() {
        return disconnectTimeout;
    }

    /** Delay between attempts at the initial connect, which Paho's own reconnect does not cover. */
    public Duration getConnectRetryInterval() {
        return connectRetryInterval;
    }

    /** Whether a failed initial connect fails context startup instead of being retried. */
    public boolean isFailFast() {
        return failFast;
    }

    @Override
    public String toString() {
        return "MqttConnectionSettings{url=" + url
                + ", connectionTimeout=" + connectionTimeout
                + ", actionTimeout=" + actionTimeout
                + ", disconnectTimeout=" + disconnectTimeout
                + ", connectRetryInterval=" + connectRetryInterval
                + ", failFast=" + failFast + '}';
    }

    public static final class Builder {

        private final String url;

        private Duration connectionTimeout = Duration.ofSeconds(30);
        private Duration actionTimeout = Duration.ofSeconds(10);
        private Duration disconnectTimeout = Duration.ofSeconds(5);
        private Duration connectRetryInterval = Duration.ofSeconds(10);
        private boolean failFast;

        private Builder(String url) {
            this.url = requireText(url, "url");
        }

        public Builder connectionTimeout(Duration connectionTimeout) {
            this.connectionTimeout = requirePositive(connectionTimeout, "connectionTimeout");
            return this;
        }

        public Builder actionTimeout(Duration actionTimeout) {
            this.actionTimeout = requirePositive(actionTimeout, "actionTimeout");
            return this;
        }

        public Builder disconnectTimeout(Duration disconnectTimeout) {
            this.disconnectTimeout = requirePositive(disconnectTimeout, "disconnectTimeout");
            return this;
        }

        public Builder connectRetryInterval(Duration connectRetryInterval) {
            this.connectRetryInterval = requirePositive(connectRetryInterval, "connectRetryInterval");
            return this;
        }

        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public MqttConnectionSettings build() {
            return new MqttConnectionSettings(this);
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            return value;
        }

        private static Duration requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException(name + " must be positive but was " + value);
            }
            return value;
        }
    }
}
