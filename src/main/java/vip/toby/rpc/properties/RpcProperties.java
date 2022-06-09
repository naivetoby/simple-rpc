package vip.toby.rpc.properties;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;

@Configuration
@ConfigurationProperties(prefix = "simple-rpc", ignoreInvalidFields = true)
public class RpcProperties {

    private Integer clientSlowCallTime;

    private Integer serverSlowCallTime;

    private String validatorFailFast;

    public int getClientSlowCallTime() {
        return Objects.requireNonNullElse(this.clientSlowCallTime, 1000);
    }

    public int getServerSlowCallTime() {
        return Objects.requireNonNullElse(this.serverSlowCallTime, 1000);
    }

    public String getValidatorFailFast() {
        if (StringUtils.isBlank(this.validatorFailFast)) {
            return "true";
        }
        return validatorFailFast;
    }

    public void setClientSlowCallTime(Integer clientSlowCallTime) {
        this.clientSlowCallTime = clientSlowCallTime;
    }

    public void setServerSlowCallTime(Integer serverSlowCallTime) {
        this.serverSlowCallTime = serverSlowCallTime;
    }

    public void setValidatorFailFast(String validatorFailFast) {
        this.validatorFailFast = validatorFailFast;
    }

}
