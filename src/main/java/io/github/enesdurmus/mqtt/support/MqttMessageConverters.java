package io.github.enesdurmus.mqtt.support;

import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The message converters the starter applies to inbound and outbound payloads, as a factory rather
 * than as configuration buried in the auto-configuration — so that anything replacing the
 * {@code mqttMessageConverter} bean, and every test, can start from the same chain.
 *
 * <p>Deliberately free of any Jackson reference: JSON support is added on top through
 * {@link JacksonMqttMessageConverterFactory} and only when Jackson is on the classpath.
 */
public final class MqttMessageConverters {

    private MqttMessageConverters() {
    }

    /**
     * The always-available converters, in the order they are consulted.
     *
     * @return a mutable list, so callers can append converters of their own
     */
    public static List<MessageConverter> defaults() {
        List<MessageConverter> converters = new ArrayList<>();
        converters.add(new ByteArrayMessageConverter());
        converters.add(new StringMessageConverter(StandardCharsets.UTF_8));
        return converters;
    }
}
