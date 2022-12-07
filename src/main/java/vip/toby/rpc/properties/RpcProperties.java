package vip.toby.rpc.properties;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "simple-rpc", ignoreInvalidFields = true)
public class RpcProperties {

    private Double clientSlowCallTimePercent;

    private Double serverSlowCallTimePercent;

    private String validatorFailFast;

    public double getClientSlowCallTimePercent() {
        if (this.clientSlowCallTimePercent == null || this.clientSlowCallTimePercent <= 0.0 || this.clientSlowCallTimePercent >= 1.0) {
            return 0.6;
        }
        return this.clientSlowCallTimePercent;
    }

    public double getServerSlowCallTimePercent() {
        if (this.serverSlowCallTimePercent == null || this.serverSlowCallTimePercent <= 0.0 || this.serverSlowCallTimePercent >= 1.0) {
            return 0.6;
        }
        return this.serverSlowCallTimePercent;
    }

    public String getValidatorFailFast() {
        if (StringUtils.isBlank(this.validatorFailFast)) {
            return "true";
        }
        return validatorFailFast;
    }

    public void setClientSlowCallTimePercent(Double clientSlowCallTimePercent) {
        this.clientSlowCallTimePercent = clientSlowCallTimePercent;
    }

    public void setServerSlowCallTimePercent(Double serverSlowCallTimePercent) {
        this.serverSlowCallTimePercent = serverSlowCallTimePercent;
    }

    public void setValidatorFailFast(String validatorFailFast) {
        this.validatorFailFast = validatorFailFast;
    }

}
