package vip.toby.rpc.util;

import vip.toby.rpc.entity.RpcType;

import java.util.Objects;

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

    public static String getRoutingKey(String rpcName, int partitionNum, Object partitionValue) {
        if (!partitionEnabled(partitionNum) || partitionValue == null) {
            return rpcName;
        }
        return getPartitionRoutingKey(rpcName, Math.floorMod(Objects.toString(partitionValue).hashCode(), partitionNum));
    }

    public static boolean partitionEnabled(int partitionNum) {
        return partitionNum >= 2;
    }

    public static String getPartitionRoutingKey(String rpcName, int partition) {
        return rpcName + "." + partition;
    }

}
