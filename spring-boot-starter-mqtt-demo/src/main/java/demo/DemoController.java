package demo;

import io.github.enesdurmus.mqtt.MqttListener;
import io.github.enesdurmus.mqtt.MqttTemplate;
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

    @MqttListener(topic = "test")
    public void test(String message) {
        log.info("message");
        mqttTemplate.publish("alo", message);
    }
}
