package demo;

import io.github.enesdurmus.mqtt.MqttListener;
import io.github.enesdurmus.mqtt.MqttTemplate;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final MqttTemplate mqttTemplate;

    public DemoController(MqttTemplate mqttTemplate) {
        this.mqttTemplate = mqttTemplate;
    }

    @MqttListener(topics = "test/#", qos = 1)
    public void test(Test message) throws MqttException {
        log.info("Received message: {}", message);
        mqttTemplate.publish("demo", message.b);
    }

    public record Test(int a, String b) {

    }
}
