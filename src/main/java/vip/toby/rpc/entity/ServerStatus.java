package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;

/**
 * RPC 调用状态码
 *
 * @author toby
 */
@Getter
public enum ServerStatus {

    SUCCESS(1, "Call Success"), // 调用成功

    FAILURE(0, "Call Failure"), // 调用失败

    NOT_EXIST(-1, "Service Not Exist"), // 调用不存在

    UNAVAILABLE(-2, "Service Unavailable"); // 调用超时, 服务不可用

    private final int status;
    private final String message;

    ServerStatus(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public static ServerStatus getServerStatus(Integer status) {
        if (status == null) {
            return FAILURE;
        }
        for (ServerStatus e : ServerStatus.values()) {
            if (e.status == status) {
                return e;
            }
        }
        return FAILURE;
    }

    public JSONObject toJSON() {
        final JSONObject json = new JSONObject();
        json.put("status", this.status);
        json.put("message", this.message);
        return json;
    }

}
