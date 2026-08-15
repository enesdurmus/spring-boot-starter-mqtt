# MQTT Spring Boot Starter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.enesdurmus/mqtt-spring-boot-starter.svg)](https://central.sonatype.com/artifact/io.github.enesdurmus/mqtt-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Annotation-driven MQTT 5 listeners for Spring Boot, in the style of `@KafkaListener` and
`@RabbitListener`. Built on `spring-messaging`, so argument binding, payload conversion and
validation work the way they do everywhere else in Spring.

```java
@MqttListener(topics = "sensors/+/temperature", qos = 1)
public void onReading(@Topic String topic, @Payload Reading reading) {
    log.info("{} reported {}", topic, reading.value());
}
```

## Installation

```xml
<dependency>
    <groupId>io.github.enesdurmus</groupId>
    <artifactId>mqtt-spring-boot-starter</artifactId>
    <version>2.0.0</version>
</dependency>
```

```groovy
implementation 'io.github.enesdurmus:mqtt-spring-boot-starter:2.0.0'
```

Requires Java 17+, Spring Boot 3.x, and a broker that speaks **MQTT 5** (Mosquitto 2.x, HiveMQ,
EMQX, VerneMQ, AWS IoT Core). Jackson is optional — add `jackson-databind` only if you publish or
consume object payloads as JSON. GraalVM native images are supported out of the box.

## Packages

| Package | Contents |
|---|---|
| `io.github.enesdurmus.mqtt` | Exceptions — everything thrown across the API extends `MqttClientException` |
| `…mqtt.annotation` | `@MqttListener`, `@Topic` |
| `…mqtt.core` | `MqttOperations` / `MqttTemplate`, `MqttConnection` / `MqttConnectionSettings`, `MqttSubscriptionManager`, `MqttHeaders`, `MqttMessageHeaderAccessor`, `MqttSubscriptionOptions` |
| `…mqtt.listener` | Endpoints, containers, `MqttListenerEndpointRegistry`, `MqttListenerConfigurer`, `MqttListenerErrorHandler` |
| `…mqtt.support` | Argument resolvers, `MqttMessageConverters` and other extension points |
| `…mqtt.autoconfigure` | `MqttAutoConfiguration`, `MqttProperties`, health indicator |

## Configuration

```yaml
mqtt:
  url: tcp://localhost:1883
```

That is the whole required configuration; the starter backs off entirely when `mqtt.url` is absent.

<details>
<summary>All properties</summary>

| Property | Description | Default |
|---|---|---|
| `mqtt.url` | Broker URL: `tcp://`, `ssl://`, `ws://`, `wss://` | *(required)* |
| `mqtt.client-id` | Client identifier | `spring-mqtt-<random>` |
| `mqtt.username` / `mqtt.password` | Broker credentials | - |
| `mqtt.clean-start` | Discard broker-held session state on connect | `true` |
| `mqtt.session-expiry-interval` | Session lifetime after disconnect (needs `clean-start: false`) | broker default |
| `mqtt.keep-alive-interval` | Ping interval | `60s` |
| `mqtt.connection-timeout` | Connect handshake timeout | `30s` |
| `mqtt.action-timeout` | Subscribe / unsubscribe / awaited publish timeout | `10s` |
| `mqtt.disconnect-timeout` | Graceful disconnect budget at shutdown | `5s` |
| `mqtt.fail-fast` | Fail application startup if the broker is unreachable | `false` |
| `mqtt.connect-retry-interval` | Retry delay before the first successful connect | `10s` |
| `mqtt.automatic-reconnect` | Reconnect after losing an established connection | `true` |
| `mqtt.reconnect-min-delay` / `mqtt.reconnect-max-delay` | Reconnect backoff bounds | `1s` / `120s` |
| `mqtt.receive-maximum` | Max in-flight QoS 1/2 messages towards this client | broker default |
| `mqtt.maximum-packet-size` | Largest accepted packet, in bytes | broker default |
| `mqtt.listener.concurrency` | Core listener threads | `3` |
| `mqtt.listener.max-concurrency` | Max listener threads | `concurrency * 2` |
| `mqtt.listener.queue-capacity` | Queued messages awaiting a thread | `100` |
| `mqtt.listener.shutdown-timeout` | Wait for in-flight messages at shutdown | `30s` |
| `mqtt.publisher.await-delivery` | Block publishes until the broker acknowledges | `true` |
| `mqtt.will.topic` | Last Will topic (empty disables the will) | - |
| `mqtt.will.payload` / `mqtt.will.qos` / `mqtt.will.retained` | Last Will message | `""` / `0` / `false` |

</details>

## Listening

### Argument binding

Listener methods use Spring's own messaging annotations, plus `@Topic` for the concrete topic a
message arrived on:

