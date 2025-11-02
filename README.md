# Spring Boot Starter MQTT

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

`spring-boot-starter-mqtt` provides auto-configuration for MQTT integration in Spring Boot applications. It simplifies publishing messages and subscribing to topics.

## Features

- **Annotation-Based Subscription:** Use `@MqttListener` to subscribe to MQTT topics.
- **Easy Publishing:** Use the `MqttTemplate` bean to publish messages.
- **Auto-Configuration:** Configure the MQTT connection using `application.properties` or `application.yml`.
- **JSON Support:** Automatic conversion of JSON payloads to Java objects.

## Getting Started

### Maven

```xml
<dependency>
    <groupId>io.github.enesdurmus</groupId>
    <artifactId>spring-boot-starter-mqtt</artifactId>
    <version>1.0.1</version>  <!-- Check for the latest version -->
</dependency>
```

## Configuration

Add the following properties to your `application.yml`:

```yaml
mqtt:
  url: tcp://localhost:1883
  client-id: spring-boot-app
  username: user
  password: password
```

### All Configuration Parameters

| Property                   | Description                                                                             | Default Value        |
|----------------------------|-----------------------------------------------------------------------------------------|----------------------|
| `mqtt.url`                 | The address of the MQTT broker to connect to (e.g., `tcp://localhost:1883`). (Required) | -                    |
| `mqtt.username`            | Username for broker authentication.                                                     | -                    |
| `mqtt.password`            | Password for broker authentication.                                                     | -                    |
| `mqtt.client-id`           | MQTT client ID.                                                                         | `spring-mqtt-client` |
| `mqtt.clean-session`       | `cleanSession` flag. If `false`, a persistent session is created.                       | `true`               |
| `mqtt.keep-alive-interval` | Keep-alive interval in seconds.                                                         | `60`                 |
| `mqtt.connection-timeout`  | Connection timeout in seconds.                                                          | `60`                 |
| `mqtt.concurrency`         | The number of threads in the thread pool that will process incoming messages.           | `3`                  |

## Usage

### Subscribing to Topics

Use the `@MqttListener` annotation on a method to subscribe to a topic.

```java
import io.github.enesdurmus.mqtt.MqttListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttListeners {

    private static final Logger log = LoggerFactory.getLogger(MqttListeners.class);

    // Listens to the 'sensor/temperature' topic
    @MqttListener(topics = "sensor/temperature")
    public void handleTemperatureReading(TemperatureReading reading) {
        log.info("New temperature reading: {}°C", reading.getTemperature());
    }

    // Listening to a wildcard topic and setting the QoS level
    @MqttListener(topics = "alerts/#", qos = 1)
    public void handleAllAlerts(String payload) {
        log.warn("Received alert: {}", payload);
    }

    public static class TemperatureReading {
        private double temperature;
        // Getters and setters
    }
}
```

### Publishing Messages

Inject the `MqttTemplate` bean to publish messages.

```java
import io.github.enesdurmus.mqtt.MqttTemplate;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final MqttTemplate mqttTemplate;

    public NotificationService(MqttTemplate mqttTemplate) {
        this.mqttTemplate = mqttTemplate;
    }

    public void sendNotification(String topic, String message) {
        try {
            mqttTemplate.publish(topic, message);
        } catch (MqttException e) {
            log.error("Failed to publish message to topic {}", topic, e);
        }
    }

    public void sendRetainedNotification(String topic, String message) {
        try {
            mqttTemplate.publish(topic, message, 1, true);
        } catch (MqttException e) {
            log.error("Failed to publish retained message to topic {}", topic, e);
        }
    }
}
```

## License

This project is licensed under the Apache 2.0 License.