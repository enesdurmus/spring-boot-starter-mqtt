/**
 * Building blocks of the messaging pipeline, exposed so that anything replacing a default can
 * start from the same pieces the auto-configuration uses:
 * {@code HandlerMethodArgumentResolver} implementations that bind
 * {@link io.github.enesdurmus.mqtt.annotation.Topic @Topic} and
 * {@link io.github.enesdurmus.mqtt.core.MqttMessageHeaderAccessor} parameters, and the message
 * converters in {@link io.github.enesdurmus.mqtt.support.MqttMessageConverters} /
 * {@link io.github.enesdurmus.mqtt.support.JacksonMqttMessageConverterFactory}.
 */
@NullMarked
package io.github.enesdurmus.mqtt.support;

import org.jspecify.annotations.NullMarked;