```java
@MqttListener(topics = "devices/+/events", qos = 1)
public void onEvent(@Topic String topic,
                    @Payload DeviceEvent event,
                    @Header(MqttHeaders.RECEIVED_QOS) int qos,
                    @Header(MqttHeaders.USER_PROPERTIES) Map<String, String> properties) {
}
```

| Parameter | Binds to |
|---|---|
| `@Payload T` | Payload converted to `T` (`String` and `byte[]` pass through, everything else is JSON) |
| `@Topic String` | The concrete topic, not the subscribed filter |
| `@Header(MqttHeaders.…)` | A single header — see `MqttHeaders` for the full list |
| `@Headers Map<String, Object>` | All headers |
| `Message<byte[]>` | The raw Spring message |
| `MqttMessageHeaderAccessor` | Typed access to every MQTT header at once |

`@Valid` on a `@Payload` parameter is honoured when a `Validator` bean is present.

### Topic placeholders

`${...}` property placeholders and `#{...}` SpEL expressions are resolved in `topics`, `id` and
`autoStartup`. A single entry may list several comma-separated filters.

```java
@MqttListener(topics = "${app.sensor-topic}", id = "sensor-listener")
public void onSensor(@Payload SensorData data) {
}
```

### Multiple listeners on one topic

Every listener subscribed to a filter receives every message. The starter keeps one broker
subscription per filter, merges the listeners' options, fans messages out, and only unsubscribes
once the last listener is gone.

## Retained messages

Retained messages are the broker's last-known-value store: a subscriber receives the retained
message for a topic the moment it subscribes, without waiting for the next publish.

### Publishing

```java
mqtt.retain("devices/pump-1/state", new State("running"), 1);   // becomes the last known value
mqtt.publish("devices/pump-1/events", event, 1);                // ordinary, not retained
mqtt.clearRetained("devices/pump-1/state", 1);                  // removes the stored value
```

### Controlling the replay on subscribe

MQTT 5 lets a subscriber decide whether it wants that replay at all:

```java
// Never receive the retained replay — only live events from here on.
@MqttListener(topics = "devices/+/state", retainHandling = RetainHandling.DO_NOT_SEND)
public void onLiveStateOnly(@Payload State state) {
}

// Receive current state on first subscribe, but not again on every reconnect.
@MqttListener(topics = "devices/+/state", qos = 1,
              retainHandling = RetainHandling.SEND_IF_NEW_SUBSCRIPTION)
public void onStateOnce(@Payload State state) {
}
```

| `RetainHandling` | Replay on subscribe |
|---|---|
| `SEND` *(default)* | Always |
| `SEND_IF_NEW_SUBSCRIPTION` | Only when the subscription did not already exist |
| `DO_NOT_SEND` | Never |

### Telling retained apart from live

By default the broker clears the RETAIN flag on forwarded messages, so
`@Header(MqttHeaders.RECEIVED_RETAINED)` is only ever `true` for the replay at subscribe time.
`retainAsPublished = true` makes the broker preserve the flag as published:

```java
@MqttListener(topics = "devices/+/state", retainAsPublished = true)
public void onState(@Payload State state,
                    @Header(MqttHeaders.RECEIVED_RETAINED) boolean retained) {
    if (retained) {
        // replayed history, not a fresh transition
    }
}
```

`noLocal = true` additionally suppresses messages this same client published.

## Publishing

`MqttOperations` extends Spring's `MessageSendingOperations`, so both styles are available:

```java
@Service
class Notifier {

    private final MqttOperations mqtt;

    Notifier(MqttOperations mqtt) {
        this.mqtt = mqtt;
    }

    void notifyAll(Alert alert) {
        mqtt.publish("alerts", alert, 1);
        mqtt.convertAndSend("alerts", alert, Map.of(
                MqttHeaders.QOS, 1,
                MqttHeaders.RESPONSE_TOPIC, "alerts/ack",
                MqttHeaders.USER_PROPERTIES, Map.of("tenant", "acme")));
    }
}
```

Failures raise `MqttPublishException`; Paho's checked exceptions never cross the API.

## Programmatic listeners

When topics are only known at runtime, register endpoints through `MqttListenerConfigurer`:

```java
@Configuration
class DeviceListeners implements MqttListenerConfigurer {

    private final DeviceRepository devices;

    @Override
    public void configureMqttListeners(MqttListenerEndpointRegistrar registrar) {
        devices.findAll().forEach(device -> registrar.registerEndpoint(
                SimpleMqttListenerEndpoint.builder("devices/" + device.id() + "/events")
                        .id("device-" + device.id())
                        .qos(1)
                        .retainHandling(RetainHandling.DO_NOT_SEND)
                        .messageHandler(message -> process(device, message))
                        .build()));
    }
}
```

## Managing listeners at runtime

