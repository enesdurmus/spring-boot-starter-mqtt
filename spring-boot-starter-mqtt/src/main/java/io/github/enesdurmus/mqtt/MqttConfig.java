package io.github.enesdurmus.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnProperty(prefix = "mqtt", name = "url")
@EnableConfigurationProperties(MqttProperties.class)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class MqttConfig {

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new JacksonMessageConverter(objectMapper);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public MqttListenerRegistry mqttListenerRegistry() {
        return new MqttListenerRegistry();
    }

    @Bean(name = "mqttListenerExecutor")
    public ThreadPoolTaskExecutor mqttListenerExecutor(MqttProperties mqttProperties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(mqttProperties.getConcurrency());
        executor.setThreadNamePrefix("mqttExecutor-");
        executor.initialize();
        return executor;
    }

    @Bean(initMethod = "start")
    public MqttListenerContainer mqttListenerContainer(MqttClient listenerClient,
                                                       MqttListenerRegistry mqttListenerRegistry,
                                                       @Qualifier("mqttListenerExecutor") ThreadPoolTaskExecutor executor) {
        return new MqttListenerContainer(listenerClient, mqttListenerRegistry, executor);
    }

    @Bean
    public MqttListenerBeanPostProcessor mqttListenerBeanPostProcessor(MqttListenerRegistry mqttListenerRegistry,
                                                                       MessageConverter messageConverter) {
        return new MqttListenerBeanPostProcessor(mqttListenerRegistry, messageConverter);
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttTemplate mqttTemplate(@Qualifier("publisherClient") MqttClient publisherClient,
                                     MessageConverter messageConverter) {
        return new DefaultMqttTemplate(publisherClient, messageConverter);
    }

    @Bean(destroyMethod = "close")
    public MqttClient listenerClient(MqttProperties props) throws MqttException {
        return buildClient(props, "listener-");
    }

    @Bean(name = "publisherClient", destroyMethod = "close")
    @Primary
    public MqttClient publisherClient(MqttProperties props) throws MqttException {
        return buildClient(props, "publisher-");
    }

    private MqttClient buildClient(MqttProperties mqttProperties, String prefix) throws MqttException {
        String brokerUrl = mqttProperties.getUrl();
        String clientId = prefix + mqttProperties.getClientId();

        MqttClient client = new MqttClient(brokerUrl, clientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(mqttProperties.getCleanSession());
        options.setAutomaticReconnect(true);
        options.setUserName(mqttProperties.getUsername());
        options.setPassword(mqttProperties.getPassword().toCharArray());
        options.setConnectionTimeout(mqttProperties.getConnectionTimeOut());
        options.setKeepAliveInterval(mqttProperties.getKeepAliveInterval());

        client.connect(options);
        return client;
    }
}