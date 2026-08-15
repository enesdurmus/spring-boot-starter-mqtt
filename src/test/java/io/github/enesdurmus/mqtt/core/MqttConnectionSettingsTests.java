package io.github.enesdurmus.mqtt.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MqttConnectionSettingsTests {

    @Test
    void defaultsMatchTheConfigurationProperties() {
        MqttConnectionSettings settings = MqttConnectionSettings.builder("tcp://localhost:1883").build();

        assertThat(settings.getUrl()).isEqualTo("tcp://localhost:1883");
        assertThat(settings.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.getActionTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.getDisconnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.getConnectRetryInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.isFailFast()).isFalse();
    }

    @Test
    void overridesAreKept() {
        MqttConnectionSettings settings = MqttConnectionSettings.builder("tcp://broker:1883")
                .connectionTimeout(Duration.ofSeconds(5))
                .actionTimeout(Duration.ofSeconds(2))
                .disconnectTimeout(Duration.ofSeconds(1))
                .connectRetryInterval(Duration.ofSeconds(3))
                .failFast(true)
                .build();

        assertThat(settings.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.getActionTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(settings.getDisconnectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(settings.getConnectRetryInterval()).isEqualTo(Duration.ofSeconds(3));
        assertThat(settings.isFailFast()).isTrue();
    }

    @Test
    void rejectsAnEmptyUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MqttConnectionSettings.builder(" "))
                .withMessageContaining("url must not be empty");
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        MqttConnectionSettings.Builder builder = MqttConnectionSettings.builder("tcp://localhost:1883");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.actionTimeout(Duration.ZERO))
                .withMessageContaining("actionTimeout must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> builder.connectRetryInterval(Duration.ofSeconds(-1)))
                .withMessageContaining("connectRetryInterval must be positive");
    }
}
