package vip.toby.rpc.entity;

import lombok.Getter;

@Getter
public enum RCode implements ICode {

    DUPLICATE(-1, "duplicate"), // 重复
    OK(0, "ok"), // 正常
    FAIL(1, "fail"); // 错误

    private final int code;
    private final String message;

    RCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static RCode of(Integer code) {
        if (code == null) {
            return FAIL;
        }
        for (RCode e : RCode.values()) {
            if (e.code == code) {
                return e;
            }
        }
        return FAIL;
    }

}
