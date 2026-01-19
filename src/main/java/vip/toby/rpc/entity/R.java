package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.annotation.JSONField;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.helpers.MessageFormatter;

import javax.annotation.Nonnull;

/**
 * R
 *
 * @author toby
 */
public class R {

    private final ICode code;
    private String message;
    private Object result;

    private R(@Nonnull ICode code) {
        this.code = code;
    }

    public static R build(@Nonnull ICode code) {
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

    @JSONField(serialize = false)
    public boolean isOk() {
        return RCode.of(this.code.getCode()) == RCode.OK;
    }

    @JSONField(serialize = false)
    public int getCode() {
        return this.code.getCode();
    }

    @JSONField(serialize = false)
    public String getMessage() {
        if (StringUtils.isNotBlank(this.message)) {
            return this.message;
        }
        return this.code.getMessage();
    }

    @JSONField(serialize = false)
    public Object getResult() {
        if (this.isOk()) {
            return this.result == null ? new JSONObject() : this.result;
        }
        return null;
    }

    public JSONObject toJSONV1() {
        final JSONObject result = new JSONObject();
        result.put("status", this.isOk() ? 1 : 0);
        result.put("message", this.getMessage());
        if (this.isOk()) {
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
        if (this.isOk()) {
            result.put("data", this.getResult());
        }
        return result;
    }

}
