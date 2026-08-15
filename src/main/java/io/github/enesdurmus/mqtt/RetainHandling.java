package io.github.enesdurmus.mqtt;

/**
 * MQTT 5 <em>Retain Handling</em> subscription option: whether the broker replays retained
 * messages when a subscription is established.
 */
public enum RetainHandling {

    /** Always replay retained messages on subscribe. */
    SEND(0),

    /** Replay retained messages only when the subscription did not already exist. */
    SEND_IF_NEW_SUBSCRIPTION(1),

    /** Never replay retained messages on subscribe. */
    DO_NOT_SEND(2);

    private final int value;

    RetainHandling(int value) {
        this.value = value;
    }

    /** @return the wire value defined by the MQTT 5 specification */
    public int value() {
        return value;
    }

    /** @throws IllegalArgumentException if {@code value} is not 0, 1 or 2 */
    public static RetainHandling fromValue(int value) {
        for (RetainHandling handling : values()) {
            if (handling.value == value) {
                return handling;
            }
        }
        throw new IllegalArgumentException("Invalid retain handling value: " + value + " (expected 0, 1 or 2)");
    }
}
