package io.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttListenerEndpointTest {

    @Test
    void invokeCallsMethodWithCorrectArguments() throws Exception {
        // given
        Method method = TestBean.class.getDeclaredMethod("handleMessage", String.class, MqttMessage.class);
        TestBean bean = spy(new TestBean());
        MessageConverter converter = mock(MessageConverter.class);
        MqttListenerEndpoint endpoint = new MqttListenerEndpoint(bean, method, List.of("topic1"), 1, true, converter);
        MqttMessage context = new MqttMessage("context".getBytes());

        // when
        endpoint.invoke("payload", context);

        // then
        verify(bean).handleMessage("payload", context);
    }

    @Test
    void invokeHandlesNullPayloadGracefully() throws Exception {
        // given
        Method method = TestBean.class.getDeclaredMethod("handleMessage", String.class, MqttMessage.class);
        TestBean bean = spy(new TestBean());
        MessageConverter converter = mock(MessageConverter.class);
        MqttListenerEndpoint endpoint = new MqttListenerEndpoint(bean, method, List.of("topic1"), 1, true, converter);
        MqttMessage context = new MqttMessage("context".getBytes());

        // when
        endpoint.invoke(null, context);

        // then
        verify(bean).handleMessage(null, context);
    }

    @Test
    void invokeConvertsPayloadToTargetType() throws Exception {
        // given
        Method method = TestBean.class.getDeclaredMethod("handleConvertedMessage", Integer.class);
        TestBean bean = spy(new TestBean());
        MessageConverter converter = mock(MessageConverter.class);
        when(converter.read("42", Integer.class)).thenReturn(42);
        MqttListenerEndpoint endpoint = new MqttListenerEndpoint(bean, method, List.of("topic1"), 1, false, converter);

        // when
        endpoint.invoke("42", null);

        // then
        verify(bean).handleConvertedMessage(42);
    }

    @Test
    void invokeThrowsIllegalStateExceptionWhenConverterFails() throws Exception {
        // given
        Method method = TestBean.class.getDeclaredMethod("handleConvertedMessage", Integer.class);
        TestBean bean = new TestBean();
        MessageConverter converter = mock(MessageConverter.class);
        when(converter.read("invalid", Integer.class)).thenThrow(new RuntimeException("Conversion failed"));
        MqttListenerEndpoint endpoint = new MqttListenerEndpoint(bean, method, List.of("topic1"), 1, false, converter);

        // when / then
        assertThrows(IllegalStateException.class, () -> endpoint.invoke("invalid", null));
    }

    static class TestBean {
        void handleMessage(String payload, MqttMessage context) {
        }

        void handleConvertedMessage(Integer value) {
        }

        void invalidMethod() {
        }
    }

}