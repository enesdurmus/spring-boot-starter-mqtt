package io.github.enesdurmus.mqtt;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MQTT message listener.
 *
 * <pre>{@code
 * @MqttListener(topics = "sensor/+/temperature", qos = 1,
 *               retainHandling = RetainHandling.DO_NOT_SEND)
 * public void onReading(@Topic String topic, @Payload Reading reading) {
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MqttListener {

    /**
     * Topic filters to subscribe to. Supports {@code +} and {@code #} wildcards and
     * {@code ${...}} / {@code #{...}} placeholders. A single entry may list several
     * comma-separated filters.
     */
    String[] topics();

    /** Subscription QoS: 0, 1 or 2. */
    int qos() default 0;

    /** Whether the broker replays retained messages when this subscription is established. */
    RetainHandling retainHandling() default RetainHandling.SEND;

    /**
     * Whether the broker preserves the RETAIN flag on forwarded messages, making
     * {@code @Header(MqttHeaders.RETAINED)} meaningful for live traffic.
     */
    boolean retainAsPublished() default false;

    /** Whether the broker suppresses messages published by this same client. */
    boolean noLocal() default false;

    /** Unique listener id. Defaults to a generated {@code beanName#methodName} value. */
    String id() default "";

    /** Bean name of a {@link MqttListenerErrorHandler}; falls back to the application-wide one. */
    String errorHandler() default "";

    /**
     * Whether this listener subscribes on startup. When {@code false} it is registered but
     * dormant until started through {@link MqttListenerRegistry}.
     */
    String autoStartup() default "true";
}
