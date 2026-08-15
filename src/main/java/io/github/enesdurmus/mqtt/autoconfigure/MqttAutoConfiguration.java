package io.github.enesdurmus.mqtt.autoconfigure;

import io.github.enesdurmus.mqtt.MqttConnectionException;
import io.github.enesdurmus.mqtt.core.DefaultMqttSubscriptionManager;
import io.github.enesdurmus.mqtt.core.MqttBeanNames;
import io.github.enesdurmus.mqtt.core.MqttConnection;
import io.github.enesdurmus.mqtt.core.MqttConnectionListener;
import io.github.enesdurmus.mqtt.core.MqttConnectionOptionsCustomizer;
import io.github.enesdurmus.mqtt.core.MqttConnectionSettings;
import io.github.enesdurmus.mqtt.core.MqttMessageHeaderAccessor;
import io.github.enesdurmus.mqtt.core.MqttOperations;
import io.github.enesdurmus.mqtt.core.MqttSubscriptionManager;
import io.github.enesdurmus.mqtt.core.MqttTemplate;
import io.github.enesdurmus.mqtt.listener.DefaultMqttListenerContainer;
import io.github.enesdurmus.mqtt.listener.MqttListenerAnnotationBeanPostProcessor;
import io.github.enesdurmus.mqtt.listener.MqttListenerContainerFactory;
import io.github.enesdurmus.mqtt.listener.MqttListenerEndpointRegistry;
import io.github.enesdurmus.mqtt.listener.MqttListenerErrorHandler;
import io.github.enesdurmus.mqtt.support.JacksonMqttMessageConverterFactory;
import io.github.enesdurmus.mqtt.support.MqttHeaderAccessorArgumentResolver;
import io.github.enesdurmus.mqtt.support.MqttMessageConverters;
import io.github.enesdurmus.mqtt.support.TopicMethodArgumentResolver;

import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.StringUtils;
import org.springframework.validation.Validator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Auto-configuration for MQTT 5 publishing and annotation-driven listeners.
 *
 * <p>Backs off entirely unless {@code mqtt.url} is set. Every bean is conditional, so any part
 * can be replaced by declaring a bean of the same type or name.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(MqttAsyncClient.class)
