package vip.toby.rpc.entity;

/**
 * HTTP请求错误码
 *
 * @author toby
 */
public enum ErrorCode {

    PARAMS_NOT_VALID(400, "Params Not Valid"), // 请求参数不正确
    AUTHORIZED_FAILED(401, "Authorized Failed"), // 登录已过期，请重新登录
    FORBIDDEN(403, "Forbidden"), // 禁止访问
    NOT_FOUND(404, "Not Found"), // 找不到
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"), // 请求方法不对
    INTERNAL_SERVER_ERROR(500, "Internal Server Error"), // 服务器内部错误
    SERVICE_UNAVAILABLE(503, "Service Unavailable"), // 服务不可用
    GATEWAY_TIMEOUT(504, "Gateway Timeout"); // 请求超时(服务器负载过高，未能及时处理请求)

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static ErrorCode getErrorCode(Integer code) {
        if (code == null) {
            return INTERNAL_SERVER_ERROR;
        }
        for (ErrorCode e : ErrorCode.values()) {
            if (e.code == code) {
                return e;
            }
        }
        return INTERNAL_SERVER_ERROR;
    }

}
