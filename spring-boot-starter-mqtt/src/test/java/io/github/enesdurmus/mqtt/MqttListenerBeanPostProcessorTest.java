package io.github.enesdurmus.mqtt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Objects;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MqttListenerBeanPostProcessorTest {

    @Mock
    private MqttListenerRegistry mqttListenerRegistry;

    @Mock
    private MessageConverter messageConverter;

    private MqttListenerBeanPostProcessor sut;

    @BeforeEach
    void setUp() {
        sut = new MqttListenerBeanPostProcessor(mqttListenerRegistry, messageConverter);
    }

    @Test
    void postProcessAfterInitializationRegistersEndpointWhenMqttListenerAnnotationIsPresent() {
        // given
        Object bean = new Object() {
            @MqttListener(topics = {"topic1"}, qos = 1)
            public void annotatedMethod() {
            }
        };
        String beanName = "testBean";

        // when
        sut.postProcessAfterInitialization(bean, beanName);

        // then
        verify(mqttListenerRegistry).register(argThat(endpoint ->
                Objects.equals(endpoint.getBeanName(), beanName) &&
                endpoint.getMethod().getName().equals("annotatedMethod") &&
                endpoint.getTopics().contains("topic1") &&
                endpoint.getQos() == 1 &&
                !endpoint.isInjectContext()
        ));
    }

    @Test
    void postProcessAfterInitializationDoesNotRegisterEndpointWhenNoMqttListenerAnnotationIsPresent() {
        // given
        Object bean = new Object() {
            public void nonAnnotatedMethod() {
            }
        };
        String beanName = "testBean";

        // when
        sut.postProcessAfterInitialization(bean, beanName);

        // then
        verifyNoInteractions(mqttListenerRegistry);
    }

    @Test
    void postProcessAfterInitializationHandlesBeanWithNoDeclaredMethods() {
        // given
        Object bean = new Object();
        String beanName = "testBean";

        // when
        sut.postProcessAfterInitialization(bean, beanName);

        // then
        verifyNoInteractions(mqttListenerRegistry);
    }
}