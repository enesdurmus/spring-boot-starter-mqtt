package io.github.enesdurmus.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;
import java.util.Arrays;

class MqttListenerBeanPostProcessor implements BeanPostProcessor, SmartInitializingSingleton, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(MqttListenerBeanPostProcessor.class);

    private final MqttListenerRegistry registry;
    private final MessageConverter converter;
    private ApplicationContext applicationContext;

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
                        beanName,
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

    @Override
    public void afterSingletonsInstantiated() {
        for (MqttListenerEndpoint endpoint : registry.getAllEndpoints()) {
            try {
                Object beanProxy = applicationContext.getBean(endpoint.getBeanName());
                endpoint.setBeanProxy(beanProxy);
            } catch (BeansException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}