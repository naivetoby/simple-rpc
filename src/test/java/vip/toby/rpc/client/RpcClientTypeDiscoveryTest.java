package vip.toby.rpc.client;

import com.demo.client.OtherSyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import vip.toby.demo.config.RpcClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Spring 在构建时应能识别客户端接口，无需先实例化代理工厂 */
class RpcClientTypeDiscoveryTest {

    @Test
    void scannedClientExposesInterfaceWithoutInitializingFactory() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            final ClassPathRpcClientScanner scanner = new ClassPathRpcClientScanner(context);
            scanner.registerFilters();
            scanner.scan(OtherSyncClient.class.getPackageName());
            assertClientType(context);
        }
    }

    @Test
    void manualClientExposesInterfaceWithoutInitializingFactory() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.getBeanFactory().registerSingleton("configurer", new RpcClientConfig());
            context.refresh();
            final RpcClientConfigurationSupport support = new RpcClientConfigurationSupport();
            support.setApplicationContext(context);
            support.postProcessBeanDefinitionRegistry(context);
            assertClientType(context);
        }
    }

    private void assertClientType(GenericApplicationContext context) {
        final String[] names = context.getBeanFactory().getBeanNamesForType(OtherSyncClient.class, true, false);
        assertEquals(1, names.length);
        assertFalse(context.getBeanFactory().containsSingleton(names[0]));
    }
}
