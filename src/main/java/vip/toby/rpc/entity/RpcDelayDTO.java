package vip.toby.rpc.entity;

import lombok.Data;
import vip.toby.rpc.annotation.RpcDTO;

@Data
@RpcDTO
public class RpcDelayDTO {

    // 延迟时间(毫秒)
    private int delay;

}
