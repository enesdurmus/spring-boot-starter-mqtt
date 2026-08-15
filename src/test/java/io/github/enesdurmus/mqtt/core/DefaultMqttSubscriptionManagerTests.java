package io.github.enesdurmus.mqtt.core;

import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DefaultMqttSubscriptionManagerTests {

    private MqttConnection connection;
    private DefaultMqttSubscriptionManager manager;

    @BeforeEach
    void setUp() {
        connection = mock(MqttConnection.class);
        manager = DefaultMqttSubscriptionManager.create(connection);
    }

    private IMqttMessageListener lastListener() {
        ArgumentCaptor<IMqttMessageListener> captor = ArgumentCaptor.forClass(IMqttMessageListener.class);
        verify(connection, org.mockito.Mockito.atLeastOnce()).subscribe(any(), captor.capture());
        return captor.getValue();
    }

    private List<MqttSubscription> subscriptions() {
        ArgumentCaptor<MqttSubscription> captor = ArgumentCaptor.forClass(MqttSubscription.class);
        verify(connection, org.mockito.Mockito.atLeastOnce()).subscribe(captor.capture(), any());
        return captor.getAllValues();
    }

    private static void deliver(IMqttMessageListener listener, String topic, String payload) throws Exception {
        listener.messageArrived(topic, new org.eclipse.paho.mqttv5.common.MqttMessage(
                payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void twoListenersOnTheSameFilterBothReceiveTheMessage() throws Exception {
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();

        manager.subscribe("sensor/temp", MqttSubscriptionOptions.DEFAULTS,
                message -> first.add(new String((byte[]) message.getPayload(), StandardCharsets.UTF_8)));
        manager.subscribe("sensor/temp", MqttSubscriptionOptions.DEFAULTS,
                message -> second.add(new String((byte[]) message.getPayload(), StandardCharsets.UTF_8)));

        deliver(lastListener(), "sensor/temp", "21.5");

        assertThat(first).containsExactly("21.5");
        assertThat(second).containsExactly("21.5");
    }

    @Test
    void oneFailingListenerDoesNotStarveTheOthers() throws Exception {
        List<String> received = new ArrayList<>();

        manager.subscribe("t", MqttSubscriptionOptions.DEFAULTS, message -> {
            throw new IllegalStateException("boom");
        });
        manager.subscribe("t", MqttSubscriptionOptions.DEFAULTS, message -> received.add("ok"));

        deliver(lastListener(), "t", "x");

        assertThat(received).containsExactly("ok");
    }

    @Test
    void sharedFilterSubscribesWithMergedOptions() {
        manager.subscribe("t", MqttSubscriptionOptions.of(0), message -> {
        });
        manager.subscribe("t", MqttSubscriptionOptions.builder()
                .qos(2).retainHandling(RetainHandling.DO_NOT_SEND).build(), message -> {
        });

        MqttSubscription last = subscriptions().get(subscriptions().size() - 1);
        assertThat(last.getQos()).isEqualTo(2);
        assertThat(last.getRetainHandling()).isEqualTo(RetainHandling.SEND.value());
    }

    @Test
    void identicalOptionsDoNotTriggerASecondSubscribe() {
        manager.subscribe("t", MqttSubscriptionOptions.of(1), message -> {
        });
        manager.subscribe("t", MqttSubscriptionOptions.of(1), message -> {
        });

        verify(connection, times(1)).subscribe(any(), any());
    }

    @Test
    void retainOptionsReachTheBroker() {
        manager.subscribe("state/#", MqttSubscriptionOptions.builder()
                .qos(1)
                .retainHandling(RetainHandling.DO_NOT_SEND)
                .retainAsPublished(true)
                .noLocal(true)
                .build(), message -> {
        });

        MqttSubscription subscription = subscriptions().get(0);
        assertThat(subscription.getTopic()).isEqualTo("state/#");
        assertThat(subscription.getQos()).isEqualTo(1);
        assertThat(subscription.getRetainHandling()).isEqualTo(2);
        assertThat(subscription.isRetainAsPublished()).isTrue();
        assertThat(subscription.isNoLocal()).isTrue();
    }

    @Test
    void unsubscribeOnlyHappensAfterTheLastListenerIsGone() {
        MqttSubscriptionManager.SubscriptionHandle first =
                manager.subscribe("t", MqttSubscriptionOptions.DEFAULTS, message -> {
                });
        MqttSubscriptionManager.SubscriptionHandle second =
                manager.subscribe("t", MqttSubscriptionOptions.DEFAULTS, message -> {
                });

        first.cancel();
        verify(connection, never()).unsubscribe("t");

        second.cancel();
        verify(connection).unsubscribe("t");
    }

    @Test
    void cancellingTwiceUnsubscribesOnce() {
        MqttSubscriptionManager.SubscriptionHandle handle =
                manager.subscribe("t", MqttSubscriptionOptions.DEFAULTS, message -> {
                });

        handle.cancel();
        handle.cancel();

        verify(connection, times(1)).unsubscribe("t");
    }

    @Test
    void cancelledListenerStopsReceiving() throws Exception {
        List<String> received = new ArrayList<>();
        MqttSubscriptionManager.SubscriptionHandle cancelled =
                manager.subscribe("t", MqttSubscriptionOptions.DEFAULTS, message -> received.add("gone"));
        manager.subscribe("t", MqttSubscriptionOptions.DEFAULTS, message -> received.add("kept"));

        IMqttMessageListener listener = lastListener();
        cancelled.cancel();
        deliver(listener, "t", "x");

        assertThat(received).containsExactly("kept");
    }

    @Test
    void subscriptionsAreRestoredAfterReconnect() {
        manager.subscribe("a", MqttSubscriptionOptions.of(1), message -> {
        });
        manager.subscribe("b", MqttSubscriptionOptions.of(2), message -> {
        });

        manager.onConnected(true);

        assertThat(subscriptions()).extracting(MqttSubscription::getTopic)
                .containsExactly("a", "b", "a", "b");
    }

    @Test
    void firstConnectDoesNotResubscribe() {
        manager.subscribe("a", MqttSubscriptionOptions.DEFAULTS, message -> {
        });

        manager.onConnected(false);

        verify(connection, times(1)).subscribe(any(), any());
    }

    @Test
    void inboundMessageCarriesTheConcreteTopic() throws Exception {
        List<Message<?>> received = new ArrayList<>();
        manager.subscribe("devices/#", MqttSubscriptionOptions.DEFAULTS, received::add);

        deliver(lastListener(), "devices/thermostat-1/events", "{}");

        assertThat(MqttMessageHeaderAccessor.wrap(received.get(0)).getReceivedTopic())
                .isEqualTo("devices/thermostat-1/events");
    }
}
