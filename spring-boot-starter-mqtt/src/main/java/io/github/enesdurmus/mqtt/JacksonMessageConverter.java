package io.github.enesdurmus.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class JacksonMessageConverter implements MessageConverter {

    private final ObjectMapper objectMapper;

    JacksonMessageConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object read(String payload, Class<?> targetType) throws JsonProcessingException {
        if (targetType == String.class) return payload;
        return objectMapper.readValue(payload, targetType);
    }

    @Override
    public byte[] write(Object payload) throws Exception {
        return objectMapper.writeValueAsBytes(payload);
    }
}
