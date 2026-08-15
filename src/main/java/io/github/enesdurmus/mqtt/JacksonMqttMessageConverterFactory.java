package io.github.enesdurmus.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.lang.Nullable;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.util.MimeTypeUtils;

/**
 * Isolates every reference to Jackson so the rest of the starter loads without it on the classpath.
 */
final class JacksonMqttMessageConverterFactory {

    private JacksonMqttMessageConverterFactory() {
    }

    static MessageConverter create(@Nullable ObjectMapper objectMapper) {
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
