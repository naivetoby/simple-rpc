package vip.toby.demo.config;

import com.demo.client.OtherSyncClient;
import org.springframework.stereotype.Component;
import vip.toby.rpc.client.RpcClientConfigurer;
import vip.toby.rpc.client.RpcClientRegistry;

@Component
public class RpcClientConfig implements RpcClientConfigurer {

    @Override
    public void addRpcClientRegistry(RpcClientRegistry rpcClientRegistry) {
        // 如果 @RpcClient 不在当前项目，可以手动配置
        rpcClientRegistry.addRegistration(OtherSyncClient.class);
    }

}
