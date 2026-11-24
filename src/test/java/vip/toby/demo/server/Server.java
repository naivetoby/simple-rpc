package vip.toby.demo.server;

import org.springframework.validation.annotation.Validated;
import vip.toby.demo.entity.BizCode;
import vip.toby.demo.entity.PlusDTO;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.R;
import vip.toby.rpc.entity.RpcType;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@RpcServer(name = "rpc-queue-name", type = {RpcType.SYNC, RpcType.ASYNC}, threadNum = 1, partitionNum = 4)
public class Server {
    public final BlockingQueue<Integer> normal = new LinkedBlockingQueue<>();
    public final BlockingQueue<Integer> partition = new LinkedBlockingQueue<>();

    @RpcServerMethod
    public R methodName1(@Validated PlusDTO dto) {
        return R.okResult(dto.getX() + dto.getY());
    }

    @RpcServerMethod("methodName2-alias")
    public R methodName2(@Validated PlusDTO dto) {
        normal.add(dto.getY());
        return R.build(BizCode.PLUS_ERROR).detail(Map.of("x", dto.getX()));
    }

    @RpcServerMethod
    public R methodName3(@Validated PlusDTO dto) {
        partition.add(dto.getY());
        return R.ok();
    }
}
