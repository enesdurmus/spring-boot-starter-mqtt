package io.github.enesdurmus.mqtt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MqttSubscriptionOptionsTests {

    @Test
    void defaultsMatchTheProtocolDefaults() {
        MqttSubscriptionOptions options = MqttSubscriptionOptions.DEFAULTS;

        assertThat(options.getQos()).isZero();
        assertThat(options.getRetainHandling()).isEqualTo(RetainHandling.SEND);
        assertThat(options.isRetainAsPublished()).isFalse();
        assertThat(options.isNoLocal()).isFalse();
    }

    @Test
    void rejectsOutOfRangeQos() {
        assertThatIllegalArgumentException().isThrownBy(() -> MqttSubscriptionOptions.of(3));
        assertThatIllegalArgumentException().isThrownBy(() -> MqttSubscriptionOptions.of(-1));
    }

    @Test
    void mergeTakesTheHighestQos() {
        MqttSubscriptionOptions merged = MqttSubscriptionOptions.of(0).mergeWith(MqttSubscriptionOptions.of(2));

        assertThat(merged.getQos()).isEqualTo(2);
    }

    @Test
    void mergeKeepsTheMostInclusiveRetainHandling() {
        MqttSubscriptionOptions doNotSend = MqttSubscriptionOptions.builder()
                .retainHandling(RetainHandling.DO_NOT_SEND).build();
        MqttSubscriptionOptions send = MqttSubscriptionOptions.builder()
                .retainHandling(RetainHandling.SEND).build();

        assertThat(doNotSend.mergeWith(send).getRetainHandling()).isEqualTo(RetainHandling.SEND);
        assertThat(send.mergeWith(doNotSend).getRetainHandling()).isEqualTo(RetainHandling.SEND);
    }

    @Test
    void mergeEnablesRetainAsPublishedIfEitherSideWantsIt() {
        MqttSubscriptionOptions wanted = MqttSubscriptionOptions.builder().retainAsPublished(true).build();

        assertThat(wanted.mergeWith(MqttSubscriptionOptions.DEFAULTS).isRetainAsPublished()).isTrue();
    }

    @Test
    void mergeKeepsNoLocalOnlyWhenBothSidesWantIt() {
        MqttSubscriptionOptions noLocal = MqttSubscriptionOptions.builder().noLocal(true).build();

        assertThat(noLocal.mergeWith(noLocal).isNoLocal()).isTrue();
        assertThat(noLocal.mergeWith(MqttSubscriptionOptions.DEFAULTS).isNoLocal()).isFalse();
    }

    @Test
    void retainHandlingMapsToSpecificationValues() {
        assertThat(RetainHandling.SEND.value()).isZero();
        assertThat(RetainHandling.SEND_IF_NEW_SUBSCRIPTION.value()).isEqualTo(1);
        assertThat(RetainHandling.DO_NOT_SEND.value()).isEqualTo(2);
        assertThat(RetainHandling.fromValue(2)).isEqualTo(RetainHandling.DO_NOT_SEND);
        assertThatIllegalArgumentException().isThrownBy(() -> RetainHandling.fromValue(3));
    }
}
