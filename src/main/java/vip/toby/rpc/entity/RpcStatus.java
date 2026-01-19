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

    OK(200, "ok"), // 成功
    FAIL(500, "fail"), // 失败
    NOT_FOUND(404, "service not found"), // 不存在
    UNAVAILABLE(504, "service unavailable"); // 超时, 服务不可用

    private final int code;
    private final String message;

    RpcStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static RpcStatus of(Integer code) {
        if (code == null) {
            return FAIL;
        }
        for (RpcStatus e : RpcStatus.values()) {
            if (e.code == code) {
                return e;
            }
        }
        return FAIL;
    }

    public JSONObject toJSON() {
        final JSONObject json = new JSONObject();
        json.put("code", this.code);
        json.put("message", this.message);
        return json;
    }

}
