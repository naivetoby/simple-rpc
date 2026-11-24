package com.demo.client;

import vip.toby.demo.entity.PlusDTO;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.annotation.RpcClientMethod;
import vip.toby.rpc.entity.RpcResult;
import vip.toby.rpc.entity.RpcType;

/**
 * OtherSyncClient
 *
 * @author toby
 */
@RpcClient(name = "rpc-queue-name-other", type = RpcType.SYNC)
public interface OtherSyncClient {

    @RpcClientMethod
    RpcResult methodName3(PlusDTO plusDTO);

}
