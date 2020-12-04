package vip.toby.rpc.config;

import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializeConfig;
import org.springframework.context.annotation.Configuration;
import vip.toby.rpc.annotation.EnableSimpleRpc;

/**
 * SimpleRpcAutoConfiguration
 *
 * @author toby
 */
@Configuration
@EnableSimpleRpc
public class SimpleRpcAutoConfiguration {

    // 预热 fastjson
    static {
        new ParserConfig();
        new SerializeConfig();
    }

}
