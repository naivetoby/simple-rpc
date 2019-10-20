package vip.toby.rpc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import vip.toby.rpc.entity.RpcType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * RpcClientProxy
 *
 * @author toby
 */
public class RpcClientProxy implements InvocationHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(RpcClientProxy.class);

    private final Class<?> rpcClientInterface;
    private final String rpcName;
    private final RpcType rpcType;
    private final RabbitTemplate sender;

    RpcClientProxy(Class<?> rpcClientInterface, String rpcName, RpcType rpcType, RabbitTemplate sender) {
        this.rpcClientInterface = rpcClientInterface;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
        this.sender = sender;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }
        LOGGER.info("调用成功: " + method.getName());
        return null;
    }

    @Override
    public String toString() {
        return this.rpcName + "_Client";
    }

}
