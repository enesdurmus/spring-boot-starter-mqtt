package io.github.enesdurmus.mqtt;

/**
 * Observes the state of an {@link MqttConnection}.
 */
public interface MqttConnectionListener {

    /**
     * @param reconnect {@code true} when this is a re-establishment rather than the first connect
     */
    default void onConnected(boolean reconnect) {
    }

    /**
     * @param cause the failure that closed the connection, or {@code null} on a clean disconnect
     */
    default void onDisconnected(Throwable cause) {
    }
}
