package example.probe;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import vip.toby.rpc.annotation.RpcDTO;

@Data
@RpcDTO
public class ProbeDTO {

    @NotBlank(message = "探针值不能为空")
    private String value;
}
