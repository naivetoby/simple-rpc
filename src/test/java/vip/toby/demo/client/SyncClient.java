package vip.toby.demo.client;

import vip.toby.demo.entity.PlusDTO;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.annotation.RpcClientMethod;
import vip.toby.rpc.entity.RpcResult;
import vip.toby.rpc.entity.RpcType;

/**
 * SyncClient
 *
 * @author toby
 */
@RpcClient(name = "rpc-queue-name", type = RpcType.SYNC)
public interface SyncClient {

    @RpcClientMethod
    RpcResult methodName1(PlusDTO plusDTO);

    @RpcClientMethod("methodName2-alias")
    RpcResult methodName2(PlusDTO plusDTO);

}