Containers can be started and stopped by id without touching the broker connection:

```java
@Autowired
MqttListenerEndpointRegistry registry;

registry.getListenerContainer("sensor-listener").stop();
registry.getListenerContainerIds();
```

Use `autoStartup = "false"` on `@MqttListener` to register a listener without subscribing at startup.

## Error handling

Listener failures go to an error handler instead of killing the subscription.

```java
@Bean
MqttListenerErrorHandler mqttListenerErrorHandler() {   // application-wide default
    return (message, exception) -> deadLetters.record(message, exception);
}

@MqttListener(topics = "critical/#", errorHandler = "criticalErrorHandler")
public void onCritical(@Payload CriticalEvent event) {
}
```

## Connection lifecycle

The broker connection is established when the application context starts, not when beans are
created, so an unreachable broker does not block startup unless `mqtt.fail-fast: true`. The client
retries the initial connect on `mqtt.connect-retry-interval`, and all subscriptions are restored
automatically after a reconnect.

Declare an `MqttConnectionListener` bean to observe it:

```java
@Bean
MqttConnectionListener connectionLogger() {
    return new MqttConnectionListener() {
        @Override
        public void onConnected(boolean reconnect) { }

        @Override
        public void onDisconnected(Throwable cause) { }
    };
}
```

## Extension points

Every bean the starter declares is conditional; declaring your own replaces it.

| Bean / interface | Replaces |
|---|---|
| `MqttConnectionOptionsCustomizer` | TLS socket factories, WebSocket headers, auth data — anything the properties do not expose |
| `MessageConverter` named `mqttMessageConverter` | Payload conversion — start from `MqttMessageConverters.defaults()` to keep the built-in ones |
| `HandlerMethodArgumentResolver` beans | Extra listener parameter types |
| `MqttListenerContainerFactory` | Container creation and dispatch |
| `MqttListenerErrorHandler` | Default error handling |
| `TaskExecutor` named `mqttListenerTaskExecutor` | The listener thread pool |
| `MqttListenerConfigurer` | Programmatic endpoint registration |

TLS example:

```java
@Bean
MqttConnectionOptionsCustomizer tlsCustomizer(SSLContext sslContext) {
    return options -> options.setSocketFactory(sslContext.getSocketFactory());
}
```

## Actuator

With `spring-boot-starter-actuator` on the classpath, `/actuator/health` reports the connection and
every listener. Disable with `management.health.mqtt.enabled: false`.

## Migrating from 1.x

2.0.0 moves from Paho MQTT 3.1.1 to Paho MQTT 5 and removes Paho types from the public API.

Types are also split out of the single flat package — see [Packages](#packages). The names are
unchanged, so search and replace `import io.github.enesdurmus.mqtt.` and let the compiler point
out the rest.

| 1.x | 2.0.0 |
|---|---|
| `MqttTemplate` | `MqttOperations` (`MqttTemplate` is the implementation) |
| `publish(topic, payload)` throws `MqttException` | Same signature, throws unchecked `MqttPublishException` |
| `io.github.enesdurmus.mqtt.Payload` / `Header` | `org.springframework.messaging.handler.annotation.Payload` / `Header` |
| `@Header("qos")`, `@Header("retained")` | `@Header(MqttHeaders.RECEIVED_QOS)`, `@Header(MqttHeaders.RECEIVED_RETAINED)` |
| `org.eclipse.paho…MqttMessage` parameter | `Message<byte[]>` or `MqttMessageHeaderAccessor` |
| `MqttMessageHandler` | `org.springframework.messaging.MessageHandler` |
| `MqttListenerContainerFactory.createContainer(...)` builder | `SimpleMqttListenerEndpoint.builder(...)` + `MqttListenerConfigurer` |
| `mqtt.clean-session` | `mqtt.clean-start` |
| `mqtt.concurrency`, `mqtt.queue-capacity` | `mqtt.listener.concurrency`, `mqtt.listener.queue-capacity` |
| `mqtt.keep-alive-interval: 60` (seconds) | `mqtt.keep-alive-interval: 60s` (duration) |

Two behavioural fixes worth knowing about: several listeners may now share a topic (previously the
last registration silently displaced the others), and payloads are UTF-8 rather than the platform
default charset.

## Building

```bash
./mvnw verify
```

Unit tests (`*Tests`) run under Surefire in `test`; integration tests (`*IT`) run under Failsafe in
`integration-test`, against a real HiveMQ broker via Testcontainers. They skip themselves when
Docker is unavailable — pass `-Dmqtt.it.required=true` to turn that skip into a failure, as CI does.

Release notes live in [CHANGELOG.md](CHANGELOG.md); the release procedure in
[RELEASING.md](RELEASING.md).

## License

[Apache License 2.0](LICENSE)
