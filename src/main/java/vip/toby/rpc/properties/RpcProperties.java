package vip.toby.rpc.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "simple-rpc", ignoreInvalidFields = true)
public class RpcProperties {

    private Integer clientSlowCallTime;

    private Integer serverSlowCallTime;

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

    public void setClientSlowCallTime(Integer clientSlowCallTime) {
        this.clientSlowCallTime = clientSlowCallTime;
    }

    public void setServerSlowCallTime(Integer serverSlowCallTime) {
        this.serverSlowCallTime = serverSlowCallTime;
    }
}
