package vip.toby.rpc.entity;

import lombok.Getter;

/**
 * RStatus
 *
 * @author toby
 */
@Getter
public enum RStatus {

    OK(1, "ok"), // 成功
    FAIL(0, "fail"); // 失败

    private final int status;
    private final String message;

    RStatus(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public static RStatus of(Integer status) {
        if (status == null) {
            return FAIL;
        }
        for (RStatus e : RStatus.values()) {
            if (e.status == status) {
                return e;
            }
        }
        return FAIL;
    }

}
