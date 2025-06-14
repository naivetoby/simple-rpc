package vip.toby.rpc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(RpcRuntimeHints.class)
public class RpcAotConfiguration {

    @Bean
    static RpcAotBeanProcessor rpcClientAotBeanProcessor() {
        return new RpcAotBeanProcessor();
    }

}

