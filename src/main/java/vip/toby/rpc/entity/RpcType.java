package vip.toby.rpc.entity;

/**
 * 调用类型
 *
 * @author toby
 */
public enum RpcType {

    // 同步
    SYNC(0, "SYNC", "同步"),
    // 异步
    ASYNC(1, "ASYNC", "异步");

    private int type;
    private String value;
    private String name;

    RpcType(int type, String value, String name) {
        this.type = type;
        this.value = value;
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public String getName() {
        return name;
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
