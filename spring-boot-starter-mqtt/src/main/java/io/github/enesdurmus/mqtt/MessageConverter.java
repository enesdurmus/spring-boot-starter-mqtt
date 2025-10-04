package io.github.enesdurmus.mqtt;

public interface MessageConverter {
    Object read(String payload, Class<?> targetType) throws Exception;

    byte[] write(Object payload) throws Exception;
}