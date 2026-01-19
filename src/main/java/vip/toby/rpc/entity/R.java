package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.helpers.MessageFormatter;

/**
 * R
 *
 * @author toby
 */
public class R {

    private final ICode code;
    private String message;
    private Object result;

    @JSONField(serialize = false)
    public int getCode() {
        return this.code.getCode();
    }

    @JSONField(serialize = false)
    public String getMessage() {
        if (StringUtils.isBlank(this.message)) {
            return this.code.getMessage();
        }
        return this.message;
    }

    @JSONField(serialize = false)
    public Object getResult() {
        if (this.isCodeOk()) {
            return this.result == null ? new JSONObject() : this.result;
        }
        return null;
    }

    private R(@NonNull ICode code) {
        this.code = code;
    }

    public static R build(@NonNull ICode code) {
        return new R(code);
    }

    public static R ok() {
        return build(RCode.OK);
    }

    public static R fail() {
        return build(RCode.FAIL);
    }

    public static R okResult(Object result) {
        return ok().result(result);
    }

    public static R failMessage(String format, Object arg) {
        return fail().message(format, arg);
    }

    public static R failMessage(String format, Object arg1, Object arg2) {
        return fail().message(format, arg1, arg2);
    }

    public static R failMessage(String format, Object... arguments) {
        return fail().message(format, arguments);
    }

    public R message(String format, Object arg) {
        this.message = MessageFormatter.format(format, arg).getMessage();
        return this;
    }

    public R message(String format, Object arg1, Object arg2) {
        this.message = MessageFormatter.format(format, arg1, arg2).getMessage();
        return this;
    }

    public R message(String format, Object... arguments) {
        this.message = MessageFormatter.format(format, arguments).getMessage();
        return this;
    }

    public R result(Object result) {
        this.result = result;
        return this;
    }

    public boolean isCodeOk() {
        return RCode.of(this.code.getCode()) == RCode.OK;
    }

    public JSONObject toJSONV1() {
        final JSONObject result = new JSONObject();
        result.put("status", this.isCodeOk() ? 1 : 0);
        result.put("message", this.getMessage());
        if (this.isCodeOk()) {
            result.put("result", this.getResult());
        } else {
            result.put("errorCode", this.getCode());
        }
        return result;
    }

    public JSONObject toJSON() {
        final JSONObject result = new JSONObject();
        result.put("code", this.getCode());
        result.put("msg", this.getMessage());
        if (this.isCodeOk()) {
            result.put("data", this.getResult());
        }
        return result;
    }

}
