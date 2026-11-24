package vip.toby.demo.server;

import vip.toby.demo.entity.PlusDTO;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.R;
import vip.toby.rpc.entity.RpcType;

/**
 * @author toby
 */
@RpcServer(name = "rpc-queue-name-other", type = RpcType.SYNC)
public class OtherServer {

    @RpcServerMethod
    public R methodName3(PlusDTO plusDTO) {
        final int x = plusDTO.getX();
        final int y = plusDTO.getY();
        return R.okResult(x + y).message("计算成功, x: {}, y: {}", x, y);
    }

}
