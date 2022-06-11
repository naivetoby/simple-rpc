package vip.toby.rpc.config;

import com.alibaba.fastjson2.JSON;
import vip.toby.rpc.annotation.EnableSimpleRpc;
import vip.toby.rpc.entity.RpcResult;
import vip.toby.rpc.entity.ServerResult;

/**
 * SimpleRpcAutoConfiguration
 *
 * @author toby
 */
@EnableSimpleRpc
public class SimpleRpcAutoConfiguration {

    static {
        JSON.parse(JSON.toJSONString(ServerResult.buildSuccess().toString()));
        JSON.parse(JSON.toJSONString(RpcResult.buildSuccess().toString()));
    }

}
