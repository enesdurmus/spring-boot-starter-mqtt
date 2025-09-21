package io.github.enesdurmus.mqtt;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Method;

class MqttListenerBeanPostProcessor implements BeanPostProcessor {

    private final MqttListenerRegistry registry;

    public MqttListenerBeanPostProcessor(MqttListenerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        for (Method method : bean.getClass().getMethods()) {
            if (method.isAnnotationPresent(MqttListener.class)) {
                MqttListener ann = method.getAnnotation(MqttListener.class);
                registry.register(ann.topic(), method, bean, ann.qos());
            }
        }
        return bean;
    }
}