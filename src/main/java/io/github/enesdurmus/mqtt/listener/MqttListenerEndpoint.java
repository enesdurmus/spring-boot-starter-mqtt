package io.github.enesdurmus.mqtt.listener;

import io.github.enesdurmus.mqtt.core.MqttSubscriptionOptions;

import org.springframework.messaging.MessageHandler;

import java.util.List;

/**
 * Description of a listener: what it subscribes to, and what handles the messages.
 * Implementations are immutable once registered.
 *
 * @see MethodMqttListenerEndpoint
 * @see SimpleMqttListenerEndpoint
 */
public interface MqttListenerEndpoint {

    String getId();

    /** Resolved topic filters, never empty. */
    List<String> getTopics();

    MqttSubscriptionOptions getSubscriptionOptions();

    /** Whether the owning container subscribes as soon as the context starts. */
    boolean isAutoStartup();

    /** Error handler for this endpoint, or {@code null} to use the application-wide one. */
    MqttListenerErrorHandler getErrorHandler();

    /** Creates the handler invoked for each message. Called once, when the container starts. */
    MessageHandler createMessageHandler();
}
