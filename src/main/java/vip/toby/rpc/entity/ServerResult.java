package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSONObject;
import org.slf4j.helpers.MessageFormatter;

/**
 * ServerResult
 *
 * @author toby
 */
public class ServerResult {

    private final OperateStatus operateStatus;
    private String message;
    private Object result;
    private int errorCode;

    private ServerResult(OperateStatus operateStatus, String message, Object result, int errorCode) {
        this.operateStatus = operateStatus;
        this.message = message;
        this.result = result;
        this.errorCode = errorCode;
    }

    public static ServerResult build(OperateStatus operateStatus) {
        return new ServerResult(operateStatus, operateStatus.getMessage(), null, 0);
    }

    public static ServerResult buildSuccess() {
        return build(OperateStatus.SUCCESS);
    }

    public static ServerResult buildFailure() {
        return build(OperateStatus.FAILURE);
    }

    public static ServerResult buildSuccessResult(Object result) {
        return buildSuccess().result(result);
    }

    public static ServerResult buildFailureMessage(String format, Object arg) {
        return buildFailure().message(format, arg);
    }

    public static ServerResult buildFailureMessage(String format, Object arg1, Object arg2) {
        return buildFailure().message(format, arg1, arg2);
    }

    public static ServerResult buildFailureMessage(String format, Object... arguments) {
        return buildFailure().message(format, arguments);
    }

    public ServerResult message(String format, Object arg) {
        this.message = MessageFormatter.format(format, arg).getMessage();
        return this;
    }

    public ServerResult message(String format, Object arg1, Object arg2) {
        this.message = MessageFormatter.format(format, arg1, arg2).getMessage();
        return this;
    }

    public ServerResult message(String format, Object... arguments) {
        this.message = MessageFormatter.format(format, arguments).getMessage();
        return this;
    }

    public ServerResult result(Object result) {
        if (result != null) {
            this.result = result;
        }
        return this;
    }

    public ServerResult errorCode(int errorCode) {
        if (this.operateStatus == OperateStatus.FAILURE) {
            this.errorCode = errorCode;
        }
        return this;
    }

    public OperateStatus getOperateStatus() {
        return operateStatus;
    }

    public String getMessage() {
        return message;
    }

    public Object getResult() {
        return result;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public JSONObject toJSON() {
        JSONObject result = new JSONObject();
        result.put("status", this.operateStatus.getStatus());
        result.put("message", this.message);
        if (this.operateStatus == OperateStatus.SUCCESS) {
            result.put("result", this.result == null ? new JSONObject() : this.result);
        } else {
            result.put("errorCode", this.errorCode);
        }
        return result;
    }

}
