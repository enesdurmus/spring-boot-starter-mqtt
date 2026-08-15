package io.github.enesdurmus.mqtt.autoconfigure;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.jspecify.annotations.Nullable;

/**
 * Registers the Paho internals a native image cannot discover statically: the message bundles
 * Paho reads on every log call, and the {@code NetworkModuleFactory} it loads per URI scheme.
 */
class MqttRuntimeHints implements RuntimeHintsRegistrar {

    private static final String[] RESOURCE_BUNDLES = {
            "org.eclipse.paho.mqttv5.common.nls.logcat",
            "org.eclipse.paho.mqttv5.common.nls.messages",
            "org.eclipse.paho.mqttv5.client.internal.nls.logcat"
    };

    private static final String[] NETWORK_MODULE_FACTORIES = {
            "org.eclipse.paho.mqttv5.client.internal.TCPNetworkModuleFactory",
            "org.eclipse.paho.mqttv5.client.internal.SSLNetworkModuleFactory",
            "org.eclipse.paho.mqttv5.client.websocket.WebSocketNetworkModuleFactory",
            "org.eclipse.paho.mqttv5.client.websocket.WebSocketSecureNetworkModuleFactory"
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (String bundle : RESOURCE_BUNDLES) {
            hints.resources().registerResourceBundle(bundle);
        }
        hints.resources().registerPattern("org/eclipse/paho/mqttv5/client/logging/jsr47min.properties");

        for (String factory : NETWORK_MODULE_FACTORIES) {
            hints.reflection().registerTypeIfPresent(classLoader, factory,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }
}
