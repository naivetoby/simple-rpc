package vip.toby.rpc.entity;

/**
 * RpcResult
 *
 * @author toby
 */
public class RpcResult {

    private ServerStatus serverStatus = ServerStatus.FAILURE;
    private ServerResult serverResult;

    public RpcResult(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    public RpcResult(ServerResult serverResult) {
        this.serverStatus = ServerStatus.SUCCESS;
        this.serverResult = serverResult;
    }

    public ServerStatus getServerStatus() {
        return this.serverStatus;
    }

    public ServerResult getServerResult() {
        return this.serverResult;
    }

    public boolean isOk() {
        return this.serverStatus == ServerStatus.SUCCESS && this.serverResult != null && this.serverResult.getOperateStatus() == OperateStatus.SUCCESS;
    }
}
