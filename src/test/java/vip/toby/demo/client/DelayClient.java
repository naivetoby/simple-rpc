package vip.toby.demo.client;

import vip.toby.demo.entity.DelayPlusDTO;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.annotation.RpcClientMethod;
import vip.toby.rpc.entity.RpcType;

/**
 * DelayClient
 *
 * @author toby
 */
@RpcClient(name = "delay-plus", type = RpcType.DELAY, partitionNum = 4)
public interface DelayClient {

    @RpcClientMethod
    void delayPlus(DelayPlusDTO delayPlusDTO);

    @RpcClientMethod(value = "delayPlus", partitionKey = "x")
    void delayPlusByKey(DelayPlusDTO delayPlusDTO);

}
