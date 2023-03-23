package vip.toby.rpc.entity;

import lombok.Getter;

/**
 * RpcMode
 *
 * @author toby
 */
public enum RpcMode {

    RPC_CLIENT_AUTO_SCANNER(0), RPC_SERVER_AUTO_SCANNER(1), RPC_CLIENT_CONFIGURER(3);

    @Getter
    private final int mode;

    RpcMode(int mode) {
        this.mode = mode;
    }

}
