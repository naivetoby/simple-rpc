package vip.toby.rpc.entity;

import lombok.Getter;

/**
 * 业务执行状态码
 *
 * @author toby
 */
@Getter
public enum OperateStatus {

    SUCCESS(1, "Success"), // 调用成功
    FAILURE(0, "Failure"); // 调用失败

    private final int status;
    private final String message;

    OperateStatus(int status, String message) {
        this.status = status;
        this.message = message;
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
