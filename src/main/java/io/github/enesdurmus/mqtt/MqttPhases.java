package io.github.enesdurmus.mqtt;

/**
 * {@link org.springframework.context.SmartLifecycle#getPhase() Lifecycle phases}. Startup runs in
 * ascending order and shutdown in descending order, so connections come up first and go down last.
 */
public final class MqttPhases {

    public static final int CONNECTION = Integer.MAX_VALUE - 2000;
    public static final int SUBSCRIPTIONS = Integer.MAX_VALUE - 1000;

    private MqttPhases() {
    }
}
