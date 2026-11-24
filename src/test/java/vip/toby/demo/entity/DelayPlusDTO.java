package vip.toby.demo.entity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import vip.toby.rpc.annotation.RpcDTO;
import vip.toby.rpc.entity.RpcDelayDTO;

@Data
@EqualsAndHashCode(callSuper = false)
@RpcDTO
public class DelayPlusDTO extends RpcDelayDTO {

    @NotNull
    private Long createTime;

    @NotNull
    @Min(1)
    private Integer x;

    private int y;

}
