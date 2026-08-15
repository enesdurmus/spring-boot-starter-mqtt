package io.github.enesdurmus.mqtt.autoconfigure;

import io.github.enesdurmus.mqtt.annotation.MqttListener;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Registers reflection hints for every {@link MqttListener}-annotated method. They are only ever
 * reached reflectively, so a native image would otherwise strip them as unreachable.
 */
class MqttListenerAotProcessor implements BeanFactoryInitializationAotProcessor {

    @Override
    @Nullable
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        Set<Method> methods = findListenerMethods(beanFactory);
        if (methods.isEmpty()) {
            return null;
        }
        return (generationContext, code) -> {
            RuntimeHints hints = generationContext.getRuntimeHints();
            for (Method method : methods) {
                hints.reflection()
                        .registerType(method.getDeclaringClass())
                        .registerMethod(method, ExecutableMode.INVOKE);
            }
        };
    }

    private Set<Method> findListenerMethods(ConfigurableListableBeanFactory beanFactory) {
        Set<Method> methods = new LinkedHashSet<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> type = beanFactory.getType(beanName, false);
            if (type == null) {
                continue;
            }
            ReflectionUtils.doWithMethods(type, methods::add,
                    method -> AnnotatedElementUtils.hasAnnotation(method, MqttListener.class));
        }
        return methods;
    }
}
