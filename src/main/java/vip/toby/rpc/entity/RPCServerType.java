package vip.toby.rpc.entity;

public enum RPCServerType {

    // 同步
    SYNC(0, "SYNC"),
    // 异步
    ASYNC(1, "ASYNC");

    private int type;
    private String name;

    RPCServerType(int type, String name) {
        this.type = type;
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public static RPCServerType getRPCServerType(Integer type) {
        if (type == null) {
            return SYNC;
        }
        for (RPCServerType e : RPCServerType.values()) {
            if (e.type == type) {
                return e;
            }
        }
        return SYNC;
    }
}
