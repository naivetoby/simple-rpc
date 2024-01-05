package vip.toby.rpc.entity;

import lombok.Getter;

/**
 * 调用类型
 *
 * @author toby
 */
@Getter
public enum RpcType {

    SYNC(0, "SYNC"), // 同步
    ASYNC(1, "ASYNC"), // 异步
    DELAY(2, "DELAY"); // 延迟

    private final int type;
    private final String name;

    RpcType(int type, String name) {
        this.type = type;
        this.name = name;
    }

    public static RpcType getRpcType(Integer type) {
        if (type == null) {
            return SYNC;
        }
        for (RpcType e : RpcType.values()) {
            if (e.type == type) {
                return e;
            }
        }
        return SYNC;
    }

}
