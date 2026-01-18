package vip.toby.rpc.util;

import vip.toby.rpc.entity.RpcType;

public class RpcUtil {

    public static String getRpcName(RpcType rpcType, String value) {
        switch (rpcType) {
            case ASYNC -> {
                return value.concat(".async");
            }
            case DELAY -> {
                return value.concat(".delay");
            }
            default -> {
                return value.concat(".sync");
            }
        }
    }

}
