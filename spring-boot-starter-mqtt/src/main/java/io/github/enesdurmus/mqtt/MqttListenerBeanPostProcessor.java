package io.github.enesdurmus.mqtt;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Method;
import java.util.Arrays;

class MqttListenerBeanPostProcessor implements BeanPostProcessor {

    private final MqttListenerRegistry registry;
    private final MessageConverter converter;

    MqttListenerBeanPostProcessor(MqttListenerRegistry registry,
                                  MessageConverter converter) {
        this.registry = registry;
        this.converter = converter;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(MqttListener.class)) {
                MqttListener annotation = method.getAnnotation(MqttListener.class);
                MqttListenerEndpoint endpoint = new MqttListenerEndpoint(
                        bean,
                        method,
                        Arrays.asList(annotation.topics()),
                        annotation.qos(),
                        annotation.injectContext(),
                        converter
                );
                registry.register(endpoint);
            }
        }
        return bean;
    }
}