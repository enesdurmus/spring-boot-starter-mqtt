package io.github.enesdurmus.mqtt.autoconfigure;

import io.github.enesdurmus.mqtt.core.MqttConnection;
import io.github.enesdurmus.mqtt.core.MqttConnectionListener;
import io.github.enesdurmus.mqtt.core.MqttConnectionOptionsCustomizer;
import io.github.enesdurmus.mqtt.core.MqttOperations;
import io.github.enesdurmus.mqtt.listener.MqttListenerContainerFactory;
import io.github.enesdurmus.mqtt.listener.MqttListenerEndpointRegistry;
import io.github.enesdurmus.mqtt.listener.MqttListenerErrorHandler;

import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MessageConverter;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MqttAutoConfigurationTests {

    /** Port 1 is never bound, so these tests never touch a broker that happens to run locally. */
    private static final String UNREACHABLE_BROKER = "mqtt.url=tcp://localhost:1";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, MqttAutoConfiguration.class));

    @Test
    void backsOffEntirelyWithoutABrokerUrl() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(MqttConnection.class)
                .doesNotHaveBean(MqttOperations.class)
                .doesNotHaveBean(MqttListenerEndpointRegistry.class));
    }

    @Test
    void registersTheInfrastructureWhenAUrlIsPresent() {
        runner.withPropertyValues(UNREACHABLE_BROKER).run(context -> assertThat(context)
                .hasSingleBean(MqttConnection.class)
                .hasSingleBean(MqttOperations.class)
                .hasSingleBean(MqttListenerEndpointRegistry.class)
                .hasSingleBean(MqttListenerContainerFactory.class)
                .hasSingleBean(MqttListenerErrorHandler.class));
    }

    @Test
    void doesNotDefineAnObjectMapperBeanOfItsOwn() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MqttAutoConfiguration.class))
                .withPropertyValues(UNREACHABLE_BROKER)
                .run(context -> assertThat(context).doesNotHaveBean("objectMapper"));
    }

    @Test
    void startsWithoutJacksonOnTheClasspath() {
        runner.withPropertyValues(UNREACHABLE_BROKER)
                .withClassLoader(new FilteredClassLoader("com.fasterxml.jackson.databind"))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasBean("mqttMessageConverter");
                    MessageConverter converter = context.getBean("mqttMessageConverter", MessageConverter.class);
                    assertThat(converter.toMessage("plain", null)).isNotNull();
                });
    }

    @Test
    void startsWithoutActuatorOnTheClasspath() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class, MqttAutoConfiguration.class,
                        MqttHealthAutoConfiguration.class))
                .withPropertyValues(UNREACHABLE_BROKER)
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.actuate"))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(MqttConnection.class)
                        .doesNotHaveBean("mqttHealthIndicator"));
    }

    @Test
    void contributesAHealthIndicatorWhenActuatorIsPresent() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class, MqttAutoConfiguration.class,
                        MqttHealthAutoConfiguration.class))
                .withPropertyValues(UNREACHABLE_BROKER)
                .run(context -> {
                    assertThat(context).hasSingleBean(MqttHealthIndicator.class);
                    assertThat(context.getBean(MqttHealthIndicator.class).health().getStatus().getCode())
                            .isEqualTo("DOWN");
                });
    }

    @Test
    void connectionIsNotEstablishedDuringContextRefresh() {
        runner.withPropertyValues(UNREACHABLE_BROKER, "spring.main.lazy-initialization=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void willIsAppliedToTheConnectionOptions() {
        runner.withPropertyValues(
                        UNREACHABLE_BROKER,
                        "mqtt.will.topic=status/app",
                        "mqtt.will.payload=offline",
                        "mqtt.will.qos=1",
                        "mqtt.will.retained=true")
                .withUserConfiguration(CapturingCustomizerConfiguration.class)
                .run(context -> {
                    context.getBean(MqttConnection.class);
                    MqttConnectionOptions options = context.getBean(CapturingCustomizer.class).captured;
                    assertThat(options.getWillDestination()).isEqualTo("status/app");
                    assertThat(options.getWillMessage().getQos()).isEqualTo(1);
                    assertThat(options.getWillMessage().isRetained()).isTrue();
                    assertThat(options.getWillMessage().getPayload())
                            .isEqualTo("offline".getBytes(StandardCharsets.UTF_8));
                });
    }

    @Test
    void connectionPropertiesReachPaho() {
        runner.withPropertyValues(
                        UNREACHABLE_BROKER,
                        "mqtt.username=user",
                        "mqtt.password=secret",
                        "mqtt.clean-start=false",
                        "mqtt.session-expiry-interval=1h",
                        "mqtt.keep-alive-interval=45s",
                        "mqtt.receive-maximum=50")
                .withUserConfiguration(CapturingCustomizerConfiguration.class)
                .run(context -> {
                    context.getBean(MqttConnection.class);
                    MqttConnectionOptions options = context.getBean(CapturingCustomizer.class).captured;
                    assertThat(options.getUserName()).isEqualTo("user");
                    assertThat(options.getPassword()).isEqualTo("secret".getBytes(StandardCharsets.UTF_8));
                    assertThat(options.isCleanStart()).isFalse();
                    assertThat(options.getSessionExpiryInterval()).isEqualTo(3600L);
                    assertThat(options.getKeepAliveInterval()).isEqualTo(45);
                    assertThat(options.getReceiveMaximum()).isEqualTo(50);
                });
    }

    @Test
    void rejectsAnInvalidListenerPoolConfiguration() {
        runner.withPropertyValues(
                        UNREACHABLE_BROKER,
                        "mqtt.listener.concurrency=4",
                        "mqtt.listener.max-concurrency=2")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Invalid MQTT configuration: "
                                    + "mqtt.listener.max-concurrency must not be smaller "
                                    + "than mqtt.listener.concurrency");
                });
    }

    @Test
    void rejectsANonPositiveTimeout() {
        runner.withPropertyValues(UNREACHABLE_BROKER, "mqtt.action-timeout=0s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Invalid MQTT configuration: "
                                    + "mqtt.action-timeout must be positive");
                });
    }

    @Test
    void userSuppliedBeansReplaceTheDefaults() {
        runner.withPropertyValues(UNREACHABLE_BROKER)
                .withUserConfiguration(CustomErrorHandlerConfiguration.class)
                .run(context -> assertThat(context.getBean(MqttListenerErrorHandler.class))
                        .isSameAs(context.getBean("myErrorHandler")));
    }

    @Test
    void connectionListenerBeansAreAttachedToTheConnection() {
        runner.withPropertyValues(UNREACHABLE_BROKER)
                .withUserConfiguration(ConnectionListenerConfiguration.class)
                .run(context -> {
                    MqttConnection connection = context.getBean(MqttConnection.class);
                    MqttConnectionListener listener = context.getBean("recordingListener", MqttConnectionListener.class);

                    // Already attached, so a second attempt is a no-op and removal is enough to detach.
                    connection.addListener(listener);
                    assertThat(connection.removeListener(listener)).isTrue();
                    assertThat(connection.removeListener(listener)).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ConnectionListenerConfiguration {

        @Bean
        MqttConnectionListener recordingListener() {
            return new MqttConnectionListener() {
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CapturingCustomizerConfiguration {

        @Bean
        CapturingCustomizer capturingCustomizer() {
            return new CapturingCustomizer();
        }
    }

    static class CapturingCustomizer implements MqttConnectionOptionsCustomizer {

        private MqttConnectionOptions captured;

        @Override
        public void customize(MqttConnectionOptions options) {
            this.captured = options;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomErrorHandlerConfiguration {

        @Bean
        MqttListenerErrorHandler myErrorHandler() {
            return (message, exception) -> {
            };
        }
    }
}
