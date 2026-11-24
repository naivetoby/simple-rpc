package vip.toby.demo.server;

import org.springframework.validation.annotation.Validated;
import vip.toby.demo.entity.DelayPlusDTO;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.R;
import vip.toby.rpc.entity.RpcType;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@RpcServer(name = "delay-plus", type = RpcType.DELAY, threadNum = 1, partitionNum = 4)
public class DelayPlusServer {
    public record Delivery(int sum, long receivedAt) {}
    public final BlockingQueue<Delivery> received = new LinkedBlockingQueue<>();

    @RpcServerMethod
    public R delayPlus(@Validated DelayPlusDTO dto) {
        received.add(new Delivery(dto.getX() + dto.getY(), System.nanoTime()));
        return R.ok();
    }
}
