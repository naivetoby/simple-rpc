package vip.toby.demo.entity;

import lombok.Getter;
import vip.toby.rpc.entity.ICode;

/**
 * 业务状态码
 */
@Getter
public enum BizCode implements ICode {

    PLUS_ERROR(1000, "计算错误"); // 计算错误

    private final int code;
    private final String message;

    BizCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
