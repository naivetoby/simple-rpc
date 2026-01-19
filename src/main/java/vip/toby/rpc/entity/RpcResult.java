package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Getter;

import java.util.Objects;

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

    @JSONField(serialize = false)
    public boolean isStatusOk() {
        return this.status == RpcStatus.OK;
    }

    @JSONField(serialize = false)
    public String getMessage() {
        if (!isStatusOk()) {
            return this.status.getMessage();
        }
        return Objects.requireNonNullElseGet(this.result, R::fail).getMessage();
    }

    @JSONField(serialize = false)
    public boolean isRCodeOk() {
        return this.isStatusOk() && this.result != null && this.result.isCodeOk();
    }

    @JSONField(serialize = false)
    public Object getRResult() {
        if (isRCodeOk()) {
            return this.result.getResult();
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
