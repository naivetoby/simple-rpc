package vip.toby.rpc.entity;

/**
 * 业务执行状态码
 */
public enum OperateStatus {

    // 调用成功
    SUCCESS(1, "操作成功"),
    // 调用失败
    FAILURE(0, "操作失败");

    private int status;
    private String message;

    OperateStatus(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public static OperateStatus getOperateStatus(Integer status) {
        if (status == null) {
            return FAILURE;
        }
        for (OperateStatus e : OperateStatus.values()) {
            if (e.status == status) {
                return e;
            }
        }
        return FAILURE;
    }
}
