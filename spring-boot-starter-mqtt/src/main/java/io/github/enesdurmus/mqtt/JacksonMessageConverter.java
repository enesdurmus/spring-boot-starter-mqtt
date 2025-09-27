package io.github.enesdurmus.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class JacksonMessageConverter implements MessageConverter {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object read(String payload, Class<?> targetType) throws JsonProcessingException {
        if (targetType == String.class) return payload;
        return objectMapper.readValue(payload, targetType);
    }
}
