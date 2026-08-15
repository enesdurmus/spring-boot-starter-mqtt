package io.github.enesdurmus.mqtt.core;

/**
 * Names of the infrastructure beans contributed by the auto-configuration. Declaring a bean under
 * one of these names replaces the default.
 *
 * <p>Declared here so the listener infrastructure can reference them without depending on the
 * {@code autoconfigure} package.
 */
public final class MqttBeanNames {

    /** The {@code MessageConverter} used for both inbound and outbound payload conversion. */
    public static final String MESSAGE_CONVERTER = "mqttMessageConverter";

    /** The {@code TaskExecutor} that listener invocations are dispatched to. */
    public static final String LISTENER_TASK_EXECUTOR = "mqttListenerTaskExecutor";

    /** The {@code MessageHandlerMethodFactory} that binds listener method arguments. */
    public static final String HANDLER_METHOD_FACTORY = "mqttHandlerMethodFactory";

    /** The single-threaded {@code TaskScheduler} used for connection retries. */
    public static final String TASK_SCHEDULER = "mqttTaskScheduler";

    private MqttBeanNames() {
    }
}
