package io.github.enesdurmus.mqtt;

final class Qos {

    private Qos() {
    }

    static int validate(int qos) {
        if (qos < 0 || qos > 2) {
            throw new IllegalArgumentException("QoS must be 0, 1 or 2 but was " + qos);
        }
        return qos;
    }
}
