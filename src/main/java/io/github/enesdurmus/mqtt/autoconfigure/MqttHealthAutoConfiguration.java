package io.github.enesdurmus.mqtt.autoconfigure;

import io.github.enesdurmus.mqtt.core.MqttConnection;
import io.github.enesdurmus.mqtt.listener.MqttListenerEndpointRegistry;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Contributes {@code /actuator/health} details for the broker connection and its listeners.
 *
 * <p>Kept as a separate auto-configuration, and conditional only on plain
 * {@code spring-boot-autoconfigure} annotations, so nothing actuator-specific has to be resolved
 * when actuator is absent from the classpath.
 */
@AutoConfiguration(after = MqttAutoConfiguration.class)
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnBean(MqttConnection.class)
@ConditionalOnProperty(name = "management.health.mqtt.enabled", matchIfMissing = true)
public class MqttHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "mqttHealthIndicator")
    public MqttHealthIndicator mqttHealthIndicator(MqttConnection connection,
                                                   MqttListenerEndpointRegistry registry) {
        return new MqttHealthIndicator(connection, registry);
    }
}
