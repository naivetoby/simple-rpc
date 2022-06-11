package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSONObject;

/**
 * RpcResult
 *
 * @author toby
 */
public class RpcResult {

    private final ServerStatus serverStatus;
    private ServerResult serverResult;

    private RpcResult(ServerStatus serverStatus, ServerResult serverResult) {
        this.serverStatus = serverStatus;
        this.serverResult = serverResult;
    }

    public static RpcResult build(ServerStatus serverStatus) {
        return new RpcResult(serverStatus, null);
    }

    public static RpcResult buildSuccess() {
        return build(ServerStatus.SUCCESS);
    }

    public static RpcResult buildFailure() {
        return build(ServerStatus.FAILURE);
    }

    public static RpcResult buildNotExist() {
        return build(ServerStatus.NOT_EXIST);
    }

    public static RpcResult buildUnavailable() {
        return build(ServerStatus.UNAVAILABLE);
    }

    public RpcResult result(ServerResult serverResult) {
        this.serverResult = serverResult;
        return this;
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
        result.put("serverStatus", this.serverStatus.toJSON());
        if (this.serverResult != null) {
            result.put("serverResult", this.serverResult.toJSON());
        }
        return result.toString();
    }

}
