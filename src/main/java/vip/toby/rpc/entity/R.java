package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import org.slf4j.helpers.MessageFormatter;

/**
 * R
 *
 * @author toby
 */
@Getter
public class R {

    private final RStatus status;
    private String message;
    private Object result;
    private int errorCode;

    private R(RStatus status, String message, Object result, int errorCode) {
        this.status = status;
        this.message = message;
        this.result = result;
        this.errorCode = errorCode;
    }

    public static R build(RStatus status) {
        return new R(status, status.getMessage(), null, 0);
    }

    public static R ok() {
        return build(RStatus.OK);
    }

    public static R fail() {
        return build(RStatus.FAIL);
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
        if (result != null) {
            this.result = result;
        }
        return this;
    }

    public R errorCode(int errorCode) {
        if (this.status == RStatus.FAIL) {
            this.errorCode = errorCode;
        }
        return this;
    }

    public JSONObject toJSON() {
        final JSONObject result = new JSONObject();
        result.put("status", this.status.getStatus());
        result.put("message", this.message);
        if (this.status == RStatus.OK) {
            result.put("result", this.result == null ? new JSONObject() : this.result);
        } else {
            result.put("errorCode", this.errorCode);
        }
        return result;
    }

}
