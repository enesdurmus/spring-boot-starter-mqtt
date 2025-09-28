package io.github.enesdurmus.mqtt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttListenerRegistryTest {

    private MqttListenerRegistry sut;

    @BeforeEach
    void setUp() {
        sut = new MqttListenerRegistry();
    }

    @Test
    void registerAddsEndpointToCorrectTopic() {
        // given
        MqttListenerEndpoint endpoint = mock(MqttListenerEndpoint.class);
        when(endpoint.getTopics()).thenReturn(List.of("topic1"));

        // when
        sut.register(endpoint);

        // then
        assertEquals(1, sut.getAllEndpoints().size());
        assertTrue(sut.getAllEndpoints().contains(endpoint));
    }

    @Test
    void registerHandlesMultipleEndpointsForSameTopic() {
        // given
        MqttListenerEndpoint endpoint1 = mock(MqttListenerEndpoint.class);
        MqttListenerEndpoint endpoint2 = mock(MqttListenerEndpoint.class);
        when(endpoint1.getTopics()).thenReturn(List.of("topic1"));
        when(endpoint2.getTopics()).thenReturn(List.of("topic1"));

        // when
        sut.register(endpoint1);
        sut.register(endpoint2);

        // then
        assertEquals(2, sut.getAllEndpoints().size());
        assertTrue(sut.getAllEndpoints().containsAll(List.of(endpoint1, endpoint2)));
    }

    @Test
    void registerHandlesEndpointsWithMultipleTopics() {
        // given
        MqttListenerEndpoint endpoint = mock(MqttListenerEndpoint.class);
        when(endpoint.getTopics()).thenReturn(List.of("topic1", "topic2"));

        // when
        sut.register(endpoint);

        // then
        assertEquals(2, sut.getAllEndpoints().size());
        assertTrue(sut.getAllEndpoints().contains(endpoint));
    }

    @Test
    void getAllEndpointsReturnsEmptyListWhenNoEndpointsRegistered() {
        // when
        List<MqttListenerEndpoint> endpoints = sut.getAllEndpoints();

        // then
        assertTrue(endpoints.isEmpty());
    }

}