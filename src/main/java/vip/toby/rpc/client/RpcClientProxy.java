package vip.toby.rpc.client;

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

    private final static String TO_STRING = "toString";
    private final static String HASH_CODE = "hashCode";
    private final static String EQUALS = "equals";
    private final static String CLONE = "clone";

    private final Class<?> rpcClientInterface;
    private final RabbitTemplate sender;
    private final RpcType rpcType;

    public RpcClientProxy(Class<?> rpcClientInterface, RpcType rpcType, RabbitTemplate sender) {
        this.rpcClientInterface = rpcClientInterface;
        this.rpcType = rpcType;
        this.sender = sender;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (Object.class.equals(method.getDeclaringClass())) {
            return method.invoke(this, args);
        }


        return null;
    }

    @Override
    public String toString() {
        return rpcClientInterface.getName();
    }

}
