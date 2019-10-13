package vip.toby.rpc.entity;

/**
 * 服务器调用类型
 *
 * @author toby
 */

public enum RpcServerType {

    // 同步
    SYNC(0, "SYNC"),
    // 异步
    ASYNC(1, "ASYNC");

    private int type;
    private String name;

    RpcServerType(int type, String name) {
        this.type = type;
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public static RpcServerType getRpcServerType(Integer type) {
        if (type == null) {
            return SYNC;
        }
        for (RpcServerType e : RpcServerType.values()) {
            if (e.type == type) {
                return e;
            }
        }
        return SYNC;
    }
}
