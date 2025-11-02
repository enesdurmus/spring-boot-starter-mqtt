package io.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.lang.reflect.Method;
import java.util.List;

public class MqttListenerEndpoint {

    private final String beanName;
    private final Method method;
    private final List<String> topics;
    private final int qos;
    private final boolean injectContext;
    private final MessageConverter converter;
    private final Class<?>[] parameterTypes;

    private Object beanProxy;

    public MqttListenerEndpoint(String beanName,
                                Method method,
                                List<String> topics,
                                int qos,
                                boolean injectContext,
                                MessageConverter converter) {
        this.beanName = beanName;
        this.method = method;
        this.topics = topics;
        this.qos = qos;
        this.injectContext = injectContext;
        this.converter = converter;
        this.parameterTypes = method.getParameterTypes();
    }

    public void invoke(String payload, MqttMessage context) {
        try {
            Object[] args = new Object[parameterTypes.length];
            for (int i = 0; i < parameterTypes.length; i++) {
                Class<?> type = parameterTypes[i];
                if (type == MqttMessage.class && injectContext) {
                    args[i] = context;
                } else if (type == String.class) {
                    args[i] = payload;
                } else {
                    args[i] = converter.read(payload, type);
                }
            }
            method.invoke(getBeanProxy(), args);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to invoke method: " + method, e);
        }
    }

    public void setBeanProxy(Object beanProxy) {
        this.beanProxy = beanProxy;
    }

    public List<String> getTopics() {
        return topics;
    }

    public int getQos() {
        return qos;
    }

    public String getBeanName() {
        return beanName;
    }

    public Object getBeanProxy() {
        return beanProxy;
    }

    public Method getMethod() {
        return method;
    }

    public boolean isInjectContext() {
        return injectContext;
    }
}
