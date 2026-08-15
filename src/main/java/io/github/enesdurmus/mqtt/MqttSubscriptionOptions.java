package io.github.enesdurmus.mqtt;

import java.util.Objects;

/**
 * Immutable MQTT 5 subscription options for a topic filter.
 */
public final class MqttSubscriptionOptions {

    public static final MqttSubscriptionOptions DEFAULTS = builder().build();

    private final int qos;
    private final RetainHandling retainHandling;
    private final boolean retainAsPublished;
    private final boolean noLocal;

    private MqttSubscriptionOptions(Builder builder) {
        this.qos = builder.qos;
        this.retainHandling = builder.retainHandling;
        this.retainAsPublished = builder.retainAsPublished;
        this.noLocal = builder.noLocal;
    }

    public static MqttSubscriptionOptions of(int qos) {
        return builder().qos(qos).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getQos() {
        return qos;
    }

    public RetainHandling getRetainHandling() {
        return retainHandling;
    }

    /**
     * Whether the broker preserves the RETAIN flag when forwarding. When {@code false} the flag
     * is cleared on forwarded messages, so {@code @Header(MqttHeaders.RETAINED)} is only ever
     * {@code true} for the replay at subscribe time.
     */
    public boolean isRetainAsPublished() {
        return retainAsPublished;
    }

    /** Whether the broker suppresses messages published by this same client. */
    public boolean isNoLocal() {
        return noLocal;
    }

    /**
     * Widening merge used when several listeners share one topic filter: maximum QoS, most
     * inclusive retain handling, retain-as-published if either side wants it, no-local only if
     * both do. Never delivers less than any participant requested.
     */
    MqttSubscriptionOptions mergeWith(MqttSubscriptionOptions other) {
        return builder()
                .qos(Math.max(this.qos, other.qos))
                .retainHandling(this.retainHandling.value() <= other.retainHandling.value()
                        ? this.retainHandling : other.retainHandling)
                .retainAsPublished(this.retainAsPublished || other.retainAsPublished)
                .noLocal(this.noLocal && other.noLocal)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MqttSubscriptionOptions other)) {
            return false;
        }
        return qos == other.qos
                && retainAsPublished == other.retainAsPublished
                && noLocal == other.noLocal
                && retainHandling == other.retainHandling;
    }

    @Override
    public int hashCode() {
        return Objects.hash(qos, retainHandling, retainAsPublished, noLocal);
    }

    @Override
    public String toString() {
        return "MqttSubscriptionOptions{qos=" + qos
                + ", retainHandling=" + retainHandling
                + ", retainAsPublished=" + retainAsPublished
                + ", noLocal=" + noLocal + '}';
    }

    public static final class Builder {

        private int qos = 0;
        private RetainHandling retainHandling = RetainHandling.SEND;
        private boolean retainAsPublished;
        private boolean noLocal;

        private Builder() {
        }

        public Builder qos(int qos) {
            this.qos = Qos.validate(qos);
            return this;
        }

        public Builder retainHandling(RetainHandling retainHandling) {
            this.retainHandling = Objects.requireNonNull(retainHandling, "retainHandling must not be null");
            return this;
        }

        public Builder retainAsPublished(boolean retainAsPublished) {
            this.retainAsPublished = retainAsPublished;
            return this;
        }

        public Builder noLocal(boolean noLocal) {
            this.noLocal = noLocal;
            return this;
        }

        public MqttSubscriptionOptions build() {
            return new MqttSubscriptionOptions(this);
        }
    }
}
