package vip.toby.demo.server;

import org.springframework.validation.annotation.Validated;
import vip.toby.demo.entity.DelayPlusDTO;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.R;
import vip.toby.rpc.entity.RpcType;

import java.util.concurrent.CompletableFuture;

@RpcServer(name = "delay-plus", type = RpcType.DELAY, threadNum = 1)
public class DelayPlusServer {
    public record Delivery(int sum, long receivedAt) {}
    public final CompletableFuture<Delivery> received = new CompletableFuture<>();

    @RpcServerMethod
    public R delayPlus(@Validated DelayPlusDTO dto) {
        received.complete(new Delivery(dto.getX() + dto.getY(), System.nanoTime()));
        return R.ok();
    }
}
