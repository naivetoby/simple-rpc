package vip.toby.rpc.entity;

import com.alibaba.fastjson2.JSONObject;

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

    public static ServerResult buildSuccessResult(Object result) {
        return new ServerResult(OperateStatus.SUCCESS, OperateStatus.SUCCESS.getMessage(), result, 0);
    }

    public static ServerResult buildFailureMessage(String message) {
        return new ServerResult(OperateStatus.FAILURE, message, null, 0);
    }

    public ServerResult message(String message) {
        this.message = message;
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

    @Override
    public String toString() {
        JSONObject result = new JSONObject();
        result.put("status", this.operateStatus.getStatus());
        result.put("message", this.message);
        if (this.operateStatus == OperateStatus.SUCCESS) {
            result.put("result", this.result == null ? new JSONObject() : this.result);
        } else {
            result.put("errorCode", this.errorCode);
        }
        return result.toJSONString();
    }

}
