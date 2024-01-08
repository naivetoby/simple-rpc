package vip.toby.rpc.entity;

import lombok.Getter;

/**
 * RpcMode
 *
 * @author toby
 */
@Getter
public enum RpcMode {

    RPC_CLIENT_AUTO_SCANNER(0), // @RpcClient 自动扫描
    RPC_SERVER_AUTO_SCANNER(1), // @RpcServer 自动扫描
    RPC_CLIENT_CONFIGURER(2); // @RpcClient 手动配置

    private final int mode;

    RpcMode(int mode) {
        this.mode = mode;
    }

}
