package vip.toby.rpc;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.core.type.AnnotationMetadata;
import vip.toby.rpc.annotation.EnableSimpleRpc;
import vip.toby.rpc.client.RpcClientConfigurerRegistrar;
import vip.toby.rpc.config.RpcDeferredImportSelector;
import vip.toby.rpc.entity.*;
import vip.toby.rpc.properties.RpcProperties;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityTest {
    @EnableSimpleRpc(mode = RpcMode.RPC_CLIENT_CONFIGURER)
    static class ManualClientOnly {}

    @Test void selectingManualClientsDoesNotEnableServerOrPackageScanner() {
        assertArrayEquals(new String[]{RpcClientConfigurerRegistrar.class.getName()},
                new RpcDeferredImportSelector().selectImports(AnnotationMetadata.introspect(ManualClientOnly.class)));
    }

    @Test void currentAndLegacyResultsKeepTheirWireKeys() {
        var ok = R.okResult(5);
        assertEquals(JSONObject.of("code", 0, "msg", "ok", "data", 5), ok.toJSON());
        assertEquals(JSONObject.of("status", 1, "message", "ok", "result", 5), ok.toJSONV1());
        var error = R.failMessage("bad {}", "input").detail("reason");
        assertEquals(JSONObject.of("code", 1, "msg", "bad input", "det", "reason"), error.toJSON());
        assertEquals(JSONObject.of("status", 0, "message", "bad input", "errorCode", 0), error.toJSONV1());
        assertEquals(RpcStatus.FAIL, RpcStatus.of(123456));
    }

    @Test void configurationFallbacksAndOverrides() {
        var properties = new RpcProperties();
        assertEquals("true", properties.getValidatorFailFast());
        properties.setServerSlowCallTimePercent(0.0);
        properties.setClientSlowCallTimePercent(1.0);
        assertEquals(0.6, properties.getServerSlowCallTimePercent());
        assertEquals(0.6, properties.getClientSlowCallTimePercent());
        properties.setClientSlowCallTimePercent(0.25);
        properties.setServerSlowCallTimePercent(0.8);
        properties.setValidatorFailFast("false");
        assertEquals(0.25, properties.getClientSlowCallTimePercent());
        assertEquals(0.8, properties.getServerSlowCallTimePercent());
        assertEquals("false", properties.getValidatorFailFast());
    }
}
