package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;

/**
 * RPC 状态码
 *
 * @author toby
 */
@Getter
public enum RpcStatus {

    OK(1, "ok"), // 成功
    FAIL(0, "fail"), // 失败
    NOT_FOUND(-1, "service not found"), // 不存在
    UNAVAILABLE(-2, "service unavailable"); // 超时, 服务不可用

    private final int status;
    private final String message;

    RpcStatus(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public static RpcStatus of(Integer status) {
        if (status == null) {
            return FAIL;
        }
        for (RpcStatus e : RpcStatus.values()) {
            if (e.status == status) {
                return e;
            }
        }
        return FAIL;
    }

    public JSONObject toJSON() {
        final JSONObject json = new JSONObject();
        json.put("status", this.status);
        json.put("message", this.message);
        return json;
    }

}
