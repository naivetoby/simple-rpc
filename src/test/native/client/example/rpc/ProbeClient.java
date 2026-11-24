package example.rpc;

import example.probe.ProbeDTO;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.annotation.RpcClientMethod;
import vip.toby.rpc.entity.RpcResult;

@RpcClient(name = "native-smoke")
public interface ProbeClient {

    @RpcClientMethod("echo")
    RpcResult probe(ProbeDTO probeDTO);
}
