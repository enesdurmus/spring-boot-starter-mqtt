package io.github.enesdurmus.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * Subscribes an endpoint's topics and dispatches every message to a {@link TaskExecutor}, so a
 * slow listener never blocks the MQTT network thread or any other listener.
 */
public class DefaultMqttListenerContainer implements MqttListenerContainer {

    private static final Logger log = LoggerFactory.getLogger(DefaultMqttListenerContainer.class);

    private final MqttListenerEndpoint endpoint;
    private final MqttSubscriptionManager subscriptionManager;
    private final TaskExecutor taskExecutor;
    private final MqttListenerErrorHandler defaultErrorHandler;

    private final Object lifecycleMonitor = new Object();
    private final List<MqttSubscriptionManager.SubscriptionHandle> handles = new ArrayList<>();
    private volatile boolean running;

    DefaultMqttListenerContainer(MqttListenerEndpoint endpoint,
                                 MqttSubscriptionManager subscriptionManager,
                                 TaskExecutor taskExecutor,
                                 MqttListenerErrorHandler defaultErrorHandler) {
        Assert.notNull(endpoint, "endpoint must not be null");
        Assert.notEmpty(endpoint.getTopics(), "endpoint must declare at least one topic");
        this.endpoint = endpoint;
        this.subscriptionManager = subscriptionManager;
        this.taskExecutor = taskExecutor;
        this.defaultErrorHandler = defaultErrorHandler;
    }

    @Override
    public String getListenerId() {
        return endpoint.getId();
    }

    @Override
    public List<String> getTopics() {
        return endpoint.getTopics();
    }

    @Override
    public void start() {
        synchronized (lifecycleMonitor) {
            if (running) {
                return;
            }
            MessageHandler handler = endpoint.createMessageHandler();
            MessageHandler dispatching = message -> dispatch(handler, message);
            try {
                for (String topic : endpoint.getTopics()) {
                    handles.add(subscriptionManager.subscribe(topic, endpoint.getSubscriptionOptions(), dispatching));
                }
            } catch (RuntimeException e) {
                cancelHandles();
                throw e;
            }
            running = true;
        }
        log.info("Started MQTT listener [{}] on {}", endpoint.getId(), endpoint.getTopics());
    }

    @Override
    public void stop() {
        synchronized (lifecycleMonitor) {
            if (!running) {
                return;
            }
            cancelHandles();
            running = false;
        }
        log.info("Stopped MQTT listener [{}]", endpoint.getId());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return endpoint.isAutoStartup();
    }

    @Override
    public int getPhase() {
        return MqttPhases.SUBSCRIPTIONS;
    }

    private void dispatch(MessageHandler handler, Message<?> message) {
        Message<?> tagged = MessageBuilder.fromMessage(message)
                .setHeader(MqttHeaders.LISTENER_ID, endpoint.getId())
                .build();
        taskExecutor.execute(() -> {
            try {
                handler.handleMessage(tagged);
            } catch (Exception e) {
                handleError(tagged, e);
            }
        });
    }

    private void handleError(Message<?> message, Exception exception) {
        MqttListenerErrorHandler handler = endpoint.getErrorHandler();
        if (handler == null) {
            handler = defaultErrorHandler;
        }
        try {
            handler.handleError(message, exception);
        } catch (Exception e) {
            log.error("Error handler for listener [{}] threw an exception", endpoint.getId(), e);
        }
    }

    private void cancelHandles() {
        handles.forEach(MqttSubscriptionManager.SubscriptionHandle::cancel);
        handles.clear();
    }
}
