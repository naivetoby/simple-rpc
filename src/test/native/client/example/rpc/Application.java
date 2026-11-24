package example.rpc;

import example.probe.ProbeDTO;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import vip.toby.rpc.annotation.EnableSimpleRpc;
import vip.toby.rpc.entity.RCode;
import vip.toby.rpc.entity.RpcMode;
import vip.toby.rpc.entity.RpcResult;

import java.util.UUID;

@EnableSimpleRpc(mode = RpcMode.RPC_CLIENT_AUTO_SCANNER)
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        try (ConfigurableApplicationContext context = SpringApplication.run(Application.class, args)) {
            final ProbeClient client = context.getBean(ProbeClient.class);
            final ProbeDTO probeDTO = new ProbeDTO();
            probeDTO.setValue(UUID.randomUUID().toString());
            final RpcResult echo = client.probe(probeDTO);
            if (!echo.isOk() || echo.getCode() != RCode.OK.getCode() || !probeDTO.getValue().equals(echo.getResult())) {
                throw new IllegalStateException("回显不匹配: " + echo);
            }

            probeDTO.setValue(" ");
            final RpcResult invalid = client.probe(probeDTO);
            if (!invalid.isOk() || invalid.getCode() != RCode.FAIL.getCode() || !"探针值不能为空".equals(invalid.getMessage())) {
                throw new IllegalStateException("未收到预期的参数校验错误: " + invalid);
            }
            System.out.println("RPC_SMOKE_OK");
        }
    }
}
