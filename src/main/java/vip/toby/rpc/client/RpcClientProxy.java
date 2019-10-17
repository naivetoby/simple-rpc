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
        // 调用object的方法
        if (method.getDeclaringClass() == Object.class) {
            String methodName = method.getName();
            if (TO_STRING.equals(methodName)) {
                return RpcClientProxy.this.toString();
            }
            if (HASH_CODE.equals(methodName)) {
                return rpcClientInterface.hashCode() * 13 + this.hashCode();
            }
            if (EQUALS.equals(methodName)) {
                return args[0] == proxy;
            }
            if (CLONE.equals(methodName)) {
                throw new CloneNotSupportedException("clone is not supported for jade dao.");
            }
            throw new UnsupportedOperationException(rpcClientInterface.getName() + "#" + method.getName());
        }


        return null;
    }

    @Override
    public String toString() {
        return rpcClientInterface.getName();
    }

}
