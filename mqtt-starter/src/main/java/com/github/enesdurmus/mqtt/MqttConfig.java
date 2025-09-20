package com.github.enesdurmus.mqtt;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "mqtt", name = "url")
@EnableConfigurationProperties(MqttProperties.class)
class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    @Bean
    public MqttListenerRegistry mqttListenerRegistry() {
        return new MqttListenerRegistry();
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttListenerBeanPostProcessor mqttListenerBeanPostProcessor(MqttListenerRegistry mqttListenerRegistry) {
        return new MqttListenerBeanPostProcessor(mqttListenerRegistry);
    }

    @Bean
    public MqttConnectOptions mqttConnectOptions(MqttProperties mqttProperties) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqttProperties.getUrl()});
        options.setUserName(mqttProperties.getUsername());
        options.setPassword(mqttProperties.getPassword().toCharArray());
        options.setCleanSession(mqttProperties.getCleanSession());
        options.setKeepAliveInterval(mqttProperties.getKeepAliveInterval());
        options.setAutomaticReconnect(true);
        return options;
    }

    @Bean
    public IntegrationFlow mqttInFlow(MqttListenerRegistry registry,
                                      MessageChannel mqttInputChannel,
                                      MqttConnectOptions options) {

        String[] topics = registry.getListeners().keySet().toArray(new String[0]);
        int[] qosArray = registry.getListeners().values().stream()
                .mapToInt(list -> list.get(0).qos())
                .toArray();

        DefaultMqttPahoClientFactory clientFactory = new DefaultMqttPahoClientFactory();
        clientFactory.setConnectionOptions(options);

        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter("spring-mqtt-client", clientFactory, topics);
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(qosArray);
        adapter.setOutputChannel(mqttInputChannel);

        MessageHandler mqttHandler = message -> {
            String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
            if (topic == null || topic.isEmpty()) {
                log.warn("Topic is empty");
                return;
            }

            registry.getListeners().getOrDefault(topic, List.of()).forEach(def -> {
                try {
                    def.method().invoke(def.bean(), message.getPayload().toString());
                } catch (Exception e) {
                    throw new RuntimeException("Error invoking MQTT listener for topic " + topic, e);
                }
            });
        };

        return IntegrationFlow.from(adapter)
                .handle(mqttHandler)
                .get();
    }

    @Bean
    public MqttPahoMessageHandler mqttPahoMessageHandler(MqttConnectOptions options) {
        DefaultMqttPahoClientFactory clientFactory = new DefaultMqttPahoClientFactory();
        clientFactory.setConnectionOptions(options);

        MqttPahoMessageHandler handler = new MqttPahoMessageHandler(
                "spring-mqtt-client-producer",
                clientFactory);
        handler.setAsync(true);
        return handler;
    }

    @Bean
    @ConditionalOnMissingBean
    public MqttTemplate mqttTemplate(MqttPahoMessageHandler handler) {
        return new DefaultMqttTemplate(handler);
    }
}