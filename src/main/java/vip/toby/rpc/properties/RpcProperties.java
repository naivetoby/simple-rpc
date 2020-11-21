package vip.toby.rpc.properties;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "simple-rpc", ignoreInvalidFields = true)
public class RpcProperties {

    private Integer clientSlowCallTime;

    private Integer serverSlowCallTime;

    private String validatorFailFast;

    public int getClientSlowCallTime() {
        if (this.clientSlowCallTime == null) {
            return 1000;
        }
        return this.clientSlowCallTime;
    }

    public int getServerSlowCallTime() {
        if (this.serverSlowCallTime == null) {
            return 1000;
        }
        return this.serverSlowCallTime;
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
