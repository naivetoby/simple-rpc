package vip.toby.rpc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class CommonUtil {

    private final static Logger logger = LoggerFactory.getLogger(CommonUtil.class);

    // 获得UUID
    public static String getUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
