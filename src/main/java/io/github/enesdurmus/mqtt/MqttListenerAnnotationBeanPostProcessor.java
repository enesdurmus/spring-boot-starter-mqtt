package io.github.enesdurmus.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.lang.Nullable;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers {@link MqttListener}-annotated methods and turns them into endpoints.
 *
 * <p>Detection runs against the target class so annotations survive AOP proxying, while the bean
 * itself is resolved lazily when the container starts, which means invocation always goes through
 * the proxy and {@code @Transactional}, {@code @Async} and friends apply as expected.
 */
public class MqttListenerAnnotationBeanPostProcessor
        implements BeanPostProcessor, SmartInitializingSingleton, BeanFactoryAware, Ordered {

    private static final Logger log = LoggerFactory.getLogger(MqttListenerAnnotationBeanPostProcessor.class);

    private final MqttListenerEndpointRegistrar registrar = new MqttListenerEndpointRegistrar();
    private final List<ListenerMethod> detected = new ArrayList<>();
    private final Map<String, Integer> idSequence = new HashMap<>();
    private final Set<Class<?>> nonAnnotatedClasses = ConcurrentHashMap.newKeySet();

    private ConfigurableBeanFactory beanFactory;
    private BeanExpressionResolver expressionResolver;
    private BeanExpressionContext expressionContext;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        if (nonAnnotatedClasses.contains(targetClass)) {
            return bean;
        }
        List<ListenerMethod> found = new ArrayList<>();
        ReflectionUtils.doWithMethods(targetClass, method -> {
            MqttListener annotation = AnnotatedElementUtils.findMergedAnnotation(method, MqttListener.class);
            if (annotation != null) {
                found.add(new ListenerMethod(beanName, method, annotation));
            }
        }, method -> !method.isBridge() && !method.isSynthetic());

        if (found.isEmpty()) {
            nonAnnotatedClasses.add(targetClass);
        } else {
            detected.addAll(found);
        }
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        MqttListenerEndpointRegistry registry = beanFactory.getBean(MqttListenerEndpointRegistry.class);
        registrar.setRegistry(registry);
        registrar.setContainerFactory(beanFactory.getBean(MqttListenerContainerFactory.class));

        for (MqttListenerConfigurer configurer : beanFactory.getBeanProvider(MqttListenerConfigurer.class)) {
            configurer.configureMqttListeners(registrar);
        }

        MessageHandlerMethodFactory handlerMethodFactory = registrar.getMessageHandlerMethodFactory() != null
                ? registrar.getMessageHandlerMethodFactory()
                : beanFactory.getBean(MqttAutoConfiguration.HANDLER_METHOD_FACTORY_BEAN,
                        MessageHandlerMethodFactory.class);

        for (ListenerMethod listener : detected) {
            registrar.registerEndpoint(createEndpoint(listener, handlerMethodFactory));
        }
        detected.clear();

        registrar.afterPropertiesSet();
        log.info("Registered {} MQTT listener(s)", registry.getListenerContainerIds().size());
    }

    private MqttListenerEndpoint createEndpoint(ListenerMethod listener, MessageHandlerMethodFactory factory) {
        MqttListener annotation = listener.annotation();
        List<String> topics = resolveTopics(annotation.topics(), listener.method());
        String id = resolveId(annotation.id(), listener);

        MqttSubscriptionOptions options = MqttSubscriptionOptions.builder()
                .qos(annotation.qos())
                .retainHandling(annotation.retainHandling())
                .retainAsPublished(annotation.retainAsPublished())
                .noLocal(annotation.noLocal())
                .build();

        return new MethodMqttListenerEndpoint(
                id,
                topics,
                options,
                resolveBoolean(annotation.autoStartup(), true),
                resolveErrorHandler(annotation.errorHandler()),
                () -> beanFactory.getBean(listener.beanName()),
                listener.method(),
                factory);
    }

    private List<String> resolveTopics(String[] topics, Method method) {
        Set<String> resolved = new LinkedHashSet<>();
        for (String topic : topics) {
            String value = resolveExpression(topic);
            if (!StringUtils.hasText(value)) {
                continue;
            }
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .forEach(resolved::add);
        }
        Assert.state(!resolved.isEmpty(),
                () -> "@MqttListener on " + method + " resolved to no topics. Check the placeholders in topics().");
        return List.copyOf(resolved);
    }

    private String resolveId(String configuredId, ListenerMethod listener) {
        if (StringUtils.hasText(configuredId)) {
            String resolved = resolveExpression(configuredId);
            Assert.state(StringUtils.hasText(resolved),
                    () -> "@MqttListener id on " + listener.method() + " resolved to an empty value");
            return resolved;
        }
        String base = listener.beanName() + "#" + listener.method().getName();
        int index = idSequence.merge(base, 0, (existing, ignored) -> existing + 1);
        return index == 0 ? base : base + "#" + index;
    }

    @Nullable
    private MqttListenerErrorHandler resolveErrorHandler(String beanName) {
        String resolved = resolveExpression(beanName);
        if (!StringUtils.hasText(resolved)) {
            return null;
        }
        return beanFactory.getBean(resolved, MqttListenerErrorHandler.class);
    }

    private boolean resolveBoolean(String value, boolean defaultValue) {
        String resolved = resolveExpression(value);
        return StringUtils.hasText(resolved) ? Boolean.parseBoolean(resolved) : defaultValue;
    }

    @Nullable
    private String resolveExpression(String value) {
        if (beanFactory == null || (!value.contains("${") && !value.contains("#{"))) {
            return value;
        }
        String resolved = beanFactory.resolveEmbeddedValue(value);
        if (resolved != null && expressionResolver != null) {
            Object evaluated = expressionResolver.evaluate(resolved, expressionContext);
            return evaluated != null ? evaluated.toString() : null;
        }
        return resolved;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        Assert.isInstanceOf(ConfigurableBeanFactory.class, beanFactory,
                "MqttListenerAnnotationBeanPostProcessor requires a ConfigurableBeanFactory");
        this.beanFactory = (ConfigurableBeanFactory) beanFactory;
        this.expressionResolver = this.beanFactory.getBeanExpressionResolver();
        this.expressionContext = new BeanExpressionContext(this.beanFactory, null);
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    private record ListenerMethod(String beanName, Method method, MqttListener annotation) {
    }
}
