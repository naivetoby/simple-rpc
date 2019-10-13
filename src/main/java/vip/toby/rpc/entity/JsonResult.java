package vip.toby.rpc.entity;

import com.alibaba.fastjson.JSONObject;

/**
 * 构建返回的json对象
 *
 * @author toby
 */
public class JsonResult {

    /**
     * 状态
     *
     * @param status
     * @return
     */
    public static JSONObject getInstance(OperateStatus status) {
        return getInstance(status, status.getMessage(), null, 0);
    }

    /**
     * 状态和提示消息
     *
     * @param status
     * @param message
     * @return
     */
    public static JSONObject getInstance(OperateStatus status, String message) {
        return getInstance(status, message, null, 0);
    }

    /**
     * 成功[提示消息和结果]
     *
     * @param message
     * @param result
     * @return
     */
    public static JSONObject getInstanceSuccess(String message, Object result) {
        return getInstance(OperateStatus.SUCCESS, message, result, 0);
    }

    /**
     * 成功[结果]
     *
     * @param result
     * @return
     */
    public static JSONObject getInstanceSuccess(Object result) {
        OperateStatus status = OperateStatus.SUCCESS;
        return getInstance(status, status.getMessage(), result, 0);
    }

    /**
     * 失败[提示消息和错误码]
     *
     * @param message
     * @param errorCode
     * @return
     */
    public static JSONObject getInstanceFailure(String message, int errorCode) {
        return getInstance(OperateStatus.FAILURE, message, null, errorCode);
    }

    /**
     * 失败[错误码]
     *
     * @param errorCode
     * @return
     */
    public static JSONObject getInstanceFailure(int errorCode) {
        OperateStatus status = OperateStatus.FAILURE;
        return getInstance(status, status.getMessage(), null, errorCode);
    }

    /**
     * 带状态和提示消息和结果
     *
     * @param status
     * @param message
     * @param result
     * @param errorCode
     * @return
     */
    private static JSONObject getInstance(OperateStatus status, String message, Object result, int errorCode) {
        JSONObject data = new JSONObject();
        data.put("status", status.getStatus());
        data.put("message", message);
        if (status == OperateStatus.SUCCESS) {
            data.put("result", result == null ? new JSONObject() : result);
        } else if (status == OperateStatus.FAILURE) {
            data.put("errorCode", Math.max(errorCode, 0));
        }
        return data;
    }
}