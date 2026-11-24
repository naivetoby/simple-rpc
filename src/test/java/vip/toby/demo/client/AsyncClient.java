package vip.toby.demo.client;

import vip.toby.demo.entity.PlusDTO;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.annotation.RpcClientMethod;
import vip.toby.rpc.entity.RpcType;

/**
 * AsyncClient
 *
 * @author toby
 */
@RpcClient(name = "rpc-queue-name", type = RpcType.ASYNC, partitionNum = 4)
public interface AsyncClient {

    @RpcClientMethod
    void methodName1(PlusDTO plusDTO);

    @RpcClientMethod("methodName2-alias")
    void methodName2(PlusDTO plusDTO);

    @RpcClientMethod(partitionKey = "x")
    void methodName3(PlusDTO plusDTO);

}
