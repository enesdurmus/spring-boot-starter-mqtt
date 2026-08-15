package io.github.enesdurmus.mqtt.listener;

import org.springframework.context.SmartLifecycle;

import java.util.List;

/**
 * Manages the subscriptions of a single {@link MqttListenerEndpoint} and can be started and
 * stopped independently of the application context.
 */
public interface MqttListenerContainer extends SmartLifecycle {

    String getListenerId();

    List<String> getTopics();
}
