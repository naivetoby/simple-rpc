package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;

import java.util.Objects;

/**
 * RpcResult
 *
 * @author toby
 */
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

    public RpcResult result(R result) {
        this.result = result;
        return this;
    }

    @JSONField(serialize = false)
    public int getStatusCode() {
        return this.status.getCode();
    }

    @JSONField(serialize = false)
    public R getR() {
        return this.result;
    }

    @JSONField(serialize = false)
    public boolean isOk() {
        return this.status == RpcStatus.OK;
    }

    @JSONField(serialize = false)
    public int getCode() {
        if (isOk()) {
            return Objects.requireNonNullElseGet(this.result, R::fail).getCode();
        }
        return RCode.FAIL.getCode();
    }

    @JSONField(serialize = false)
    public String getMessage() {
        if (isOk()) {
            return Objects.requireNonNullElseGet(this.result, R::fail).getMessage();
        }
        return this.status.getMessage();
    }

    @JSONField(serialize = false)
    public Object getResult() {
        if (isOk()) {
            return Objects.requireNonNullElseGet(this.result, R::fail).getResult();
        }
        return null;
    }

    @JSONField(serialize = false)
    public <T> T getResult(Class<T> clazz) {
        return JSON.to(clazz, getResult());
    }

    @JSONField(serialize = false)
    public int getIntResult(int defaultValue) {
        final Object result = getResult();
        if (result instanceof Number) {
            return ((Number) result).intValue();
        }
        return defaultValue;
    }

    @JSONField(serialize = false)
    public long getLongResult(long defaultValue) {
        final Object result = getResult();
        if (result instanceof Number) {
            return ((Number) result).longValue();
        }
        return defaultValue;
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
