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

    private final RpcStatus status;
    private R result;

    private RpcResult(RpcStatus status, R result) {
        this.status = status;
        this.result = result;
    }

    public static RpcResult build(RpcStatus status) {
        return new RpcResult(status, null);
    }

    public static RpcResult okResult(R result) {
        return build(RpcStatus.OK).result(result);
    }

    public RpcResult result(R result) {
        this.result = result;
        return this;
    }

    public boolean isRStatusOk() {
        return this.getStatus() == RpcStatus.OK && this.getResult() != null && this.getResult()
                .getStatus() == RStatus.OK;
    }

    public Object getRResult() {
        if (isRStatusOk()) {
            return this.getResult().getResult();
        }
        return null;
    }

    @Override
    public String toString() {
        final JSONObject result = new JSONObject();
        result.put("status", this.status.toJSON());
        if (this.result != null) {
            result.put("result", this.result.toJSON());
        }
        return result.toJSONString();
    }

}
