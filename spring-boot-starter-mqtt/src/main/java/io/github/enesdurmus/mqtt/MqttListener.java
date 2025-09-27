package io.github.enesdurmus.mqtt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MqttListener {
    String[] topics();

    int qos() default 0;

    boolean injectContext() default false;
}