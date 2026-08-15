package io.github.enesdurmus.mqtt.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.MimeTypeUtils;

/**
 * Creates the JSON converter the starter uses, configured for MQTT: payloads are serialized to
 * {@code byte[]}, and content-type matching is lenient because brokers and devices routinely
 * publish JSON without an MQTT 5 content-type property.
 *
 * <p>Isolates every reference to Jackson in one class, so the rest of the starter loads without
 * Jackson on the classpath. Only call this when {@code com.fasterxml.jackson.databind.ObjectMapper}
 * is present.
 */
public final class JacksonMqttMessageConverterFactory {

    private JacksonMqttMessageConverterFactory() {
    }

    /**
     * @param objectMapper the mapper to use, or {@code null} to let the converter create its own
     */
    public static MessageConverter create(@Nullable ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter =
                new MappingJackson2MessageConverter(MimeTypeUtils.APPLICATION_JSON);
        if (objectMapper != null) {
            converter.setObjectMapper(objectMapper);
        }
        converter.setSerializedPayloadClass(byte[].class);
        converter.setStrictContentTypeMatch(false);
        return converter;
    }
}
