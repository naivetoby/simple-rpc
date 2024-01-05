package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;

/**
 * RpcResult
 *
 * @author toby
 */
@Getter
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

    @Override
    public String toString() {
        final JSONObject result = new JSONObject();
        result.put("serverStatus", this.serverStatus.toJSON());
        if (this.serverResult != null) {
            result.put("serverResult", this.serverResult.toJSON());
        }
        return result.toString();
    }

}
