package vip.toby.demo.entity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import vip.toby.rpc.annotation.RpcDTO;
import vip.toby.rpc.entity.RpcDelayDTO;

@Data
@RpcDTO
public class DelayPlusDTO extends RpcDelayDTO {

    @NotNull
    private Long createTime;

    @NotNull
    @Min(1)
    private Integer x;

    private int y;

}
