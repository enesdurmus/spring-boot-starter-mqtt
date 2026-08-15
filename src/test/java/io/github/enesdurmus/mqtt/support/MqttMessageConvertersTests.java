package io.github.enesdurmus.mqtt.support;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MqttMessageConvertersTests {

    @Test
    void defaultsAreByteArrayThenString() {
        assertThat(MqttMessageConverters.defaults())
                .hasExactlyElementsOfTypes(ByteArrayMessageConverter.class, StringMessageConverter.class);
    }

    @Test
    void defaultsAreMutableSoCallersCanAppendTheirOwn() {
        List<MessageConverter> converters = MqttMessageConverters.defaults();
        converters.add(JacksonMqttMessageConverterFactory.create(new ObjectMapper()));

        assertThat(converters).hasSize(3);
        assertThat(MqttMessageConverters.defaults()).hasSize(2);
    }

    @Test
    void theJsonConverterSerializesToBytesAndDoesNotRequireAContentType() {
        MappingJackson2MessageConverter converter =
                (MappingJackson2MessageConverter) JacksonMqttMessageConverterFactory.create(new ObjectMapper());

        assertThat(converter.getSerializedPayloadClass()).isEqualTo(byte[].class);
        assertThat(converter.isStrictContentTypeMatch()).isFalse();
        assertThat(converter.getSupportedMimeTypes()).containsExactly(
                org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
    }

    @Test
    void aSuppliedObjectMapperIsUsed() {
        ObjectMapper mapper = new ObjectMapper();
        MappingJackson2MessageConverter converter =
                (MappingJackson2MessageConverter) JacksonMqttMessageConverterFactory.create(mapper);

        assertThat(converter.getObjectMapper()).isSameAs(mapper);
    }
}
