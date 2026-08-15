package io.github.enesdurmus.mqtt.core;

import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.springframework.scheduling.TaskScheduler;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MqttConnectionTests {

    private MqttConnection connection;
    private MqttCallback callback;

    @BeforeEach
    void setUp() {
        MqttAsyncClient client = mock(MqttAsyncClient.class);
        connection = new MqttConnection("client-1", client, new MqttConnectionOptions(),
                MqttConnectionSettings.builder("tcp://localhost:1883").build(), mock(TaskScheduler.class));

        ArgumentCaptor<MqttCallback> captor = ArgumentCaptor.forClass(MqttCallback.class);
        verify(client).setCallback(captor.capture());
        callback = captor.getValue();
    }

    @Test
    void inboundMessagesGoToTheRegisteredListener() throws Exception {
        List<String> received = new ArrayList<>();
        connection.setMessageListener((topic, message) -> received.add(topic));

        callback.messageArrived("devices/pump/events", new MqttMessage("x".getBytes()));

        assertThat(received).containsExactly("devices/pump/events");
    }

    @Test
    void aFailingMessageListenerDoesNotBringTheConnectionDown() throws Exception {
        connection.setMessageListener((topic, message) -> {
            throw new IllegalStateException("boom");
        });

        callback.messageArrived("t", new MqttMessage("x".getBytes()));
    }

    @Test
    void messagesArrivingWithoutAListenerAreDropped() throws Exception {
        callback.messageArrived("t", new MqttMessage("x".getBytes()));
    }

    private static MqttConnectionListener onConnected(List<String> events) {
        return new MqttConnectionListener() {
            @Override
            public void onConnected(boolean reconnect) {
                events.add("connected");
            }
        };
    }

    @Test
    void listenersAreNotifiedOnConnectAndDisconnect() {
        List<String> events = new ArrayList<>();
        connection.addListener(new MqttConnectionListener() {
            @Override
            public void onConnected(boolean reconnect) {
                events.add("connected:" + reconnect);
            }

            @Override
            public void onDisconnected(Throwable cause) {
                events.add("disconnected");
            }
        });

        callback.connectComplete(false, "tcp://localhost:1883");
        callback.disconnected(new MqttDisconnectResponse(null));

        assertThat(events).containsExactly("connected:false", "disconnected");
    }

    @Test
    void registeringTheSameListenerTwiceNotifiesItOnce() {
        List<String> events = new ArrayList<>();
        MqttConnectionListener listener = onConnected(events);

        connection.addListener(listener);
        connection.addListener(listener);
        callback.connectComplete(true, "tcp://localhost:1883");

        assertThat(events).containsExactly("connected");
    }

    @Test
    void removedListenersStopReceivingEvents() {
        List<String> events = new ArrayList<>();
        MqttConnectionListener listener = onConnected(events);

        connection.addListener(listener);
        connection.removeListener(listener);
        callback.connectComplete(true, "tcp://localhost:1883");

        assertThat(events).isEmpty();
    }

    @Test
    void aFailingListenerDoesNotStopTheOthers() {
        List<String> events = new ArrayList<>();
        connection.addListener(new MqttConnectionListener() {
            @Override
            public void onConnected(boolean reconnect) {
                throw new IllegalStateException("boom");
            }
        });
        connection.addListener(onConnected(events));

        callback.connectComplete(true, "tcp://localhost:1883");

        assertThat(events).containsExactly("connected");
    }
}
