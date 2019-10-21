package vip.toby.rpc.entity;

import com.alibaba.fastjson.JSONObject;

/**
 * RpcResult
 *
 * @author toby
 */
public class RpcResult {

    private ServerStatus serverStatus;
    private ServerResult serverResult;

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
        result.put("serverStatus", this.serverStatus);
        if (this.serverStatus != null) {
            result.put("serverResult", this.serverResult);
        }
        return result.toJSONString();
    }
}
