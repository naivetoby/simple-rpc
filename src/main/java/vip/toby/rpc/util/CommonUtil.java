package vip.toby.rpc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * 工具类
 *
 * @author toby
 */
public class CommonUtil {

    private final static Logger LOGGER = LoggerFactory.getLogger(CommonUtil.class);

    /**
     * 获得UUID
     */
    public static String getUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
