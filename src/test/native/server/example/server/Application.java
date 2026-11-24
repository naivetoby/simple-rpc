package example.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import vip.toby.rpc.annotation.EnableSimpleRpc;
import vip.toby.rpc.entity.RpcMode;
import vip.toby.rpc.server.RpcServerHandlerInterceptor;

@EnableSimpleRpc(mode = RpcMode.RPC_SERVER_AUTO_SCANNER)
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    RpcServerHandlerInterceptor rpcServerHandlerInterceptor() {
        return new RpcServerHandlerInterceptor() {};
    }
}
