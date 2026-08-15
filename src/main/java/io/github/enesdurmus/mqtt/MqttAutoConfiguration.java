package io.github.enesdurmus.mqtt;

import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
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
public class MqttAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MqttAutoConfiguration.class);

    static final String MESSAGE_CONVERTER_BEAN = "mqttMessageConverter";
    static final String TASK_EXECUTOR_BEAN = "mqttListenerTaskExecutor";
    static final String HANDLER_METHOD_FACTORY_BEAN = "mqttHandlerMethodFactory";
    static final String TASK_SCHEDULER_BEAN = "mqttTaskScheduler";

    @Bean
    @ConditionalOnMissingBean
    public MqttConnection mqttConnection(MqttProperties properties,
                                         ObjectProvider<MqttConnectionOptionsCustomizer> customizers,
                                         @Qualifier(TASK_SCHEDULER_BEAN) TaskScheduler taskScheduler) {
        List<String> errors = properties.validate();
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid MQTT configuration: " + String.join("; ", errors));
        }

        String clientId = resolveClientId(properties);
        MqttConnectionOptions options = buildOptions(properties, customizers);
        try {
            MqttAsyncClient client = new MqttAsyncClient(properties.getUrl(), clientId, new MemoryPersistence());
            return new MqttConnection(clientId, client, options, properties, taskScheduler);
        } catch (MqttException e) {
            throw new MqttConnectionException(
                    "Failed to create MQTT client [" + clientId + "] for " + properties.getUrl(), e);
        }
    }

    @Bean(name = TASK_SCHEDULER_BEAN)
    @ConditionalOnMissingBean(name = TASK_SCHEDULER_BEAN)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public TaskScheduler mqttTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("mqtt-reconnect-");
        scheduler.setDaemon(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    MqttSubscriptionManager mqttSubscriptionManager(MqttConnection connection) {
        return new MqttSubscriptionManager(connection);
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttOperations mqttTemplate(MqttConnection connection,
                                       MqttProperties properties,
                                       @Qualifier(MESSAGE_CONVERTER_BEAN) MessageConverter messageConverter) {
        MqttTemplate template = new MqttTemplate(connection, properties.getPublisher().isAwaitDelivery());
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean(name = TASK_EXECUTOR_BEAN, destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = TASK_EXECUTOR_BEAN)
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
            @Qualifier(TASK_EXECUTOR_BEAN) TaskExecutor taskExecutor,
            MqttListenerErrorHandler errorHandler) {
        return endpoint -> new DefaultMqttListenerContainer(endpoint, subscriptionManager, taskExecutor, errorHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static MqttListenerAnnotationBeanPostProcessor mqttListenerAnnotationBeanPostProcessor() {
        return new MqttListenerAnnotationBeanPostProcessor();
    }

    @Bean(name = HANDLER_METHOD_FACTORY_BEAN)
    @ConditionalOnMissingBean(name = HANDLER_METHOD_FACTORY_BEAN)
    public MessageHandlerMethodFactory mqttHandlerMethodFactory(
            @Qualifier(MESSAGE_CONVERTER_BEAN) MessageConverter messageConverter,
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

        @Bean(name = MESSAGE_CONVERTER_BEAN)
        @ConditionalOnMissingBean(name = MESSAGE_CONVERTER_BEAN)
        MessageConverter mqttMessageConverter(ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapper) {
            List<MessageConverter> converters = baseConverters();
            converters.add(JacksonMqttMessageConverterFactory.create(mapper.getIfAvailable()));
            return new CompositeMessageConverter(converters);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingClass("com.fasterxml.jackson.databind.ObjectMapper")
    static class SimpleConverterConfiguration {

        @Bean(name = MESSAGE_CONVERTER_BEAN)
        @ConditionalOnMissingBean(name = MESSAGE_CONVERTER_BEAN)
        MessageConverter mqttMessageConverter() {
            return new CompositeMessageConverter(baseConverters());
        }
    }

    private static List<MessageConverter> baseConverters() {
        List<MessageConverter> converters = new ArrayList<>();
        converters.add(new ByteArrayMessageConverter());
        converters.add(new StringMessageConverter(StandardCharsets.UTF_8));
        return converters;
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
            message.setQos(Qos.validate(will.getQos()));
            message.setRetained(will.isRetained());
            options.setWill(will.getTopic(), message);
        }

        customizers.orderedStream().forEach(customizer -> customizer.customize(options));
        return options;
    }

    private static String resolveClientId(MqttProperties properties) {
        if (StringUtils.hasText(properties.getClientId())) {
            return properties.getClientId();
        }
        return "spring-mqtt-" + UUID.randomUUID().toString().substring(0, 8);
    }


}
