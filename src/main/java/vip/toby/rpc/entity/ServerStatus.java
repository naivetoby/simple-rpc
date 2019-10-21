package vip.toby.rpc.entity;

/**
 * rpc调用状态码
 *
 * @author toby
 */
public enum ServerStatus {

    // 调用成功
    SUCCESS(1, "调用成功"),
    // 调用失败
    FAILURE(0, "调用失败"),
    // 调用不存在
    NOT_EXIST(-1, "调用不存在"),
    // 调用超时, 服务不可用
    UNAVAILABLE(-2, "调用超时, 服务不可用");

    private int status;
    private String message;

    ServerStatus(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public static ServerStatus getServerStatus(Integer status) {
        if (status == null) {
            return FAILURE;
        }
        for (ServerStatus e : ServerStatus.values()) {
            if (e.status == status) {
                return e;
            }
        }
        return FAILURE;
    }
}