@ConditionalOnProperty(prefix = "mqtt", name = "url")
@EnableConfigurationProperties(MqttProperties.class)
@ImportRuntimeHints(MqttRuntimeHints.class)
public class MqttAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MqttAutoConfiguration.class);


    @Bean
    @ConditionalOnMissingBean
    public MqttConnection mqttConnection(MqttProperties properties,
                                         ObjectProvider<MqttConnectionOptionsCustomizer> customizers,
                                         @Qualifier(MqttBeanNames.TASK_SCHEDULER) TaskScheduler taskScheduler) {
        List<String> errors = properties.validate();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid MQTT configuration: " + String.join("; ", errors));
        }

        String clientId = resolveClientId(properties);
        MqttConnectionOptions options = buildOptions(properties, customizers);
        MqttConnectionSettings settings = buildSettings(properties);
        try {
            MqttAsyncClient client = new MqttAsyncClient(properties.getUrl(), clientId, new MemoryPersistence());
            return new MqttConnection(clientId, client, options, settings, taskScheduler);
        } catch (MqttException e) {
            throw new MqttConnectionException(
                    "Failed to create MQTT client [" + clientId + "] for " + properties.getUrl(), e);
        }
    }

    @Bean(name = MqttBeanNames.TASK_SCHEDULER)
    @ConditionalOnMissingBean(name = MqttBeanNames.TASK_SCHEDULER)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public TaskScheduler mqttTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("mqtt-reconnect-");
        scheduler.setDaemon(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    /** Attaches every {@link MqttConnectionListener} bean, once all of them exist. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SmartInitializingSingleton mqttConnectionListenerRegistrar(MqttConnection connection,
                                                               ObjectProvider<MqttConnectionListener> listeners) {
        return () -> listeners.orderedStream().forEach(connection::addListener);
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    MqttSubscriptionManager mqttSubscriptionManager(MqttConnection connection) {
        return DefaultMqttSubscriptionManager.create(connection);
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttOperations mqttTemplate(MqttConnection connection,
                                       MqttProperties properties,
                                       @Qualifier(MqttBeanNames.MESSAGE_CONVERTER) MessageConverter messageConverter) {
        MqttTemplate template = new MqttTemplate(connection, properties.getPublisher().isAwaitDelivery());
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean(name = MqttBeanNames.LISTENER_TASK_EXECUTOR, destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = MqttBeanNames.LISTENER_TASK_EXECUTOR)
    public TaskExecutor mqttListenerTaskExecutor(MqttProperties properties) {
        MqttProperties.Listener listener = properties.getListener();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(listener.getConcurrency());
        executor.setMaxPoolSize(listener.getMaxConcurrency());
        executor.setQueueCapacity(listener.getQueueCapacity());
        executor.setThreadNamePrefix("mqtt-listener-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds((int) listener.getShutdownTimeout().toSeconds());
        executor.setRejectedExecutionHandler((task, pool) -> log.error(
                "MQTT listener pool saturated, dropping a message. "
                        + "Raise mqtt.listener.concurrency or mqtt.listener.queue-capacity."));
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttListenerErrorHandler mqttListenerErrorHandler() {
        return (message, exception) -> {
            MqttMessageHeaderAccessor headers = MqttMessageHeaderAccessor.wrap(message);
            log.error("Listener [{}] failed to process a message from [{}]",
                    headers.getListenerId(), headers.getReceivedTopic(), exception);
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttListenerEndpointRegistry mqttListenerEndpointRegistry() {
        return new MqttListenerEndpointRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    MqttListenerContainerFactory mqttListenerContainerFactory(
            MqttSubscriptionManager subscriptionManager,
            @Qualifier(MqttBeanNames.LISTENER_TASK_EXECUTOR) TaskExecutor taskExecutor,
            MqttListenerErrorHandler errorHandler) {
        return endpoint -> new DefaultMqttListenerContainer(endpoint, subscriptionManager, taskExecutor, errorHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static MqttListenerAnnotationBeanPostProcessor mqttListenerAnnotationBeanPostProcessor() {
        return new MqttListenerAnnotationBeanPostProcessor();
    }

    @Bean(name = MqttBeanNames.HANDLER_METHOD_FACTORY)
    @ConditionalOnMissingBean(name = MqttBeanNames.HANDLER_METHOD_FACTORY)
    public MessageHandlerMethodFactory mqttHandlerMethodFactory(
            @Qualifier(MqttBeanNames.MESSAGE_CONVERTER) MessageConverter messageConverter,
            ObjectProvider<Validator> validator,
            ObjectProvider<HandlerMethodArgumentResolver> customResolvers) {

        DefaultMessageHandlerMethodFactory factory = new DefaultMessageHandlerMethodFactory();
        factory.setMessageConverter(messageConverter);
        validator.ifAvailable(factory::setValidator);

        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();
        resolvers.add(new TopicMethodArgumentResolver());
        resolvers.add(new MqttHeaderAccessorArgumentResolver());
        customResolvers.orderedStream().forEach(resolvers::add);
        factory.setCustomArgumentResolvers(resolvers);

        factory.afterPropertiesSet();
        return factory;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
    static class JacksonConverterConfiguration {

        @Bean(name = MqttBeanNames.MESSAGE_CONVERTER)
        @ConditionalOnMissingBean(name = MqttBeanNames.MESSAGE_CONVERTER)
        MessageConverter mqttMessageConverter(ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapper) {
            List<MessageConverter> converters = MqttMessageConverters.defaults();
            converters.add(JacksonMqttMessageConverterFactory.create(mapper.getIfAvailable()));
            return new CompositeMessageConverter(converters);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("com.fasterxml.jackson.databind.ObjectMapper")
    static class SimpleConverterConfiguration {

        @Bean(name = MqttBeanNames.MESSAGE_CONVERTER)
        @ConditionalOnMissingBean(name = MqttBeanNames.MESSAGE_CONVERTER)
        MessageConverter mqttMessageConverter() {
            return new CompositeMessageConverter(MqttMessageConverters.defaults());
        }
    }

    private static MqttConnectionOptions buildOptions(MqttProperties props,
                                                      ObjectProvider<MqttConnectionOptionsCustomizer> customizers) {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setServerURIs(new String[]{props.getUrl()});
        options.setCleanStart(props.isCleanStart());
        options.setKeepAliveInterval((int) props.getKeepAliveInterval().toSeconds());
        options.setConnectionTimeout((int) props.getConnectionTimeout().toSeconds());
        options.setAutomaticReconnect(props.isAutomaticReconnect());
        options.setAutomaticReconnectDelay(
                (int) props.getReconnectMinDelay().toSeconds(),
                (int) props.getReconnectMaxDelay().toSeconds());

        if (props.getSessionExpiryInterval() != null) {
            options.setSessionExpiryInterval(props.getSessionExpiryInterval().toSeconds());
        }
        if (props.getReceiveMaximum() != null) {
            options.setReceiveMaximum(props.getReceiveMaximum());
        }
        if (props.getMaximumPacketSize() != null) {
            options.setMaximumPacketSize(props.getMaximumPacketSize());
        }
        if (StringUtils.hasText(props.getUsername())) {
            options.setUserName(props.getUsername());
        }
        if (props.getPassword() != null) {
            options.setPassword(props.getPassword().getBytes(StandardCharsets.UTF_8));
        }

        MqttProperties.Will will = props.getWill();
        if (StringUtils.hasText(will.getTopic())) {
            org.eclipse.paho.mqttv5.common.MqttMessage message =
                    new org.eclipse.paho.mqttv5.common.MqttMessage(
                            will.getPayload().getBytes(StandardCharsets.UTF_8));
            message.setQos(will.getQos());
            message.setRetained(will.isRetained());
            options.setWill(will.getTopic(), message);
        }

        customizers.orderedStream().forEach(customizer -> customizer.customize(options));
        return options;
    }

    private static MqttConnectionSettings buildSettings(MqttProperties props) {
        return MqttConnectionSettings.builder(props.getUrl())
                .connectionTimeout(props.getConnectionTimeout())
                .actionTimeout(props.getActionTimeout())
                .disconnectTimeout(props.getDisconnectTimeout())
                .connectRetryInterval(props.getConnectRetryInterval())
                .failFast(props.isFailFast())
                .build();
    }

    private static String resolveClientId(MqttProperties properties) {
        if (StringUtils.hasText(properties.getClientId())) {
            return properties.getClientId();
        }
        return "spring-mqtt-" + UUID.randomUUID().toString().substring(0, 8);
    }


}
