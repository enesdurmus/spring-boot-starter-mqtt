# Spring Boot Starter MQTT

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

`spring-boot-starter-mqtt` is a starter package that allows you to easily add MQTT integration to your Spring Boot
applications. It uses the `spring-integration-mqtt` and `eclipse-paho-client` libraries in the background, providing a
simple annotation-based interface to subscribe to MQTT topics and publish messages.

## Features

- **Annotation-Based Subscription:** Easily listen to MQTT topics with the `@MqttListener` annotation.
- **Easy Publishing:** Simply publish messages via the `MqttTemplate` bean.
- **Auto-Configuration:** Comprehensive support for `application.properties` or `application.yml`.
- **JSON Support:** Automatic conversion of incoming JSON messages to Java objects.
- **Asynchronous Processing:** Processing of incoming messages in a configurable thread pool.

## Installation

### Maven

```xml

<dependency>
    <groupId>io.github.enesdurmus</groupId>
    <artifactId>spring-boot-starter-mqtt</artifactId>
    <version>1.0.0</version> <!-- Check for the latest version -->
</dependency>
```

## Configuration

You can add the following properties to your `application.yml` (or `application.properties`) file to configure the
project.

```yaml
mqtt:
  url: tcp://localhost:1883
  client-id: spring-boot-app
  username: user
  password: password
  clean-session: true
  keep-alive-interval: 60
  connection-timeout: 60
  concurrency: 5
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
| `mqtt.concurrency`         | The number of threads in the thread pool that will process incoming messages.           | `1`                  |

## Usage

### Listening for Messages (`@MqttListener`)

You can listen to specific topics by adding the `@MqttListener` annotation to a method. The starter automatically tries
to convert incoming JSON formatted messages to the object type in the method parameter.

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

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }
    }
}
```

### Publishing Messages (`MqttTemplate`)

To publish a message, you can inject the `MqttTemplate` bean and use the `publish` method.

```java
import io.github.enesdurmus.mqtt.MqttTemplate;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final MqttTemplate mqttTemplate;

    @Autowired
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
