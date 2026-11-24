package vip.toby.demo.entity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import vip.toby.rpc.annotation.RpcDTO;

@Data
@RpcDTO
public class PlusDTO {

    @NotNull
    @Min(1)
    private Integer x;

    private int y;

}
