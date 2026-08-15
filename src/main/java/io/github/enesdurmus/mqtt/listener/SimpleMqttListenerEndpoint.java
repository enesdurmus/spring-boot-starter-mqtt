package io.github.enesdurmus.mqtt.listener;

import io.github.enesdurmus.mqtt.annotation.MqttListener;
import io.github.enesdurmus.mqtt.core.MqttSubscriptionOptions;
import io.github.enesdurmus.mqtt.core.RetainHandling;

import org.springframework.messaging.MessageHandler;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Endpoint backed by a directly supplied {@link MessageHandler}, for listeners registered
 * programmatically rather than through {@link MqttListener}.
 *
 * <pre>{@code
 * SimpleMqttListenerEndpoint.builder("devices/" + deviceId + "/events")
 *         .id("device-" + deviceId)
 *         .qos(1)
 *         .retainHandling(RetainHandling.DO_NOT_SEND)
 *         .messageHandler(message -> process(message.getPayload()))
 *         .build();
 * }</pre>
 */
public final class SimpleMqttListenerEndpoint implements MqttListenerEndpoint {

    private final String id;
    private final List<String> topics;
    private final MqttSubscriptionOptions subscriptionOptions;
    private final MessageHandler messageHandler;
    private final MqttListenerErrorHandler errorHandler;
    private final boolean autoStartup;

    private SimpleMqttListenerEndpoint(Builder builder) {
        this.id = builder.id != null ? builder.id : "mqtt-" + UUID.randomUUID();
        this.topics = List.copyOf(builder.topics);
        this.subscriptionOptions = builder.options.build();
        this.messageHandler = Objects.requireNonNull(builder.messageHandler, "messageHandler is required");
        this.errorHandler = builder.errorHandler;
        this.autoStartup = builder.autoStartup;
    }

    public static Builder builder(String... topics) {
        return new Builder().topics(topics);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<String> getTopics() {
        return topics;
    }

    @Override
    public MqttSubscriptionOptions getSubscriptionOptions() {
        return subscriptionOptions;
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    @Override
    public MqttListenerErrorHandler getErrorHandler() {
        return errorHandler;
    }

    @Override
    public MessageHandler createMessageHandler() {
        return messageHandler;
    }

    public static final class Builder {

        private String id;
        private List<String> topics = List.of();
        private final MqttSubscriptionOptions.Builder options = MqttSubscriptionOptions.builder();
        private MessageHandler messageHandler;
        private MqttListenerErrorHandler errorHandler;
        private boolean autoStartup = true;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder topics(String... topics) {
            return topics(Arrays.asList(topics));
        }

        public Builder topics(List<String> topics) {
            this.topics = topics;
            return this;
        }

        public Builder qos(int qos) {
            options.qos(qos);
            return this;
        }

        public Builder retainHandling(RetainHandling retainHandling) {
            options.retainHandling(retainHandling);
            return this;
        }

        public Builder retainAsPublished(boolean retainAsPublished) {
            options.retainAsPublished(retainAsPublished);
            return this;
        }

        public Builder noLocal(boolean noLocal) {
            options.noLocal(noLocal);
            return this;
        }

        public Builder messageHandler(MessageHandler messageHandler) {
            this.messageHandler = messageHandler;
            return this;
        }

        public Builder errorHandler(MqttListenerErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        public Builder autoStartup(boolean autoStartup) {
            this.autoStartup = autoStartup;
            return this;
        }

        public SimpleMqttListenerEndpoint build() {
            Assert.notEmpty(topics, "at least one topic is required");
            return new SimpleMqttListenerEndpoint(this);
        }
    }
}
