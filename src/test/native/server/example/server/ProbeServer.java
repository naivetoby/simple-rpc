package example.server;

import example.probe.ProbeDTO;
import jakarta.validation.Valid;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.R;

@RpcServer(name = "native-smoke", threadNum = 1)
public class ProbeServer {

    @RpcServerMethod
    public R echo(@Valid ProbeDTO probeDTO) {
        return R.okResult(probeDTO.getValue());
    }
}
