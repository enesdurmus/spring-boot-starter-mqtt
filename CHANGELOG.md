# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-08-15

Complete rewrite on MQTT 5 (Paho `mqttv5`) and `spring-messaging`. This is a breaking release;
see [Migrating from 1.x](#migrating-from-1x).

### Added

- MQTT 5 subscription options on `@MqttListener`: `retainHandling`, `retainAsPublished`, `noLocal`.
- Retained-message API on `MqttOperations`: `retain(...)` and `clearRetained(...)`.
- MQTT 5 message properties surfaced as headers — content type, response topic, correlation data,
  message expiry interval and user properties — with typed access through
  `MqttMessageHeaderAccessor`.
- `MqttListenerConfigurer` and `SimpleMqttListenerEndpoint` for registering listeners
  programmatically.
- `MqttListenerEndpointRegistry` for starting and stopping individual listeners by id at runtime.
- `MqttListenerErrorHandler`, per-listener or application-wide.
- `MqttConnectionOptionsCustomizer` for Paho settings not exposed through `mqtt.*` properties.
- `MqttConnectionListener` beans are attached to the connection and notified on connect and
  disconnect.
- Actuator health contribution reporting the connection and every listener's state.
- GraalVM native image support: reflection hints for `@MqttListener` methods and for the Paho
  internals a native image cannot discover statically.
- `Automatic-Module-Name: io.github.enesdurmus.mqtt` in the jar manifest.
- `MqttConnectionSettings`, the timing and failure-handling values an `MqttConnection` needs.
  Building one by hand is all it takes to use `MqttConnection` outside the auto-configuration.
- `MqttSubscriptionManager`, the interface listener containers subscribe through —
  `DefaultMqttSubscriptionManager` is the implementation, replaceable by declaring a bean of the
  interface.
- `MqttMessageConverters` and `JacksonMqttMessageConverterFactory`, the default converter chain,
  so a custom `mqttMessageConverter` bean can build on it instead of reproducing it.

### Changed

- **Types are split across packages** instead of one flat package — see the migration table below.
- Listener invocation goes through Spring's `InvocableHandlerMethod`, so argument binding,
  payload conversion and `@Valid` behave as they do for `@KafkaListener` and `@RabbitListener`.
- Messages are dispatched to a dedicated `TaskExecutor`, so a slow listener no longer blocks the
  MQTT network thread or other listeners.
- Several listeners may now share one topic filter; their subscription options are merged and the
  broker sees a single subscription per filter.
- The connection is established on context start rather than at bean creation, so an unreachable
  broker no longer prevents startup unless `mqtt.fail-fast` is set.
- Subscriptions are restored automatically after a reconnect.
- The build imports `spring-boot-dependencies` as a BOM rather than inheriting from
  `spring-boot-starter-parent`, so the published POM no longer imposes build conventions on
  consumers.

### Migrating from 1.x

Imports move; the type names themselves are unchanged.

| 1.x                                          | 2.0                                                             |
|----------------------------------------------|-----------------------------------------------------------------|
| `io.github.enesdurmus.mqtt.MqttListener`     | `io.github.enesdurmus.mqtt.annotation.MqttListener`             |
| `io.github.enesdurmus.mqtt.Topic`            | `io.github.enesdurmus.mqtt.annotation.Topic`                    |
| `io.github.enesdurmus.mqtt.MqttOperations`   | `io.github.enesdurmus.mqtt.core.MqttOperations`                 |
| `io.github.enesdurmus.mqtt.MqttTemplate`     | `io.github.enesdurmus.mqtt.core.MqttTemplate`                   |
| `io.github.enesdurmus.mqtt.MqttHeaders`      | `io.github.enesdurmus.mqtt.core.MqttHeaders`                    |
| `io.github.enesdurmus.mqtt.MqttProperties`   | `io.github.enesdurmus.mqtt.autoconfigure.MqttProperties`        |
| (listener containers, endpoints, registry)   | `io.github.enesdurmus.mqtt.listener.*`                          |
| (exceptions)                                 | `io.github.enesdurmus.mqtt` — unchanged                         |

Search and replace `import io.github.enesdurmus.mqtt.` and let the compiler point out the rest.
The `mqtt.*` configuration properties and the `@MqttListener` attributes are source-compatible
apart from the new MQTT 5 additions.

## [1.1.0]

Last MQTT 3.1.1 release. See the [release notes](https://github.com/enesdurmus/mqtt-spring-boot-starter/releases).

[2.0.0]: https://github.com/enesdurmus/mqtt-spring-boot-starter/releases/tag/v2.0.0
[1.1.0]: https://github.com/enesdurmus/mqtt-spring-boot-starter/releases/tag/v1.1.0
