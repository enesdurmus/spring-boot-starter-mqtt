package io.github.enesdurmus.mqtt.core;

/**
 * Quality-of-service level checks. Not public API: callers outside this package validate their
 * own input and phrase their own error messages.
 */
final class Qos {

    private Qos() {
    }

    /** @throws IllegalArgumentException if {@code qos} is not 0, 1 or 2 */
    static int validate(int qos) {
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("QoS must be 0, 1 or 2 but was " + qos);
        }
        return qos;
    }
}
