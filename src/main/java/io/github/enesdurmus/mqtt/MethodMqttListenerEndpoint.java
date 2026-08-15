package io.github.enesdurmus.mqtt;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

/**
 * Endpoint backed by a {@link MqttListener}-annotated method. Invocation goes through Spring's
 * {@link InvocableHandlerMethod}, so argument binding, payload conversion and {@code @Valid}
 * behave exactly as they do for {@code @KafkaListener} or {@code @RabbitListener}.
 */
public class MethodMqttListenerEndpoint implements MqttListenerEndpoint {

    private final String id;
    private final List<String> topics;
    private final MqttSubscriptionOptions subscriptionOptions;
    private final boolean autoStartup;
    private final MqttListenerErrorHandler errorHandler;
    private final Supplier<Object> beanSupplier;
    private final Method method;
    private final MessageHandlerMethodFactory handlerMethodFactory;

    MethodMqttListenerEndpoint(String id,
                               List<String> topics,
                               MqttSubscriptionOptions subscriptionOptions,
                               boolean autoStartup,
                               MqttListenerErrorHandler errorHandler,
                               Supplier<Object> beanSupplier,
                               Method method,
                               MessageHandlerMethodFactory handlerMethodFactory) {
        Assert.notEmpty(topics, "at least one topic is required");
        this.id = id;
        this.topics = List.copyOf(topics);
        this.subscriptionOptions = subscriptionOptions;
        this.autoStartup = autoStartup;
        this.errorHandler = errorHandler;
        this.beanSupplier = beanSupplier;
        this.method = method;
        this.handlerMethodFactory = handlerMethodFactory;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public List<String> getTopics() {
        return topics;
    }

    @Override
    public MqttSubscriptionOptions getSubscriptionOptions() {
        return subscriptionOptions;
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    @Override
    public MqttListenerErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public Method getMethod() {
        return method;
    }

    @Override
    public MessageHandler createMessageHandler() {
        InvocableHandlerMethod handlerMethod =
                handlerMethodFactory.createInvocableHandlerMethod(beanSupplier.get(), method);
        return message -> invoke(handlerMethod, message);
    }

    private void invoke(InvocableHandlerMethod handlerMethod, Message<?> message) {
        try {
            handlerMethod.invoke(message);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new MqttListenerInvocationException(message,
                    "Listener [" + id + "] failed to handle message from ["
                            + MqttMessageHeaderAccessor.wrap(message).getReceivedTopic() + "]", e);
        }
    }
}
