package io.github.enesdurmus.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JacksonMessageConverterTest {

    @Mock
    private ObjectMapper mapper;

    private JacksonMessageConverter sut;

    @BeforeEach
    void setUp() {
        sut = new JacksonMessageConverter(mapper);
    }

    @Test
    void read_whenTargetString_shouldReturnEarly() throws JsonProcessingException {
        // given
        String payload = "test";
        Class<?> targetType = String.class;

        // when
        Object result = sut.read(payload, targetType);

        // then
        assertEquals(payload, result);
    }

    @Test
    void read_whenTargetNotString_shouldCallObjectMapper() throws JsonProcessingException {
        // given
        String payload = "{\"key\":\"value\"}";
        Class<Object> targetType = Object.class;
        Object result = mock(Object.class);

        when(mapper.readValue(payload, targetType)).thenReturn(result);

        // when
        Object actual = sut.read(payload, targetType);

        // then
        assertEquals(result, actual);
    }
}