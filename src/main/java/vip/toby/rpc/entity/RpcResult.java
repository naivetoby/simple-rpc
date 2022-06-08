package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * RpcResult
 *
 * @author toby
 */
public class RpcResult {

    private final ServerStatus serverStatus;
    private final ServerResult serverResult;

    public RpcResult(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
        this.serverResult = null;
    }

    public RpcResult(ServerResult serverResult) {
        this.serverStatus = ServerStatus.SUCCESS;
        this.serverResult = serverResult;
    }

    public ServerStatus getServerStatus() {
        return this.serverStatus;
    }

    public ServerResult getServerResult() {
        return this.serverResult;
    }

    @Override
    public String toString() {
        JSONObject result = new JSONObject();
        result.put("serverStatus", JSON.parse(this.serverStatus.toString()));
        if (this.serverResult != null) {
            result.put("serverResult", JSON.parse(this.serverResult.toString()));
        }
        return result.toJSONString();
    }

}
