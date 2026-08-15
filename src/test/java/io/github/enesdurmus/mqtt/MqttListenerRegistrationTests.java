package io.github.enesdurmus.mqtt;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.messaging.handler.annotation.Payload;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Registration-time behaviour of {@link MqttListener}: no broker is contacted because the
 * containers are configured with {@code auto-startup=false}.
 */
class MqttListenerRegistrationTests {

    private static final String UNREACHABLE_BROKER = "mqtt.url=tcp://localhost:1";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, MqttAutoConfiguration.class))
            .withPropertyValues(UNREACHABLE_BROKER);

    @Test
    void registersOneContainerPerAnnotatedMethod() {
        runner.withUserConfiguration(TwoListeners.class).run(context -> {
            MqttListenerEndpointRegistry registry = context.getBean(MqttListenerEndpointRegistry.class);
            assertThat(registry.getListenerContainerIds()).containsExactlyInAnyOrder("first", "second");
        });
    }

    @Test
    void resolvesPlaceholdersInTopicsAndIds() {
        runner.withUserConfiguration(PlaceholderListener.class)
                .withPropertyValues("app.topic=resolved/topic", "app.listener-id=resolved-id")
                .run(context -> {
                    MqttListenerContainer container = context.getBean(MqttListenerEndpointRegistry.class)
                            .getListenerContainer("resolved-id");
                    assertThat(container.getTopics()).containsExactly("resolved/topic");
                });
    }

    @Test
    void splitsCommaSeparatedTopics() {
        runner.withUserConfiguration(CommaSeparatedListener.class).run(context -> {
            MqttListenerContainer container = context.getBean(MqttListenerEndpointRegistry.class)
                    .getListenerContainer("comma");
            assertThat(container.getTopics()).containsExactly("a/1", "a/2", "b/1");
        });
    }

    @Test
    void failsFastOnDuplicateListenerIds() {
        runner.withUserConfiguration(DuplicateIds.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already registered with id [duplicate]");
        });
    }

    @Test
    void failsFastWhenTopicsResolveToNothing() {
        runner.withUserConfiguration(UnresolvableTopic.class)
                .withPropertyValues("app.empty=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("resolved to no topics");
                });
    }

    @Test
    void carriesSubscriptionOptionsFromTheAnnotation() {
        runner.withUserConfiguration(RetainListener.class).run(context -> {
            MqttListenerEndpointRegistry registry = context.getBean(MqttListenerEndpointRegistry.class);
            assertThat(registry.getListenerContainerIds()).contains("retain-options");
        });
    }

    @Test
    void detectsListenersOnAopProxiedBeans() {
        runner.withUserConfiguration(ProxiedListener.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MqttListenerEndpointRegistry.class).getListenerContainerIds())
                    .contains("proxied");
            assertThat(org.springframework.aop.support.AopUtils.isAopProxy(context.getBean("advisedListener")))
                    .isTrue();
        });
    }

    @Test
    void autoStartupFalseLeavesTheContainerStopped() {
        runner.withUserConfiguration(TwoListeners.class).run(context -> {
            MqttListenerContainer container = context.getBean(MqttListenerEndpointRegistry.class)
                    .getListenerContainer("first");
            assertThat(container.isRunning()).isFalse();
            assertThat(container.isAutoStartup()).isFalse();
        });
    }

    @Test
    void programmaticEndpointsAreRegisteredThroughAConfigurer() {
        runner.withUserConfiguration(ProgrammaticListeners.class).run(context -> {
            MqttListenerEndpointRegistry registry = context.getBean(MqttListenerEndpointRegistry.class);
            assertThat(registry.getListenerContainerIds()).contains("sensor-a", "sensor-b");
            assertThat(registry.getListenerContainer("sensor-a").getTopics())
                    .containsExactly("sensors/a/data");
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoListeners {

        @MqttListener(id = "first", topics = "a", autoStartup = "false")
        void first(@Payload String payload) {
        }

        @MqttListener(id = "second", topics = "b", autoStartup = "false")
        void second(@Payload String payload) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PlaceholderListener {

        @MqttListener(id = "${app.listener-id}", topics = "${app.topic}", autoStartup = "false")
        void handle(@Payload String payload) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CommaSeparatedListener {

        @MqttListener(id = "comma", topics = {"a/1, a/2", "b/1"}, autoStartup = "false")
        void handle(@Payload String payload) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateIds {

        @MqttListener(id = "duplicate", topics = "a", autoStartup = "false")
        void first(@Payload String payload) {
        }

        @MqttListener(id = "duplicate", topics = "b", autoStartup = "false")
        void second(@Payload String payload) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UnresolvableTopic {

        @MqttListener(topics = "${app.empty}", autoStartup = "false")
        void handle(@Payload String payload) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RetainListener {

        @MqttListener(id = "retain-options", topics = "state/#", qos = 2, autoStartup = "false",
                retainHandling = RetainHandling.DO_NOT_SEND, retainAsPublished = true, noLocal = true)
        void handle(@Payload String payload) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProxiedListener {

        @Bean
        Object advisedListener() {
            ProxyFactory factory = new ProxyFactory(new AdvisedListener());
            factory.setProxyTargetClass(true);
            factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                AdvisedListener.INTERCEPTED.add(invocation.getMethod().getName());
                return invocation.proceed();
            });
            return factory.getProxy();
        }
    }

    static class AdvisedListener {

        static final List<String> INTERCEPTED = new java.util.concurrent.CopyOnWriteArrayList<>();

        @MqttListener(id = "proxied", topics = "tx/topic", autoStartup = "false")
        public void handle(@Payload String payload) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProgrammaticListeners {

        @Bean
        MqttListenerConfigurer configurer() {
            return registrar -> List.of("a", "b").forEach(name -> registrar.registerEndpoint(
                    SimpleMqttListenerEndpoint.builder("sensors/" + name + "/data")
                            .id("sensor-" + name)
                            .qos(1)
                            .autoStartup(false)
                            .messageHandler(message -> {
                            })
                            .build()));
        }
    }
}
