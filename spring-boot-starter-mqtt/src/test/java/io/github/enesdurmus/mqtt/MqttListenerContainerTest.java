package io.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqttListenerContainerTest {

    @Mock
    private MqttClient mqttClient;

    @Mock
    private MqttListenerRegistry registry;

    @Mock
    private ThreadPoolTaskExecutor executor;

    private MqttListenerContainer sut;

    @BeforeEach
    void setUp() {
        sut = new MqttListenerContainer(mqttClient, registry, executor);
    }

    @Test
    void startSubscribesToMultipleTopicsWithDifferentQos() throws Exception {
        // given
        MqttListenerEndpoint endpoint1 = mock(MqttListenerEndpoint.class);
        MqttListenerEndpoint endpoint2 = mock(MqttListenerEndpoint.class);
        when(endpoint1.getTopics()).thenReturn(List.of("topic1"));
        when(endpoint1.getQos()).thenReturn(0);
        when(endpoint2.getTopics()).thenReturn(List.of("topic2"));
        when(endpoint2.getQos()).thenReturn(2);
        when(registry.getAllEndpoints()).thenReturn(List.of(endpoint1, endpoint2));

        // when
        sut.start();

        // then
        verify(mqttClient).subscribe(eq("topic1"), eq(0), any());
        verify(mqttClient).subscribe(eq("topic2"), eq(2), any());
    }

    @Test
    void startHandlesEmptyTopicsListGracefully() {
        // given
        MqttListenerEndpoint endpoint = mock(MqttListenerEndpoint.class);
        when(endpoint.getTopics()).thenReturn(Collections.emptyList());
        when(registry.getAllEndpoints()).thenReturn(List.of(endpoint));

        // when
        sut.start();

        // then
        verifyNoInteractions(mqttClient);
    }

    @Test
    void startHandlesExceptionFromExecutorGracefully() throws Exception {
        // given
        MqttListenerEndpoint endpoint = mock(MqttListenerEndpoint.class);
        when(endpoint.getTopics()).thenReturn(List.of("topic1"));
        when(endpoint.getQos()).thenReturn(1);
        when(registry.getAllEndpoints()).thenReturn(List.of(endpoint));

        doAnswer(invocation -> {
            MqttMessageListener listener = invocation.getArgument(2);
            listener.messageArrived("topic1", new MqttMessage("payload".getBytes()));
            return null;
        }).when(mqttClient).subscribe(eq("topic1"), eq(1), any());

        doThrow(new RuntimeException("Executor failure")).when(executor).execute(any(Runnable.class));

        // when / then
        assertThrows(RuntimeException.class, () -> sut.start());
    }


}